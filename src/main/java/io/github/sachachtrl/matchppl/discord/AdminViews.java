package io.github.sachachtrl.matchppl.discord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

final class AdminViews {
    static final String PREFIX = "admin:";
    static final String ADD = PREFIX + "add";
    static final String REMOVE_PREFIX = PREFIX + "remove:";

    private static final int MAX_SHOWN_ROLES = 8;
    private static final int MAX_SELECTABLE_ROLES = 25;

    private AdminViews() {
    }

    static Container listCard(Guild guild, Set<Long> adminRoleIds, List<String> notes, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "admin.title") + "\n-# "
                + Messages.get(locale, "adminlist.subtitle")));
        for (String note : notes) {
            children.add(TextDisplay.of("-# " + note));
        }
        List<Long> sorted = sortByPosition(guild, adminRoleIds);
        if (sorted.isEmpty()) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(TextDisplay.of(Messages.get(locale, "adminlist.empty")));
        }
        for (long roleId : sorted.subList(0, Math.min(sorted.size(), MAX_SHOWN_ROLES))) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(Section.of(
                    Button.danger(REMOVE_PREFIX + roleId, Messages.get(locale, "adminlist.button.remove")),
                    TextDisplay.of("<@&" + roleId + ">")));
        }
        if (sorted.size() > MAX_SHOWN_ROLES) {
            children.add(TextDisplay.of("-# " + Messages.format(locale, "adminlist.more",
                    sorted.size() - MAX_SHOWN_ROLES)));
        }
        children.add(Separator.createDivider(Separator.Spacing.LARGE));
        children.add(TextDisplay.of("**" + Messages.get(locale, "adminlist.add.title") + "**"));
        children.add(ActionRow.of(EntitySelectMenu.create(ADD, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(Messages.get(locale, "adminlist.add.placeholder"))
                .setRequiredRange(1, MAX_SELECTABLE_ROLES)
                .build()));
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    private static List<Long> sortByPosition(Guild guild, Set<Long> roleIds) {
        return roleIds.stream()
                .sorted(Comparator.comparingInt((Long id) -> position(guild, id)).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static int position(Guild guild, long roleId) {
        Role role = guild.getRoleById(roleId);
        return role == null ? -1 : role.getPosition();
    }
}
