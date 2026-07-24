#!/usr/bin/env bash
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
project="$(cd "$here/.." && pwd)"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK containing platform 35}"

export PATH="$(go env GOPATH)/bin:$PATH"
if ! command -v gomobile >/dev/null 2>&1; then
  go install golang.org/x/mobile/cmd/gomobile@latest
fi
gomobile init
mkdir -p "$project/app/libs"
cd "$here"
gomobile bind \
  -androidapi 26 \
  -target android/arm,android/arm64,android/amd64,android/386 \
  -javapkg app.packetjam.nativecore \
  -o "$project/app/libs/packetjam-tun.aar" \
  ./packetjamtun
