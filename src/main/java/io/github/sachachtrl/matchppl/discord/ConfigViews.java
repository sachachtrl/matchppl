package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.GuildSettings;

import java.time.Duration;
import java.util.Locale;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.ChannelType;

final class ConfigViews {
    static final String PREFIX = "config:";
    static final String RESPONSE_DELAY = PREFIX + "responsedelay";
    static final String MATCHING_INTERVAL = PREFIX + "matchinginterval";
    static final String ROOMS_ON = PREFIX + "rooms:on";
    static final String ROOMS_OFF = PREFIX + "rooms:off";
    static final String ROOM_LIFETIME = PREFIX + "roomlifetime";
    static final String ROOM_PREFIX = PREFIX + "roomprefix";
    static final String CATEGORY = PREFIX + "category";
    static final String LOG_CHANNEL = PREFIX + "logchannel";
    static final String STOP_ALL = PREFIX + "stopall";
    static final String STOP_ALL_CONFIRM = PREFIX + "stopall:confirm";
    static final String BACK = PREFIX + "back";

    private ConfigViews() {
    }

    static Container card(GuildSettings settings, int searchingCount, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.get(locale, "config.title") + "\n-# "
                        + Messages.get(locale, "config.subtitle")),
                Separator.createDivider(Separator.Spacing.LARGE),
                TextDisplay.of("### " + Messages.get(locale, "config.section.proposals")),
                editSection(RESPONSE_DELAY, "config.response_delay", settings.responseDelay(), locale),
                editSection(MATCHING_INTERVAL, "config.matching_interval", settings.matchingInterval(), locale),
                Separator.createDivider(Separator.Spacing.LARGE),
                TextDisplay.of("### " + Messages.get(locale, "config.section.channels")),
                roomsSection(settings.roomsEnabled(), locale),
                editSection(ROOM_LIFETIME, "config.room_lifetime", settings.roomLifetime(), locale),
                Section.of(
                        Button.secondary(ROOM_PREFIX, Messages.get(locale, "config.button.edit")),
                        TextDisplay.of("**" + Messages.get(locale, "config.room_prefix.title") + "**\n-# "
                                + Messages.format(locale, "config.room_prefix.value",
                                        settings.roomPrefix(), settings.roomPrefix()))),
                TextDisplay.of("**" + Messages.get(locale, "config.category.title") + "**\n-# "
                        + Messages.get(locale, "config.category.hint")),
                ActionRow.of(channelSelect(CATEGORY, ChannelType.CATEGORY, settings.categoryId(),
                        Messages.get(locale, "config.category.placeholder"))),
                Separator.createDivider(Separator.Spacing.LARGE),
                TextDisplay.of("### " + Messages.get(locale, "config.section.moderation")),
                TextDisplay.of("**" + Messages.get(locale, "config.log_channel.title") + "**\n-# "
                        + Messages.get(locale, "config.log_channel.hint")),
                ActionRow.of(channelSelect(LOG_CHANNEL, ChannelType.TEXT, settings.logChannelId(),
                        Messages.get(locale, "config.log_channel.placeholder"))),
                Separator.createDivider(Separator.Spacing.SMALL),
                Section.of(
                        Button.danger(STOP_ALL, Messages.get(locale, "config.stop_all.button"))
                                .withDisabled(searchingCount == 0),
                        TextDisplay.of("**" + Messages.get(locale, "config.stop_all.title") + "**\n-# "
                                + countText(locale, "config.stop_all.value", searchingCount)))
        ).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    static Container stopAllConfirmation(int searchingCount, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.get(locale, "config.stop_all.confirm.title")),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(Messages.get(locale, "config.stop_all.confirm.explanation")),
                TextDisplay.of("-# " + countText(locale, "config.stop_all.confirm.count", searchingCount)),
                TextDisplay.of("-# " + Messages.get(locale, "config.stop_all.confirm.undo")),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(
                        Button.danger(STOP_ALL_CONFIRM, Messages.get(locale, "config.stop_all.button")),
                        Button.secondary(BACK, Messages.get(locale, "config.button.cancel")))
        ).withAccentColor(Cards.ERROR_COLOR);
    }

    static Container stopAllDone(int stoppedCount, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.get(locale, "config.stop_all.done.title")),
                TextDisplay.of(countText(locale, "config.stop_all.done", stoppedCount)),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(Button.secondary(BACK, Messages.get(locale, "config.button.back")))
        ).withAccentColor(Cards.SUCCESS_COLOR);
    }

    private static String countText(Locale locale, String keyPrefix, int count) {
        return count == 1
                ? Messages.get(locale, keyPrefix + ".one")
                : Messages.format(locale, keyPrefix + ".other", count);
    }

    private static Section editSection(String id, String keyPrefix, Duration value, Locale locale) {
        return Section.of(
                Button.secondary(id, Messages.get(locale, "config.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, keyPrefix + ".title") + "**\n-# "
                        + Messages.format(locale, keyPrefix + ".value", DurationText.format(value, locale))));
    }

    private static Section roomsSection(boolean enabled, Locale locale) {
        Button button = enabled
                ? Button.secondary(ROOMS_OFF, Messages.get(locale, "toggle.turn_off"))
                : Button.secondary(ROOMS_ON, Messages.get(locale, "toggle.turn_on"));
        return Section.of(button, TextDisplay.of("**" + Messages.get(locale, "config.rooms.title") + "**\n-# "
                + Messages.get(locale, enabled ? "toggle.on" : "toggle.off") + " · "
                + Messages.get(locale, enabled ? "config.rooms.enabled" : "config.rooms.disabled")));
    }

    private static EntitySelectMenu channelSelect(String id, ChannelType type, Long selected, String placeholder) {
        EntitySelectMenu.Builder menu = EntitySelectMenu.create(id, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(type)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, 1);
        if (selected != null) {
            menu.setDefaultValues(EntitySelectMenu.DefaultValue.channel(selected));
        }
        return menu.build();
    }
}
