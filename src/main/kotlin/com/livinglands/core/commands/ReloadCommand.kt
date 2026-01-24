package com.livinglands.core.commands

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule

/**
 * Subcommand to hot-reload configuration files.
 * 
 * Usage: /ll reload [module]
 * 
 * - /ll reload         - Reload all configs
 * - /ll reload core    - Reload only core.yml
 * - /ll reload metabolism - Reload only metabolism.yml
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
    
    override fun executeSync(ctx: CommandContext) {
        val configManager = CoreModule.config
        
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
                ctx.sendMessage(Message.raw("[Living Lands] Config '$moduleId' is not loaded. Available: $available"))
                return
            }
            
            val reloaded = configManager.reload(moduleId)
            if (reloaded.isNotEmpty()) {
                ctx.sendMessage(Message.raw("[Living Lands] Reloaded: ${reloaded.joinToString(", ")}"))
            } else {
                ctx.sendMessage(Message.raw("[Living Lands] Failed to reload '$moduleId' (check logs)"))
            }
        } else {
            // Reload all configs
            val available = configManager.getLoadedConfigs()
            ctx.sendMessage(Message.raw("[Living Lands] Found configs: ${available.joinToString(", ")}"))
            
            val reloaded = configManager.reload()
            
            if (reloaded.isEmpty()) {
                ctx.sendMessage(Message.raw("[Living Lands] No configurations reloaded (configs unchanged or error - check logs)"))
            } else {
                ctx.sendMessage(Message.raw("[Living Lands] Successfully reloaded: ${reloaded.joinToString(", ")}"))
            }
        }
    }
}
