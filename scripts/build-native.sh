#!/usr/bin/env bash
# Builds the open-adblock-core JNI library for the four Android ABIs into
# core/engine-bridge/src/main/jniLibs and seals the stamp that verifyNativeCore checks.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.2.13676358}"
case "$(uname -s)" in
    Darwin) NDK_HOST=darwin-x86_64 ;;
    Linux) NDK_HOST=linux-x86_64 ;;
    *) echo 'Use macOS or Linux to build Android native libraries.' >&2; exit 1 ;;
esac
NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/bin"
cd "$PROJECT_DIR"
[[ -f open-adblock-core/Cargo.toml ]] || git submodule update --init open-adblock-core
JNI_LIBS="$PROJECT_DIR/core/engine-bridge/src/main/jniLibs"
source_fingerprint="$(python3 scripts/native-stamp.py --root "$PROJECT_DIR")"
rm -f "$JNI_LIBS/.build.properties"
# Library name before the core moved to its own repository; never package it next to the new one.
rm -f "$JNI_LIBS"/*/libblockads_core.so
ANDROID_MIN_SDK=33 bash open-adblock-core/scripts/build-android.sh "$JNI_LIBS"
python3 scripts/native-stamp.py --root "$PROJECT_DIR" --seal --expected "$source_fingerprint" --ndk-bin "$NDK_BIN"
