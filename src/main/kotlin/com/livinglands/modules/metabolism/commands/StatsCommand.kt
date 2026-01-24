package com.livinglands.modules.metabolism.commands

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismService

/**
 * Command to display current metabolism stats.
 * 
 * Usage: /ll stats
 * 
 * Shows hunger, thirst, and energy values for the executing player.
 * Can only be used by players (not console).
 */
class StatsCommand(
    private val metabolismService: MetabolismService
) : CommandBase(
    "ll stats",                          // Command name
    "Display your metabolism stats",     // Description
    false                                // Does not require operator
) {
    
    override fun executeSync(ctx: CommandContext) {
        // Check if sender is a player
        if (!ctx.isPlayer) {
            ctx.sendMessage(Message.raw("[Living Lands] This command can only be used by players"))
            return
        }
        
        // Get player entity reference
        val entityRef = ctx.senderAsPlayerRef() ?: run {
            ctx.sendMessage(Message.raw("[Living Lands] Unable to get player reference"))
            return
        }
        
        // Get the PlayerRef component from the entity to access UUID
        val store = entityRef.store
        val playerRefComponent = store.getComponent(entityRef, PlayerRef.getComponentType())
        
        if (playerRefComponent == null) {
            ctx.sendMessage(Message.raw("[Living Lands] Unable to get player component"))
            return
        }
        
        // Get player UUID from PlayerRef component
        @Suppress("DEPRECATION")
        val playerId = playerRefComponent.uuid.toString()
        
        // Get metabolism stats from service
        val stats = metabolismService.getStats(playerId)
        
        if (stats == null) {
            ctx.sendMessage(Message.raw("[Living Lands] Metabolism data not loaded. Please wait a moment and try again."))
            return
        }
        
        // Format the stats display
        val hungerBar = formatStatBar(stats.hunger)
        val thirstBar = formatStatBar(stats.thirst)
        val energyBar = formatStatBar(stats.energy)
        
        // Build and send the message
        ctx.sendMessage(Message.raw(""))  // Empty line for spacing
        ctx.sendMessage(Message.raw("--- Metabolism Stats ---"))
        ctx.sendMessage(Message.raw(""))
        ctx.sendMessage(Message.raw("Hunger:  ${formatValue(stats.hunger)} / 100  $hungerBar"))
        ctx.sendMessage(Message.raw("Thirst:  ${formatValue(stats.thirst)} / 100  $thirstBar"))
        ctx.sendMessage(Message.raw("Energy:  ${formatValue(stats.energy)} / 100  $energyBar"))
        ctx.sendMessage(Message.raw(""))
        ctx.sendMessage(Message.raw("------------------------"))
    }
    
    /**
     * Format a stat value for display (1 decimal place).
     */
    private fun formatValue(value: Float): String {
        return String.format("%5.1f", value)
    }
    
    /**
     * Create a visual progress bar for a stat.
     * 
     * @param value Stat value (0-100)
     * @return A text-based progress bar
     */
    private fun formatStatBar(value: Float): String {
        val filledCount = (value / 10).toInt()
        val emptyCount = 10 - filledCount
        
        val filled = "|".repeat(filledCount)
        val empty = ".".repeat(emptyCount)
        
        return "[$filled$empty]"
    }
}
