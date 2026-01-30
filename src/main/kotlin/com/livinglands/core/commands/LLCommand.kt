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
    
    /**
     * Register a subcommand to the main /ll command.
     * This allows modules to add their own subcommands.
     */
    fun registerSubCommand(subCommand: CommandBase) {
        addSubCommand(subCommand)
    }
    
    override fun executeSync(ctx: CommandContext) {
        // When /ll is typed without subcommand, show help
        val gold = Color(255, 170, 0)
        val aqua = Color(85, 255, 255)
        val gray = Color(170, 170, 170)
        
        MessageFormatter.commandRaw(ctx, "=== Living Lands v1.0.1 ===", gold)
        MessageFormatter.commandRaw(ctx, "", gray)
        MessageFormatter.commandRaw(ctx, "Commands:", gold)
        MessageFormatter.commandRaw(ctx, "/ll reload - Reload configuration files", aqua)
        MessageFormatter.commandRaw(ctx, "", gray)
        MessageFormatter.commandRaw(ctx, "HUD Toggles:", gold)
        MessageFormatter.commandRaw(ctx, "/ll stats - Toggle metabolism stats display", aqua)
        MessageFormatter.commandRaw(ctx, "/ll buffs - Toggle buffs display", aqua)
        MessageFormatter.commandRaw(ctx, "/ll debuffs - Toggle debuffs display", aqua)
        MessageFormatter.commandRaw(ctx, "/ll professions - Toggle professions panel", aqua)
        MessageFormatter.commandRaw(ctx, "/ll progress - Toggle professions progress", aqua)
    }
}
