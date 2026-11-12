package io.github.sachachtrl.matchppl;

import io.github.sachachtrl.matchppl.discord.ActivityListener;
import io.github.sachachtrl.matchppl.discord.AdminListener;
import io.github.sachachtrl.matchppl.discord.BlockListener;
import io.github.sachachtrl.matchppl.discord.BotCommands;
import io.github.sachachtrl.matchppl.discord.ConfigListener;
import io.github.sachachtrl.matchppl.discord.GuildListener;
import io.github.sachachtrl.matchppl.discord.InteractionTimer;
import io.github.sachachtrl.matchppl.discord.MatchListener;
import io.github.sachachtrl.matchppl.discord.MemberListener;
import io.github.sachachtrl.matchppl.discord.ProposalListener;
import io.github.sachachtrl.matchppl.discord.ProposalMessenger;
import io.github.sachachtrl.matchppl.discord.ReportListener;
import io.github.sachachtrl.matchppl.discord.ResetListener;
import io.github.sachachtrl.matchppl.discord.RoomListener;
import io.github.sachachtrl.matchppl.discord.RoomManager;
import io.github.sachachtrl.matchppl.discord.TagListener;
import io.github.sachachtrl.matchppl.model.Proposal;
import io.github.sachachtrl.matchppl.model.Room;
import io.github.sachachtrl.matchppl.repository.ActivityRepository;
import io.github.sachachtrl.matchppl.repository.BlockRepository;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.GuildDataRepository;
import io.github.sachachtrl.matchppl.repository.HeartbeatRepository;
import io.github.sachachtrl.matchppl.repository.KeyCheck;
import io.github.sachachtrl.matchppl.repository.ProfileRepository;
import io.github.sachachtrl.matchppl.repository.ProposalRepository;
import io.github.sachachtrl.matchppl.repository.RoomRepository;
import io.github.sachachtrl.matchppl.repository.SqliteActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteBlockRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildDataRepository;
import io.github.sachachtrl.matchppl.repository.SqliteHeartbeatRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProposalRepository;
import io.github.sachachtrl.matchppl.repository.SqliteRoomRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;
import io.github.sachachtrl.matchppl.repository.TagRepository;
import io.github.sachachtrl.matchppl.repository.TextCipher;
import io.github.sachachtrl.matchppl.scheduler.ActivityPurgeTask;
import io.github.sachachtrl.matchppl.scheduler.MatchmakingTask;
import io.github.sachachtrl.matchppl.service.ActivityService;
import io.github.sachachtrl.matchppl.service.BlockService;
import io.github.sachachtrl.matchppl.service.ConditionChecker;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.GuildDataService;
import io.github.sachachtrl.matchppl.service.HeartbeatService;
import io.github.sachachtrl.matchppl.service.MatchingService;
import io.github.sachachtrl.matchppl.service.MatchmakingService;
import io.github.sachachtrl.matchppl.service.MemberDataService;
import io.github.sachachtrl.matchppl.service.ProfileService;
import io.github.sachachtrl.matchppl.service.ProposalService;
import io.github.sachachtrl.matchppl.service.RoomService;
import io.github.sachachtrl.matchppl.service.TagService;

