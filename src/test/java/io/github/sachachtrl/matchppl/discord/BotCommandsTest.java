package io.github.sachachtrl.matchppl.discord;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BotCommandsTest {
    @Test
    void everyCommandAndOptionHasAFrenchDescription() {
        for (SlashCommandData command : BotCommands.all()) {
            assertNotNull(command.getDescriptionLocalizations().get(DiscordLocale.FRENCH), command.getName());
            for (OptionData option : command.getOptions()) {
                assertNotNull(option.getDescriptionLocalizations().get(DiscordLocale.FRENCH),
                        command.getName() + " " + option.getName());
            }
        }
    }

    @Test
    void theDefaultDescriptionStaysInEnglish() {
        SlashCommandData match = BotCommands.all().stream()
                .filter(command -> command.getName().equals("match"))
                .findFirst()
                .orElseThrow();

        assertEquals("Manage your matchmaking profile", match.getDescription());
        assertEquals("Gérer votre profil de matchmaking",
                match.getDescriptionLocalizations().get(DiscordLocale.FRENCH));
    }
}
