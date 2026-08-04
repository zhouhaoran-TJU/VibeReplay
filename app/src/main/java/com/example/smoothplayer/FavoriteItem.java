package com.example.smoothplayer;

final class FavoriteItem {
    final String key;
    final String title;
    final String path;
    final boolean shizuku;
    final boolean contentUri;

    FavoriteItem(String key, String title, String path, boolean shizuku, boolean contentUri) {
        this.key = key;
        this.title = title;
        this.path = path;
        this.shizuku = shizuku;
        this.contentUri = contentUri;
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
}
