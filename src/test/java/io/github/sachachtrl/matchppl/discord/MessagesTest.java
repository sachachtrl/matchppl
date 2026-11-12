package io.github.sachachtrl.matchppl.discord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?[sd%]");

    private static Properties load(String fileName) throws IOException {
        try (InputStream stream = MessagesTest.class.getResourceAsStream("/" + fileName)) {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        }
    }

    private static List<String> placeholders(String text) {
        return PLACEHOLDER.matcher(text).results().map(MatchResult::group).toList();
    }

    @Test
    void frenchHasExactlyTheEnglishKeysAndPlaceholders() throws IOException {
        Properties english = load("messages.properties");
        Properties french = load("messages_fr.properties");

        assertEquals(english.stringPropertyNames(), french.stringPropertyNames());
        for (String key : english.stringPropertyNames()) {
            assertEquals(placeholders(english.getProperty(key)), placeholders(french.getProperty(key)), key);
        }
    }

    @Test
    void eachLanguageReadsItsOwnFile() {
        assertEquals("Delete", Messages.get(Locale.ENGLISH, "tag.button.delete"));
        assertEquals("Supprimer", Messages.get(Locale.FRENCH, "tag.button.delete"));
        assertEquals("2 jours", DurationText.format(Duration.ofDays(2), Locale.FRENCH));
    }
}
