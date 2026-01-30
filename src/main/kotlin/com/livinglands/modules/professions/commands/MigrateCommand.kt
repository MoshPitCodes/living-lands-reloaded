package com.livinglands.modules.professions.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter

/**
 * Admin command to manually migrate v2.6.0 player data.
 * 
 * **STATUS:** Placeholder - Full implementation pending CommandContext API clarification
 * 
 * **Manual Migration Workaround:**
 * 1. Stop the server
 * 2. Delete the global livinglands.db database
 * 3. Delete the v2.6.0 JSON files (they will be migrated on next startup)
 * 4. Start the server - migration will run automatically
 * 
 * **Automatic Migration Issue:**
 * - Migration runs after player join (creates default data)
 * - Can't overwrite existing data without this command
 * - Need proper argument parsing from CommandContext
 * 
 * TODO: Implement proper argument parsing when CommandContext API is available
 * TODO: Add --force flag to confirm overwrite
 * TODO: Add preview mode (show what will be migrated)
 */
class MigrateCommand : CommandBase(
    "migrate",
    "Migrate v2.6.0 player data manually",
    true  // Requires operator
) {
    
    private val logger: HytaleLogger = CoreModule.logger
    
    override fun executeSync(ctx: CommandContext) {
        MessageFormatter.commandError(ctx, "Manual migration command not yet implemented")
        MessageFormatter.commandInfo(ctx, "")
        MessageFormatter.commandInfo(ctx, "To migrate v2.6.0 data:")
        MessageFormatter.commandInfo(ctx, "1. Stop the server")
        MessageFormatter.commandInfo(ctx, "2. Delete: mods/MPC_LivingLandsReloaded/data/global/livinglands.db")
        MessageFormatter.commandInfo(ctx, "3. Ensure v2.6.0 JSON files exist in:")
        MessageFormatter.commandInfo(ctx, "   UserData/Mods/LivingLands/leveling/playerdata/")
        MessageFormatter.commandInfo(ctx, "4. Restart server - migration runs automatically")
        MessageFormatter.commandInfo(ctx, "")
        MessageFormatter.commandWarning(ctx, "This will migrate ALL players, not just one.")
    }
}
