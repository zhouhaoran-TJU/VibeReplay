package com.example.smoothplayer;

import java.util.ArrayList;
import java.util.List;

final class PlaybackItem {
    final String path;
    final boolean shizuku;
    final boolean contentUri;

    PlaybackItem(String path, boolean shizuku) {
        this(path, shizuku, false);
    }

    PlaybackItem(String path, boolean shizuku, boolean contentUri) {
        this.path = path;
        this.shizuku = shizuku;
        this.contentUri = contentUri;
    }

    boolean matches(String otherPath, boolean otherShizuku, boolean otherContentUri) {
        return path.equals(otherPath)
                && shizuku == otherShizuku
                && contentUri == otherContentUri;
    }

    static List<PlaybackItem> fromFavorites(List<FavoriteItem> favorites) {
        List<PlaybackItem> queue = new ArrayList<>();
        for (FavoriteItem favorite : favorites) {
            queue.add(new PlaybackItem(favorite.path, favorite.shizuku, favorite.contentUri));
        }
        return queue;
    }
}
