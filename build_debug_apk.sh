#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_ROOT="/home/mi/WorkSpace/Projects/VibeCoding/lightroom_android"

export JAVA_HOME="$ENV_ROOT/.build-env/jdk-17"
export ANDROID_HOME="$ENV_ROOT/.build-env/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$ENV_ROOT/.gradle-home"

"$ENV_ROOT/.build-env/gradle-7.6.4/bin/gradle" --no-daemon :app:assembleDebug
