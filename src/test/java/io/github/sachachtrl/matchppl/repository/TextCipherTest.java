package io.github.sachachtrl.matchppl.repository;

import java.security.GeneralSecurityException;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextCipherTest {
    static final TextCipher CIPHER = new TextCipher(new byte[32]);
    private static final String CONTEXT = "profile:1:10";

    @Test
    void decryptingReturnsTheOriginalText() throws GeneralSecurityException {
        String encrypted = CIPHER.encrypt("Chill player, mostly evenings 🎮", CONTEXT);

        assertEquals("Chill player, mostly evenings 🎮", CIPHER.decrypt(encrypted, CONTEXT));
    }

    @Test
    void encryptedTextDoesNotContainThePlainText() {
        String encrypted = CIPHER.encrypt("secret introduction", CONTEXT);

        assertFalse(new String(Base64.getDecoder().decode(encrypted)).contains("secret"));
    }

    @Test
    void encryptingTwiceGivesDifferentResults() {
        assertNotEquals(CIPHER.encrypt("same text", CONTEXT), CIPHER.encrypt("same text", CONTEXT));
    }

    @Test
    void textEncryptedForAnotherProfileCannotBeDecrypted() {
        String encrypted = CIPHER.encrypt("introduction", CONTEXT);

        assertThrows(GeneralSecurityException.class, () -> CIPHER.decrypt(encrypted, "profile:1:20"));
    }

    @Test
    void modifiedTextIsRejected() {
        byte[] data = Base64.getDecoder().decode(CIPHER.encrypt("introduction", CONTEXT));
        data[data.length - 1] ^= 1;

        assertThrows(GeneralSecurityException.class,
                () -> CIPHER.decrypt(Base64.getEncoder().encodeToString(data), CONTEXT));
    }

    @Test
    void anotherKeyCannotDecrypt() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String encrypted = CIPHER.encrypt("introduction", CONTEXT);

        assertThrows(GeneralSecurityException.class, () -> new TextCipher(otherKey).decrypt(encrypted, CONTEXT));
    }

    @Test
    void keyMustBe32BytesLong() {
        assertThrows(IllegalArgumentException.class, () -> new TextCipher(new byte[16]));
    }
}
