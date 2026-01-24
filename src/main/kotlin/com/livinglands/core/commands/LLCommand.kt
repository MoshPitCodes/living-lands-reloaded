package com.livinglands.core.commands

import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase

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
        ctx.sendMessage(Message.raw("=== Living Lands v1.0.0-beta ==="))
        ctx.sendMessage(Message.raw("/ll reload [module] - Reload configuration"))
        ctx.sendMessage(Message.raw("/ll stats - Show your metabolism stats (coming soon)"))
    }
}
