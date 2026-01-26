#!/bin/bash
set -e

MODS_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods"

echo "=== Deploying to Windows Hytale Client ==="
echo "Mods Directory: $MODS_DIR"
echo ""

# Check if main jar exists (with dependencies)
if [ ! -f "build/libs/livinglands-reloaded-1.0.0-beta.jar" ]; then
	echo "ERROR: Main jar not found. Run './gradlew build' first."
	exit 1
fi

# Remove old jars with different names (if exist)
if [ -f "$MODS_DIR/livinglands-1.0.0-beta.jar" ]; then
	echo "Removing old jar: livinglands-1.0.0-beta.jar"
	rm "$MODS_DIR/livinglands-1.0.0-beta.jar"
fi
if [ -f "$MODS_DIR/livinglands-reloaded-1.0.0-beta-shadow.jar" ]; then
	echo "Removing old jar: livinglands-reloaded-1.0.0-beta-shadow.jar"
	rm "$MODS_DIR/livinglands-reloaded-1.0.0-beta-shadow.jar"
fi

# Copy main jar to mods directory
echo "Copying JAR to mods directory..."
cp build/libs/livinglands-reloaded-1.0.0-beta.jar "$MODS_DIR/livinglands-reloaded-1.0.0-beta.jar"

# Verify copy
JAR_SIZE=$(stat -c%s "$MODS_DIR/livinglands-reloaded-1.0.0-beta.jar")
echo "Deployed: livinglands-reloaded-1.0.0-beta.jar ($((JAR_SIZE / 1024 / 1024)) MB)"

echo ""
echo "=== Deployment Complete ==="
echo ""
echo "Next steps:"
echo "1. Start/restart Hytale and host 'Test1' world"
echo "2. Monitor logs with: ./watch_windows_logs.sh"
echo "3. Join the server (as client or from another instance)"
echo "4. Look for '=== ADD PLAYER TO WORLD EVENT FIRED ==='"
