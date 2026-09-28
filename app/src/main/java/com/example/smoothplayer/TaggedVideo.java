package com.example.smoothplayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TaggedVideo {
    final String key;
    final String title;
    final String path;
    final boolean shizuku;
    final boolean contentUri;
    final List<String> tags;
    final int rating;

    TaggedVideo(String key, String title, String path, boolean shizuku, boolean contentUri,
            List<String> tags) {
        this(key, title, path, shizuku, contentUri, tags, 0);
    }

    TaggedVideo(String key, String title, String path, boolean shizuku, boolean contentUri,
            List<String> tags, int rating) {
        this.key = key;
        this.title = title;
        this.path = path;
        this.shizuku = shizuku;
        this.contentUri = contentUri;
        this.tags = Collections.unmodifiableList(new ArrayList<>(TagCatalog.normalizeTags(tags)));
        this.rating = TagCatalog.normalizeRating(rating);
    }

    FavoriteItem asFavoriteItem() {
        return new FavoriteItem(key, title, path, shizuku, contentUri, rating, tags);
    }
}
