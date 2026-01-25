package com.livinglands.core.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import java.awt.Color

/**
 * Subcommand to hot-reload configuration files.
 * 
 * Usage: /ll reload [module] [--confirm]
 * 
 * - /ll reload                    - Lists available configs
 * - /ll reload --confirm          - Reload all configs
 * - /ll reload metabolism         - Lists metabolism config status
 * - /ll reload metabolism --confirm - Reload only metabolism.yml
 * 
 * The --confirm flag is required to actually perform the reload.
 * Without it, the command just shows what would be reloaded.
 * 
 * Requires operator permission.
 */
class ReloadCommand : CommandBase(
    "reload",          // Subcommand name (no "ll" prefix)
    "Reload Living Lands configuration",  // Description
    true               // Requires operator permission
) {
    
    // Optional module argument
    private val moduleArg = withOptionalArg(
        "module",
        "Specific module to reload",
        ArgTypes.STRING
    )
    
    // Optional confirm flag
    private val confirmArg = withOptionalArg(
        "confirm",
        "Confirm the reload operation",
        ArgTypes.BOOLEAN
    )
    
    override fun executeSync(ctx: CommandContext) {
        val configManager = CoreModule.config
        
        // Check if confirm flag was provided
        val confirm = ctx.provided(confirmArg) && ctx.get(confirmArg) == true
        
        // Check if module argument was provided
        val moduleId: String? = if (ctx.provided(moduleArg)) {
            ctx.get(moduleArg)
        } else {
            null
        }
        
        if (moduleId != null) {
            // Reload specific module
            if (!configManager.isLoaded(moduleId)) {
                val available = configManager.getLoadedConfigs().joinToString(", ")
                MessageFormatter.commandError(ctx, "Config '$moduleId' is not loaded. Available: $available")
                return
            }
            
            if (!confirm) {
                // Dry-run: Show what would be reloaded
                MessageFormatter.commandWarning(ctx, "Would reload config: $moduleId")
                MessageFormatter.commandInfo(ctx, "Add --confirm true to actually reload: /ll reload $moduleId --confirm true", Color(170, 170, 170))
                return
            }
            
            // Actually reload
            val reloaded = configManager.reload(moduleId)
            if (reloaded.isNotEmpty()) {
                MessageFormatter.commandSuccess(ctx, "Reloaded: ${reloaded.joinToString(", ")}")
            } else {
                MessageFormatter.commandError(ctx, "Failed to reload '$moduleId' (check logs)")
            }
        } else {
            // Reload all configs
            val available = configManager.getLoadedConfigs()
            
            if (!confirm) {
                // Dry-run: Show what would be reloaded
                MessageFormatter.commandWarning(ctx, "Would reload all configs: ${available.joinToString(", ")}")
                MessageFormatter.commandInfo(ctx, "Add --confirm true to actually reload: /ll reload --confirm true", Color(170, 170, 170))
                return
            }
            
            // Actually reload all
            val reloaded = configManager.reload()
            
            if (reloaded.isEmpty()) {
                MessageFormatter.commandWarning(ctx, "No configurations reloaded (configs unchanged or error - check logs)")
            } else {
                MessageFormatter.commandSuccess(ctx, "Successfully reloaded: ${reloaded.joinToString(", ")}")
            }
        }
    }
}
