#!/bin/bash

# Migration script for LivingLands → LivingLandsReloaded folder rename
# Run this after deploying the new JAR to migrate existing data

OLD_FOLDER="LivingLands"
NEW_FOLDER="LivingLandsReloaded"
MODS_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods"

cd "$MODS_DIR" || exit 1

if [ -d "$OLD_FOLDER" ] && [ -d "$NEW_FOLDER" ]; then
    echo "Both folders exist. Merging data from old to new..."
    
    # Copy config files (user may have customized)
    if [ -d "$OLD_FOLDER/config" ]; then
        echo "Copying config files..."
        cp -rv "$OLD_FOLDER/config/"* "$NEW_FOLDER/config/" 2>/dev/null
    fi
    
    # Copy database files (preserve player data)
    if [ -d "$OLD_FOLDER/data" ]; then
        echo "Copying database files..."
        cp -rv "$OLD_FOLDER/data/"* "$NEW_FOLDER/data/" 2>/dev/null
    fi
    
    echo ""
    echo "Migration complete! Old folder kept for safety."
    echo "To remove old folder after verifying data:"
    echo "  rm -rf \"$MODS_DIR/$OLD_FOLDER\""
    
elif [ -d "$OLD_FOLDER" ] && [ ! -d "$NEW_FOLDER" ]; then
    echo "Old folder exists, new folder doesn't. Renaming..."
    mv "$OLD_FOLDER" "$NEW_FOLDER"
    echo "Renamed $OLD_FOLDER → $NEW_FOLDER"
    
elif [ ! -d "$OLD_FOLDER" ] && [ -d "$NEW_FOLDER" ]; then
    echo "New folder already exists. Nothing to migrate."
    
else
    echo "Neither folder exists. Start the server to create the new folder."
fi

echo ""
echo "Current mod folders:"
ls -la "$MODS_DIR" | grep livinglands || echo "  (none)"
