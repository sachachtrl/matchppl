package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.PartnerPreferences;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;
import io.github.sachachtrl.matchppl.service.ProfileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

final class ProfileViews {
    static final String PREFIX = "profile:";
    static final String SEARCH_ON = PREFIX + "search:on";
    static final String SEARCH_OFF = PREFIX + "search:off";
    static final String PRESENTATION = PREFIX + "presentation";
    static final String TAGS = PREFIX + "tags";
    static final String EXCLUDED = PREFIX + "excluded";
    static final String ANONYMOUS_ON = PREFIX + "anonymous:on";
    static final String ANONYMOUS_OFF = PREFIX + "anonymous:off";
    static final String ROOMS_ON = PREFIX + "rooms:on";
    static final String ROOMS_OFF = PREFIX + "rooms:off";
    static final String ADVANCED = PREFIX + "advanced";
    static final String MAIN = PREFIX + "main";
    static final String REQUIRED = PREFIX + "required";
    static final String COMPATIBILITY = PREFIX + "compatibility";
    static final String EXCLUDE_ANONYMOUS_ON = PREFIX + "excludeanonymous:on";
    static final String EXCLUDE_ANONYMOUS_OFF = PREFIX + "excludeanonymous:off";
    static final String EXCLUDE_AVATARS_ON = PREFIX + "excludeavatars:on";
    static final String EXCLUDE_AVATARS_OFF = PREFIX + "excludeavatars:off";
    static final String MIN_COMMON_TAGS = PREFIX + "mincommontags";
    static final String CONDITIONS = PREFIX + "conditions";

    static final int MAX_SELECT_OPTIONS = 25;

    private ProfileViews() {
    }

