package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.service.GuildConfigService;

import java.util.Locale;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WelcomeListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(WelcomeListener.class);

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        log.info("Joined a server: guild={}, members={}", guild.getIdLong(), guild.getMemberCount());
        Member self = guild.getSelfMember();
        Container card = WelcomeViews.card(new ConfigViews.Warnings(!self.hasPermission(Permission.MANAGE_CHANNEL),
                !self.hasPermission(Permission.MANAGE_ROLES), false), languageOf(guild.getLocale().toLocale()));
        TextChannel channel = guild.getSystemChannel();
        if (channel != null && self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)) {
            channel.sendMessageComponents(card).useComponentsV2().queue(
                    message -> log.info("Welcome message sent: guild={}, to=system channel", guild.getIdLong()),
                    error -> this.sendToOwner(guild, card));
        } else {
            this.sendToOwner(guild, card);
        }
    }

    static Locale languageOf(Locale guildLocale) {
        return GuildConfigService.SUPPORTED_LANGUAGES.stream()
                .filter(language -> language.getLanguage().equals(guildLocale.getLanguage()))
                .findFirst()
                .orElse(Messages.DEFAULT_LOCALE);
    }

    private void sendToOwner(Guild guild, Container card) {
        guild.retrieveOwner()
                .map(Member::getUser)
                .flatMap(User::openPrivateChannel)
                .flatMap(channel -> channel.sendMessageComponents(card).useComponentsV2())
                .queue(message -> log.info("Welcome message sent: guild={}, to=owner", guild.getIdLong()),
                        error -> log.warn("Cannot send the welcome message: guild={}", guild.getIdLong()));
    }
}
