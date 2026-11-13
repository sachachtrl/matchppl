package io.github.sachachtrl.matchppl.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

final class HelpViews {
    static final List<String> MEMBER_COMMANDS = List.of("match", "block", "unblock", "blocklist", "help");
    static final List<String> ADMIN_COMMANDS = List.of("config", "newtag", "tags", "setadmin", "removeadmin",
            "listadmin", "reset", "resetpair", "resetall");

    private HelpViews() {
    }

    static Container card(boolean admin, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "help.title") + "\n-# "
                + Messages.get(locale, "help.subtitle")));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("**" + Messages.get(locale, "help.members") + "**\n"
                + commandLines(MEMBER_COMMANDS, locale)));
        if (admin) {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(TextDisplay.of("**" + Messages.get(locale, "help.admins") + "**\n"
                    + commandLines(ADMIN_COMMANDS, locale)));
        }
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    private static String commandLines(List<String> commands, Locale locale) {
        return commands.stream()
                .map(name -> "`/" + name + "` - " + Messages.get(locale, "command." + name + ".description"))
                .collect(Collectors.joining("\n"));
    }
}
