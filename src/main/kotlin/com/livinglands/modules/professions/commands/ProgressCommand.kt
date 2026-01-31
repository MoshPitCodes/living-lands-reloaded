package com.livinglands.modules.professions.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.commands.ModuleCommand

/**
 * Command to toggle the professions progress panel.
 * 
 * Usage: /ll progress
 * 
 * Shows/hides a compact progress panel in the unified Living Lands HUD displaying:
 * - All 5 professions at once
 * - Current level (e.g., "Lv 5")
 * - Visual progress bar
 * - Progress percentage (e.g., "24%")
 * - XP numbers (e.g., "1.2K / 5.0K XP")
 * 
 * Extends [ModuleCommand] to automatically check if the professions module is enabled
 * before executing. If disabled, shows a user-friendly error message.
 */
class ProgressCommand : ModuleCommand(
    name = "progress",
    description = "Toggle professions progress panel",
    moduleId = "professions"
) {
    
    private val logger: HytaleLogger = CoreModule.logger
    
    override fun executeIfModuleEnabled(ctx: CommandContext) {
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
        
        // Toggle the progress panel
        // The toggle method handles visibility and data population
        val newState = hudElement.toggleProgressPanel()
        
        // Send feedback to player
        val message = if (newState) {
            "Showing professions progress"
        } else {
            "Hiding professions progress"
        }
        
        MessageFormatter.commandSuccess(ctx, message)
    }
}
