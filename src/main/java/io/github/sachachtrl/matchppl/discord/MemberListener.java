package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.ActivityService;
import io.github.sachachtrl.matchppl.service.ProfileService;
import io.github.sachachtrl.matchppl.service.ProposalService;
import io.github.sachachtrl.matchppl.service.RoomService;

import java.util.List;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemberListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(MemberListener.class);

    private final ProfileService profileService;
    private final ProposalService proposalService;
    private final RoomService roomService;
    private final ProposalMessenger messenger;
    private final RoomManager roomManager;
    private final ActivityService activityService;

    public MemberListener(ProfileService profileService, ProposalService proposalService, RoomService roomService,
            ProposalMessenger messenger, RoomManager roomManager, ActivityService activityService) {
        this.profileService = profileService;
        this.proposalService = proposalService;
        this.roomService = roomService;
        this.messenger = messenger;
        this.roomManager = roomManager;
        this.activityService = activityService;
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        long guildId = event.getGuild().getIdLong();
        long userId = event.getUser().getIdLong();
        List<Proposal> interrupted = this.proposalService.interruptAllFor(guildId, userId);
        List<Room> closed = this.roomService.closeAllFor(guildId, userId);
        this.profileService.deleteProfile(guildId, userId);
        this.activityService.forgetMember(guildId, userId);
        for (Proposal proposal : interrupted) {
            this.messenger.showUnavailable(event.getJDA(), proposal);
        }
        for (Room room : closed) {
            this.roomManager.remove(event.getJDA(), room);
        }
        log.info("Member left, profile and activity deleted: guild={}, user={}, interruptedProposals={}, "
                        + "closedRooms={}",
                guildId, userId, interrupted.size(), closed.size());
    }
}
