#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
TOOLS="$ROOT/.render-tools"
PUBLIC="$ROOT/render-update/public"
ANDROID_HOME="$TOOLS/android-sdk"
EXPECTED_SIGNER="B5BBDB2521ACE477BBC1AA2F431ADDE7061268A38E267331FCB67D1E97640AFD"
UPDATE_BASE_URL="${UPDATE_BASE_URL:-https://remplissage-papier-officiel-updates.onrender.com}"

rm -rf "$PUBLIC"
mkdir -p "$PUBLIC" "$TOOLS" "$ANDROID_HOME"

if [ -z "${ANDROID_KEYSTORE_BASE64:-}" ] || [ -z "${ANDROID_KEYSTORE_PASSWORD:-}" ]; then
  echo "Permanent signing environment variables are missing"
  exit 1
fi

# JDK 17 fallback for Render build images that do not already provide Java.
if ! command -v java >/dev/null 2>&1; then
  JDK_DIR="$TOOLS/jdk17"
  if [ ! -x "$JDK_DIR/bin/java" ]; then
    rm -rf "$JDK_DIR" "$TOOLS/jdk17.tar.gz" "$TOOLS/jdk-extract"
    mkdir -p "$TOOLS/jdk-extract"
    curl -fL --retry 3 --retry-delay 3 \
      "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" \
      -o "$TOOLS/jdk17.tar.gz"
    tar -xzf "$TOOLS/jdk17.tar.gz" -C "$TOOLS/jdk-extract"
    FOUND_JDK="$(find "$TOOLS/jdk-extract" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
    mv "$FOUND_JDK" "$JDK_DIR"
    rm -rf "$TOOLS/jdk-extract" "$TOOLS/jdk17.tar.gz"
  fi
  export JAVA_HOME="$JDK_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

java -version

SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  rm -rf "$ANDROID_HOME/cmdline-tools"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -fL --retry 3 --retry-delay 3 \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
    -o "$TOOLS/android-tools.zip"
  mkdir -p "$TOOLS/android-tools-extract"
  rm -rf "$TOOLS/android-tools-extract"/*
  python3 -m zipfile -e "$TOOLS/android-tools.zip" "$TOOLS/android-tools-extract"
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
  cp -R "$TOOLS/android-tools-extract/cmdline-tools/." "$ANDROID_HOME/cmdline-tools/latest/"
  rm -rf "$TOOLS/android-tools.zip" "$TOOLS/android-tools-extract"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
set -o pipefail
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0"

KEYSTORE="$TOOLS/remplissage-permanent-signing.p12"
printf '%s' "$ANDROID_KEYSTORE_BASE64" | tr -d '\r\n ' | base64 -d > "$KEYSTORE"
chmod 600 "$KEYSTORE"

export ANDROID_KEYSTORE_FILE="$KEYSTORE"
export ANDROID_KEYSTORE_PASSWORD

chmod +x gradlew
./gradlew clean assembleRelease

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "Signed release APK not found: $APK"
  exit 1
fi

APKSIGNER="$ANDROID_HOME/build-tools/34.0.0/apksigner"
VERIFY_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$APK")"
echo "$VERIFY_OUTPUT"
ACTUAL_SIGNER="$(printf '%s\n' "$VERIFY_OUTPUT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1 | tr '[:lower:]' '[:upper:]' | tr -d ':[:space:]')"
if [ "$ACTUAL_SIGNER" != "$EXPECTED_SIGNER" ]; then
  echo "Unexpected Android signer: $ACTUAL_SIGNER"
  exit 1
fi

VERSION="$(sed -n "s/.*versionName ['\"]\([^'\"]*\)['\"].*/\1/p" app/build.gradle | head -n 1)"
VERSION_CODE="$(sed -n "s/.*versionCode[[:space:]]\+\([0-9][0-9]*\).*/\1/p" app/build.gradle | head -n 1)"
if [ -z "$VERSION" ] || [ -z "$VERSION_CODE" ]; then
  echo "Unable to read Android version"
  exit 1
fi

APK_NAME="Remplissage-papier-officiel-v${VERSION}.apk"
cp "$APK" "$PUBLIC/$APK_NAME"
cp "$APK" "$PUBLIC/Remplissage-papier-officiel-latest.apk"
SHA256="$(sha256sum "$APK" | awk '{print $1}')"

cat > "$PUBLIC/update.json" <<EOF
{
  "version": "$VERSION",
  "versionCode": $VERSION_CODE,
  "releaseName": "Remplissage Papier Officiel v$VERSION",
  "apkUrl": "$UPDATE_BASE_URL/$APK_NAME",
  "sha256": "sha256:$SHA256",
  "signerSha256": "$ACTUAL_SIGNER"
}
EOF

cat > "$PUBLIC/index.html" <<EOF
<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Remplissage Papier Officiel</title></head><body><h1>Remplissage Papier Officiel</h1><p>Version disponible : $VERSION</p><p><a href="/$APK_NAME">Télécharger l'APK signé</a></p></body></html>
EOF

echo "Signed update ready: $APK_NAME"
echo "SHA-256: $SHA256"
echo "Signer: $ACTUAL_SIGNER"
