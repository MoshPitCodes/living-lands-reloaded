package com.livinglands.core.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter

/**
 * Subcommand to hot-reload configuration files.
 * 
 * Usage: /ll reload
 * 
 * Reloads all Living Lands configuration files from disk.
 * 
 * Requires operator permission.
 */
class ReloadCommand : CommandBase(
    "reload",          // Subcommand name (no "ll" prefix)
    "Reload Living Lands configuration",  // Description
    true               // Requires operator permission
) {
    
    override fun executeSync(ctx: CommandContext) {
        val configManager = CoreModule.config
        
        // Reload all configs
        val reloaded = configManager.reload()
        
        if (reloaded.isEmpty()) {
            MessageFormatter.commandWarning(ctx, "No configurations reloaded (configs unchanged or error - check logs)")
        } else {
            MessageFormatter.commandSuccess(ctx, "Successfully reloaded: ${reloaded.joinToString(", ")}")
            
            // Show world information
            val worlds = CoreModule.worlds.getAllContexts()
            if (worlds.isNotEmpty()) {
                MessageFormatter.commandInfo(ctx, "Active worlds: ${worlds.size}")
                worlds.forEach { worldContext ->
                    val hasOverride = worldContext.metabolismConfig != null
                    val status = if (hasOverride) "✓ custom config" else "○ default config"
                    MessageFormatter.commandInfo(ctx, "  • ${worldContext.worldName}: $status")
                }
            }
        }
    }
}
