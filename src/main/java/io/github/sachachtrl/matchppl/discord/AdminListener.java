package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;

import java.util.Locale;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(AdminListener.class);

    private static final String SET_ADMIN_COMMAND = "setadmin";
    private static final String REMOVE_ADMIN_COMMAND = "removeadmin";
    private static final String ROLE_OPTION = "role";
    private static final int SUCCESS_COLOR = 0x57F287;
    private static final int NEUTRAL_COLOR = 0x5865F2;
    private static final int ERROR_COLOR = 0xED4245;

    private final GuildConfigService guildConfigService;

    public AdminListener(GuildConfigService guildConfigService) {
        this.guildConfigService = guildConfigService;
    }

    public static SlashCommandData setAdminCommand() {
        return adminCommand(SET_ADMIN_COMMAND, "command.setadmin");
    }

    public static SlashCommandData removeAdminCommand() {
        return adminCommand(REMOVE_ADMIN_COMMAND, "command.removeadmin");
    }

    private static SlashCommandData adminCommand(String name, String keyPrefix) {
        Locale locale = Messages.DEFAULT_LOCALE;
        return Commands.slash(name, Messages.get(locale, keyPrefix + ".description"))
                .addOption(OptionType.ROLE, ROLE_OPTION, Messages.get(locale, keyPrefix + ".option.role"), true)
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if ((!commandName.equals(SET_ADMIN_COMMAND) && !commandName.equals(REMOVE_ADMIN_COMMAND))
                || guild == null || member == null) {
            return;
        }
        Locale locale = Messages.DEFAULT_LOCALE;
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            this.reply(event, ERROR_COLOR, Messages.get(locale, "error.title"),
                    Messages.get(locale, "error.manage_server_required"));
            return;
        }
        Role role = event.getOption(ROLE_OPTION, OptionMapping::getAsRole);
        if (role == null) {
            return;
        }
        if (role.isPublicRole()) {
            this.reply(event, ERROR_COLOR, Messages.get(locale, "error.title"), Messages.get(locale, "admin.everyone"));
            return;
        }
        long guildId = guild.getIdLong();
        long roleId = role.getIdLong();
        boolean changed;
        String messageKey;
        if (commandName.equals(SET_ADMIN_COMMAND)) {
            changed = this.guildConfigService.addAdminRole(guildId, roleId);
            messageKey = changed ? "admin.added" : "admin.already";
        } else {
            changed = this.guildConfigService.removeAdminRole(guildId, roleId);
            messageKey = changed ? "admin.removed" : "admin.not_admin";
        }
        this.reply(event, changed ? SUCCESS_COLOR : NEUTRAL_COLOR, Messages.get(locale, "admin.title"),
                Messages.format(locale, messageKey, role.getAsMention()));
        if (changed) {
            log.info("Admin roles updated: command={}, guild={}, role={}, by={}",
                    commandName, guildId, roleId, member.getIdLong());
        }
    }

    private void reply(SlashCommandInteractionEvent event, int color, String title, String description) {
        event.replyComponents(Container.of(
                                TextDisplay.of("## " + title),
                                TextDisplay.of(description))
                        .withAccentColor(color))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }
}
