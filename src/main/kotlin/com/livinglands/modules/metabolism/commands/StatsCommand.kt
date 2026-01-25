package com.livinglands.modules.metabolism.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.modules.metabolism.MetabolismService
import com.livinglands.core.MessageFormatter
import java.awt.Color

/**
 * Command to display current metabolism stats.
 * 
 * Usage: /ll show
 * 
 * Shows hunger, thirst, and energy values for the executing player.
 * Can only be used by players (not console).
 * 
 * Note: /ll stats is now a toggle command for HUD visibility.
 */
class StatsCommand(
    private val metabolismService: MetabolismService
) : CommandBase(
    "show",                              // Subcommand name (no "ll" prefix)
    "Display your metabolism stats",     // Description
    false                                // Does not require operator
) {
    
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
        
        // Get the PlayerRef component from the entity to access UUID
        val store = entityRef.store
        val playerRefComponent = store.getComponent(entityRef, PlayerRef.getComponentType())
        
        if (playerRefComponent == null) {
            MessageFormatter.commandError(ctx, "Unable to get player component")
            return
        }
        
        // Get player UUID from PlayerRef component
        @Suppress("DEPRECATION")
        val playerId = playerRefComponent.uuid.toString()
        
        // Get metabolism stats from service
        val stats = metabolismService.getStats(playerId)
        
        if (stats == null) {
            MessageFormatter.commandError(ctx, "Metabolism data not loaded. Please wait a moment and try again.")
            return
        }
        
        // Format the stats display
        val hungerBar = formatStatBar(stats.hunger)
        val thirstBar = formatStatBar(stats.thirst)
        val energyBar = formatStatBar(stats.energy)
        
        // Colors for stats
        val orange = Color(255, 170, 85)
        val lightAqua = Color(170, 255, 255)
        val yellow = Color(255, 255, 85)
        val gray = Color(170, 170, 170)
        
        // Build and send the message
        MessageFormatter.commandRaw(ctx, "")  // Empty line for spacing
        MessageFormatter.commandRaw(ctx, "--- Metabolism Stats ---", yellow)
        MessageFormatter.commandRaw(ctx, "")
        MessageFormatter.commandRaw(ctx, "Hunger:  ${formatValue(stats.hunger)} / 100  $hungerBar", orange)
        MessageFormatter.commandRaw(ctx, "Thirst:  ${formatValue(stats.thirst)} / 100  $thirstBar", lightAqua)
        MessageFormatter.commandRaw(ctx, "Energy:  ${formatValue(stats.energy)} / 100  $energyBar", yellow)
        MessageFormatter.commandRaw(ctx, "")
        MessageFormatter.commandRaw(ctx, "------------------------", gray)
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
