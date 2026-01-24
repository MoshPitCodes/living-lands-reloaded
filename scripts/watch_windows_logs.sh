#!/bin/bash

# Watch logs from Windows Hytale server
SERVER_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1"
LOGFILE=$(ls -t "$SERVER_DIR/logs/"*.log 2>/dev/null | head -1)

if [ -z "$LOGFILE" ]; then
	echo "No log files found in $SERVER_DIR/logs/"
	exit 1
fi

echo "=== Monitoring: $LOGFILE ==="
echo "=== Filtering for Living Lands messages ==="
echo ""

# Tail the log and filter for our debug markers
tail -f "$LOGFILE" | grep --line-buffered -E "(LivingLands|===|>>>)"
