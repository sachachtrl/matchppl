package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.service.ProposalService;

import java.util.Optional;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProposalListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ProposalListener.class);

    private final ProposalService proposalService;
    private final ProposalMessenger messenger;

    public ProposalListener(ProposalService proposalService, ProposalMessenger messenger) {
        this.proposalService = proposalService;
        this.messenger = messenger;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(ProposalViews.PREFIX)) {
            return;
        }
        String[] parts = id.substring(ProposalViews.PREFIX.length()).split(":");
        ProposalService.Action action = switch (parts[0]) {
            case ProposalViews.ACCEPT -> ProposalService.Action.ACCEPT;
            case ProposalViews.SKIP -> ProposalService.Action.SKIP;
            case ProposalViews.CANCEL -> ProposalService.Action.CANCEL;
            default -> null;
        };
        if (action == null || parts.length != 2) {
            return;
        }
        long proposalId = Long.parseLong(parts[1]);
        long userId = event.getUser().getIdLong();
        switch (this.proposalService.answer(proposalId, userId, action)) {
            case ProposalService.Accepted accepted ->
                    this.show(event, accepted.proposal(), userId, ProposalViews.CardState.ACCEPTED);
            case ProposalService.Skipped skipped ->
                    this.show(event, skipped.proposal(), userId, ProposalViews.CardState.SKIPPED);
            case ProposalService.Cancelled cancelled ->
                    this.show(event, cancelled.proposal(), userId, ProposalViews.CardState.CANCELLED);
            case ProposalService.Matched matched -> {
                Proposal proposal = matched.proposal();
                this.show(event, proposal, userId, ProposalViews.CardState.MATCHED);
                this.messenger.update(event.getJDA(), proposal, proposal.otherUserId(userId),
                        ProposalViews.CardState.MATCHED);
                log.info("Match: proposal={}, guild={}", proposal.id(), proposal.guildId());
            }
            case ProposalService.Ignored _ -> {
                Optional<Proposal> current = this.proposalService.findProposal(proposalId)
                        .filter(proposal -> proposal.involves(userId));
                if (current.isPresent()) {
                    this.show(event, current.get(), userId, this.messenger.stateFor(current.get(), userId));
                } else {
                    event.deferEdit().queue();
                }
            }
        }
    }

    private void show(ButtonInteractionEvent event, Proposal proposal, long userId, ProposalViews.CardState state) {
        event.editComponents(this.messenger.render(event.getJDA(), proposal, userId, state))
                .useComponentsV2()
                .queue();
    }
}
