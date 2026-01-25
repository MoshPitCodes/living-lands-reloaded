package com.livinglands.core.commands

import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.MessageFormatter
import java.awt.Color

/**
 * Main Living Lands command - /ll
 * 
 * Shows help or executes subcommands.
 */
class LLCommand : CommandBase(
    "ll",
    "Living Lands main command",
    false  // Not operator-only
) {
    
    init {
        // Allow all game modes (like v2.6.0 did)
        setPermissionGroups(GameMode.Adventure.toString(), GameMode.Creative.toString())
        
        // Add reload subcommand
        addSubCommand(ReloadCommand())
    }
    
    override fun executeSync(ctx: CommandContext) {
        // When /ll is typed without subcommand, show help
        val gold = Color(255, 170, 0)
        val aqua = Color(85, 255, 255)
        val gray = Color(170, 170, 170)
        
        MessageFormatter.commandRaw(ctx, "=== Living Lands v1.0.0-beta ===", gold)
        MessageFormatter.commandRaw(ctx, "/ll reload [module] --confirm true - Reload configuration", aqua)
        MessageFormatter.commandRaw(ctx, "/ll stats - Show your metabolism stats", aqua)
    }
}
