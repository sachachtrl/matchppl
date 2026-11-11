package io.github.sachachtrl.matchppl;

import io.github.sachachtrl.matchppl.discord.ActivityListener;
import io.github.sachachtrl.matchppl.discord.AdminListener;
import io.github.sachachtrl.matchppl.discord.MatchListener;
import io.github.sachachtrl.matchppl.discord.TagListener;
import io.github.sachachtrl.matchppl.repository.ActivityRepository;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.ProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteTagRepository;
import io.github.sachachtrl.matchppl.repository.TagRepository;
import io.github.sachachtrl.matchppl.repository.TextCipher;
import io.github.sachachtrl.matchppl.service.ActivityService;
import io.github.sachachtrl.matchppl.service.ConditionChecker;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProfileService;
import io.github.sachachtrl.matchppl.service.TagService;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final Duration HTTP_PING_INTERVAL = Duration.ofSeconds(15);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

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
        ActivityRepository activityRepo = new SqliteActivityRepository(database);
        ActivityService activityService = new ActivityService(activityRepo);
        ActivityListener activityListener = new ActivityListener(activityService);
        ConditionChecker conditionChecker = new ConditionChecker(activityService, Clock.systemUTC());
        GuildConfigRepository guildConfigRepo = new SqliteGuildConfigRepository(database);
        GuildConfigService guildConfigService = new GuildConfigService(guildConfigRepo);
        AdminListener adminListener = new AdminListener(guildConfigService);
        TagRepository tagRepo = new SqliteTagRepository(database);
        TagService tagService = new TagService(tagRepo, conditionChecker);
        TagListener tagListener = new TagListener(tagService, guildConfigService);
        ProfileRepository profileRepo = new SqliteProfileRepository(database, cipher);
        ProfileService profileService = new ProfileService(profileRepo, tagService, Clock.systemUTC());
        MatchListener matchListener = new MatchListener(profileService, tagService);
        JDABuilder builder = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                .setHttpClientBuilder(new OkHttpClient.Builder().pingInterval(HTTP_PING_INTERVAL))
                .addEventListeners(activityListener, matchListener, adminListener, tagListener);
        try {
            JDA bot = builder.build();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(bot, database)));
            bot.awaitReady();
            bot.updateCommands()
                    .addCommands(
                            MatchListener.command(),
                            AdminListener.setAdminCommand(),
                            AdminListener.removeAdminCommand(),
                            AdminListener.listAdminCommand(),
                            TagListener.newTagCommand(),
                            TagListener.tagsCommand())
                    .queue();
            log.info("Bot is logged in as {}", bot.getSelfUser().getAsTag());
        } catch (InvalidTokenException e) {
            log.error("Invalid token: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void shutdown(JDA bot, Database database) {
        log.info("Shutting down");
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
