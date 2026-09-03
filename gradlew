#!/usr/bin/env sh
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.7
GRADLE_SHA256=544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d
BOOT_DIR="$ROOT_DIR/.gradle-bootstrap"
GRADLE_HOME="$BOOT_DIR/gradle-$GRADLE_VERSION"
ZIP_FILE="$BOOT_DIR/gradle-$GRADLE_VERSION-bin.zip"

verify_zip() {
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$ZIP_FILE" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL=$(shasum -a 256 "$ZIP_FILE" | awk '{print $1}')
  else
    echo "Aucun outil SHA-256 disponible pour vérifier Gradle." >&2
    exit 1
  fi

  if [ "$ACTUAL" != "$GRADLE_SHA256" ]; then
    echo "Archive Gradle refusée : empreinte SHA-256 incorrecte." >&2
    rm -f "$ZIP_FILE"
    exit 1
  fi
}

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$BOOT_DIR"
  if [ ! -f "$ZIP_FILE" ]; then
    curl --fail --location --proto '=https' --tlsv1.2 \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
      -o "$ZIP_FILE"
  fi
  verify_zip
  rm -rf "$GRADLE_HOME"
  unzip -q "$ZIP_FILE" -d "$BOOT_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
