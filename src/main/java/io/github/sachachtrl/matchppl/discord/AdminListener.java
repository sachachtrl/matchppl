package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
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
    private static final String LIST_ADMIN_COMMAND = "listadmin";
    private static final String ROLE_OPTION = "role";

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

    public static SlashCommandData listAdminCommand() {
        return Commands.slash(LIST_ADMIN_COMMAND,
                        Messages.get(Messages.DEFAULT_LOCALE, "command.listadmin.description"))
                .setContexts(InteractionContextType.GUILD)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
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
        if ((!commandName.equals(SET_ADMIN_COMMAND) && !commandName.equals(REMOVE_ADMIN_COMMAND)
                && !commandName.equals(LIST_ADMIN_COMMAND)) || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.canManage(event, member, locale)) {
            return;
        }
        if (commandName.equals(LIST_ADMIN_COMMAND)) {
            event.replyComponents(this.listView(guild, List.of(), locale))
                    .useComponentsV2()
                    .setEphemeral(true)
                    .queue();
            return;
        }
        Role role = event.getOption(ROLE_OPTION, OptionMapping::getAsRole);
        if (role == null) {
            return;
        }
        if (role.isPublicRole()) {
            this.reply(event, Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                    Messages.get(locale, "admin.everyone"));
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
        this.reply(event, changed ? Cards.SUCCESS_COLOR : Cards.NEUTRAL_COLOR, Messages.get(locale, "admin.title"),
                Messages.format(locale, messageKey, role.getAsMention()));
        if (changed) {
            log.info("Admin roles updated: command={}, guild={}, role={}, by={}",
                    commandName, guildId, roleId, member.getIdLong());
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!id.startsWith(AdminViews.REMOVE_PREFIX) || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.canManage(event, member, locale)) {
            return;
        }
        long roleId;
        try {
            roleId = Long.parseLong(id.substring(AdminViews.REMOVE_PREFIX.length()));
        } catch (NumberFormatException e) {
            return;
        }
        String mention = "<@&" + roleId + ">";
        String note;
        if (this.guildConfigService.removeAdminRole(guild.getIdLong(), roleId)) {
            note = Messages.format(locale, "adminlist.note.removed", mention);
            log.info("Admin roles updated: command={}, guild={}, role={}, by={}",
                    LIST_ADMIN_COMMAND, guild.getIdLong(), roleId, member.getIdLong());
        } else {
            note = Messages.format(locale, "adminlist.note.already_removed", mention);
        }
        event.editComponents(this.listView(guild, List.of(note), locale)).useComponentsV2().queue();
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!event.getComponentId().equals(AdminViews.ADD) || guild == null || member == null) {
            return;
        }
        Locale locale = this.guildConfigService.language(guild.getIdLong());
        if (!this.canManage(event, member, locale)) {
            return;
        }
        List<String> added = new ArrayList<>();
        List<String> already = new ArrayList<>();
        boolean everyoneRefused = false;
        for (Role role : event.getMentions().getRoles()) {
            if (role.isPublicRole()) {
                everyoneRefused = true;
            } else if (this.guildConfigService.addAdminRole(guild.getIdLong(), role.getIdLong())) {
                added.add(role.getAsMention());
                log.info("Admin roles updated: command={}, guild={}, role={}, by={}",
                        LIST_ADMIN_COMMAND, guild.getIdLong(), role.getIdLong(), member.getIdLong());
            } else {
                already.add(role.getAsMention());
            }
        }
        List<String> notes = new ArrayList<>();
        if (!added.isEmpty()) {
            notes.add(Messages.format(locale, "adminlist.note.added", String.join(", ", added)));
        }
        if (!already.isEmpty()) {
            notes.add(Messages.format(locale, "adminlist.note.already", String.join(", ", already)));
        }
        if (everyoneRefused) {
            notes.add(Messages.get(locale, "admin.everyone"));
        }
        event.editComponents(this.listView(guild, notes, locale)).useComponentsV2().queue();
    }

    private Container listView(Guild guild, List<String> notes, Locale locale) {
        return AdminViews.listCard(guild, this.guildConfigService.listAdminRoles(guild.getIdLong()), notes, locale);
    }

    private boolean canManage(IReplyCallback event, Member member, Locale locale) {
        if (member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        event.replyComponents(Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                        Messages.get(locale, "error.manage_server_required")))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
        return false;
    }

    private void reply(SlashCommandInteractionEvent event, int color, String title, String description) {
        event.replyComponents(Cards.simple(color, title, description))
                .useComponentsV2()
                .setEphemeral(true)
                .queue();
    }
}
