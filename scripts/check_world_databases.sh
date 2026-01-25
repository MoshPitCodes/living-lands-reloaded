#!/bin/bash
# Check all world databases for Living Lands Reloaded

echo "=== Living Lands Reloaded - World Database Check ==="
echo ""

SAVES_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves"

# Find all Living Lands data directories
find "$SAVES_DIR" -type d -path "*/mods/MPC_LivingLandsReloaded/data/*" 2>/dev/null | while read -r data_dir; do
	# Extract world name and UUID
	world_name=$(echo "$data_dir" | awk -F'/' '{for(i=1;i<=NF;i++) if($i=="Saves") print $(i+1)}')
	world_uuid=$(basename "$data_dir")

	echo "╔════════════════════════════════════════════════════════════════"
	echo "║ World: $world_name"
	echo "║ UUID: $world_uuid"
	echo "╚════════════════════════════════════════════════════════════════"

	# Check if database exists
	db_file="$data_dir/livinglands.db"
	if [ -f "$db_file" ]; then
		echo "✅ Database exists: livinglands.db"

		# Get file size
		size=$(du -h "$db_file" | cut -f1)
		echo "   Size: $size"

		# Query player stats
		echo ""
		echo "   Player Stats:"
		echo "   ┌──────────────────────────────────────────┬────────┬────────┬────────┬─────────────────────┐"
		echo "   │ Player ID                                │ Hunger │ Thirst │ Energy │ Last Updated        │"
		echo "   ├──────────────────────────────────────────┼────────┼────────┼────────┼─────────────────────┤"

		sqlite3 "$db_file" "SELECT 
            substr(player_id, 1, 40) as player,
            printf('%6.2f', hunger) as h,
            printf('%6.2f', thirst) as t,
            printf('%6.2f', energy) as e,
            datetime(last_updated/1000, 'unixepoch') as updated
        FROM metabolism_stats;" | while read -r line; do
			echo "   │ $line │"
		done

		echo "   └──────────────────────────────────────────┴────────┴────────┴────────┴─────────────────────┘"

		# Count total players
		player_count=$(sqlite3 "$db_file" "SELECT COUNT(*) FROM metabolism_stats;")
		echo "   Total players: $player_count"
	else
		echo "❌ No database found"
	fi

	echo ""
done

echo "=== Check Complete ==="
