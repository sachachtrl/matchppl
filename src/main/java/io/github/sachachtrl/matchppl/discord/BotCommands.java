package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;

import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public final class BotCommands {
    private BotCommands() {
    }

    public static List<SlashCommandData> all() {
        return List.of(
                        MatchListener.command(),
                        AdminListener.setAdminCommand(),
                        AdminListener.removeAdminCommand(),
                        AdminListener.listAdminCommand(),
                        TagListener.newTagCommand(),
                        TagListener.tagsCommand(),
                        ResetListener.resetCommand(),
                        ResetListener.resetAllCommand(),
                        ResetListener.resetPairCommand(),
                        ConfigListener.command())
                .stream()
                .map(BotCommands::localize)
                .toList();
    }

    static SlashCommandData localize(SlashCommandData command) {
        String prefix = "command." + command.getName();
        for (Locale language : GuildConfigService.SUPPORTED_LANGUAGES) {
            if (language.equals(Messages.DEFAULT_LOCALE)) {
                continue;
            }
            DiscordLocale discordLocale = DiscordLocale.from(language);
            command.setDescriptionLocalization(discordLocale, Messages.get(language, prefix + ".description"));
            for (OptionData option : command.getOptions()) {
                option.setDescriptionLocalization(discordLocale,
                        Messages.get(language, prefix + ".option." + option.getName()));
            }
        }
        return command;
    }
}
