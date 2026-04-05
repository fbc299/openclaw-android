#!/bin/sh
# OpenClaw Android Setup Script
set -e

OPENCLAW_DATA_DIR="${OPENCLAW_DATA_DIR:-$HOME/.openclaw}"
NODE_DIR="${OPENCLAW_DATA_DIR}/node-v22.14.0-linux-arm64"
NODE_BIN="$NODE_DIR/bin/node"
NODEJS_LD="$NODE_DIR/lib/ld-linux-aarch64.so.1"

# Create directories
mkdir -p "$OPENCLAW_DATA_DIR/bin"
mkdir -p "$OPENCLAW_DATA_DIR/tmp"
mkdir -p "$OPENCLAW_DATA_DIR/npm"
mkdir -p "$OPENCLAW_DATA_DIR/openclaw"

# Create node wrapper script
cat > "$OPENCLAW_DATA_DIR/bin/node" << 'WRAPPER'
#!/bin/sh
export HOME="$OPENCLAW_DATA_DIR"
export TMPDIR="$OPENCLAW_DATA_DIR/tmp"
export npm_config_cache="$OPENCLAW_DATA_DIR/npm"
export npm_config_prefix="$OPENCLAW_DATA_DIR/npm"
export NODE_PATH="$OPENCLAW_DATA_DIR/npm/lib/node_modules"

# Inject bionic bypass
"$NODEJS_LD" --library-path "$NODE_DIR/lib" --insecure-rpath \
    -e "require('./bionic-bypass.js')" "$NODE_BIN" "$@"
WRAPPER
chmod +x "$OPENCLAW_DATA_DIR/bin/node"

# Setup PATH
export PATH="$OPENCLAW_DATA_DIR/bin:$PATH"

echo "Setup complete. Node.js ready at $OPENCLAW_DATA_DIR/bin/node"
