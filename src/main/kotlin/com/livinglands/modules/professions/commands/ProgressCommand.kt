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
         
         // Try to get HUD element (may not be initialized if command called immediately after join)
         var hudElement = CoreModule.hudManager.getHud(playerId)
         
         if (hudElement == null) {
             // HUD not registered yet - request metabolism module to lazy-initialize it
             val metabolismModule = CoreModule.getModule<MetabolismModule>("metabolism")
             if (metabolismModule != null && metabolismModule.state.isOperational()) {
                 try {
                     // Ensure HUD is registered (this is a suspend function that we need to block on)
                     val hudRegistered = runBlocking {
                         metabolismModule.ensureHudRegistered(playerId)
                     }
                     if (!hudRegistered) {
                         MessageFormatter.commandError(ctx, "Unable to load HUD - player or world not found")
                         return
                     }
                     // Now try to get the HUD element again
                     hudElement = CoreModule.hudManager.getHud(playerId)
                     if (hudElement == null) {
                         MessageFormatter.commandError(ctx, "HUD initialization failed - please try again")
                         return
                     }
                 } catch (e: Exception) {
                     logger.atWarning().withCause(e).log("Error initializing HUD for /ll progress: ${e.message}")
                     MessageFormatter.commandError(ctx, "HUD initialization error - please rejoin the server")
                     return
                 }
             } else {
                 MessageFormatter.commandError(ctx, "Metabolism module not available - HUD cannot be initialized")
                 return
             }
         }
         
         // Toggle the progress panel
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
