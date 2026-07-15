package com.example.smoothplayer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoreOptionsTest {
    @Test
    public void mapsMenuItemsToActions() {
        assertArrayEquals(
                new String[]{"检查更新", "文件访问权限", "删除当前视频"},
                MoreOptions.labels());
        assertEquals(MoreOptions.Action.CHECK_UPDATE, MoreOptions.actionAt(0));
        assertEquals(MoreOptions.Action.FILE_ACCESS, MoreOptions.actionAt(1));
        assertEquals(MoreOptions.Action.DELETE_CURRENT_VIDEO, MoreOptions.actionAt(2));
    }
}
