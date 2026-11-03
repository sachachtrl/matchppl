package io.github.sachachtrl.matchppl.discord;

import java.util.Locale;
import java.util.ResourceBundle;

public final class Messages {
    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private static final String BUNDLE_NAME = "messages";

    private Messages() {
    }

    public static String get(Locale locale, String key) {
        return ResourceBundle.getBundle(
                BUNDLE_NAME,
                locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT)
        ).getString(key);
    }
}
