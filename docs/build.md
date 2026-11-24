# Build và kiểm tra

Cần JDK 25 (Gradle daemon), Android SDK 37, build tools 36.0.0, NDK 28.2.13676358 và
Rust qua rustup. Lõi native nằm ở git submodule `open-adblock-core/`
(https://github.com/ToThangGTVT/open-adblock-core); `open-adblock-core/rust-toolchain.toml`
pin Rust 1.99.1 cùng bốn target Android. Sau khi clone:
`git submodule update --init` (hoặc `git clone --recurse-submodules`).
`local.properties` chứa đường dẫn SDK riêng máy và không được commit.

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk" # Linux: đặt theo SDK của máy
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
scripts/build-native.sh
./gradlew :app:assembleDebug
```

Bản debug dùng application ID `com.utc.color.blockads.dev` để cài song song với
bản phát hành. Bản release (kể cả GitHub Actions) giữ `com.utc.color.blockads`.

Gradle tự chạy script native khi nguồn hoặc binary chưa khớp. Script gọi
`open-adblock-core/scripts/build-android.sh`, tạo `libopenadblock_jni.so` cho arm64-v8a, armeabi-v7a, x86_64 và x86, link cho
API 33 và page alignment 16 KB. Các binary được đóng gói qua `core/engine-bridge`,
không commit binary. Xem [phát hành, khóa ký và cập nhật](releases.md).

```sh
(cd open-adblock-core && cargo fmt --all --check)
(cd open-adblock-core && cargo clippy --locked --workspace --all-targets -- -D warnings)
(cd open-adblock-core && cargo test --locked --workspace -- --test-threads=1)
./gradlew :core:data:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

## Lõi native (open-adblock-core)

App chỉ gọi lõi qua API Java `AdBlockEngine` (bọc trong `core/engine-bridge/EngineBridge.kt`).
App Android theo nhánh `main` của lõi (`branch = main` trong `.gitmodules`). Repo app vẫn ghi
một commit cụ thể của lõi để mỗi bản build tái tạo được; lấy commit mới nhất của `main`:

```sh
git submodule update --init --remote open-adblock-core
git add open-adblock-core && git commit -m "Update open-adblock-core"
```

Sửa lõi: commit và push lên `main` trong `open-adblock-core/`, rồi commit con trỏ submodule mới
ở repo app.
Kiến trúc, API (Java/C/Rust) và lộ trình macOS/Windows nằm trong `open-adblock-core/docs/`.
