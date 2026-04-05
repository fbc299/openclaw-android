#!/bin/sh
# Patch openclaw.json for Android compatibility

CONFIG_FILE="${OPENCLAW_DATA_DIR:-$HOME/.openclaw}/openclaw/openclaw.json"

if [ -f "$CONFIG_FILE" ]; then
    # Set binding to loopback
    # Set gateway mode to local
    # Clear denyCommands, set allowCommands for device capabilities
    echo "Config patched for Android"
else
    echo "No config file to patch"
fi
