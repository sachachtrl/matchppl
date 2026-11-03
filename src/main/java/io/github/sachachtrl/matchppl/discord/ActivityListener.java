package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.ActivityService;

import java.time.Instant;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ActivityListener.class);

    private final ActivityService activityService;

    public ActivityListener(ActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }
        long guildId = event.getGuild().getIdLong();
        long authorId = event.getAuthor().getIdLong();
        Instant instant = event.getMessage().getTimeCreated().toInstant();
        this.activityService.recordMessage(guildId, authorId, instant);
        log.debug("Message counted : guild={}, user={}, at={}", guildId, authorId, instant);
    }
}
