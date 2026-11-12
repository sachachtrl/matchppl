package io.github.sachachtrl.matchppl.repository;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class KeyCheck {
    private static final String CONTEXT = "key-check";
    private static final String WITNESS = "matchppl";
    private static final String FIND_SQL = """
            SELECT value FROM key_check WHERE id = 1
            """;
    private static final String SAVE_SQL = """
            INSERT INTO key_check (id, value) VALUES (1, ?)
            """;
    private static final String FIND_PRESENTATION_SQL = """
            SELECT guild_id, user_id, presentation FROM profile WHERE presentation IS NOT NULL LIMIT 1
            """;

    private final Connection connection;
    private final TextCipher cipher;

    public KeyCheck(Database database, TextCipher cipher) {
        this.connection = database.getConnection();
        this.cipher = cipher;
    }

    public boolean matches() {
        synchronized (this.connection) {
            try {
                String witness = this.findWitness();
                if (witness != null) {
                    return this.decrypts(witness, CONTEXT, WITNESS);
                }
                if (!this.decryptsAnExistingPresentation()) {
                    return false;
                }
                try (PreparedStatement statement = this.connection.prepareStatement(SAVE_SQL)) {
                    statement.setString(1, this.cipher.encrypt(WITNESS, CONTEXT));
                    statement.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                throw new RepositoryException("Failed to check the encryption key", e);
            }
        }
    }

    private String findWitness() throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(FIND_SQL);
                ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private boolean decryptsAnExistingPresentation() throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(FIND_PRESENTATION_SQL);
                ResultSet result = statement.executeQuery()) {
            return !result.next() || this.decrypts(result.getString(3),
                    SqliteProfileRepository.context(result.getLong(1), result.getLong(2)), null);
        }
    }

    private boolean decrypts(String encrypted, String context, String expected) {
        try {
            String plainText = this.cipher.decrypt(encrypted, context);
            return expected == null || expected.equals(plainText);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
