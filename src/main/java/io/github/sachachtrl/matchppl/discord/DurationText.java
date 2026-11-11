package io.github.sachachtrl.matchppl.discord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DurationText {
    private static final Pattern FORMAT = Pattern.compile(
            "^\\s*(?:(\\d{1,4})\\s*d)?\\s*(?:(\\d{1,5})\\s*h)?\\s*(?:(\\d{1,6})\\s*m)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    private DurationText() {
    }

    static Duration parse(String input) {
        Matcher matcher = FORMAT.matcher(input == null ? "" : input);
        if (!matcher.matches() || (matcher.group(1) == null && matcher.group(2) == null && matcher.group(3) == null)) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return Duration.ofDays(number(matcher.group(1)))
                .plusHours(number(matcher.group(2)))
                .plusMinutes(number(matcher.group(3)));
    }

    static String toInput(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append('d');
        }
        if (hours > 0) {
            text.append(hours).append('h');
        }
        if (minutes > 0 || text.isEmpty()) {
            text.append(minutes).append('m');
        }
        return text.toString();
    }

    static String format(Duration duration, Locale locale) {
        List<String> parts = new ArrayList<>();
        addPart(parts, duration.toDays(), "duration.day", locale);
        addPart(parts, duration.toHoursPart(), "duration.hour", locale);
        addPart(parts, duration.toMinutesPart(), "duration.minute", locale);
        return parts.isEmpty() ? Messages.format(locale, "duration.minute.other", 0) : String.join(" ", parts);
    }

    private static void addPart(List<String> parts, long value, String key, Locale locale) {
        if (value == 1) {
            parts.add(Messages.get(locale, key + ".one"));
        } else if (value > 1) {
            parts.add(Messages.format(locale, key + ".other", value));
        }
    }

    private static long number(String group) {
        return group == null ? 0 : Long.parseLong(group);
    }
}
