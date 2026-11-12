package io.github.sachachtrl.matchppl.discord;

import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigViewsTest {
    @Test
    void onlyTheMissingPermissionIsNamed() {
        String warning = ConfigViews.roomsWarning(new ConfigViews.Warnings(false, true, false), Locale.ENGLISH);

        assertTrue(warning.contains("Manage Roles permission"));
        assertFalse(warning.contains("Manage Channels"));
    }

    @Test
    void bothMissingPermissionsAreNamedInTheServerLanguage() {
        String warning = ConfigViews.roomsWarning(new ConfigViews.Warnings(true, true, false), Locale.FRENCH);

        assertTrue(warning.contains("Gérer les salons et Gérer les rôles"));
    }

    @Test
    void noWarningWhenNothingIsMissing() {
        assertNull(ConfigViews.roomsWarning(new ConfigViews.Warnings(false, false, true), Locale.ENGLISH));
    }
}
