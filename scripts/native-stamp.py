#!/usr/bin/env python3
"""Content fingerprint and JNI export verification for Android native packaging.

The file list must match `verifyNativeCore` in core/engine-bridge/build.gradle.kts exactly.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess

CORE = "open-adblock-core"
LIBRARY = "libopenadblock_jni.so"
ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64", "x86"]


def source_files(root):
    core = root / CORE
    fixed = [f"{CORE}/Cargo.toml", f"{CORE}/Cargo.lock", f"{CORE}/rust-toolchain.toml",
             f"{CORE}/scripts/build-android.sh", f"{CORE}/scripts/check-jni-exports.py",
             "scripts/build-native.sh", "scripts/native-stamp.py"]
    crates = [p for p in (core / "crates").rglob("*")
              if p.is_file() and (p.suffix in (".rs", ".js") or p.name == "Cargo.toml")
              and not {"tests", "benches"} & set(p.relative_to(core / "crates").parts[:-1])]
    vendored = [p for p in (core / "vendor").rglob("*")
                if p.is_file() and (p.suffix == ".rs" or p.name == "Cargo.toml")]
    java = [p for p in (core / "bindings/java/src/main").rglob("*")
            if p.is_file() and p.suffix in (".java", ".pro")]
    missing = [name for name in fixed if not (root / name).is_file()]
    if missing:
        raise SystemExit(f"Missing {', '.join(missing)}; run `git submodule update --init`.")
    return sorted([root / name for name in fixed] + crates + vendored + java,
                  key=lambda p: p.relative_to(root).as_posix())


def fingerprint(root):
    digest = hashlib.sha256()
    for path in source_files(root):
        digest.update(path.relative_to(root).as_posix().encode())
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--seal", action="store_true")
    parser.add_argument("--expected")
    parser.add_argument("--ndk-bin", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    current = fingerprint(root)
    if not args.seal:
        print(current)
        return
    if current != args.expected:
        raise SystemExit("Rust/JNI sources changed during the native build; rebuild before packaging.")
    directory = root / "core/engine-bridge/src/main/jniLibs"
    binaries = [directory / abi / LIBRARY for abi in ABIS]
    subprocess.check_call(["python3", str(root / CORE / "scripts/check-jni-exports.py"),
                           "--nm", str(args.ndk_bin / "llvm-nm"), *map(str, binaries)])
    lines = ["sources=" + current]
    lines += [abi + "=" + hashlib.sha256(binary.read_bytes()).hexdigest()
              for abi, binary in zip(ABIS, binaries)]
    temporary = directory / ".build.properties.tmp"
    temporary.write_text("\n".join(lines) + "\n")
    temporary.replace(directory / ".build.properties")
    print("Verified source fingerprint, four ABI binaries and every NativeBindings JNI export.")


if __name__ == "__main__":
    main()
