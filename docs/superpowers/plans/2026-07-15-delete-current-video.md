# Delete Current Video Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe “删除当前视频” action to the playback screen menu and publish updated Android artifacts.

**Architecture:** Keep all existing file deletion branches in `MainActivity`, but move the operation menu labels and index-to-action mapping into a small pure Java `MoreOptions` class. `MainActivity` renders those labels and dispatches the selected action, while a local JVM test protects the mapping without requiring Android UI instrumentation.

**Tech Stack:** Java, Android SDK 33, JUnit 4, Gradle 7.6.4, Android Gradle Plugin 7.4.2

---

### Task 1: Operation Menu Mapping

**Files:**
- Create: `app/src/test/java/com/example/smoothplayer/MoreOptionsTest.java`
- Create: `app/src/main/java/com/example/smoothplayer/MoreOptions.java`
- Modify: `app/build.gradle`

- [ ] **Step 1: Add JUnit and write the failing mapping test**

Add `testImplementation "junit:junit:4.13.2"` to `dependencies`, then create a test asserting:

```java
assertArrayEquals(new String[]{"检查更新", "文件访问权限", "删除当前视频"}, MoreOptions.labels());
assertEquals(MoreOptions.Action.CHECK_UPDATE, MoreOptions.actionAt(0));
assertEquals(MoreOptions.Action.FILE_ACCESS, MoreOptions.actionAt(1));
assertEquals(MoreOptions.Action.DELETE_CURRENT_VIDEO, MoreOptions.actionAt(2));
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
JAVA_HOME=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/jdk-17 \
ANDROID_HOME=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/android-sdk \
ANDROID_SDK_ROOT=/home/mi/WorkSpace/Projects/VibeCoding/.build-env/android-sdk \
GRADLE_USER_HOME=/home/mi/WorkSpace/Projects/VibeCoding/video_player_android/.gradle-home \
/home/mi/WorkSpace/Projects/VibeCoding/.build-env/gradle-7.6.4/bin/gradle --no-daemon :app:testDebugUnitTest
```

Expected: FAIL because `MoreOptions` does not exist.

- [ ] **Step 3: Implement the minimum pure Java mapping**

Create `MoreOptions` with:

```java
final class MoreOptions {
    enum Action {
        CHECK_UPDATE,
        FILE_ACCESS,
        DELETE_CURRENT_VIDEO
    }

    private static final String[] LABELS = {
            "检查更新", "文件访问权限", "删除当前视频"
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
```

- [ ] **Step 4: Run the unit test and verify GREEN**

Run the Task 1 Gradle command again.

Expected: `BUILD SUCCESSFUL`, one test class passing.

### Task 2: Playback Menu Integration

**Files:**
- Modify: `app/src/main/java/com/example/smoothplayer/MainActivity.java:628`

- [ ] **Step 1: Render and dispatch the mapped actions**

Change `showMoreOptions()` to use `MoreOptions.labels()` and dispatch every action explicitly:

```java
private void showMoreOptions() {
    new AlertDialog.Builder(this)
            .setTitle("操作")
            .setItems(MoreOptions.labels(), (dialog, which) -> {
                MoreOptions.Action action = MoreOptions.actionAt(which);
                if (action == MoreOptions.Action.CHECK_UPDATE) {
                    checkForUpdates(true);
                } else if (action == MoreOptions.Action.FILE_ACCESS) {
                    showAccessOptions();
                } else if (action == MoreOptions.Action.DELETE_CURRENT_VIDEO) {
                    confirmDeleteCurrentFile();
                }
            })
            .show();
}
```

- [ ] **Step 2: Run unit tests**

Run `:app:testDebugUnitTest` with the Task 1 environment.

Expected: `BUILD SUCCESSFUL`.

### Task 3: Version And Release Metadata

**Files:**
- Modify: `app/build.gradle`
- Modify: `dist/version.json`
- Replace: `dist/SmoothPlayer-debug.apk`
- Replace: `dist/SmoothPlayer-beta.apk`

- [ ] **Step 1: Bump application version**

Set:

```gradle
versionCode 27
versionName "3.6"
```

- [ ] **Step 2: Build debug and beta APKs**

Run `./build_debug_apk.sh` and `./build_beta_apk.sh`.

Expected: both commands end with `BUILD SUCCESSFUL` and replace the two APKs under `dist/`.

- [ ] **Step 3: Calculate beta artifact metadata**

Run:

```bash
stat -c %s dist/SmoothPlayer-beta.apk
sha256sum dist/SmoothPlayer-beta.apk
```

Update `dist/version.json` to version code `27`, version name `3.6-beta`, the exact size and SHA-256, and release notes describing current-video deletion.

- [ ] **Step 4: Verify metadata and repository diff**

Recalculate size and SHA-256, parse `dist/version.json`, and run:

```bash
git diff --check
git status --short
```

Expected: metadata matches the beta APK; unrelated existing changes remain unstaged.

### Task 4: Final Verification, Commit And Push

**Files:**
- Verify all files changed in Tasks 1-3

- [ ] **Step 1: Run fresh tests and builds**

Run `:app:testDebugUnitTest`, `./build_debug_apk.sh`, and `./build_beta_apk.sh` again.

Expected: every command exits zero with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Stage only task files and inspect the staged diff**

Stage `app/build.gradle`, `MainActivity.java`, `MoreOptions.java`, `MoreOptionsTest.java`, both APKs, `dist/version.json`, and this plan. Do not stage `README.md`, `build_debug_apk.sh`, or `build_beta_apk.sh`.

Run `git diff --cached --check` and inspect `git diff --cached --stat`.

- [ ] **Step 3: Commit and push**

```bash
git commit -m "Add current video deletion option"
git push origin main
```

Expected: push updates `origin/main` and leaves only the pre-existing build-path changes unstaged.
