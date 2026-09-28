package com.example.smoothplayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class FavoriteItem {
    final String key;
    final String title;
    final String path;
    final boolean shizuku;
    final boolean contentUri;
    final int rating;
    final List<String> tags;

    FavoriteItem(String key, String title, String path, boolean shizuku, boolean contentUri) {
        this(key, title, path, shizuku, contentUri, 0, Collections.emptyList());
    }

    FavoriteItem(String key, String title, String path, boolean shizuku, boolean contentUri,
            int rating, List<String> tags) {
        this.key = key;
        this.title = title;
        this.path = path;
        this.shizuku = shizuku;
        this.contentUri = contentUri;
        this.rating = TagCatalog.normalizeRating(rating);
        this.tags = Collections.unmodifiableList(new ArrayList<>(TagCatalog.normalizeTags(tags)));
    }

    String previewKey() {
        return "favorite:" + key;
    }

    String sourceLabel() {
        if (shizuku) {
            return "Shizuku";
        }
        if (contentUri) {
            return "系统文件";
        }
        return "本地文件";
    }

    String metadataLabel() {
        StringBuilder value = new StringBuilder(sourceLabel());
        if (rating > 0) {
            value.append(" 评分 ").append(rating);
        }
        for (String tag : tags) {
            value.append(" #").append(tag);
        }
        return value.toString();
    }
}
