package io.github.sachachtrl.matchppl.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

final class WelcomeViews {
    private WelcomeViews() {
    }

    static Container card(ConfigViews.Warnings warnings, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "welcome.title")));
        children.add(TextDisplay.of(Messages.get(locale, "welcome.intro")));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("**" + Messages.get(locale, "welcome.admins.title") + "**\n"
                + Messages.get(locale, "welcome.admins.steps")));
        String roomsWarning = ConfigViews.roomsWarning(warnings, locale);
        if (roomsWarning != null) {
            children.add(TextDisplay.of(roomsWarning));
        }
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        children.add(TextDisplay.of("**" + Messages.get(locale, "welcome.members.title") + "**\n"
                + Messages.get(locale, "welcome.members.text")));
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }
}
