#!/bin/bash
set -e

# Change to project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_ROOT"

# Read version from version.properties (single source of truth)
VERSION=$(grep "mod.version=" version.properties | cut -d'=' -f2)
if [ -z "$VERSION" ]; then
	echo "ERROR: Could not read version from version.properties"
	exit 1
fi

GLOBAL_MODS_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods"
ASSET_PACK_DIR="$GLOBAL_MODS_DIR/MPC_LivingLandsReloaded"
JAR_NAME="livinglands-reloaded-${VERSION}.jar"

echo "=== Deploying Living Lands Reloaded v${VERSION} to Windows Hytale Client ==="
echo "Global Mods Directory: $GLOBAL_MODS_DIR"
echo "Asset Pack Directory: $ASSET_PACK_DIR"
echo ""

# Build if jar doesn't exist
if [ ! -f "build/libs/${JAR_NAME}" ]; then
	echo "JAR not found, building..."
	./gradlew build
fi

# Check if main jar exists (with dependencies)
if [ ! -f "build/libs/${JAR_NAME}" ]; then
	echo "ERROR: Main jar not found at build/libs/${JAR_NAME}"
	echo "Run './gradlew build' first."
	exit 1
fi

# Remove old jars with different versions
echo "Cleaning old JARs..."
rm -f "$GLOBAL_MODS_DIR"/livinglands-reloaded-*.jar 2>/dev/null || true
rm -f "$GLOBAL_MODS_DIR"/livinglands-*.jar 2>/dev/null || true

# Copy main jar to mods directory
echo "Copying JAR to mods directory..."
cp "build/libs/${JAR_NAME}" "$GLOBAL_MODS_DIR/${JAR_NAME}"

# Verify copy
JAR_SIZE=$(stat -c%s "$GLOBAL_MODS_DIR/${JAR_NAME}")
echo "Deployed: ${JAR_NAME} ($((JAR_SIZE / 1024 / 1024)) MB)"

# Extract asset pack to separate folder
echo ""
echo "=== Extracting Asset Pack ==="

# Create asset pack directory
mkdir -p "$ASSET_PACK_DIR"

# Extract Common/ folder and manifest.json from JAR to asset pack folder
echo "Extracting assets from JAR..."

# Extract Common/ directory
unzip -o "build/libs/${JAR_NAME}" "Common/*" -d "$ASSET_PACK_DIR" 2>/dev/null || true

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
echo "  1. JAR: $GLOBAL_MODS_DIR/${JAR_NAME}"
echo "  2. Asset Pack: $ASSET_PACK_DIR/"
echo ""
echo "IMPORTANT: Full Hytale restart required!"
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
