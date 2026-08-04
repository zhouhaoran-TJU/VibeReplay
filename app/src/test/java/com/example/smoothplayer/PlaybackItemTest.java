package com.example.smoothplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class PlaybackItemTest {
    @Test
    public void convertsFavoritesInDisplayOrderAndPreservesSources() {
        List<FavoriteItem> favorites = Arrays.asList(
                new FavoriteItem("file:/a.mp4", "A", "/a.mp4", false, false),
                new FavoriteItem("shizuku:/b.mp4", "B", "/b.mp4", true, false),
                new FavoriteItem("content://videos/c", "C", "content://videos/c", false, true));

        List<PlaybackItem> queue = PlaybackItem.fromFavorites(favorites);

        assertEquals(3, queue.size());
        assertEquals("/a.mp4", queue.get(0).path);
        assertFalse(queue.get(0).shizuku);
        assertFalse(queue.get(0).contentUri);
        assertTrue(queue.get(1).shizuku);
        assertTrue(queue.get(2).contentUri);
    }

    @Test
    public void conversionReturnsIndependentSnapshot() {
        List<FavoriteItem> favorites = new ArrayList<>();
        favorites.add(new FavoriteItem("file:/a.mp4", "A", "/a.mp4", false, false));
        List<PlaybackItem> queue = PlaybackItem.fromFavorites(favorites);

        favorites.clear();

        assertEquals(1, queue.size());
        assertEquals("/a.mp4", queue.get(0).path);
    }

    @Test
    public void matchesPathAndSourceTogether() {
        PlaybackItem contentItem = new PlaybackItem("content://videos/a", false, true);

        assertTrue(contentItem.matches("content://videos/a", false, true));
        assertFalse(contentItem.matches("content://videos/a", false, false));
        assertFalse(contentItem.matches("content://videos/b", false, true));
    }
}
