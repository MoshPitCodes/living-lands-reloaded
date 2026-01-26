package com.livinglands.modules.professions.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter

/**
 * Command to toggle the professions panel.
 * 
 * Usage: /ll profession
 * 
 * Shows/hides the professions panel in the unified Living Lands HUD displaying:
 * - All 5 professions with current levels and XP
 * - All 15 abilities (3 per profession) with unlock status
 * - Total XP earned summary
 */
class ProfessionCommand : CommandBase(
    "profession",
    "Toggle professions panel",
    false
) {
    
    private val logger: HytaleLogger = CoreModule.logger
    
    override fun executeSync(ctx: CommandContext) {
        // Check if sender is a player
        if (!ctx.isPlayer) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }
        
        // Get player entity reference
        val entityRef = ctx.senderAsPlayerRef() ?: run {
            MessageFormatter.commandError(ctx, "Unable to get player reference")
            return
        }
        
        // Find player session by entity ref
        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }
        if (session == null) {
            MessageFormatter.commandError(ctx, "Player session not found")
            return
        }
        
        val playerId = session.playerId
        
        // Get the unified HUD element for this player
        val hudElement = CoreModule.hudManager.getHud(playerId)
        
        if (hudElement == null) {
            MessageFormatter.commandError(ctx, "HUD not available. Please rejoin the server.")
            return
        }
        
        // Toggle the professions panel
        val newState = hudElement.toggleProfessionsPanel()
        
        // Force HUD refresh by accessing the player entity
        val world = session.world
        world.execute {
            try {
                val store = session.store
                val player = store.getComponent(session.entityRef, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                if (player != null) {
                    @Suppress("DEPRECATION")
                    val playerRef = player.playerRef
                    if (playerRef != null) {
                        CoreModule.hudManager.refreshHud(player, playerRef)
                    }
                }
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to refresh HUD for player $playerId")
            }
        }
        
        // Send feedback to player
        val message = if (newState) {
            "Showing professions panel"
        } else {
            "Hiding professions panel"
        }
        
        MessageFormatter.commandSuccess(ctx, message)
    }
}
