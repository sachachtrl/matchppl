package io.github.sachachtrl.matchppl.scheduler;

import io.github.sachachtrl.matchppl.discord.MemberSnapshots;
import io.github.sachachtrl.matchppl.discord.ProposalMessenger;
import io.github.sachachtrl.matchppl.discord.RoomManager;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.service.HeartbeatService;
import io.github.sachachtrl.matchppl.service.MatchmakingService;
import io.github.sachachtrl.matchppl.service.ProposalService;
import io.github.sachachtrl.matchppl.service.RoomService;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchmakingTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MatchmakingTask.class);

    private final JDA jda;
    private final ProposalService proposalService;
    private final MatchmakingService matchmakingService;
    private final RoomService roomService;
    private final ProposalMessenger messenger;
    private final RoomManager roomManager;
    private final HeartbeatService heartbeatService;

    public MatchmakingTask(JDA jda, ProposalService proposalService, MatchmakingService matchmakingService,
            RoomService roomService, ProposalMessenger messenger, RoomManager roomManager,
            HeartbeatService heartbeatService) {
        this.jda = jda;
        this.proposalService = proposalService;
        this.matchmakingService = matchmakingService;
        this.roomService = roomService;
        this.messenger = messenger;
        this.roomManager = roomManager;
        this.heartbeatService = heartbeatService;
    }

    @Override
    public void run() {
        try {
            this.heartbeatService.beat();
        } catch (RuntimeException e) {
            log.error("Failed to record heartbeat", e);
        }
        try {
            for (Proposal proposal : this.proposalService.expireDue()) {
                this.messenger.showEnded(this.jda, proposal);
            }
        } catch (RuntimeException e) {
            log.error("Failed to close expired proposals", e);
        }
        try {
            for (Room room : this.roomService.closeDue()) {
                this.roomManager.remove(this.jda, room);
            }
            for (Room room : this.roomService.remindersDue()) {
                this.roomManager.remind(this.jda, room);
            }
        } catch (RuntimeException e) {
            log.error("Failed to process rooms", e);
        }
        for (Guild guild : this.jda.getGuilds()) {
            try {
                List<Proposal> created = this.matchmakingService.runRound(guild.getIdLong(),
                        userId -> Optional.ofNullable(guild.getMemberById(userId)).map(MemberSnapshots::of));
                for (Proposal proposal : created) {
                    this.messenger.deliver(this.jda, proposal);
                }
            } catch (RuntimeException e) {
                log.error("Matchmaking round failed: guild={}", guild.getIdLong(), e);
            }
        }
    }
}
