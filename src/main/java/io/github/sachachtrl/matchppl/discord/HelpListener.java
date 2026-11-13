package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class HelpListener extends ListenerAdapter {
    private static final String COMMAND_NAME = "help";

    private final GuildConfigService guildConfigService;

    public HelpListener(GuildConfigService guildConfigService) {
        this.guildConfigService = guildConfigService;
    }

    public static SlashCommandData command() {
        return Commands.slash(COMMAND_NAME, Messages.get(Messages.DEFAULT_LOCALE, "command.help.description"))
                .setContexts(InteractionContextType.GUILD);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (!event.getName().equals(COMMAND_NAME) || member == null) {
            return;
        }
        boolean admin = AdminAccess.isAdmin(this.guildConfigService, member);
        event.replyComponents(HelpViews.card(admin,
                        this.guildConfigService.language(member.getGuild().getIdLong())))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }
}
