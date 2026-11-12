package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Room;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

final class RoomViews {
    static final String PREFIX = "room:";
    static final String END = "end";
    static final String END_CONFIRM = "endconfirm";
    static final String END_KEEP = PREFIX + "endkeep";
    static final String REPORT_PREFIX = "report:";
    static final String REPORT_ROOM = "room";
    static final String REPORT_PROPOSAL = "proposal";

    private RoomViews() {
    }

    static String buttonId(String action, long roomId) {
        return PREFIX + action + ":" + roomId;
    }

    static String reportId(String kind, long id) {
        return REPORT_PREFIX + kind + ":" + id;
    }

    static Container welcome(Room room, long voiceChannelId, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.get(locale, "room.welcome.title")),
                TextDisplay.of(Messages.format(locale, "room.welcome.body", mention(room.firstUserId()),
                        mention(room.secondUserId()), "<#" + voiceChannelId + ">")),
                TextDisplay.of("-# " + Messages.format(locale, "room.welcome.lifetime", relative(room))),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("-# " + Messages.get(locale, "room.welcome.report")),
                ActionRow.of(
                        Button.danger(buttonId(END, room.id()), Messages.get(locale, "room.button.end")),
                        Button.secondary(reportId(REPORT_ROOM, room.id()), Messages.get(locale, "report.button"))))
                .withAccentColor(Cards.SUCCESS_COLOR);
    }

    static Container reminder(Room room, Locale locale) {
        return Container.of(TextDisplay.of(Messages.format(locale, "room.reminder", mention(room.firstUserId()),
                        mention(room.secondUserId()), relative(room))))
                .withAccentColor(Cards.WARNING_COLOR);
    }

    static Container endConfirmation(Room room, long viewerId, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.get(locale, "room.end.confirm.title") + "\n"
                        + Messages.format(locale, "room.end.confirm", mention(room.otherUserId(viewerId)))),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.danger(buttonId(END_CONFIRM, room.id()),
                                Messages.get(locale, "room.end.confirm.button")),
                        Button.secondary(END_KEEP, Messages.get(locale, "room.end.keep"))))
                .withAccentColor(Cards.WARNING_COLOR);
    }

    static Container report(long reporterId, long otherId, String context, String reason,
            Collection<Long> adminRoleIds, Locale locale) {
        String admins = adminRoleIds.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
        String header = "## " + Messages.get(locale, "report.log.title")
                + (admins.isEmpty() ? "" : "\n" + admins);
        return Container.of(
                TextDisplay.of(header),
                TextDisplay.of(Messages.format(locale, "report.log.body", mention(reporterId), mention(otherId))),
                TextDisplay.of("-# " + context),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("**" + Messages.get(locale, "report.log.reason") + "**\n>>> " + reason))
                .withAccentColor(Cards.WARNING_COLOR);
    }

    private static String mention(long userId) {
        return "<@" + userId + ">";
    }

    private static String relative(Room room) {
        return "<t:" + room.deleteAt().getEpochSecond() + ":R>";
    }
}
