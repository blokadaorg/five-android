#!/bin/sh

set -e

export PATH=$PATH:~/.cargo/bin:/usr/local/bin
export ANDROID_NDK_HOME=$ANDROID_NDK

JNI_LIBS=app/src/engine/jniLibs

echo "Building boringtun..."
echo "Using NDK location: $ANDROID_NDK_HOME"

mkdir -p $JNI_LIBS/arm64-v8a
mkdir -p $JNI_LIBS/armeabi-v7a

cd boringtun
hash=$(git describe --abbrev=4 --always --tags --dirty)
commit="sync: update boringtun to: $hash"

echo "Building for aarch64..."
cargo ndk --platform 21 --target aarch64-linux-android build --release --lib

echo "Building for arm7..."
cargo ndk --platform 21 --target armv7-linux-androideabi build --release --lib

cd ../
cp ./boringtun/target/aarch64-linux-android/release/libboringtun.so $JNI_LIBS/arm64-v8a/
cp ./boringtun/target/armv7-linux-androideabi/release/libboringtun.so $JNI_LIBS/armeabi-v7a/

git commit -am "$commit"

echo "Done"
