package io.github.sachachtrl.matchppl;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {
    public void main() throws InterruptedException {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String token = env.get("TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("No token");
            System.exit(1);
        }
        JDABuilder builder = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES);
        try {
            JDA bot = builder.build();
            bot.awaitReady();
            System.out.println("Bot is logged as " + bot.getSelfUser().getName());
        } catch (InvalidTokenException e) {
            System.err.println("Invalid token : " + e.getMessage());
            System.exit(1);
        }
    }
}
