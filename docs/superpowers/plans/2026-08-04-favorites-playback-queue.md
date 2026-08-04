# Favorites Playback Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make videos opened from Favorites use the displayed favorites as the previous/next playback queue.

**Architecture:** Move the existing favorite and playback item value types into package-private Java classes so their conversion can be unit tested without Android UI dependencies. `MainActivity` will snapshot the displayed favorites into the existing playback queue and use the existing playback-mode boundary behavior for manual and automatic switching.

**Tech Stack:** Java, Android `MediaPlayer`, JUnit 4, Gradle 7.6.4

---

### Task 1: Test favorite-to-playback conversion

**Files:**
- Create: `app/src/test/java/com/example/smoothplayer/PlaybackItemTest.java`
- Create: `app/src/main/java/com/example/smoothplayer/FavoriteItem.java`
- Create: `app/src/main/java/com/example/smoothplayer/PlaybackItem.java`
- Modify: `app/src/main/java/com/example/smoothplayer/MainActivity.java`

- [x] **Step 1: Write the failing conversion tests**

```java
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
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `JAVA_HOME=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/jdk-17 ANDROID_HOME=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/android-sdk ANDROID_SDK_ROOT=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/android-sdk GRADLE_USER_HOME=/home/mi/WorkSpace/Projects/VibeCoding/video_player_android/.gradle-home /home/mi/WorkSpace/Projects/VibeCoding/.build-env/gradle-7.6.4/bin/gradle --no-daemon :app:testDebugUnitTest --tests com.example.smoothplayer.PlaybackItemTest`

Expected: compilation fails because `PlaybackItem.fromFavorites` and source metadata do not yet exist as testable package classes.

- [x] **Step 3: Extract the value types and implement minimal conversion**

```java
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

    static List<PlaybackItem> fromFavorites(List<FavoriteItem> favorites) {
        List<PlaybackItem> queue = new ArrayList<>();
        for (FavoriteItem favorite : favorites) {
            queue.add(new PlaybackItem(favorite.path, favorite.shizuku, favorite.contentUri));
        }
        return queue;
    }
}
```

Move the unchanged `FavoriteItem` fields, constructor, `previewKey()`, and `sourceLabel()` from the bottom of `MainActivity` into `FavoriteItem.java`, then remove both old nested declarations.

- [x] **Step 4: Run the focused test and full unit suite**

Run: use the external Gradle command above with `:app:testDebugUnitTest --tests com.example.smoothplayer.PlaybackItemTest`.

Expected: PASS.

Run: use the same external Gradle environment with `:app:testDebugUnitTest`.

Expected: all unit tests PASS.

### Task 2: Build and use the favorites queue

**Files:**
- Modify: `app/src/main/java/com/example/smoothplayer/MainActivity.java`

- [x] **Step 1: Pass the displayed favorites and selected index from the click handler**

```java
listView.setOnItemClickListener((parent, view, which, id) -> {
    dialog.dismiss();
    openFavoriteItem(favorites, which);
});
```

- [x] **Step 2: Replace single-item favorite opening with queue initialization**

```java
private void openFavoriteItem(List<FavoriteItem> favorites, int selectedIndex) {
    if (selectedIndex < 0 || selectedIndex >= favorites.size()) {
        return;
    }
    List<PlaybackItem> queue = PlaybackItem.fromFavorites(favorites);
    setPlaybackQueue(queue, selectedIndex);
    openPlaybackItem(queue.get(selectedIndex), true);
}

private void setPlaybackQueue(List<PlaybackItem> items, int selectedIndex) {
    playbackQueue.clear();
    playbackQueue.addAll(items);
    currentQueueIndex = selectedIndex >= 0 && selectedIndex < playbackQueue.size()
            ? selectedIndex : -1;
}
```

- [x] **Step 3: Open content URI queue entries correctly**

```java
if (item.shizuku) {
    openVideo(Uri.fromParts("shizuku", item.path, null), 0, autoPlay);
} else if (item.contentUri) {
    openVideo(Uri.parse(item.path), 0, autoPlay);
} else {
    openVideo(Uri.fromFile(new File(item.path)), 0, autoPlay);
}
```

- [x] **Step 4: Run unit tests and static diff checks**

Run: use the same external Gradle environment with `:app:testDebugUnitTest`.

Expected: all tests PASS.

Run: `git diff --check`

Expected: no output.

- [x] **Step 5: Preserve mixed-source queues when deleting content URI items**

Add a source-aware `PlaybackItem.matches(...)` check and use it when removing deleted files from the queue. On successful `content://` deletion, open the adjacent queue item when available instead of clearing the entire queue. Verify the source matcher with a focused unit test.

### Task 3: Version and release artifacts

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Modify: `dist/SmoothPlayer-debug.apk`
- Modify: `dist/SmoothPlayer-beta.apk`
- Modify: `dist/version.json`

- [x] **Step 1: Bump and document the release**

Set `versionCode 28`, `versionName "3.7"`, add the Favorites queue behavior to the README feature list, and set release notes to `从收藏列表播放时支持按收藏顺序切换上一个、下一个视频，并支持自动连播。`.

- [x] **Step 2: Build debug and beta artifacts with repository scripts**

Run: `./build_debug_apk.sh`

Expected: `app/build/outputs/apk/debug/app-debug.apk` is produced. Copy it to `dist/SmoothPlayer-debug.apk`.

Run: `./build_beta_apk.sh`

Expected: `app/build/outputs/apk/beta/app-beta.apk` is produced and copied to `dist/SmoothPlayer-beta.apk`.

- [x] **Step 3: Update and verify release metadata**

Run: `stat -c %s dist/SmoothPlayer-beta.apk` and `sha256sum dist/SmoothPlayer-beta.apk`; write the exact results to `dist/version.json` with version code 28 and version name `3.7-beta`.

Run: `git diff --check` and the external Gradle environment from Task 1 with `:app:testDebugUnitTest`.

Expected: no whitespace errors and all tests PASS.

### Task 4: Commit and publish

**Files:**
- Commit all feature, test, release, design, plan, and pre-existing build-path corrections that are part of the verified release.

- [x] **Step 1: Review the final scope**

Run: `git status --short` and `git diff --stat`.

Expected: only the favorites queue feature, versioned APKs, release metadata, documentation, and build environment path corrections are present.

- [ ] **Step 2: Commit the implementation**

Run: `git add <verified release files>` then `git commit -m "Add favorites playback navigation"`.

Expected: one implementation/release commit after the design commit.

- [ ] **Step 3: Push and verify the fixed download URL**

Run: `git push origin main`.

Expected: `origin/main` advances to the implementation commit.

Run: `curl -I https://github.com/zhouhaoran-TJU/VibeReplay/raw/main/dist/SmoothPlayer-beta.apk`.

Expected: a successful GitHub response or redirect and no local Git divergence.
