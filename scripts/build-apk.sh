#!/bin/bash
# OpenClaw Android — APK Build Script
# Usage: bash scripts/build-apk.sh [--split] [--aab]
#
# Builds a release APK for Android. By default produces a per-ABI split APK.
# Pass --aab to build an Android App Bundle instead.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "🦞 OpenClaw Android — APK Build"
echo "   Project: $PROJECT_DIR"
echo ""

# ── Pre-flight checks ──────────────────────────────────────────────

if ! command -v flutter &> /dev/null; then
    echo "❌ Flutter not found. Install from https://flutter.dev"
    exit 1
fi

flutter_version=$(flutter --version --machine 2>/dev/null | head -1 || echo "unknown")
echo "📱 Flutter: $flutter_version"

# ── Configuration ──────────────────────────────────────────────────

BUILD_AAB=false
BUILD_SPLIT=true

for arg in "$@"; do
    case "$arg" in
        --aab)
            BUILD_AAB=true
            BUILD_SPLIT=false
            ;;
        --split)
            BUILD_SPLIT=true
            BUILD_AAB=false
            ;;
    esac
done

# ── Download Node.js runtime (if needed) ──────────────────────────

NODE_DIR="$PROJECT_DIR/assets/node"
NODE_VERSION="v22.14.0"

if [ "$BUILD_SPLIT" = true ] || [ "$BUILD_AAB" = true ]; then
    # Only needed when building release artifacts that bundle the runtime
    : # Node.js is downloaded separately if needed; skip here to keep build fast
fi

# ── Get Flutter dependencies ───────────────────────────────────────

echo ""
echo "📦 Getting Flutter dependencies..."
flutter pub get

# ── Build ──────────────────────────────────────────────────────────

echo ""
if [ "$BUILD_AAB" = true ]; then
    echo "🔨 Building AAB (Android App Bundle)..."
    flutter build appbundle --release
    echo ""
    echo "✅ AAB ready at:"
    echo "   build/app/outputs/bundle/release/app-release.aab"
else
    echo "🔨 Building APK (per-ABI)..."
    flutter build apk --release --split-per-abi
    echo ""
    echo "✅ APK(s) ready at:"
    echo "   build/app/outputs/flutter-apk/app-arm64-v8a-release.apk"
    echo "   build/app/outputs/flutter-apk/app-armeabi-v7a-release.apk"
    echo "   build/app/outputs/flutter-apk/app-x86_64-release.apk"
fi

echo ""
echo "📏 APK sizes:"
find build/app/outputs -name "*.apk" -o -name "*.aab" 2>/dev/null | while read -r f; do
    size=$(du -h "$f" | cut -f1)
    echo "   $size  $(basename "$f")"
done || echo "   (no output found)"

echo ""
echo "✅ Done!"
