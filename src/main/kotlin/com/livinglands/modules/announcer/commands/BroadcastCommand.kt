package com.livinglands.modules.announcer.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.core.commands.ModuleCommand
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter

/**
 * Command for broadcasting messages to all players.
 * Usage: /ll broadcast <message>
 * 
 * Requires operator permissions.
 */
class BroadcastCommand : ModuleCommand(
    name = "broadcast",
    description = "Broadcast a message to all players",
    moduleId = "announcer",
    operatorOnly = true
) {
    override fun executeIfModuleEnabled(ctx: CommandContext) {
        // Collect all arguments into message
        val inputString = ctx.inputString.orEmpty()
        val message = inputString.trim()
        
        if (message.isBlank()) {
            MessageFormatter.commandError(ctx, "Usage: /ll broadcast <message>")
            return
        }
        
        // Broadcast to all players
        val sessions = CoreModule.players.getAllSessions()
        
        if (sessions.isEmpty()) {
            MessageFormatter.commandWarning(ctx, "No players online to broadcast to")
            return
        }
        
        var successCount = 0
        sessions.forEach { session ->
            try {
                // Get PlayerRef from session
                val playerRef = session.world.entityStore.store.getComponent(
                    session.entityRef,
                    PlayerRef.getComponentType()
                ) ?: return@forEach
                
                MessageFormatter.announcement(playerRef, "&6[Broadcast] &f$message")
                successCount++
            } catch (e: Exception) {
                // Silently skip failed sends
            }
        }
        
        MessageFormatter.commandInfo(ctx, "Broadcast sent to $successCount/${sessions.size} players")
    }
}
