#!/bin/sh

set -e

export PATH=$PATH:~/.cargo/bin:/usr/local/bin
# NDK r28 links 64-bit libs with 16 KB pages; the flag makes 32-bit match.
export ANDROID_NDK_HOME=${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/28.2.13676358}
export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384 -C strip=symbols --remap-path-prefix=$HOME=/workspace"
export CARGO_TARGET_DIR=${CARGO_TARGET_DIR:-${TMPDIR:-/tmp}/blokada-five-native/boringtun}

JNI_LIBS=app/src/engine/jniLibs

echo "Building boringtun..."
echo "Using NDK location: $ANDROID_NDK_HOME"

mkdir -p $JNI_LIBS/arm64-v8a
mkdir -p $JNI_LIBS/armeabi-v7a

cd boringtun

echo "Building for aarch64..."
cargo ndk --platform 21 --target aarch64-linux-android build --release --lib

echo "Building for arm7..."
cargo ndk --platform 21 --target armv7-linux-androideabi build --release --lib

cd ../
cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/libboringtun.so" $JNI_LIBS/arm64-v8a/
cp "$CARGO_TARGET_DIR/armv7-linux-androideabi/release/libboringtun.so" $JNI_LIBS/armeabi-v7a/

echo "Done"
