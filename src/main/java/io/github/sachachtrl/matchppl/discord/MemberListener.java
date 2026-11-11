package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.service.ProfileService;
import io.github.sachachtrl.matchppl.service.ProposalService;

import java.util.List;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemberListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MemberListener.class);

    private final ProfileService profileService;
    private final ProposalService proposalService;
    private final ProposalMessenger messenger;

    public MemberListener(ProfileService profileService, ProposalService proposalService,
            ProposalMessenger messenger) {
        this.profileService = profileService;
        this.proposalService = proposalService;
        this.messenger = messenger;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        List<Proposal> interrupted = this.proposalService.interruptAllFor(guildId, userId);
        this.profileService.deleteProfile(guildId, userId);
        for (Proposal proposal : interrupted) {
            this.messenger.showUnavailable(event.getJDA(), proposal);
        }
        log.info("Member left, profile deleted: guild={}, user={}, interruptedProposals={}",
                guildId, userId, interrupted.size());
    }
}
