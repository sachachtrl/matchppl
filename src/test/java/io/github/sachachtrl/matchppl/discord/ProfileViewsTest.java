package io.github.sachachtrl.matchppl.discord;

import io.github.sachachtrl.matchppl.model.MemberConditions;
import io.github.sachachtrl.matchppl.model.Profile;
import io.github.sachachtrl.matchppl.model.Tag;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileViewsTest {
    private static List<Tag> tags(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> new Tag(id, 1L, "Tag " + id, "🎮", MemberConditions.NONE, Set.of(), Set.of()))
                .toList();
    }

    @Test
    void tagsAreSplitIntoPagesOfTwentyFive() {
        List<Tag> tags = tags(30);

        assertEquals(2, ProfileViews.pageCount(tags));
        assertEquals(25, ProfileViews.page(tags, 0).size());
        assertEquals(List.of(26L, 27L, 28L, 29L, 30L), ProfileViews.page(tags, 1).stream().map(Tag::id).toList());
    }

    @Test
    void aPageOutOfRangeShowsTheClosestOne() {
        List<Tag> tags = tags(30);

        assertEquals(1, ProfileViews.clampPage(tags, 7));
        assertEquals(0, ProfileViews.clampPage(tags, -1));
        assertEquals(1, ProfileViews.pageCount(List.of()));
    }

    @Test
    void cardsWithManyTagsCanBeBuilt() {
        List<Tag> tags = tags(60);
        Profile profile = Profile.empty(1L, 10L);

        assertDoesNotThrow(() -> ProfileViews.mainCard(profile, tags, tags, emojiId -> true,
                new ProfileViews.TagPages(2, 1), Messages.DEFAULT_LOCALE));
        assertDoesNotThrow(() -> ProfileViews.advancedCard(profile, tags, emojiId -> true,
                new ProfileViews.TagPages(1, 0), Messages.DEFAULT_LOCALE));
    }
}
