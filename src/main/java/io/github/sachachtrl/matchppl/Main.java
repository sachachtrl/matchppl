package io.github.sachachtrl.matchppl;

import io.github.sachachtrl.matchppl.discord.ActivityListener;
import io.github.sachachtrl.matchppl.discord.AdminListener;
import io.github.sachachtrl.matchppl.discord.MatchListener;
import io.github.sachachtrl.matchppl.repository.ActivityRepository;
import io.github.sachachtrl.matchppl.repository.Database;
import io.github.sachachtrl.matchppl.repository.GuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.ProfileRepository;
import io.github.sachachtrl.matchppl.repository.SqliteActivityRepository;
import io.github.sachachtrl.matchppl.repository.SqliteGuildConfigRepository;
import io.github.sachachtrl.matchppl.repository.SqliteProfileRepository;
import io.github.sachachtrl.matchppl.service.ActivityService;
import io.github.sachachtrl.matchppl.service.GuildConfigService;
import io.github.sachachtrl.matchppl.service.ProfileService;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.SQLException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public void main() throws InterruptedException {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String token = env.get("TOKEN");
        if (token == null || token.isBlank()) {
            log.error("No token");
            System.exit(1);
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
        ProfileRepository profileRepo = new SqliteProfileRepository(database);
        ProfileService profileService = new ProfileService(profileRepo);
        MatchListener matchListener = new MatchListener(profileService);
        GuildConfigRepository guildConfigRepo = new SqliteGuildConfigRepository(database);
        GuildConfigService guildConfigService = new GuildConfigService(guildConfigRepo);
        AdminListener adminListener = new AdminListener(guildConfigService);
        JDABuilder builder = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(activityListener, matchListener, adminListener);
        try {
            JDA bot = builder.build();
            bot.awaitReady();
            bot.updateCommands()
                    .addCommands(
                            MatchListener.command(),
                            AdminListener.setAdminCommand(),
                            AdminListener.removeAdminCommand())
                    .queue();
            log.info("Bot is logged in as {}", bot.getSelfUser().getAsTag());
        } catch (InvalidTokenException e) {
            log.error("Invalid token: {}", e.getMessage());
            System.exit(1);
        }
    }
}
