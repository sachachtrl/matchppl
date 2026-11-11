package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.ActivityPeriod;
import io.github.sachachtrl.matchppl.model.MemberConditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.selections.SelectOption;

final class ConditionTexts {
    private ConditionTexts() {
    }

    static String ageText(MemberConditions conditions, Locale locale) {
        return conditions.minAccountAgeDays() == null
                ? Messages.get(locale, "tag.no_minimum")
                : Messages.format(locale, "tag.age.value", conditions.minAccountAgeDays());
    }

    static String seniorityText(MemberConditions conditions, Locale locale) {
        return conditions.minMemberDays() == null
                ? Messages.get(locale, "tag.no_minimum")
                : Messages.format(locale, "tag.seniority.value", conditions.minMemberDays());
    }

    static String activityText(MemberConditions conditions, Locale locale) {
        if (conditions.minMessages() == null) {
            return Messages.get(locale, "tag.no_minimum");
        }
        return Messages.format(locale, "tag.activity.value", conditions.minMessages(),
                periodText(conditions.activityPeriod(), locale));
    }

    static List<String> shortParts(MemberConditions conditions, Locale locale) {
        List<String> parts = new ArrayList<>();
        if (conditions.minAccountAgeDays() != null) {
            parts.add(Messages.format(locale, "tag.short.age", conditions.minAccountAgeDays()));
        }
        if (conditions.minMemberDays() != null) {
            parts.add(Messages.format(locale, "tag.short.seniority", conditions.minMemberDays()));
        }
        if (conditions.minMessages() != null) {
            parts.add(Messages.format(locale, "tag.short.activity", conditions.minMessages(),
                    periodText(conditions.activityPeriod(), locale)));
        }
        return parts;
    }

    static String shortSummary(List<String> parts, Locale locale) {
        return parts.isEmpty() ? Messages.get(locale, "tag.short.none") : String.join(" · ", parts);
    }

    static String periodText(ActivityPeriod period, Locale locale) {
        return Messages.get(locale, "tag.period." + period.name().toLowerCase(Locale.ROOT));
    }

    static List<SelectOption> periodOptions(ActivityPeriod selected, Locale locale) {
        return Arrays.stream(ActivityPeriod.values())
                .map(period -> SelectOption.of(
                                Messages.get(locale, "tag.period.option." + period.name().toLowerCase(Locale.ROOT)),
                                period.name())
                        .withDefault(period == selected))
                .toList();
    }
}
