package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.service.GuildDataService;
import io.github.sachachtrl.matchppl.service.ProposalService;

import java.util.List;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(GuildListener.class);

    private final GuildDataService guildDataService;
    private final ProposalService proposalService;
    private final ProposalMessenger messenger;

    public GuildListener(GuildDataService guildDataService, ProposalService proposalService,
            ProposalMessenger messenger) {
        this.guildDataService = guildDataService;
        this.proposalService = proposalService;
        this.messenger = messenger;
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        long guildId = event.getGuild().getIdLong();
        List<Proposal> open = this.proposalService.openProposals(guildId);
        for (Proposal proposal : open) {
            this.messenger.showServerLeft(event.getJDA(), proposal);
        }
        this.guildDataService.forgetGuild(guildId);
        log.info("Bot removed from a server, all its data deleted: guild={}, openProposals={}", guildId, open.size());
    }
}
