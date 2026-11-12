package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProposalService;
import io.github.sachachtrl.matchppl.service.RoomService;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomManager {
    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    static final String DEFAULT_CATEGORY_NAME = "Match People";

    private static final Set<Permission> HIDDEN = EnumSet.of(Permission.VIEW_CHANNEL);
    private static final Set<Permission> MEMBER_TEXT = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES);
    private static final Set<Permission> MEMBER_VOICE = EnumSet.of(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT,
            Permission.VOICE_SPEAK, Permission.VOICE_STREAM);
    private static final Set<Permission> BOT_TEXT = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY, Permission.MESSAGE_EMBED_LINKS);
    private static final Set<Permission> BOT_VOICE = EnumSet.of(Permission.VIEW_CHANNEL);

    private final RoomService roomService;
    private final ProposalService proposalService;
    private final GuildConfigService guildConfigService;
    private final ProposalMessenger messenger;

    public RoomManager(RoomService roomService, ProposalService proposalService,
            GuildConfigService guildConfigService, ProposalMessenger messenger) {
        this.roomService = roomService;
        this.proposalService = proposalService;
        this.guildConfigService = guildConfigService;
        this.messenger = messenger;
    }

    void open(JDA jda, Proposal proposal, Room room) {
        Guild guild = jda.getGuildById(room.guildId());
        if (guild == null) {
            this.abandon(jda, proposal, room, null);
            return;
        }
        GuildSettings settings = this.guildConfigService.settings(room.guildId());
        String name = settings.roomPrefix() + " #" + room.number();
        Consumer<Throwable> failure = error -> this.abandon(jda, proposal, room, error);
        this.findCategory(guild, settings,
                category -> this.createChannels(jda, proposal, room, category, name, failure), failure);
    }

    public void remove(JDA jda, Room room) {
        Guild guild = jda.getGuildById(room.guildId());
        if (guild != null) {
            Room current = this.roomService.findRoom(room.id()).orElse(room);
            this.deleteChannel(guild, room, current.textChannelId());
            this.deleteChannel(guild, room, current.voiceChannelId());
        }
        this.proposalService.findProposal(room.proposalId()).ifPresent(proposal ->
                this.messenger.showMatched(jda, proposal));
        log.info("Room closed: room={}, guild={}", room.id(), room.guildId());
    }

    public void remind(JDA jda, Room room) {
        Guild guild = jda.getGuildById(room.guildId());
        TextChannel channel = guild == null || room.textChannelId() == null
                ? null
                : guild.getTextChannelById(room.textChannelId());
        if (channel != null) {
            channel.sendMessageComponents(RoomViews.reminder(room, this.guildConfigService.language(room.guildId())))
                    .useComponentsV2()
                    .queue(message -> {
                    }, error -> log.warn("Cannot send room reminder: room={}", room.id()));
        }
    }

    private void createChannels(JDA jda, Proposal proposal, Room room, Category category, String name,
            Consumer<Throwable> failure) {
        Guild guild = category.getGuild();
        this.restrict(guild.createTextChannel(name, category), guild, room, MEMBER_TEXT, BOT_TEXT).queue(
                text -> this.restrict(guild.createVoiceChannel(name, category), guild, room, MEMBER_VOICE, BOT_VOICE)
                        .queue(voice -> this.opened(jda, proposal, room, text, voice.getIdLong()), error -> {
                            this.deleteChannel(guild, room, text.getIdLong());
                            failure.accept(error);
                        }),
                failure);
    }

    private void deleteChannel(Guild guild, Room room, Long channelId) {
        GuildChannel channel = channelId == null ? null : guild.getGuildChannelById(channelId);
        if (channel != null) {
            channel.delete().queue(done -> {
            }, error -> log.warn("Cannot delete room channel: room={}, channel={}", room.id(), channelId));
        }
    }

    private void opened(JDA jda, Proposal proposal, Room room, TextChannel text, long voiceChannelId) {
        this.roomService.attachChannels(room.id(), text.getIdLong(), voiceChannelId);
        Room current = this.roomService.findRoom(room.id()).orElse(room);
        if (!current.open()) {
            this.remove(jda, current);
            return;
        }
        Locale locale = this.guildConfigService.language(room.guildId());
        text.sendMessageComponents(RoomViews.welcome(current, voiceChannelId, locale))
                .useComponentsV2()
                .queue(message -> {
                }, error -> log.warn("Cannot send room welcome message: room={}", room.id()));
        this.messenger.showMatched(jda, proposal);
        log.info("Room opened: room={}, guild={}, number={}", room.id(), room.guildId(), room.number());
    }

    private void abandon(JDA jda, Proposal proposal, Room room, Throwable error) {
        this.roomService.close(room.id());
        this.messenger.showMatched(jda, proposal);
        log.error("Cannot create the channels of a match, members were sent each other's profile instead: "
                        + "guild={}, room={}, reason={}. Make sure the bot has the Manage Channels and "
                        + "Manage Roles permissions.",
                room.guildId(), room.id(), error == null ? "server unavailable" : error.getMessage());
    }

    private void findCategory(Guild guild, GuildSettings settings, Consumer<Category> then,
            Consumer<Throwable> failure) {
        Category configured = settings.categoryId() == null ? null : guild.getCategoryById(settings.categoryId());
        if (configured != null) {
            then.accept(configured);
            return;
        }
        List<Category> existing = guild.getCategoriesByName(DEFAULT_CATEGORY_NAME, true);
        if (!existing.isEmpty()) {
            then.accept(existing.getFirst());
            return;
        }
        guild.createCategory(DEFAULT_CATEGORY_NAME)
                .addPermissionOverride(guild.getPublicRole(), null, HIDDEN)
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), BOT_VOICE, null)
                .queue(then, failure);
    }

    private <T extends GuildChannel> ChannelAction<T> restrict(ChannelAction<T> action, Guild guild, Room room,
            Set<Permission> memberPermissions, Set<Permission> botPermissions) {
        return action
                .addPermissionOverride(guild.getPublicRole(), null, HIDDEN)
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), botPermissions, null)
                .addMemberPermissionOverride(room.firstUserId(), memberPermissions, null)
                .addMemberPermissionOverride(room.secondUserId(), memberPermissions, null);
    }
}
