package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    static final String ADVANCED = PREFIX + "advanced";
    static final String MAIN = PREFIX + "main";
    static final String REQUIRED = PREFIX + "required";
    static final String COMPATIBILITY = PREFIX + "compatibility";
    static final String PHOTO_ON = PREFIX + "photo:on";
    static final String PHOTO_OFF = PREFIX + "photo:off";
    static final String CONDITIONS = PREFIX + "conditions";

    static final int MAX_SELECT_OPTIONS = 25;

    private ProfileViews() {
    }

    static Container mainCard(Profile profile, List<Tag> usableTags, List<Tag> allTags, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "profile.title") + "\n-# "
                + Messages.get(locale, "profile.subtitle")));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(searchButton(profile, locale), TextDisplay.of(
                "**" + Messages.get(locale, "profile.search.title") + "**\n-# " + searchText(profile, locale))));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                Button.secondary(PRESENTATION, Messages.get(locale, "profile.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, "profile.presentation.title") + "**\n"
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
        children.add(Section.of(
                toggleButton(profile.anonymous(), ANONYMOUS_ON, ANONYMOUS_OFF, locale),
                TextDisplay.of("**" + Messages.get(locale, "profile.anonymous.title") + "**\n-# "
                        + Messages.get(locale, profile.anonymous()
                                ? "profile.anonymous.enabled"
                                : "profile.anonymous.disabled"))));
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(ActionRow.of(Button.secondary(ADVANCED, Messages.get(locale, "profile.button.advanced"))));
        return Container.of(children)
                .withAccentColor(profile.searching() ? Cards.SUCCESS_COLOR : Cards.ERROR_COLOR);
    }

    static Container advancedCard(Profile profile, List<Tag> allTags, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "advanced.title") + "\n-# "
                + Messages.get(locale, "advanced.subtitle")));
        if (!allTags.isEmpty()) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(TextDisplay.of("**" + Messages.get(locale, "advanced.required.title") + "**\n-# "
                    + Messages.get(locale, "advanced.required.hint")));
            children.add(ActionRow.of(tagSelect(REQUIRED, allTags, profile.requiredTagIds(),
                    Messages.get(locale, "advanced.required.placeholder"))));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                Button.secondary(COMPATIBILITY, Messages.get(locale, "profile.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, "advanced.compat.title") + "**\n-# "
                        + compatibilityText(profile, locale))));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                toggleButton(profile.requirePhoto(), PHOTO_ON, PHOTO_OFF, locale),
                TextDisplay.of("**" + Messages.get(locale, "advanced.photo.title") + "**\n-# "
                        + Messages.get(locale, profile.requirePhoto()
                                ? "advanced.photo.enabled"
                                : "advanced.photo.disabled"))));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(Section.of(
                Button.secondary(CONDITIONS, Messages.get(locale, "profile.button.edit")),
                TextDisplay.of("**" + Messages.get(locale, "advanced.conditions.title") + "**\n-# "
                        + ConditionTexts.shortSummary(
                                ConditionTexts.shortParts(profile.partnerConditions(), locale), locale))));
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

    private static String searchText(Profile profile, Locale locale) {
        if (profile.searching()) {
            return Messages.get(locale, "profile.search.enabled");
        }
        String text = Messages.get(locale, "profile.search.disabled");
        return profile.tagIds().isEmpty() ? text + "\n-# " + Messages.get(locale, "profile.search.needs_tags") : text;
    }

    private static Button toggleButton(boolean enabled, String onId, String offId, Locale locale) {
        return enabled
                ? Button.danger(offId, Messages.get(locale, "match.button.disable"))
                : Button.success(onId, Messages.get(locale, "match.button.enable"));
    }

    private static String presentationText(Profile profile, Locale locale) {
        return profile.presentation() == null
                ? "-# " + Messages.get(locale, "profile.presentation.empty")
                : ">>> " + profile.presentation();
    }

    private static String compatibilityText(Profile profile, Locale locale) {
        return profile.minCompatibility() == null
                ? Messages.get(locale, "tag.no_minimum")
                : Messages.format(locale, "advanced.compat.value", profile.minCompatibility());
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
