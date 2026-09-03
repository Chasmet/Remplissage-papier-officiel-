#!/usr/bin/env sh
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
BOOT_DIR="$ROOT_DIR/.gradle-bootstrap"
GRADLE_HOME="$BOOT_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$BOOT_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$BOOT_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_FILE"
  fi
  rm -rf "$GRADLE_HOME"
  unzip -q "$ZIP_FILE" -d "$BOOT_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
