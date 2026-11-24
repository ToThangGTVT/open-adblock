# core/engine-bridge

App-side adapter over the native core's Java API (`AdBlockEngine` from
[open-adblock-core](https://github.com/ToThangGTVT/open-adblock-core), git submodule
`open-adblock-core/`). Adds Android defaults and localized errors; engine logic stays in the core.

`verifyNativeCore` fingerprints the core sources and rebuilds `libopenadblock_jni.so` for the four
ABIs with `scripts/build-native.sh` when they change.
