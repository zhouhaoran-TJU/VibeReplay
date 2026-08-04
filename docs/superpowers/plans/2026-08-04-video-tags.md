# Video Tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add editable multi-tag metadata for local videos and browse/play videos by tag.

**Architecture:** Store tagged video snapshots as JSON in the existing activity preferences. Keep tag normalization and collection operations in pure Java value/helper classes, while `MainActivity` owns Android dialogs, JSON persistence, and conversion into the existing favorites-style playback queue.

**Tech Stack:** Java, Android framework views, SharedPreferences, org.json, JUnit 4, Gradle.

---

### Task 1: Tag domain model

**Files:**
- Create: `app/src/main/java/com/example/smoothplayer/TaggedVideo.java`
- Create: `app/src/main/java/com/example/smoothplayer/TagCatalog.java`
- Test: `app/src/test/java/com/example/smoothplayer/TagCatalogTest.java`

- [x] Write failing tests for input normalization, update/removal, history, and filtering.
- [x] Run `:app:testDebugUnitTest --tests com.example.smoothplayer.TagCatalogTest` and verify missing tag classes cause failure.
- [x] Implement immutable tagged video records and catalog operations.
- [x] Re-run the focused test and verify it passes.

### Task 2: Android UI and persistence

**Files:**
- Modify: `app/src/main/java/com/example/smoothplayer/MainActivity.java`
- Modify: `app/src/main/java/com/example/smoothplayer/MoreOptions.java`
- Test: `app/src/test/java/com/example/smoothplayer/MoreOptionsTest.java`

- [x] Extend the menu mapping test with `EDIT_CURRENT_VIDEO_TAGS` and verify it fails.
- [x] Add the menu action, current-video tag editor, historical multi-choice list, JSON load/save, browse-menu tag picker, filtered video dialog, and playback queue conversion.
- [x] Remove tag records after successful video deletion.
- [x] Run the full unit test suite.

### Task 3: Release

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Modify: `dist/SmoothPlayer-beta.apk`
- Modify: `dist/version.json`

- [x] Upgrade to version code 29 / version name 3.8 and document video tags.
- [x] Build debug and beta APKs, copy beta to `dist/`, and update release size/hash/notes.
- [x] Run `git diff --check`, unit tests, both APK builds, manifest/signature checks, and metadata comparison.
- [ ] Commit, push `main`, download the raw GitHub APK/manifest, and compare them byte-for-byte with local release files.
