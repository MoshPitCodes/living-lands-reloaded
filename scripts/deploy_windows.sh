#!/bin/bash
set -e

GLOBAL_MODS_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods"
ASSET_PACK_DIR="$GLOBAL_MODS_DIR/MPC_LivingLandsReloaded"

echo "=== Deploying to Windows Hytale Client ==="
echo "Global Mods Directory: $GLOBAL_MODS_DIR"
echo "Asset Pack Directory: $ASSET_PACK_DIR"
echo ""

# Check if main jar exists (with dependencies)
if [ ! -f "build/libs/livinglands-reloaded-1.0.0-beta.jar" ]; then
	echo "ERROR: Main jar not found. Run './gradlew build' first."
	exit 1
fi

# Remove old jars with different names (if exist)
if [ -f "$GLOBAL_MODS_DIR/livinglands-1.0.0-beta.jar" ]; then
	echo "Removing old jar: livinglands-1.0.0-beta.jar"
	rm "$GLOBAL_MODS_DIR/livinglands-1.0.0-beta.jar"
fi
if [ -f "$GLOBAL_MODS_DIR/livinglands-reloaded-1.0.0-beta-shadow.jar" ]; then
	echo "Removing old jar: livinglands-reloaded-1.0.0-beta-shadow.jar"
	rm "$GLOBAL_MODS_DIR/livinglands-reloaded-1.0.0-beta-shadow.jar"
fi

# Copy main jar to mods directory
echo "Copying JAR to mods directory..."
cp build/libs/livinglands-reloaded-1.0.0-beta.jar "$GLOBAL_MODS_DIR/livinglands-reloaded-1.0.0-beta.jar"

# Verify copy
JAR_SIZE=$(stat -c%s "$GLOBAL_MODS_DIR/livinglands-reloaded-1.0.0-beta.jar")
echo "Deployed: livinglands-reloaded-1.0.0-beta.jar ($((JAR_SIZE / 1024 / 1024)) MB)"

# Extract asset pack to separate folder
echo ""
echo "=== Extracting Asset Pack ==="

# Create asset pack directory
mkdir -p "$ASSET_PACK_DIR"

# Extract Common/ folder and manifest.json from JAR to asset pack folder
echo "Extracting assets from JAR..."

# Extract Common/ directory
unzip -o "build/libs/livinglands-reloaded-1.0.0-beta.jar" "Common/*" -d "$ASSET_PACK_DIR" 2>/dev/null || true

# Create a proper asset pack manifest.json at root level
# (The Common/manifest.json is for the Common assets subfolder, but the pack also needs a root manifest)
cat >"$ASSET_PACK_DIR/manifest.json" <<'EOF'
{
  "Group": "MPC",
  "Name": "LivingLandsReloaded"
}
EOF

echo "Asset pack deployed to: $ASSET_PACK_DIR"

# List what was extracted
echo ""
echo "Asset pack contents:"
find "$ASSET_PACK_DIR" -type f | head -20

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "Deployed files:"
echo "  1. JAR: $GLOBAL_MODS_DIR/livinglands-reloaded-1.0.0-beta.jar"
echo "  2. Asset Pack: $ASSET_PACK_DIR/"
echo ""
echo "⚠️  IMPORTANT: Full Hytale restart required!"
echo ""
echo "Next steps:"
echo "  1. Close Hytale completely (if running)"
echo "  2. Restart Hytale client"
echo "  3. Host your world"
echo "  4. Join the server"
echo "  5. Verify HUD displays correctly"
echo ""
echo "Troubleshooting:"
echo "  - Check server logs: ./scripts/watch_windows_logs.sh"
echo "  - Verify asset pack loaded: grep 'Loaded pack: MPC:LivingLandsReloaded' logs"
echo "  - Check for UI errors: grep 'Could not find document' logs"
