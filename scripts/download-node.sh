#!/bin/bash
# OpenClaw Android — Download Node.js Runtime
#
# Downloads the Node.js binary runtime for Android (arm64/aarch64).
# Extracted binaries are placed in assets/node/ for bundling with the APK.
#
# Usage: bash scripts/download-node.sh [arch] [version]
#
#   arch:    arm64 | arm | x86_64 | x86  (default: arm64)
#   version: Node.js version              (default: v22.14.0)

set -euo pipefail

ARCH="${1:-arm64}"
NODE_VERSION="${2:-v22.14.0}"

# ── Map architecture to Node.js dist name ──────────────────────────

case "$ARCH" in
    arm64|aarch64)
        DIST_ARCH="linux-arm64"
        ABI="arm64-v8a"
        ;;
    arm|armv7l|armhf)
        DIST_ARCH="linux-armv7l"
        ABI="armeabi-v7a"
        ;;
    x86_64)
        DIST_ARCH="linux-x64"
        ABI="x86_64"
        ;;
    x86|i686)
        DIST_ARCH="linux-x86"
        ABI="x86"
        ;;
    *)
        echo "❌ Unknown architecture: $ARCH"
        echo "   Supported: arm64, arm, x86_64, x86"
        exit 1
        ;;
esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
NODE_DIR="$PROJECT_DIR/assets/node/$ABI"
TARBALL="/tmp/nodejs-${ARCH}-${NODE_VERSION}.tar.gz"

NODE_DIST="node-${NODE_VERSION}-${DIST_ARCH}"
DOWNLOAD_URL="https://nodejs.org/dist/${NODE_VERSION}/${NODE_DIST}.tar.gz"

echo "🦞 OpenClaw Android — Download Node.js Runtime"
echo "   Architecture: $ARCH ($ABI)"
echo "   Node version: $NODE_VERSION"
echo "   Target dir:   $NODE_DIR"
echo ""

# ── Download ──────────────────────────────────────────────────────

if [ -f "$TARBALL" ]; then
    echo "⏭️  Cached tarball found: $TARBALL"
else
    echo "⬇️  Downloading from $DOWNLOAD_URL ..."
    curl -L --progress-bar "$DOWNLOAD_URL" -o "$TARBALL"
fi

# ── Extract ────────────────────────────────────────────────────────

echo ""
echo "📦 Extracting..."
mkdir -p "$NODE_DIR"
tar xzf "$TARBALL" -C /tmp/

# ── Install ────────────────────────────────────────────────────────

EXTRACTED="/tmp/${NODE_DIST}"

if [ -f "$EXTRACTED/bin/node" ]; then
    cp "$EXTRACTED/bin/node" "$NODE_DIR/node"
    chmod +x "$NODE_DIR/node"
    echo "✅ node binary installed → $NODE_DIR/node"
else
    echo "❌ node binary not found in extracted archive"
    exit 1
fi

# Copy glibc linker (arm64) if available
if [ "$ARCH" = "arm64" ]; then
    LINKER="$EXTRACTED/lib/ld-linux-aarch64.so.1"
    if [ -f "$LINKER" ]; then
        cp "$LINKER" "$NODE_DIR/ld-linux-aarch64.so.1"
        echo "✅ glibc linker installed"
    else
        echo "⚠️  glibc linker not found (may be statically linked)"
    fi

    # Copy lib directory
    if [ -d "$EXTRACTED/lib" ]; then
        if [ -d "$NODE_DIR/lib" ]; then
            rm -rf "$NODE_DIR/lib"
        fi
        cp -r "$EXTRACTED/lib" "$NODE_DIR/lib"
        echo "✅ Node.js libs installed"
    fi
fi

# ── Cleanup ────────────────────────────────────────────────────────

rm -rf "$EXTRACTED"

echo ""
echo "✅ Node.js $NODE_VERSION ($ABI) ready!"
echo ""
echo "📏 Installed files:"
du -sh "$NODE_DIR" 2>/dev/null || echo "   $NODE_DIR"
ls -la "$NODE_DIR"/ 2>/dev/null || true

echo ""
echo "Hint: Re-run with a different arch to download another ABI:"
echo "  bash scripts/download-node.sh arm    v22.14.0"
echo "  bash scripts/download-node.sh x86_64 v22.14.0"
