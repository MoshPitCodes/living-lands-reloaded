package com.livinglands.modules.professions.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.modules.professions.hud.ProfessionsProgressElement

/**
 * Command to toggle the professions progress panel.
 * 
 * Usage: /ll progress
 * 
 * Shows/hides a compact progress panel displaying:
 * - All 5 professions at once
 * - Current level (e.g., "Lv 5")
 * - Visual progress bar
 * - Progress percentage (e.g., "24%")
 * - XP numbers (e.g., "1.2K / 5.0K XP")
 */
class ProgressCommand : CommandBase(
    "progress",
    "Toggle professions progress panel",
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
        
        // Get the HUD manager
        val hudManager = CoreModule.hudManager
        
        // Get the progress panel element for this player
        val hudElements = hudManager.hudElements[playerId]
        val progressElement = hudElements?.get(ProfessionsProgressElement.NAMESPACE) as? ProfessionsProgressElement
        
        if (progressElement == null) {
            MessageFormatter.commandError(ctx, "Progress panel not available. Please rejoin the server.")
            return
        }
        
        // Toggle the panel
        val newState = progressElement.togglePanel()
        
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
                        hudManager.refreshHud(player, playerRef)
                    }
                }
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to refresh HUD for player $playerId")
            }
        }
        
        // Send feedback to player
        val message = if (newState) {
            "§aShowing professions progress"
        } else {
            "§7Hiding professions progress"
        }
        
        MessageFormatter.commandSuccess(ctx, message)
    }
}
