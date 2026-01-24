#!/bin/bash
set -e

echo "=== Deploying Debug Build ==="

# Stop server
echo "Stopping server..."
pkill -f HytaleServer || true
sleep 2

# Clean old plugin
echo "Removing old plugin..."
rm libs/livinglands-*.jar 2>/dev/null || true

# Deploy new build
echo "Deploying new build..."
cp build/libs/livinglands-1.0.0-beta.jar libs/

echo "=== Deployment Complete ==="
echo ""
echo "Start server with:"
echo "  cd libs && java -jar HytaleServer.jar"
echo ""
echo "Monitor logs with:"
echo "  tail -f libs/*.log | grep -E '(LivingLands|===|>>>)'"
