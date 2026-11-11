package io.github.sachachtrl.matchppl.discord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteractionTimer {
    private static final Logger log = LoggerFactory.getLogger(InteractionTimer.class);

    private static final Duration WARNING_THRESHOLD = Duration.ofSeconds(1);

    private final Clock clock;
    private final Map<Long, Instant> arrivals = new ConcurrentHashMap<>();

    public InteractionTimer(Clock clock) {
        this.clock = clock;
    }

    public ListenerAdapter arrivalListener() {
        return new ListenerAdapter() {
            @Override
            public void onGenericInteractionCreate(GenericInteractionCreateEvent event) {
                InteractionTimer.this.arrivals.put(event.getIdLong(), InteractionTimer.this.clock.instant());
            }
        };
    }

    public ListenerAdapter completionListener() {
        return new ListenerAdapter() {
            @Override
            public void onGenericInteractionCreate(GenericInteractionCreateEvent event) {
                InteractionTimer.this.report(event);
            }
        };
    }

    private void report(GenericInteractionCreateEvent event) {
        Instant arrival = this.arrivals.remove(event.getIdLong());
        if (arrival == null) {
            return;
        }
        Instant created = event.getTimeCreated().toInstant();
        Duration delivery = Duration.between(created, arrival);
        Duration handling = Duration.between(arrival, this.clock.instant());
        Duration total = delivery.plus(handling);
        if (total.compareTo(WARNING_THRESHOLD) > 0) {
            log.warn("Slow interaction: interaction={}, deliveryMs={}, handlingMs={}, totalMs={}",
                    describe(event), delivery.toMillis(), handling.toMillis(), total.toMillis());
        } else {
            log.debug("Interaction timing: interaction={}, deliveryMs={}, handlingMs={}",
                    describe(event), delivery.toMillis(), handling.toMillis());
        }
    }

    private static String describe(GenericInteractionCreateEvent event) {
        return switch (event.getInteraction()) {
            case CommandInteraction command -> "/" + command.getName();
            case ComponentInteraction component -> component.getComponentId();
            case ModalInteraction modal -> modal.getModalId();
            default -> event.getType().name();
        };
    }
}
