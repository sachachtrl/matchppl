package io.github.sachachtrl.matchppl.repository;

import io.github.sachachtrl.matchppl.model.Profile;

import java.sql.SQLException;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyCheckTest {
    private static final TextCipher RIGHT_KEY = new TextCipher(new byte[32]);
    private static final TextCipher WRONG_KEY = new TextCipher(filled((byte) 1));

    private Database database;

    private static byte[] filled(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return key;
    }

    @BeforeEach
    void setUp() throws SQLException {
        this.database = new Database("jdbc:sqlite::memory:");
    }

    @AfterEach
    void tearDown() throws SQLException {
        this.database.close();
    }

    @Test
    void theFirstKeyIsRememberedAndAnotherOneIsRefused() {
        assertTrue(new KeyCheck(this.database, RIGHT_KEY).matches());

        assertTrue(new KeyCheck(this.database, RIGHT_KEY).matches());
        assertFalse(new KeyCheck(this.database, WRONG_KEY).matches());
    }

    @Test
    void anExistingDatabaseIsCheckedAgainstItsIntroductions() {
        new SqliteProfileRepository(this.database, RIGHT_KEY).save(Profile.empty(1L, 10L).withPresentation("Hello"));

        assertFalse(new KeyCheck(this.database, WRONG_KEY).matches());
        assertTrue(new KeyCheck(this.database, RIGHT_KEY).matches());
        assertFalse(new KeyCheck(this.database, WRONG_KEY).matches());
    }
}
