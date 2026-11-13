package io.github.sachachtrl.matchppl.discord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.utils.ComponentIterator;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpViewsTest {
    private static String text(Container card) {
        return ComponentIterator.createStream(List.of(card))
                .filter(TextDisplay.class::isInstance)
                .map(TextDisplay.class::cast)
                .map(TextDisplay::getContent)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void everyRegisteredCommandIsListedExactlyOnce() {
        Set<String> registered = BotCommands.all().stream().map(SlashCommandData::getName).collect(Collectors.toSet());
        List<String> listed = new ArrayList<>(HelpViews.MEMBER_COMMANDS);
        listed.addAll(HelpViews.ADMIN_COMMANDS);

        assertEquals(registered, new HashSet<>(listed));
        assertEquals(listed.size(), new HashSet<>(listed).size());
    }

    @Test
    void theAdminSectionIsShownToAdminsOnly() {
        String member = text(HelpViews.card(false, Locale.ENGLISH));
        String admin = text(HelpViews.card(true, Locale.FRENCH));

        assertTrue(member.contains("`/match`"));
        assertFalse(member.contains("`/config`"));
        assertTrue(admin.contains("`/config` - Modifier les réglages du bot pour ce serveur"));
    }
}
