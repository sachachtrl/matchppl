package io.github.sachachtrl.matchppl.service;

import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;

import java.util.List;

public class MemberDataService {
    public record Forgotten(List<Proposal> interruptedProposals, List<Room> closedRooms) {}

    private final ProfileService profileService;
    private final ActivityService activityService;
    private final ProposalService proposalService;
    private final RoomService roomService;

    public MemberDataService(ProfileService profileService, ActivityService activityService,
            ProposalService proposalService, RoomService roomService) {
        this.profileService = profileService;
        this.activityService = activityService;
        this.proposalService = proposalService;
        this.roomService = roomService;
    }

    public synchronized Forgotten forgetLeavingMember(long guildId, long userId) {
        List<Proposal> interrupted = this.proposalService.interruptAllFor(guildId, userId);
        List<Room> closed = this.roomService.closeAllFor(guildId, userId);
        this.deleteData(guildId, userId);
        return new Forgotten(interrupted, closed);
    }

    public synchronized Forgotten deleteOnRequest(long guildId, long userId) {
        List<Proposal> interrupted = this.proposalService.interruptAllFor(guildId, userId);
        this.deleteData(guildId, userId);
        return new Forgotten(interrupted, List.of());
    }

    private void deleteData(long guildId, long userId) {
        this.profileService.deleteProfile(guildId, userId);
        this.activityService.forgetMember(guildId, userId);
    }
}
