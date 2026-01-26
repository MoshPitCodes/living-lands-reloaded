package com.livinglands.modules.metabolism.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter

/**
 * Toggle HUD element visibility.
 * Commands: /ll stats, /ll buffs, /ll debuffs (matching v2.6.0)
 * 
 * These toggle visibility of sections within the unified Living Lands HUD.
 */
class HudToggleCommand(
    private val toggleType: ToggleType
) : CommandBase(
    toggleType.commandName,  // Subcommand name (no "ll" prefix)
    toggleType.description,
    false  // Not operator-only
) {
    
    enum class ToggleType(
        val commandName: String,
        val description: String,
        val displayName: String
    ) {
        STATS("stats", "Toggle metabolism stats display", "Metabolism Stats"),
        BUFFS("buffs", "Toggle buffs display", "Buffs"),
        DEBUFFS("debuffs", "Toggle debuffs display", "Debuffs")
    }
    
    override fun executeSync(ctx: CommandContext) {
        // Check if sender is a player
        if (!ctx.isPlayer) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }
        
        // Get player entity reference (just to identify the player)
        val entityRef = ctx.senderAsPlayerRef() ?: run {
            MessageFormatter.commandError(ctx, "Unable to get player reference")
            return
        }
        
        // Find player session by entity ref (no ECS access, just registry lookup)
        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }
        if (session == null) {
            MessageFormatter.commandError(ctx, "Player session not found")
            return
        }
        
        // Get player UUID from session
        val playerId = session.playerId
        
        // Get unified HUD element from MultiHudManager
        val hudElement = CoreModule.hudManager.getHud(playerId)
        
        if (hudElement == null) {
            MessageFormatter.commandError(ctx, "HUD not loaded yet. Please wait a moment and try again.")
            return
        }
        
        // Toggle the appropriate element
        val nowVisible = when (toggleType) {
            ToggleType.STATS -> hudElement.toggleStats()
            ToggleType.BUFFS -> hudElement.toggleBuffs()
            ToggleType.DEBUFFS -> hudElement.toggleDebuffs()
        }
        
        // Send feedback message
        val status = if (nowVisible) "shown" else "hidden"
        MessageFormatter.commandSuccess(ctx, "${toggleType.displayName} ${status}")
    }
}
