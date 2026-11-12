package io.github.sachachtrl.matchppl.discord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;

final class ProposalViews {
    static final String PREFIX = "proposal:";
    static final String ACCEPT = "accept";
    static final String SKIP = "skip";
    static final String CANCEL = "cancel";

    enum CardState {
        PENDING,
        ACCEPTED,
        SKIPPED,
        CANCELLED,
        MATCHED,
        ENDED,
        UNAVAILABLE,
        INTERRUPTED
    }

    record OtherMember(String name, String avatarUrl, String presentation) {}

    record MatchInfo(String text, boolean reportable) {}

    private ProposalViews() {
    }

    static String buttonId(String action, long proposalId) {
        return PREFIX + action + ":" + proposalId;
    }

    static Container card(long proposalId, CardState state, String guildName, OtherMember other,
            List<String> commonTags, Instant deadline, MatchInfo match, Locale locale) {
        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("## " + Messages.get(locale, "proposal.title") + "\n-# "
                + Messages.format(locale, "proposal.from", guildName)));
        children.add(Separator.createDivider(Separator.Spacing.SMALL));
        boolean showsProfile = state == CardState.PENDING || state == CardState.ACCEPTED
                || state == CardState.MATCHED;
        if (showsProfile) {
            children.add(profileBlock(other, locale));
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            String tags = commonTags.isEmpty()
                    ? Messages.get(locale, "proposal.no_common_tag")
                    : Messages.format(locale, "proposal.common_tags", String.join(" · ", commonTags));
            children.add(TextDisplay.of("-# " + tags));
            if (state != CardState.MATCHED) {
                children.add(TextDisplay.of("-# " + Messages.format(locale, "proposal.deadline",
                        "<t:" + deadline.getEpochSecond() + ":R>")));
            }
        }
        String stateKey = stateKey(state);
        String stateText = state == CardState.MATCHED && match != null ? match.text()
                : stateKey == null ? null : Messages.get(locale, stateKey);
        if (stateText != null) {
            if (showsProfile) {
                children.add(Separator.createDivider(Separator.Spacing.SMALL));
            }
            children.add(TextDisplay.of(stateText));
        }
        ActionRow buttons = state == CardState.MATCHED && match != null && match.reportable()
                ? ActionRow.of(Button.secondary(RoomViews.reportId(RoomViews.REPORT_PROPOSAL, proposalId),
                        Messages.get(locale, "report.button")))
                : buttons(proposalId, state, locale);
        if (buttons != null) {
            children.add(Separator.createDivider(Separator.Spacing.LARGE));
            children.add(buttons);
        }
        return Container.of(children).withAccentColor(color(state));
    }

    private static ContainerChildComponent profileBlock(OtherMember other, Locale locale) {
        String presentation = other.presentation() == null
                ? "-# " + Messages.get(locale, "proposal.no_presentation")
                : ">>> " + other.presentation();
        TextDisplay text = TextDisplay.of("**" + other.name() + "**\n" + presentation);
        return other.avatarUrl() == null ? text : Section.of(Thumbnail.fromUrl(other.avatarUrl()), text);
    }

    private static String stateKey(CardState state) {
        return switch (state) {
            case PENDING -> null;
            case ACCEPTED -> "proposal.state.accepted";
            case SKIPPED -> "proposal.state.skipped";
            case CANCELLED -> "proposal.state.cancelled";
            case MATCHED -> null;
            case ENDED -> "proposal.state.ended";
            case UNAVAILABLE -> "proposal.state.unavailable";
            case INTERRUPTED -> "proposal.state.interrupted";
        };
    }

    private static ActionRow buttons(long proposalId, CardState state, Locale locale) {
        return switch (state) {
            case PENDING -> ActionRow.of(
                    Button.danger(buttonId(SKIP, proposalId), Messages.get(locale, "proposal.button.skip")),
                    Button.success(buttonId(ACCEPT, proposalId), Messages.get(locale, "proposal.button.accept")));
            case ACCEPTED -> ActionRow.of(
                    Button.secondary(buttonId(CANCEL, proposalId), Messages.get(locale, "proposal.button.cancel")));
            default -> null;
        };
    }

    private static int color(CardState state) {
        return switch (state) {
            case PENDING -> Cards.NEUTRAL_COLOR;
            case ACCEPTED, MATCHED -> Cards.SUCCESS_COLOR;
            case UNAVAILABLE, INTERRUPTED -> Cards.WARNING_COLOR;
            case SKIPPED, CANCELLED, ENDED -> Cards.MUTED_COLOR;
        };
    }
}