import io.github.cdimascio.dotenv.Dotenv;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final Duration HTTP_PING_INTERVAL = Duration.ofSeconds(15);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final long FIRST_ROUND_DELAY_SECONDS = 10;
    private static final long ROUND_PERIOD_SECONDS = 60;
    private static final long PURGE_PERIOD_HOURS = 24;

    public void main() throws InterruptedException {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String token = env.get("TOKEN");
        if (token == null || token.isBlank()) {
            log.error("No token");
            System.exit(1);
        }
        TextCipher cipher;
        try {
            cipher = TextCipher.fromBase64(env.get("PROFILE_KEY", ""));
        } catch (IllegalArgumentException e) {
            log.error("PROFILE_KEY is missing or invalid: it must be 32 random bytes encoded in Base64");
            System.exit(1);
            return;
        }
        Database database;
        try {
            database = new Database("jdbc:sqlite:matchppl.db");
        } catch (SQLException e) {
            log.error("Cannot open database: {}", e.getMessage());
            System.exit(1);
            return;
        }
        if (!new KeyCheck(database, cipher).matches()) {
            log.error("PROFILE_KEY does not match the key used to encrypt this database: refusing to start so that "
                    + "no introduction is lost. Put back the original key.");
            System.exit(1);
            return;
        }
        Clock clock = Clock.systemUTC();

        ActivityRepository activityRepo = new SqliteActivityRepository(database);
        ActivityService activityService = new ActivityService(activityRepo);
        ActivityListener activityListener = new ActivityListener(activityService);
        ConditionChecker conditionChecker = new ConditionChecker(activityService, clock);

        GuildConfigRepository guildConfigRepo = new SqliteGuildConfigRepository(database);
        GuildConfigService guildConfigService = new GuildConfigService(guildConfigRepo);
        AdminListener adminListener = new AdminListener(guildConfigService);

        TagRepository tagRepo = new SqliteTagRepository(database);
        TagService tagService = new TagService(tagRepo, conditionChecker);
        TagListener tagListener = new TagListener(tagService, guildConfigService);

        ProfileRepository profileRepo = new SqliteProfileRepository(database, cipher);
        ProfileService profileService = new ProfileService(profileRepo, tagService, clock);
        ConfigListener configListener = new ConfigListener(guildConfigService, profileService);

        ProposalRepository proposalRepo = new SqliteProposalRepository(database);
        RoomRepository roomRepo = new SqliteRoomRepository(database);
        RoomService roomService = new RoomService(roomRepo, profileService, guildConfigService, clock);
        ProposalService proposalService = new ProposalService(proposalRepo, profileService, roomService, clock);
        BlockRepository blockRepo = new SqliteBlockRepository(database);
        BlockService blockService = new BlockService(blockRepo, proposalService, clock);
        MatchingService matchingService = new MatchingService(conditionChecker, new SecureRandom());
        MatchmakingService matchmakingService = new MatchmakingService(profileService, tagService, matchingService,
                proposalService, guildConfigService, blockService, clock);
        ProposalMessenger messenger = new ProposalMessenger(proposalService, profileService, tagService,
                roomService, guildConfigService);
        RoomManager roomManager = new RoomManager(roomService, proposalService, guildConfigService, messenger);
        ProposalListener proposalListener = new ProposalListener(proposalService, messenger, roomManager);
        RoomListener roomListener = new RoomListener(roomService, roomManager, guildConfigService);
        ReportListener reportListener = new ReportListener(roomService, proposalService, guildConfigService);
        MemberDataService memberDataService = new MemberDataService(profileService, activityService,
                proposalService, roomService, blockService);
        MemberListener memberListener = new MemberListener(memberDataService, messenger, roomManager);
        MatchListener matchListener = new MatchListener(profileService, tagService, guildConfigService,
                memberDataService, messenger);
        ResetListener resetListener = new ResetListener(proposalService, guildConfigService);
        BlockListener blockListener = new BlockListener(blockService, guildConfigService, messenger);
        GuildDataRepository guildDataRepo = new SqliteGuildDataRepository(database);
        GuildDataService guildDataService = new GuildDataService(guildDataRepo);
        GuildListener guildListener = new GuildListener(guildDataService, proposalService, messenger);
        InteractionTimer interactionTimer = new InteractionTimer(clock);

        HeartbeatRepository heartbeatRepo = new SqliteHeartbeatRepository(database);
        HeartbeatService heartbeatService = new HeartbeatService(heartbeatRepo, proposalService, clock);
        Optional<Duration> downtime = heartbeatService.downtime();
        List<Proposal> interrupted = heartbeatService.recoverFromDowntime();
        downtime.ifPresent(gap -> log.warn("Bot was offline for {} min: {} ongoing proposals interrupted",
                gap.toMinutes(), interrupted.size()));
        List<Room> unfinishedRooms = roomService.closeUnfinished();
        if (!unfinishedRooms.isEmpty()) {
            log.warn("{} matches had no channel yet when the bot stopped: members were put in touch directly",
                    unfinishedRooms.size());
        }

        JDABuilder builder = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EXPRESSIONS)
                .enableCache(CacheFlag.EMOJI)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setHttpClientBuilder(new OkHttpClient.Builder().pingInterval(HTTP_PING_INTERVAL))
                .addEventListeners(interactionTimer.arrivalListener())
                .addEventListeners(activityListener, matchListener, adminListener, tagListener, proposalListener,
                        memberListener, resetListener, configListener, roomListener, reportListener, guildListener,
                        blockListener)
                .addEventListeners(interactionTimer.completionListener());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "matchmaking");
            thread.setDaemon(true);
            return thread;
        });
        try {
            JDA bot = builder.build();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(bot, database, scheduler)));
            bot.awaitReady();
            for (Proposal proposal : interrupted) {
                messenger.showInterrupted(bot, proposal);
            }
            for (Room room : unfinishedRooms) {
                roomManager.remove(bot, room);
            }
            bot.updateCommands().addCommands(BotCommands.all()).queue();
            scheduler.scheduleAtFixedRate(new MatchmakingTask(bot, proposalService, matchmakingService, roomService,
                            messenger, roomManager, heartbeatService),
                    FIRST_ROUND_DELAY_SECONDS, ROUND_PERIOD_SECONDS, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(new ActivityPurgeTask(activityService, clock), 0, PURGE_PERIOD_HOURS,
                    TimeUnit.HOURS);
            log.info("Bot is logged in as {}", bot.getSelfUser().getAsTag());
        } catch (InvalidTokenException e) {
            log.error("Invalid token: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void shutdown(JDA bot, Database database, ScheduledExecutorService scheduler) {
        log.info("Shutting down");
        scheduler.shutdownNow();
        bot.shutdown();
        try {
            if (!bot.awaitShutdown(SHUTDOWN_TIMEOUT)) {
                bot.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            database.close();
        } catch (SQLException e) {
            log.error("Cannot close database: {}", e.getMessage());
        }
        log.info("Bot stopped");
    }
}
