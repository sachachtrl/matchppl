package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
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
    static final String LIST = "list";
    static final String MANAGE = "manage";
    static final String DELETE = "delete";
    static final String CONFIRM_DELETE = "confirmdelete";
    static final int PAGE_SIZE = 8;

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
                ActionRow.of(
                        Button.success(componentId(DONE, tag.id()), Messages.get(locale, "tag.button.done")),
                        Button.danger(componentId(DELETE, tag.id()), Messages.get(locale, "tag.button.delete")),
                        allTagsButton(locale))
        ).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    static Container listCard(List<Tag> tags, int page, Locale locale) {
        if (tags.isEmpty()) {
            return Cards.simple(Cards.NEUTRAL_COLOR, Messages.get(locale, "tags.title"),
                    Messages.get(locale, "tags.empty"));
        }
        int pageCount = pageCount(tags.size());
        int currentPage = Math.clamp(page, 0, pageCount - 1);
        String countKey = tags.size() == 1 ? "tags.count.one" : "tags.count.other";
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "tags.title") + "\n-# "
                + Messages.format(locale, countKey, tags.size())));
        int from = currentPage * PAGE_SIZE;
        for (Tag tag : tags.subList(from, Math.min(from + PAGE_SIZE, tags.size()))) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(Section.of(
                    Button.secondary(componentId(MANAGE, tag.id()), Messages.get(locale, "tags.button.manage")),
                    TextDisplay.of("**" + title(tag) + "**\n-# " + shortConditions(tag, locale))));
        }
        if (pageCount > 1) {
            children.add(Separator.createDivider(Separator.Spacing.LARGE));
            children.add(ActionRow.of(
                    Button.secondary(componentId(LIST, currentPage - 1), Messages.get(locale, "tags.button.previous"))
                            .withDisabled(currentPage == 0),
                    Button.secondary(componentId(LIST, currentPage + 1), Messages.get(locale, "tags.button.next"))
                            .withDisabled(currentPage == pageCount - 1)));
        }
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    static Container confirmDeleteCard(Tag tag, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.format(locale, "tag.delete.title", title(tag))),
                TextDisplay.of(Messages.get(locale, "tag.delete.warning")),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(
                        Button.danger(componentId(CONFIRM_DELETE, tag.id()),
                                Messages.get(locale, "tag.delete.confirm")),
                        Button.secondary(componentId(MANAGE, tag.id()), Messages.get(locale, "tag.delete.cancel")))
        ).withAccentColor(Cards.WARNING_COLOR);
    }

    static Container deletedCard(Tag tag, Locale locale) {
        return Container.of(
                TextDisplay.of("## " + Messages.format(locale, "tag.deleted.title", title(tag))),
                TextDisplay.of(Messages.get(locale, "tag.deleted.text")),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(allTagsButton(locale))
        ).withAccentColor(Cards.ERROR_COLOR);
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
                                rolesText(tag.forbiddenRoleIds(), locale))),
                Separator.createDivider(Separator.Spacing.LARGE),
                ActionRow.of(allTagsButton(locale))
        ).withAccentColor(Cards.SUCCESS_COLOR);
    }

    static String title(Tag tag) {
        return tag.emoji() + " " + tag.name();
    }

    private static Button allTagsButton(Locale locale) {
        return Button.secondary(componentId(LIST, 0), Messages.get(locale, "tags.button.all"));
    }

    private static int pageCount(int tagCount) {
        return (tagCount + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private static String shortConditions(Tag tag, Locale locale) {
        List<String> parts = new ArrayList<>(ConditionTexts.shortParts(tag.conditions(), locale));
        if (!tag.requiredRoleIds().isEmpty()) {
            parts.add(Messages.format(locale, "tag.short.required", tag.requiredRoleIds().size()));
        }
        if (!tag.forbiddenRoleIds().isEmpty()) {
            parts.add(Messages.format(locale, "tag.short.forbidden", tag.forbiddenRoleIds().size()));
        }
        return ConditionTexts.shortSummary(parts, locale);
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
        return ConditionTexts.ageText(tag.conditions(), locale);
    }

    private static String seniorityText(Tag tag, Locale locale) {
        return ConditionTexts.seniorityText(tag.conditions(), locale);
    }

    private static String activityText(Tag tag, Locale locale) {
        return ConditionTexts.activityText(tag.conditions(), locale);
    }

    private static String rolesText(Set<Long> roleIds, Locale locale) {
        if (roleIds.isEmpty()) {
            return Messages.get(locale, "tag.roles.none");
        }
        return roleIds.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(", "));
    }
}
