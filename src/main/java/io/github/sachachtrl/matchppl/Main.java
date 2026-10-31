package io.github.sachachtrl.matchppl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public void main() throws InterruptedException {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String token = env.get("TOKEN");
        if (token == null || token.isBlank()) {
            log.error("No token");
            System.exit(1);
        }
        JDABuilder builder = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES);
        try {
            JDA bot = builder.build();
            bot.awaitReady();
            log.info("Bot is logged in as {}", bot.getSelfUser().getAsTag());
        } catch (InvalidTokenException e) {
            log.error("Invalid token : {}", e.getMessage());
            System.exit(1);
        }
    }
}
