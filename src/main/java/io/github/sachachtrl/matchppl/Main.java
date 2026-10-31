package io.github.sachachtrl.matchppl;

import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    public void main() {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String token = env.get("TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Token absent");
            System.exit(1);
        }
    }
}
