package io.github.sachachtrl.matchppl.discord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

final class BlockViews {
    static final String UNBLOCK_PREFIX = "block:unblock:";

    private static final int MAX_SHOWN = 8;

    private BlockViews() {
    }

    static Container blocked(String mention, String withdrawalKey, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "block.done.title")));
        children.add(TextDisplay.of(Messages.format(locale, "block.done.text", mention)));
        if (withdrawalKey != null) {
            children.add(TextDisplay.of("-# " + Messages.get(locale, withdrawalKey)));
        }
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }

    static Container list(List<Long> blocked, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "blocklist.title") + "\n-# "
                + Messages.get(locale, "blocklist.subtitle")));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        if (blocked.isEmpty()) {
            children.add(TextDisplay.of(Messages.get(locale, "blocklist.empty")));
        }
        for (long userId : blocked.subList(0, Math.min(blocked.size(), MAX_SHOWN))) {
            children.add(Section.of(
                    Button.secondary(UNBLOCK_PREFIX + userId, Messages.get(locale, "blocklist.button.unblock")),
                    TextDisplay.of("<@" + userId + ">")));
        }
        if (blocked.size() > MAX_SHOWN) {
            children.add(TextDisplay.of("-# " + Messages.format(locale, "blocklist.more", blocked.size() - MAX_SHOWN)));
        }
        return Container.of(children).withAccentColor(Cards.NEUTRAL_COLOR);
    }
}
