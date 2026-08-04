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

    TaggedVideo(String key, String title, String path, boolean shizuku, boolean contentUri,
            List<String> tags) {
        this.key = key;
        this.title = title;
        this.path = path;
        this.shizuku = shizuku;
        this.contentUri = contentUri;
        this.tags = Collections.unmodifiableList(new ArrayList<>(TagCatalog.normalizeTags(tags)));
    }

    FavoriteItem asFavoriteItem() {
        return new FavoriteItem(key, title, path, shizuku, contentUri);
    }
}
