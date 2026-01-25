#!/bin/bash
cd libs

echo "=== Starting Hytale Server with Living Lands Reloaded ==="
echo "Logs will be filtered for Living Lands events..."
echo ""

# Start server in background and tail logs
java -jar Server/HytaleServer.jar 2>&1 | tee server.log | grep --line-buffered -E "(LivingLands|metabolism|ensureStats|LOADED|CREATED|UPDATE metabolism)"
