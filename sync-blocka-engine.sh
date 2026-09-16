#!/bin/sh

set -e

export PATH=$PATH:~/.cargo/bin:/usr/local/bin
# NDK r28 links 64-bit libs with 16 KB pages; the flag makes 32-bit match.
export ANDROID_NDK_HOME=${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/28.2.13676358}
export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384 -C strip=symbols --remap-path-prefix=$HOME=/workspace"
export CARGO_NET_GIT_FETCH_WITH_CLI=true
export CARGO_TARGET_DIR=${CARGO_TARGET_DIR:-${TMPDIR:-/tmp}/blokada-five-native/blocka-engine}
echo "Using NDK location: $ANDROID_NDK_HOME"

JNI_LIBS=app/src/libre/jniLibs

mkdir -p $JNI_LIBS/arm64-v8a
mkdir -p $JNI_LIBS/armeabi-v7a

cd blocka_engine/blocka_dns

echo "Building for aarch64..."
cargo ndk --platform 21 --target aarch64-linux-android build --release

echo "Building for arm7..."
cargo ndk --platform 21 --target armv7-linux-androideabi build --release

cd ../../
cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/libblocka_dns.so" $JNI_LIBS/arm64-v8a/
cp "$CARGO_TARGET_DIR/armv7-linux-androideabi/release/libblocka_dns.so" $JNI_LIBS/armeabi-v7a/

# Vendored OpenSSL embeds its generated engine directory even though dynamic engines are disabled.
python3 - "$JNI_LIBS/arm64-v8a/libblocka_dns.so" "$JNI_LIBS/armeabi-v7a/libblocka_dns.so" <<'PY'
import re
import sys
from pathlib import Path

replacement = b"/workspace/openssl/engines-1.1"
for filename in sys.argv[1:]:
    path = Path(filename)
    data = path.read_bytes()
    matches = list(re.finditer(rb"[^\x00]*/openssl-build/install/lib/engines-1\.1", data))
    if len(matches) != 1:
        raise SystemExit(f"Expected one generated OpenSSL engine path in {filename}, found {len(matches)}")
    match = matches[0]
    if len(replacement) > len(match.group()):
        raise SystemExit(f"Replacement OpenSSL engine path is too long for {filename}")
    data = data[:match.start()] + replacement.ljust(len(match.group()), b"\x00") + data[match.end():]
    path.write_bytes(data)
PY

echo "Done"
