package io.github.sachachtrl.matchppl.discord;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagEmojisTest {
    @Test
    void realEmojisAreAccepted() {
        List<String> emojis = List.of("🎮", "❤️", "👍🏽", "👨‍👩‍👧", "🇫🇷", "1️⃣",
                "🏳️‍🌈");
        for (String emoji : emojis) {
            assertTrue(TagEmojis.isValidUnicode(emoji), emoji);
        }
    }

    @Test
    void symbolsTextAndSeveralEmojisAreRefused() {
        for (String input : List.of("", "!", "->", "*", "#", "7", "a", "🎮🎮", "🎮!", "♥ love")) {
            assertFalse(TagEmojis.isValidUnicode(input), input);
        }
    }

    @Test
    void anInvalidStoredEmojiIsLeftOutOfMenus() {
        assertNull(TagEmojis.forMenu("!", emojiId -> true));
        assertNotNull(TagEmojis.forMenu("🎮", emojiId -> true));
        assertNotNull(TagEmojis.forMenu("<:pepe:123456789012345678>", emojiId -> true));
    }

    @Test
    void aDeletedCustomEmojiIsLeftOutOfMenus() {
        assertNull(TagEmojis.forMenu("<:pepe:123456789012345678>", emojiId -> false));
        assertNotNull(TagEmojis.forMenu("<:pepe:123456789012345678>", emojiId -> emojiId == 123456789012345678L));
        assertNotNull(TagEmojis.forMenu("🎮", emojiId -> false));
    }
}
