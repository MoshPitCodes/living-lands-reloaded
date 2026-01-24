#!/bin/bash

# Find the most recent log file in libs/
LOGFILE=$(ls -t libs/*.log 2>/dev/null | head -1)

if [ -z "$LOGFILE" ]; then
	echo "No log files found in libs/"
	echo "Make sure the server is running"
	exit 1
fi

echo "=== Monitoring: $LOGFILE ==="
echo "=== Filtering for Living Lands messages ==="
echo ""

# Tail the log and filter for our debug markers
tail -f "$LOGFILE" | grep --line-buffered -E "(LivingLands|===|>>>)"
