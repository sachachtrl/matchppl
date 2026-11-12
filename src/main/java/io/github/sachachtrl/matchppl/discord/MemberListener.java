package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.MemberDataService;

import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemberListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MemberListener.class);

    private final MemberDataService memberDataService;
    private final ProposalMessenger messenger;
    private final RoomManager roomManager;

    public MemberListener(MemberDataService memberDataService, ProposalMessenger messenger, RoomManager roomManager) {
        this.memberDataService = memberDataService;
        this.messenger = messenger;
        this.roomManager = roomManager;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        MemberDataService.Forgotten forgotten = this.memberDataService.forgetLeavingMember(guildId, userId);
        for (Proposal proposal : forgotten.interruptedProposals()) {
            this.messenger.showUnavailable(event.getJDA(), proposal);
        }
        for (Room room : forgotten.closedRooms()) {
            this.roomManager.remove(event.getJDA(), room);
        }
        log.info("Member left, profile and activity deleted: guild={}, user={}, interruptedProposals={}, "
                        + "closedRooms={}",
                guildId, userId, forgotten.interruptedProposals().size(), forgotten.closedRooms().size());
    }
}
