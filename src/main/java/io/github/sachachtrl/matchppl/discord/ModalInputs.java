package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.service.ConditionRules;

import java.util.Locale;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

final class ModalInputs {
    static final int MAX_DAYS = 36500;
    static final int MAX_MESSAGES = 100000;

    private static final int MAX_NUMBER_LENGTH = 6;

    private ModalInputs() {
    }

    static TextInput numberInput(String id, Integer current) {
        TextInput.Builder input = TextInput.create(id, TextInputStyle.SHORT)
                .setRequired(false)
                .setMaxLength(MAX_NUMBER_LENGTH)
                .setPlaceholder("0");
        if (current != null) {
            input.setValue(String.valueOf(current));
        }
        return input.build();
    }

    static StringSelectMenu periodSelect(String id, ActivityPeriod current, Locale locale) {
        ActivityPeriod selected = current == null ? ConditionRules.DEFAULT_ACTIVITY_PERIOD : current;
        return StringSelectMenu.create(id)
                .addOptions(ConditionTexts.periodOptions(selected, locale))
                .setRequiredRange(1, 1)
                .build();
    }

    static Integer parseNumber(ModalMapping mapping, int max) {
        String raw = mapping == null ? "" : mapping.getAsString().strip();
        if (raw.isEmpty()) {
            return null;
        }
        if (!raw.chars().allMatch(Character::isDigit) || raw.length() > MAX_NUMBER_LENGTH) {
            throw new IllegalArgumentException("Not a whole number: " + raw);
        }
        int value = Integer.parseInt(raw);
        if (value > max) {
            throw new IllegalArgumentException("Number too large: " + raw);
        }
        return value;
    }

    static ActivityPeriod parsePeriod(ModalMapping mapping) {
        if (mapping == null || mapping.getAsStringList().isEmpty()) {
            return null;
        }
        return ActivityPeriod.valueOf(mapping.getAsStringList().getFirst());
    }
}
