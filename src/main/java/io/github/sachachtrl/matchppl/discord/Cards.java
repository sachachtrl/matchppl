package io.github.sachachtrl.matchppl.discord;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

final class Cards {
    static final int SUCCESS_COLOR = 0x57F287;
    static final int NEUTRAL_COLOR = 0x5865F2;
    static final int WARNING_COLOR = 0xFEE75C;
    static final int ERROR_COLOR = 0xED4245;

    private Cards() {
    }

    static Container simple(int color, String title, String description) {
        return Container.of(
                TextDisplay.of("## " + title),
                TextDisplay.of(description)
        ).withAccentColor(color);
    }
}
