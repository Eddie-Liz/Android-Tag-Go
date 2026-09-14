#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE="$SCRIPT_DIR/gradlew"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
APK_SRC="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
AAB_SRC="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
MAPPING_SRC="$SCRIPT_DIR/app/build/outputs/mapping/release/mapping.txt"
APK_DIR="$SCRIPT_DIR/apk"
PLAY_CREDENTIALS="$SCRIPT_DIR/play-service-account.json"

BUMP=false
PUBLISH=false
PROMOTE=true
PROMOTE_TRACK="rooti"

usage() {
  cat <<'USAGE'
Usage: build_release.sh [options]

  --bump, -b             Increment versionCode before building (reverted if the build fails)
  --publish, -p          Upload the AAB to the Play internal testing track, then promote it
  --promote-track <id>   Closed testing track to promote to (default: rooti); implies --publish
  --no-promote           With --publish, stop after the internal upload
  --help, -h             Show this message

With no options the script only builds and copies artifacts, exactly as before.
USAGE
}

while [ $# -gt 0 ]; do
  case "$1" in
    --bump|-b|bump) BUMP=true ;;
    --publish|-p) PUBLISH=true ;;
    --promote-track)
      PUBLISH=true
      # `${2:?}` only rejects empty/unset, so `--promote-track --bump` would silently swallow
      # the next flag and promote to a track literally named "--bump".
      case "${2:-}" in
        ''|-*) echo "Error: --promote-track requires a track id"; echo; usage; exit 1 ;;
      esac
      # Allowlist: `beta` is OPEN testing — anyone can join it from a public link — and
      # --release-status completed would push there live, with no second gate.
      case "$2" in
        rooti|internal) ;;
        beta|production)
          echo "Error: track '$2' reaches real users; this script only publishes to testing tracks."
          echo "For production use .claude/commands/play_release_production.sh"
          exit 1 ;;
        *) echo "Error: unknown track '$2' (allowed: rooti, internal)"; exit 1 ;;
      esac
      PROMOTE_TRACK="$2"
      shift
      ;;
    --no-promote) PROMOTE=false ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Error: unknown option '$1'"; echo; usage; exit 1 ;;
  esac
  shift
done

# Fail before the build rather than after it: a missing credential discovered five minutes
# later costs a whole build cycle. Without the file the Play tasks are not even registered.
if [ "$PUBLISH" = true ] && [ ! -f "$PLAY_CREDENTIALS" ]; then
  echo "Error: Play service account key not found at $PLAY_CREDENTIALS"
  echo
  echo "The key belongs to service account:"
  echo "  play-publisher@tag-and-go-publisher.iam.gserviceaccount.com"
  echo
  echo "To mint a replacement: GCP project 'tag-and-go-publisher' -> IAM -> Service Accounts"
  echo "-> play-publisher -> Keys -> Add key -> JSON, then save it as $PLAY_CREDENTIALS"
  echo
  echo "The account is already invited in Play Console with release permission for the"
  echo "testing tracks, so only the key file itself needs restoring."
  exit 1
fi

# Set the moment the upload command is entered, not when it returns. publishReleaseBundle
# uploads the bundle and THEN commits the edit as a separate step, and Play already holds the
# version code once the bundle reaches the artifact library. So a non-zero exit does not mean
# the code is still free, and reverting the bump there would make the next --bump regenerate a
# code Play rejects forever.
UPLOAD_STARTED=false

revert_bump() {
  if [ "$UPLOAD_STARTED" = true ]; then
    echo
    echo "WARNING: upload had already started — versionCode $NEW_CODE may already be consumed"
    echo "by Play even though the command failed. NOT reverting the bump."
    echo "Check the Play Console artifact library for a stray draft before the next release."
    return
  fi
  echo "Build failed, reverting versionCode to $CURRENT_CODE"
  sed -i "" -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1$CURRENT_CODE/" "$BUILD_GRADLE"
}

if [ "$BUMP" = true ]; then
  CURRENT_CODE=$(grep -E '^\s*versionCode\s*=\s*[0-9]+' "$BUILD_GRADLE" | grep -oE '[0-9]+' | head -n1)
  if [ -z "$CURRENT_CODE" ]; then
    echo "Error: cannot read versionCode from build.gradle.kts"
    exit 1
  fi
  NEW_CODE=$((CURRENT_CODE + 1))
  sed -i '' -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1$NEW_CODE/" "$BUILD_GRADLE"
  echo "versionCode: $CURRENT_CODE → $NEW_CODE"
  trap revert_bump ERR
else
  echo "versionCode: not bumped (use --bump to increment)"
fi

# 從 build.gradle.kts 取得版本號
VERSION=$(grep -E '^\s*versionName\s*=\s*"[^"]+"' "$BUILD_GRADLE" | sed 's/.*"\(.*\)".*/\1/')
if [ -z "$VERSION" ]; then
  echo "Error: cannot read versionName from build.gradle.kts"
  exit 1
fi
VERSION_CODE=$(grep -E '^\s*versionCode\s*=\s*[0-9]+' "$BUILD_GRADLE" | grep -oE '[0-9]+' | head -n1)
# `head` masks grep's exit status, so set -e never fires on a miss. Without this guard an empty
# value would reach `--version-code ""` — in the one branch where the code is already consumed.
if [ -z "$VERSION_CODE" ]; then
  echo "Error: cannot read versionCode from build.gradle.kts"
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

if [ "$PUBLISH" = true ]; then
  echo "Uploading AAB to Play internal track (version: $VERSION, code: $VERSION_CODE)..."
  UPLOAD_STARTED=true
  "$GRADLE" publishReleaseBundle

  # Upload committed successfully: the code is definitely consumed, so drop the trap entirely.
  trap - ERR
  echo "UPLOADED: versionCode $VERSION_CODE → internal"

  if [ "$PROMOTE" = true ]; then
    echo "Promoting internal → $PROMOTE_TRACK..."
    # --version-code pins the promotion to the build we just uploaded, so a stale release
    # sitting on the internal track cannot be promoted by mistake.
    if "$GRADLE" promoteReleaseArtifact \
        --from-track internal \
        --promote-track "$PROMOTE_TRACK" \
        --version-code "$VERSION_CODE" \
        --release-status completed; then
      echo "PROMOTED: internal → $PROMOTE_TRACK"
    else
      echo
      echo "ERROR: uploaded to internal, but promotion to '$PROMOTE_TRACK' failed."
      echo "versionCode $VERSION_CODE is already used on Play — do NOT re-run with --bump."
      echo "Retry the promotion on its own with:"
      echo "  ./gradlew promoteReleaseArtifact --from-track internal --promote-track $PROMOTE_TRACK --version-code $VERSION_CODE --release-status completed"
      echo "If the track id is wrong, find the right one in the Play Console track URL."
      exit 1
    fi
  fi
fi

echo "Done."
