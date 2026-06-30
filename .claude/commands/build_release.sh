#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE="$SCRIPT_DIR/gradlew"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
APK_SRC="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
AAB_SRC="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
MAPPING_SRC="$SCRIPT_DIR/app/build/outputs/mapping/release/mapping.txt"
APK_DIR="$SCRIPT_DIR/apk"

# 預設不進版號；帶 --bump / -b 才自動遞增 versionCode
BUMP=false
case "${1:-}" in
  --bump|-b|bump) BUMP=true ;;
esac

if [ "$BUMP" = true ]; then
  CURRENT_CODE=$(grep -E '^\s*versionCode\s*=\s*[0-9]+' "$BUILD_GRADLE" | grep -oE '[0-9]+' | head -n1)
  if [ -z "$CURRENT_CODE" ]; then
    echo "Error: cannot read versionCode from build.gradle.kts"
    exit 1
  fi
  NEW_CODE=$((CURRENT_CODE + 1))
  sed -i '' -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1$NEW_CODE/" "$BUILD_GRADLE"
  echo "versionCode: $CURRENT_CODE → $NEW_CODE"
  trap 'echo "Build failed, reverting versionCode to $CURRENT_CODE"; sed -i "" -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1$CURRENT_CODE/" "$BUILD_GRADLE"' ERR
else
  echo "versionCode: not bumped (use --bump to increment)"
fi

# 從 build.gradle.kts 取得版本號
VERSION=$(grep -E '^\s*versionName\s*=\s*"[^"]+"' "$BUILD_GRADLE" | sed 's/.*"\(.*\)".*/\1/')
if [ -z "$VERSION" ]; then
  echo "Error: cannot read versionName from build.gradle.kts"
  exit 1
fi

DEST_DIR="$APK_DIR/$VERSION"
mkdir -p "$DEST_DIR"

cd "$SCRIPT_DIR"

echo "Building release APK (version: $VERSION)..."
"$GRADLE" assembleRelease
cp "$APK_SRC" "$DEST_DIR/Tag&Go_V${VERSION}.apk"
echo "APK: $DEST_DIR/Tag&Go_V${VERSION}.apk"

echo "Building release AAB (version: $VERSION)..."
"$GRADLE" bundleRelease
cp "$AAB_SRC" "$DEST_DIR/Tag&Go_V${VERSION}.aab"
echo "AAB: $DEST_DIR/Tag&Go_V${VERSION}.aab"

if [ -f "$MAPPING_SRC" ]; then
  cp "$MAPPING_SRC" "$DEST_DIR/mapping_V${VERSION}.txt"
  echo "Mapping: $DEST_DIR/mapping_V${VERSION}.txt"
fi

echo "Done."
