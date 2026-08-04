package com.example.smoothplayer;

final class MoreOptions {
    enum Action {
        EDIT_CURRENT_VIDEO_TAGS,
        CHECK_UPDATE,
        FILE_ACCESS,
        DELETE_CURRENT_VIDEO
    }

    private static final String[] LABELS = {
            "编辑当前视频标签", "检查更新", "文件访问权限", "删除当前视频"
    };

    private MoreOptions() {
    }

    static String[] labels() {
        return LABELS.clone();
    }

    static Action actionAt(int index) {
        return Action.values()[index];
    }
}
