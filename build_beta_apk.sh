#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$ROOT_DIR/.." && pwd)"

export JAVA_HOME="$WORKSPACE_DIR/.build-env/jdk-17"
export ANDROID_HOME="$WORKSPACE_DIR/.build-env/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$ROOT_DIR/.gradle-home"

"$WORKSPACE_DIR/.build-env/gradle-7.6.4/bin/gradle" --no-daemon :app:assembleBeta
mkdir -p "$ROOT_DIR/dist"
cp "$ROOT_DIR/app/build/outputs/apk/beta/app-beta.apk" "$ROOT_DIR/dist/SmoothPlayer-beta.apk"