    static Container mainCard(Profile profile, List<Tag> usableTags, List<Tag> allTags, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "profile.title") + "\n-# "
                + Messages.get(locale, "profile.subtitle")));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(searchButton(profile, locale), searchTexts(profile, locale)));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                Button.secondary(PRESENTATION, Messages.get(locale, "profile.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, "profile.presentation.title") + "**\n-# "
                        + Messages.get(locale, "profile.presentation.hint") + "\n"
                        + presentationText(profile, locale))));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("**" + Messages.get(locale, "profile.tags.title") + "**\n-# "
                + Messages.get(locale, usableTags.isEmpty() ? "profile.tags.none" : "profile.tags.hint")));
        if (!usableTags.isEmpty()) {
            children.add(ActionRow.of(tagSelect(TAGS, usableTags, profile.tagIds(),
                    Messages.get(locale, "profile.tags.placeholder"))));
        }
        if (!allTags.isEmpty()) {
            children.add(TextDisplay.of("**" + Messages.get(locale, "profile.excluded.title") + "**\n-# "
                    + Messages.get(locale, "profile.excluded.hint")));
            children.add(ActionRow.of(tagSelect(EXCLUDED, allTags, profile.excludedTagIds(),
                    Messages.get(locale, "profile.excluded.placeholder"))));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(toggleSection(profile.anonymous(), ANONYMOUS_ON, ANONYMOUS_OFF, "profile.anonymous", locale));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(toggleSection(profile.allowRooms(), ROOMS_ON, ROOMS_OFF, "profile.rooms", locale));
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(ActionRow.of(Button.secondary(ADVANCED, Messages.get(locale, "profile.button.advanced"))));
        return Container.of(children)
                .withAccentColor(profile.searching() ? Cards.SUCCESS_COLOR : Cards.ERROR_COLOR);
    }

    static Container advancedCard(Profile profile, List<Tag> allTags, Locale locale) {
        PartnerPreferences preferences = profile.preferences();
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "advanced.title") + "\n-# "
                + Messages.get(locale, "advanced.subtitle")));
        if (!allTags.isEmpty()) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(TextDisplay.of("**" + Messages.get(locale, "advanced.required.title") + "**\n-# "
                    + Messages.get(locale, "advanced.required.hint")));
            children.add(ActionRow.of(tagSelect(REQUIRED, allTags, preferences.requiredTagIds(),
                    Messages.get(locale, "advanced.required.placeholder"))));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("**" + Messages.get(locale, "advanced.common.title") + "**\n-# "
                + Messages.get(locale, "advanced.common.hint")));
        children.add(ActionRow.of(minCommonTagsSelect(preferences.minCommonTags(), locale)));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                Button.secondary(COMPATIBILITY, Messages.get(locale, "profile.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, "advanced.compat.title") + "**\n-# "
                        + compatibilityText(preferences, locale))));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(toggleSection(preferences.excludeAnonymous(), EXCLUDE_ANONYMOUS_ON, EXCLUDE_ANONYMOUS_OFF,
                "advanced.anonymous", locale));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(toggleSection(preferences.excludeDefaultAvatars(), EXCLUDE_AVATARS_ON, EXCLUDE_AVATARS_OFF,
                "advanced.avatar", locale));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                Button.secondary(CONDITIONS, Messages.get(locale, "profile.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, "advanced.conditions.title") + "**\n-# "
                        + ConditionTexts.shortSummary(
                                ConditionTexts.shortParts(preferences.conditions(), locale), locale))));
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(ActionRow.of(Button.secondary(MAIN, Messages.get(locale, "advanced.button.back"))));
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    private static Button searchButton(Profile profile, Locale locale) {
        if (profile.searching()) {
            return Button.danger(SEARCH_OFF, Messages.get(locale, "match.button.disable"));
        }
        return Button.success(SEARCH_ON, Messages.get(locale, "match.button.enable"))
                .withDisabled(profile.tagIds().isEmpty());
    }

    private static List<TextDisplay> searchTexts(Profile profile, Locale locale) {
        List<TextDisplay> texts = new ArrayList<>();
        texts.add(TextDisplay.of("**" + Messages.get(locale, "profile.search.title") + "**\n-# "
                + Messages.get(locale, profile.searching() ? "profile.search.enabled" : "profile.search.disabled")));
        if (!profile.searching() && profile.dmUnreachable()) {
            texts.add(TextDisplay.of("-# " + Messages.get(locale, "profile.search.unreachable")));
        }
        if (!profile.searching() && profile.tagIds().isEmpty()) {
            texts.add(TextDisplay.of("-# " + Messages.get(locale, "profile.search.needs_tags")));
        }
        return texts;
    }

    private static Button toggleButton(boolean enabled, String onId, String offId, Locale locale) {
        return enabled
                ? Button.secondary(offId, Messages.get(locale, "toggle.turn_off"))
                : Button.secondary(onId, Messages.get(locale, "toggle.turn_on"));
    }

    private static String presentationText(Profile profile, Locale locale) {
        return profile.presentation() == null
                ? "-# " + Messages.get(locale, "profile.presentation.empty")
                : ">>> " + profile.presentation();
    }

    private static String compatibilityText(PartnerPreferences preferences, Locale locale) {
        return preferences.minCompatibility() == null
                ? Messages.get(locale, "tag.no_minimum")
                : Messages.format(locale, "advanced.compat.value", preferences.minCompatibility());
    }

    private static Section toggleSection(boolean enabled, String onId, String offId, String keyPrefix, Locale locale) {
        return Section.of(
                toggleButton(enabled, onId, offId, locale),
                TextDisplay.of("**" + Messages.get(locale, keyPrefix + ".title") + "**\n-# "
                        + Messages.get(locale, enabled ? "toggle.on" : "toggle.off") + " · "
                        + Messages.get(locale, keyPrefix + (enabled ? ".enabled" : ".disabled"))));
    }

    private static StringSelectMenu minCommonTagsSelect(int selected, Locale locale) {
        List<SelectOption> options = IntStream.rangeClosed(0, ProfileService.MAX_MIN_COMMON_TAGS)
                .mapToObj(count -> SelectOption.of(minCommonTagsLabel(count, locale), String.valueOf(count))
                        .withDefault(count == selected))
                .toList();
        return StringSelectMenu.create(MIN_COMMON_TAGS)
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    private static String minCommonTagsLabel(int count, Locale locale) {
        return switch (count) {
            case 0 -> Messages.get(locale, "advanced.common.option.zero");
            case 1 -> Messages.get(locale, "advanced.common.option.one");
            default -> Messages.format(locale, "advanced.common.option.other", count);
        };
    }

    private static StringSelectMenu tagSelect(String id, List<Tag> tags, Set<Long> selected, String placeholder) {
        List<Tag> shown = tags.subList(0, Math.min(tags.size(), MAX_SELECT_OPTIONS));
        List<SelectOption> options = shown.stream()
                .map(tag -> SelectOption.of(tag.name(), String.valueOf(tag.id()))
                        .withEmoji(Emoji.fromFormatted(tag.emoji()))
                        .withDefault(selected.contains(tag.id())))
                .toList();
        return StringSelectMenu.create(id)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, options.size())
                .addOptions(options)
                .build();
    }
}
