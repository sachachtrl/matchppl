package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.GuildSettings;
import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.utils.ComponentIterator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentLimitTest {
    private static final long MAX_COMPONENTS = 40;

    private static long count(Container card) {
        return ComponentIterator.createStream(List.of(card)).count();
    }

    @Test
    void theConfigCardStaysUnderTheLimitWithEveryWarning() {
        GuildSettings settings = GuildSettings.defaults(1L).withLogChannelId(2L).withCategoryId(3L);
        Container card = ConfigViews.card(settings, 5, new ConfigViews.Warnings(true, true, true),
                Messages.DEFAULT_LOCALE);

        long count = count(card);
        assertTrue(count <= MAX_COMPONENTS, "config card has " + count + " components");
    }

    @Test
    void theProfileCardStaysUnderTheLimitWithTwoPagedMenus() {
        List<Tag> tags = LongStream.rangeClosed(1, 60)
                .mapToObj(id -> new Tag(id, 1L, "Tag " + id, "🎮", MemberConditions.NONE, Set.of(), Set.of()))
                .toList();
        Profile profile = Profile.empty(1L, 10L).withDmUnreachable(true);
        Container card = ProfileViews.mainCard(profile, tags, tags, emojiId -> true, new ProfileViews.TagPages(1, 1),
                Messages.DEFAULT_LOCALE);

        long count = count(card);
        assertTrue(count <= MAX_COMPONENTS, "profile card has " + count + " components");
    }
}
