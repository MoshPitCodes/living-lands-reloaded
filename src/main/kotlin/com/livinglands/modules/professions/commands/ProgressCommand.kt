package com.livinglands.modules.professions.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.commands.ModuleCommand
import com.livinglands.modules.metabolism.MetabolismModule
import kotlinx.coroutines.runBlocking

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
         
         // Ensure HUD is registered (may not be if command called before onPlayerJoin completes)
         val hudElement = CoreModule.hudManager.getHud(playerId)
         if (hudElement == null) {
             // HUD not registered yet - try to register it now
             val metabolismModule = CoreModule.getModule<MetabolismModule>("metabolism")
             if (metabolismModule != null && metabolismModule.state.isOperational()) {
                 try {
                     val hudRegistered = runBlocking {
                         metabolismModule.ensureHudRegistered(playerId)
                     }
                     if (!hudRegistered) {
                         MessageFormatter.commandError(ctx, "Failed to initialize HUD. Please rejoin the server.")
                         return
                     }
                 } catch (e: Exception) {
                     logger.atWarning().withCause(e).log("Error registering HUD for /ll progress")
                     MessageFormatter.commandError(ctx, "HUD initialization failed. Please rejoin the server.")
                     return
                 }
             } else {
                 MessageFormatter.commandError(ctx, "Metabolism module not available. HUD cannot be initialized.")
                 return
             }
         }
         
         // Get the unified HUD element (should be available now)
         val hudElement2 = CoreModule.hudManager.getHud(playerId)
         if (hudElement2 == null) {
             MessageFormatter.commandError(ctx, "HUD not available. Please rejoin the server.")
             return
         }
         
         // Toggle the progress panel
         // The toggle method handles visibility and data population
         val newState = hudElement2.toggleProgressPanel()
        
        // Send feedback to player
        val message = if (newState) {
            "Showing professions progress"
        } else {
            "Hiding professions progress"
        }
        
        MessageFormatter.commandSuccess(ctx, message)
    }
}
