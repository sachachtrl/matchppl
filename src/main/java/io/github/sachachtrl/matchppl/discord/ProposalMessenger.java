package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.ProposalStatus;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.ProfileService;
import io.github.sachachtrl.matchppl.service.ProposalService;
import io.github.sachachtrl.matchppl.service.RoomService;
import io.github.sachachtrl.matchppl.service.TagService;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProposalMessenger {
    private static final Logger log = LoggerFactory.getLogger(ProposalMessenger.class);

    private final ProposalService proposalService;
    private final ProfileService profileService;
    private final TagService tagService;
    private final RoomService roomService;

    public ProposalMessenger(ProposalService proposalService, ProfileService profileService, TagService tagService,
            RoomService roomService) {
        this.proposalService = proposalService;
        this.profileService = profileService;
        this.tagService = tagService;
        this.roomService = roomService;
    }

    public void deliver(JDA jda, Proposal proposal) {
        this.send(jda, proposal, proposal.firstUserId(),
                () -> this.send(jda, proposal, proposal.secondUserId(),
                        () -> log.info("Proposal sent: proposal={}, guild={}", proposal.id(), proposal.guildId()),
                        () -> this.proposalService.findProposal(proposal.id()).ifPresent(updated ->
                                this.update(jda, updated, updated.firstUserId(),
                                        ProposalViews.CardState.UNAVAILABLE))),
                () -> {
                });
    }

    public void showEnded(JDA jda, Proposal proposal) {
        this.updateMembersStillWaiting(jda, proposal, ProposalViews.CardState.ENDED);
    }

    public void showUnavailable(JDA jda, Proposal proposal) {
        this.updateMembersStillWaiting(jda, proposal, ProposalViews.CardState.UNAVAILABLE);
    }

    void update(JDA jda, Proposal proposal, long viewerId, ProposalViews.CardState state) {
        Long messageId = proposal.messageIdOf(viewerId);
        if (messageId == null) {
            return;
        }
        Container card = this.render(jda, proposal, viewerId, state);
        jda.retrieveUserById(viewerId)
                .flatMap(User::openPrivateChannel)
                .flatMap(channel -> channel.editMessageComponentsById(messageId, card).useComponentsV2())
                .queue(message -> {
                }, error -> log.warn("Cannot update proposal message: proposal={}, user={}",
                        proposal.id(), viewerId));
    }

    ProposalViews.CardState stateFor(Proposal proposal, long viewerId) {
        if (proposal.status() == ProposalStatus.MATCHED) {
            return ProposalViews.CardState.MATCHED;
        }
        if (proposal.status() == ProposalStatus.INTERRUPTED) {
            return ProposalViews.CardState.UNAVAILABLE;
        }
        boolean open = proposal.status() == ProposalStatus.OPEN;
        return switch (proposal.answerOf(viewerId)) {
            case PENDING -> open ? ProposalViews.CardState.PENDING : ProposalViews.CardState.ENDED;
            case ACCEPTED -> open ? ProposalViews.CardState.ACCEPTED : ProposalViews.CardState.ENDED;
            case SKIPPED -> ProposalViews.CardState.SKIPPED;
            case CANCELLED -> ProposalViews.CardState.CANCELLED;
        };
    }

    Container render(JDA jda, Proposal proposal, long viewerId, ProposalViews.CardState state) {
        Locale locale = Messages.DEFAULT_LOCALE;
        long guildId = proposal.guildId();
        long otherId = proposal.otherUserId(viewerId);
        Guild guild = jda.getGuildById(guildId);
        Profile viewerProfile = this.profileService.getProfile(guildId, viewerId);
        Profile otherProfile = this.profileService.getProfile(guildId, otherId);
        boolean hidden = state != ProposalViews.CardState.MATCHED
                && (viewerProfile.anonymous() || otherProfile.anonymous());
        Member otherMember = guild == null ? null : guild.getMemberById(otherId);
        String name;
        if (hidden) {
            name = Messages.get(locale, "proposal.anonymous");
        } else if (otherMember == null) {
            name = Messages.get(locale, "proposal.unknown_member");
        } else {
            name = otherMember.getEffectiveName();
        }
        String avatarUrl = hidden || otherMember == null ? null : otherMember.getEffectiveAvatarUrl();
        List<String> commonTags = this.tagService.listTags(guildId).stream()
                .filter(tag -> viewerProfile.tagIds().contains(tag.id()) && otherProfile.tagIds().contains(tag.id()))
                .map(TagViews::title)
                .toList();
        String guildName = guild == null ? Messages.get(locale, "proposal.unknown_server") : guild.getName();
        ProposalViews.MatchInfo match = state == ProposalViews.CardState.MATCHED
                ? this.matchInfo(proposal, otherId, locale)
                : null;
        return ProposalViews.card(proposal.id(), state, guildName,
                new ProposalViews.OtherMember(name, avatarUrl, otherProfile.presentation()), commonTags,
                proposal.deadline(), match, locale);
    }

    void showMatched(JDA jda, Proposal proposal) {
        this.update(jda, proposal, proposal.firstUserId(), ProposalViews.CardState.MATCHED);
        this.update(jda, proposal, proposal.secondUserId(), ProposalViews.CardState.MATCHED);
    }

    private ProposalViews.MatchInfo matchInfo(Proposal proposal, long otherId, Locale locale) {
        Optional<Room> room = this.roomService.findByProposal(proposal.id());
        if (room.isPresent() && room.get().textChannelId() != null) {
            return room.get().open()
                    ? new ProposalViews.MatchInfo(Messages.format(locale, "proposal.state.matched.room",
                            "<#" + room.get().textChannelId() + ">"), false)
                    : new ProposalViews.MatchInfo(Messages.get(locale, "proposal.state.matched.room_closed"), true);
        }
        if (room.isPresent() && room.get().open()) {
            return new ProposalViews.MatchInfo(Messages.get(locale, "proposal.state.matched.creating"), false);
        }
        return new ProposalViews.MatchInfo(Messages.format(locale, "proposal.state.matched.direct",
                "<@" + otherId + ">"), true);
    }

    private void send(JDA jda, Proposal proposal, long viewerId, Runnable onSuccess, Runnable onFailure) {
        Container card = this.render(jda, proposal, viewerId, ProposalViews.CardState.PENDING);
        jda.retrieveUserById(viewerId)
                .flatMap(User::openPrivateChannel)
                .flatMap(channel -> channel.sendMessageComponents(card).useComponentsV2())
                .queue(message -> {
                    this.proposalService.recordMessage(proposal.id(), viewerId, message.getIdLong());
                    onSuccess.run();
                }, error -> {
                    this.handleDeliveryFailure(proposal, viewerId, error);
                    onFailure.run();
                });
    }

    private void handleDeliveryFailure(Proposal proposal, long viewerId, Throwable error) {
        this.proposalService.interrupt(proposal.id());
        boolean unreachable = error instanceof ErrorResponseException response
                && response.getErrorResponse() == ErrorResponse.CANNOT_SEND_TO_USER;
        if (unreachable) {
            this.profileService.markUnreachable(proposal.guildId(), viewerId);
        }
        log.info("Proposal not delivered: proposal={}, user={}, unreachable={}", proposal.id(), viewerId, unreachable);
    }

    private void updateMembersStillWaiting(JDA jda, Proposal proposal, ProposalViews.CardState state) {
        for (long userId : List.of(proposal.firstUserId(), proposal.secondUserId())) {
            if (proposal.answerOf(userId).keepsMemberBusy()) {
                this.proposalService.findProposal(proposal.id())
                        .ifPresent(updated -> this.update(jda, updated, userId, state));
            }
        }
    }
}
