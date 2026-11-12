package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.RoomService;

import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(RoomListener.class);

    private final RoomService roomService;
    private final RoomManager roomManager;
    private final GuildConfigService guildConfigService;

    public RoomListener(RoomService roomService, RoomManager roomManager, GuildConfigService guildConfigService) {
        this.roomService = roomService;
        this.roomManager = roomManager;
        this.guildConfigService = guildConfigService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(RoomViews.PREFIX)) {
            return;
        }
        Locale locale = event.getGuild() == null
                ? Messages.DEFAULT_LOCALE
                : this.guildConfigService.language(event.getGuild().getIdLong());
        if (id.equals(RoomViews.END_KEEP)) {
            event.editComponents(Container.of(TextDisplay.of(Messages.get(locale, "room.end.kept")))
                            .withAccentColor(Cards.MUTED_COLOR))
                    .useComponentsV2()
                    .queue();
            return;
        }
        String[] parts = id.substring(RoomViews.PREFIX.length()).split(":");
        if (parts.length != 2) {
            return;
        }
        long roomId = Long.parseLong(parts[1]);
        long userId = event.getUser().getIdLong();
        switch (parts[0]) {
            case RoomViews.END -> {
                Optional<Room> room = this.roomService.findRoom(roomId)
                        .filter(found -> found.open() && found.involves(userId));
                if (room.isEmpty()) {
                    this.replyUnavailable(event, locale);
                    return;
                }
                event.replyComponents(RoomViews.endConfirmation(room.get(), userId, locale))
                        .useComponentsV2()
                        .setEphemeral(true)
                        .queue();
            }
            case RoomViews.END_CONFIRM -> {
                Optional<Room> ended = this.roomService.endNow(roomId, userId);
                if (ended.isEmpty()) {
                    event.editComponents(Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                            Messages.get(locale, "room.unavailable"))).useComponentsV2().queue();
                    return;
                }
                event.deferEdit().queue();
                this.roomManager.remove(event.getJDA(), ended.get());
                log.info("Room ended by a member: room={}, user={}", roomId, userId);
            }
            default -> {
            }
        }
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        this.roomService.closeByChannel(event.getChannel().getIdLong())
                .ifPresent(room -> this.roomManager.remove(event.getJDA(), room));
    }

    private void replyUnavailable(ButtonInteractionEvent event, Locale locale) {
        event.replyComponents(Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                        Messages.get(locale, "room.unavailable")))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }
}
