package io.github.sachachtrl.matchppl.discord;

import java.text.BreakIterator;
import java.util.function.LongPredicate;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

final class TagEmojis {
    private static final int MAX_LENGTH = 64;
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int VARIATION_SELECTOR = 0xFE0F;
    private static final int KEYCAP = 0x20E3;
    private static final int FIRST_TAG = 0xE0020;
    private static final int LAST_TAG = 0xE007F;

    private TagEmojis() {
    }

    static boolean isValidUnicode(String input) {
        if (input.isEmpty() || input.length() > MAX_LENGTH || graphemes(input) != 1) {
            return false;
        }
        boolean allowed = input.codePoints().allMatch(TagEmojis::isEmojiPart);
        boolean visible = input.codePoints().anyMatch(c -> Character.isEmojiPresentation(c)
                || Character.isExtendedPictographic(c) || c == KEYCAP);
        return allowed && visible;
    }

    static Emoji forMenu(String stored, LongPredicate customEmojiExists) {
        if (stored.startsWith("<")) {
            try {
                EmojiUnion emoji = Emoji.fromFormatted(stored);
                return emoji.getType() == Emoji.Type.CUSTOM && customEmojiExists.test(emoji.asCustom().getIdLong())
                        ? emoji
                        : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return isValidUnicode(stored) ? Emoji.fromUnicode(stored) : null;
    }

    private static boolean isEmojiPart(int c) {
        return Character.isEmoji(c) || Character.isEmojiComponent(c) || Character.isEmojiModifier(c)
                || c == ZERO_WIDTH_JOINER || c == VARIATION_SELECTOR || (c >= FIRST_TAG && c <= LAST_TAG);
    }

    private static int graphemes(String input) {
        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(input);
        int count = 0;
        while (iterator.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
