#!/bin/bash

# Deploy Living Lands Assets to Client Data Directory
# This ensures the client can load UI files from the asset pack

set -e

CLIENT_DATA_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/install/release/package/game/latest/Client/Data"
ASSET_SOURCE="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/MPC_LivingLandsReloaded"
CLIENT_DEST="$CLIENT_DATA_DIR/Mods/MPC_LivingLandsReloaded"

echo "=== Deploying Living Lands Assets to Client ==="
echo "Source: $ASSET_SOURCE"
echo "Destination: $CLIENT_DEST"
echo ""

# Check if source exists
if [ ! -d "$ASSET_SOURCE" ]; then
	echo "❌ Error: Asset source not found!"
	echo "Please run ./scripts/deploy_windows.sh first to build and extract assets"
	exit 1
fi

# Create client mods directory if it doesn't exist
mkdir -p "$CLIENT_DATA_DIR/Mods"

# Remove old assets if they exist
if [ -d "$CLIENT_DEST" ]; then
	echo "Removing old client assets..."
	rm -rf "$CLIENT_DEST"
fi

# Copy assets to client directory
echo "Copying assets to client..."
cp -r "$ASSET_SOURCE" "$CLIENT_DEST"

# Verify copy
if [ -d "$CLIENT_DEST/Common/UI/Custom/Hud" ]; then
	echo "✅ Assets copied successfully!"
	echo ""
	echo "Client asset files:"
	find "$CLIENT_DEST" -name "*.ui" -type f | sed "s|$CLIENT_DEST/||"
else
	echo "❌ Error: Copy failed!"
	exit 1
fi

echo ""
echo "=== Client Asset Deployment Complete ==="
echo ""
echo "⚠️  IMPORTANT: You must RESTART Hytale completely for changes to take effect!"
echo "    1. Close Hytale client (if running)"
echo "    2. Start Hytale"
echo "    3. Host/join your world"
echo "    4. UI files will now load properly"
