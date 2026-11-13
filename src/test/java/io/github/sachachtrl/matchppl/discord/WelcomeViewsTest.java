package io.github.sachachtrl.matchppl.discord;

import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.utils.ComponentIterator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeViewsTest {
    private static String text(Container card) {
        return ComponentIterator.createStream(List.of(card))
                .filter(TextDisplay.class::isInstance)
                .map(TextDisplay.class::cast)
                .map(TextDisplay::getContent)
                .reduce("", String::concat);
    }

    @Test
    void theWelcomeUsesTheServerLanguageWhenTheBotSpeaksIt() {
        assertEquals(Locale.FRENCH, WelcomeListener.languageOf(Locale.FRANCE));
        assertEquals(Locale.ENGLISH, WelcomeListener.languageOf(Locale.US));
        assertEquals(Locale.ENGLISH, WelcomeListener.languageOf(Locale.GERMANY));
    }

    @Test
    void aMissingPermissionIsMentionedOnlyWhenItIsMissing() {
        Container missing = WelcomeViews.card(new ConfigViews.Warnings(false, true, false), Locale.ENGLISH);
        Container complete = WelcomeViews.card(new ConfigViews.Warnings(false, false, false), Locale.ENGLISH);

        assertTrue(text(missing).contains("Manage Roles"));
        assertFalse(text(complete).contains("Manage Roles"));
    }

    @Test
    void theWelcomeStaysUnderTheComponentLimit() {
        Container card = WelcomeViews.card(new ConfigViews.Warnings(true, true, false), Locale.FRENCH);

        assertTrue(ComponentIterator.createStream(List.<Component>of(card)).count() <= 40);
    }
}
