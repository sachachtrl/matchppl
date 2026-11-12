package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProposalService;
import io.github.sachachtrl.matchppl.service.RoomService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ReportListener.class);

    private static final String MODAL_PREFIX = "reportmodal:";
    private static final String REASON_INPUT = "reason";
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MAX_MANAGERS_NOTIFIED = 10;

    private record Report(long guildId, long otherUserId, String context) {}

    private final RoomService roomService;
    private final ProposalService proposalService;
    private final GuildConfigService guildConfigService;

    public ReportListener(RoomService roomService, ProposalService proposalService,
            GuildConfigService guildConfigService) {
        this.roomService = roomService;
        this.proposalService = proposalService;
        this.guildConfigService = guildConfigService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(RoomViews.REPORT_PREFIX)) {
            return;
        }
        String target = id.substring(RoomViews.REPORT_PREFIX.length());
        Optional<Report> report = this.find(target, event.getUser().getIdLong());
        Locale locale = this.languageOf(report);
        if (report.isEmpty()) {
            this.replyCard(event, Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                    Messages.get(locale, "room.unavailable")));
            return;
        }
        TextInput reason = TextInput.create(REASON_INPUT, TextInputStyle.PARAGRAPH)
                .setRequired(true)
                .setMaxLength(MAX_REASON_LENGTH)
                .build();
        event.replyModal(Modal.create(MODAL_PREFIX + target, Messages.get(locale, "report.modal.title"))
                        .addComponents(Label.of(Messages.get(locale, "report.modal.label"),
                                Messages.get(locale, "report.modal.hint"), reason))
                        .build())
                .queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith(MODAL_PREFIX)) {
            return;
        }
        long reporterId = event.getUser().getIdLong();
        Optional<Report> report = this.find(modalId.substring(MODAL_PREFIX.length()), reporterId);
        Locale locale = this.languageOf(report);
        Guild guild = report.map(found -> event.getJDA().getGuildById(found.guildId())).orElse(null);
        ModalMapping value = event.getValue(REASON_INPUT);
        if (report.isEmpty() || guild == null || value == null) {
            this.replyCard(event, Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                    Messages.get(locale, "room.unavailable")));
            return;
        }
        String reason = value.getAsString().strip();
        event.deferReply(true).queue();
        this.deliver(guild, reporterId, report.get(), reason, locale, sent -> {
            Container card = sent
                    ? Cards.simple(Cards.SUCCESS_COLOR, Messages.get(locale, "report.sent.title"),
                            Messages.get(locale, "report.sent"))
                    : Cards.simple(Cards.ERROR_COLOR, Messages.get(locale, "error.title"),
                            Messages.get(locale, "report.failed"));
            event.getHook().sendMessageComponents(card).useComponentsV2().setEphemeral(true).queue();
            log.info("Report submitted: guild={}, reporter={}, delivered={}", guild.getIdLong(), reporterId, sent);
        });
    }

    private Optional<Report> find(String target, long userId) {
        String[] parts = target.split(":");
        if (parts.length != 2) {
            return Optional.empty();
        }
        long id = Long.parseLong(parts[1]);
        return switch (parts[0]) {
            case RoomViews.REPORT_ROOM -> this.roomService.findRoom(id)
                    .filter(room -> room.involves(userId))
                    .map(room -> new Report(room.guildId(), room.otherUserId(userId), this.roomContext(room)));
            case RoomViews.REPORT_PROPOSAL -> this.proposalService.findProposal(id)
                    .filter(proposal -> proposal.involves(userId) && proposal.status() == ProposalStatus.MATCHED)
                    .map(proposal -> new Report(proposal.guildId(), proposal.otherUserId(userId),
                            this.proposalContext(proposal)));
            default -> Optional.empty();
        };
    }

    private Locale languageOf(Optional<Report> report) {
        return report.map(found -> this.guildConfigService.language(found.guildId()))
                .orElse(Messages.DEFAULT_LOCALE);
    }

    private String roomContext(Room room) {
        Locale locale = this.guildConfigService.language(room.guildId());
        String name = this.guildConfigService.settings(room.guildId()).roomPrefix() + " #" + room.number();
        String channel = room.textChannelId() == null ? "" : " (<#" + room.textChannelId() + ">)";
        return Messages.format(locale, "report.log.room", "**" + name + "**" + channel);
    }

    private String proposalContext(Proposal proposal) {
        Optional<Room> room = this.roomService.findByProposal(proposal.id())
                .filter(found -> found.textChannelId() != null);
        return room.isPresent()
                ? this.roomContext(room.get())
                : Messages.format(this.guildConfigService.language(proposal.guildId()), "report.log.direct",
                        "<t:" + proposal.createdAt().getEpochSecond() + ":f>");
    }

    private void deliver(Guild guild, long reporterId, Report report, String reason, Locale locale,
            Consumer<Boolean> done) {
        GuildSettings settings = this.guildConfigService.settings(guild.getIdLong());
        GuildMessageChannel channel = settings.logChannelId() == null
                ? null
                : guild.getChannelById(GuildMessageChannel.class, settings.logChannelId());
        Runnable fallback = () -> this.notifyManagers(guild,
                RoomViews.report(reporterId, report.otherUserId(), report.context(), reason, Set.of(), locale), done);
        if (channel == null) {
            fallback.run();
            return;
        }
        Container card = RoomViews.report(reporterId, report.otherUserId(), report.context(), reason,
                this.guildConfigService.listAdminRoles(guild.getIdLong()), locale);
        channel.sendMessageComponents(card).useComponentsV2().queue(message -> done.accept(true), error -> {
            log.warn("Cannot send report to the log channel: guild={}, channel={}", guild.getIdLong(),
                    channel.getIdLong());
            fallback.run();
        });
    }

    private void notifyManagers(Guild guild, Container card, Consumer<Boolean> done) {
        List<Member> managers = guild.getMembers().stream()
                .filter(member -> !member.getUser().isBot() && member.hasPermission(Permission.MANAGE_SERVER))
                .limit(MAX_MANAGERS_NOTIFIED)
                .toList();
        if (managers.isEmpty()) {
            done.accept(false);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(managers.size());
        AtomicBoolean delivered = new AtomicBoolean(false);
        Runnable finish = () -> {
            if (remaining.decrementAndGet() == 0) {
                done.accept(delivered.get());
            }
        };
        for (Member manager : managers) {
            manager.getUser().openPrivateChannel()
                    .flatMap(privateChannel -> privateChannel.sendMessageComponents(card).useComponentsV2())
                    .queue(message -> {
                        delivered.set(true);
                        finish.run();
                    }, error -> finish.run());
        }
    }

    private void replyCard(IReplyCallback event, Container card) {
        event.replyComponents(card).useComponentsV2().setEphemeral(true).queue();
    }
}
