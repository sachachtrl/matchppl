package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.Tag;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

final class TagViews {
    static final String COMPONENT_PREFIX = "tag:";
    static final String AGE = "age";
    static final String SENIORITY = "seniority";
    static final String ACTIVITY = "activity";
    static final String REQUIRED = "required";
    static final String FORBIDDEN = "forbidden";
    static final String DONE = "done";

    private static final int MAX_SELECTABLE_ROLES = 25;

    private TagViews() {
    }

    static String componentId(String action, long tagId) {
        return COMPONENT_PREFIX + action + ":" + tagId;
    }

    static Container configCard(Tag tag, Locale locale, boolean justCreated) {
        String subtitle = Messages.get(locale, justCreated ? "tag.created" : "tag.autosave");
        return Container.of(
                TextDisplay.of("## " + title(tag) + "\n-# " + subtitle),
                Separator.createDivider(Separator.Spacing.SMALL),
                conditionSection(AGE, tag, Messages.get(locale, "tag.age.title"), ageText(tag, locale), locale),
                Separator.createDivider(Separator.Spacing.SMALL),
                conditionSection(SENIORITY, tag, Messages.get(locale, "tag.seniority.title"),
                        seniorityText(tag, locale), locale),
                Separator.createDivider(Separator.Spacing.SMALL),
                conditionSection(ACTIVITY, tag, Messages.get(locale, "tag.activity.title"),
                        activityText(tag, locale), locale),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of("**" + Messages.get(locale, "tag.required.title") + "**\n-# "
                        + Messages.get(locale, "tag.required.hint")),
                ActionRow.of(roleSelect(REQUIRED, tag, tag.requiredRoleIds(), locale)),
                TextDisplay.of("**" + Messages.get(locale, "tag.forbidden.title") + "**\n-# "
                        + Messages.get(locale, "tag.forbidden.hint")),
                ActionRow.of(roleSelect(FORBIDDEN, tag, tag.forbiddenRoleIds(), locale)),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(Button.success(componentId(DONE, tag.id()), Messages.get(locale, "tag.button.done")))
        ).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    static Container savedCard(Tag tag, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + title(tag) + "\n-# " + Messages.get(locale, "tag.saved")),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        summaryLine(Messages.get(locale, "tag.age.title"), ageText(tag, locale))
                        + summaryLine(Messages.get(locale, "tag.seniority.title"), seniorityText(tag, locale))
                        + summaryLine(Messages.get(locale, "tag.activity.title"), activityText(tag, locale))
                        + summaryLine(Messages.get(locale, "tag.required.title"),
                                rolesText(tag.requiredRoleIds(), locale))
                        + summaryLine(Messages.get(locale, "tag.forbidden.title"),
                                rolesText(tag.forbiddenRoleIds(), locale)))
        ).withAccentColor(Cards.SUCCESS_COLOR);
    }

    static String title(Tag tag) {
        return tag.emoji() + " " + tag.name();
    }

    private static Section conditionSection(String action, Tag tag, String title, String value, Locale locale) {
        return Section.of(
                Button.secondary(componentId(action, tag.id()), Messages.get(locale, "tag.button.edit")),
                TextDisplay.of("**" + title + "**\n-# " + value));
    }

    private static EntitySelectMenu roleSelect(String action, Tag tag, Set<Long> selected, Locale locale) {
        return EntitySelectMenu.create(componentId(action, tag.id()), EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(Messages.get(locale, "tag.roles.placeholder"))
                .setRequiredRange(0, MAX_SELECTABLE_ROLES)
                .setDefaultValues(selected.stream().map(EntitySelectMenu.DefaultValue::role).toList())
                .build();
    }

    private static String summaryLine(String title, String value) {
        return "**" + title + "**: " + value + "\n";
    }

    private static String ageText(Tag tag, Locale locale) {
        return tag.minAccountAgeDays() == null
                ? Messages.get(locale, "tag.no_minimum")
                : Messages.format(locale, "tag.age.value", tag.minAccountAgeDays());
    }

    private static String seniorityText(Tag tag, Locale locale) {
        return tag.minMemberDays() == null
                ? Messages.get(locale, "tag.no_minimum")
                : Messages.format(locale, "tag.seniority.value", tag.minMemberDays());
    }

    private static String activityText(Tag tag, Locale locale) {
        if (tag.minMessages() == null) {
            return Messages.get(locale, "tag.no_minimum");
        }
        return Messages.format(locale, "tag.activity.value", tag.minMessages(), periodText(tag.activityPeriod(), locale));
    }

    static String periodText(ActivityPeriod period, Locale locale) {
        return Messages.get(locale, "tag.period." + period.name().toLowerCase(Locale.ROOT));
    }

    private static String rolesText(Set<Long> roleIds, Locale locale) {
        if (roleIds.isEmpty()) {
            return Messages.get(locale, "tag.roles.none");
        }
        return roleIds.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(", "));
    }
}
