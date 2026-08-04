package com.example.smoothplayer;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TagCatalogTest {
    @Test
    public void combinesSelectedAndTypedTagsWithTrimmingAndCaseInsensitiveDeduplication() {
        List<String> tags = TagCatalog.combineTags(
                Arrays.asList("旅行", "收藏"),
                " Drama，旅行, drama\n纪录片 ");

        assertEquals(Arrays.asList("旅行", "收藏", "Drama", "纪录片"), tags);
    }

    @Test
    public void upsertReplacesMatchingVideoAndEmptyTagsRemoveIt() {
        TaggedVideo first = video("file:/a.mp4", "A", "旅行");
        TaggedVideo second = video("file:/b.mp4", "B", "纪录片");
        List<TaggedVideo> records = Arrays.asList(first, second);

        TaggedVideo updated = video("file:/b.mp4", "B2", "收藏");
        List<TaggedVideo> afterUpdate = TagCatalog.upsert(records, updated);

        assertEquals(Arrays.asList(updated, first), afterUpdate);
        assertEquals(Collections.singletonList(first),
                TagCatalog.upsert(afterUpdate, video("file:/b.mp4", "B2")));
    }

    @Test
    public void collectsTagHistoryAndFiltersVideosInStoredOrder() {
        TaggedVideo first = video("file:/a.mp4", "A", "旅行", "收藏");
        TaggedVideo second = video("file:/b.mp4", "B", "收藏", "纪录片");
        TaggedVideo third = video("file:/c.mp4", "C", "旅行");
        List<TaggedVideo> records = Arrays.asList(first, second, third);

        assertEquals(Arrays.asList("旅行", "收藏", "纪录片"), TagCatalog.allTags(records));
        assertEquals(Arrays.asList(first, second), TagCatalog.videosForTag(records, "收藏"));
    }

    private TaggedVideo video(String key, String title, String... tags) {
        return new TaggedVideo(key, title, key.substring("file:".length()), false, false,
                Arrays.asList(tags));
    }
}
