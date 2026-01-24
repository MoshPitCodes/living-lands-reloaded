package com.livinglands.modules.metabolism.hud

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.concurrent.atomic.AtomicReference

/**
 * HUD element for displaying metabolism stats (hunger, thirst, energy).
 * 
 * This extends CustomUIHud and builds a UI with three progress bars
 * and value labels for each metabolism stat. The UI is updated when
 * [updateStats] is called with new values.
 * 
 * The HUD uses Hytale's UI system to:
 * 1. Append the metabolism HUD UI template
 * 2. Set dynamic values for the progress bars and labels
 * 
 * UI element IDs:
 * - ll_hunger_bar: Progress bar for hunger (0-100)
 * - ll_hunger_value: Text showing hunger value
 * - ll_thirst_bar: Progress bar for thirst (0-100)
 * - ll_thirst_value: Text showing thirst value
 * - ll_energy_bar: Progress bar for energy (0-100)
 * - ll_energy_value: Text showing energy value
 * 
 * @param playerRef The player this HUD is for
 */
class MetabolismHudElement(
    playerRef: PlayerRef
) : CustomUIHud(playerRef) {
    
    /** Current stat values, updated atomically for thread safety */
    private val currentStats = AtomicReference(StatValues(100f, 100f, 100f))
    
    /** Flag indicating if this is the first build (need to append UI template) */
    @Volatile
    private var firstBuild = true
    
    /**
     * Build the metabolism HUD UI.
     * 
     * On first build, appends the UI template file.
     * Then sets all the current stat values.
     */
    override fun build(builder: UICommandBuilder) {
        // Append the UI file every time (Hytale might need this)
        builder.append("Hud/MetabolismHud.ui")
        
        // Set all three bars
        val stats = currentStats.get()
        builder.set("#HungerBar.Text", buildTextBar(stats.hunger))
        builder.set("#ThirstBar.Text", buildTextBar(stats.thirst))
        builder.set("#EnergyBar.Text", buildTextBar(stats.energy))
    }
    
    /**
     * Build a text-based progress bar matching v2.6.0 format.
     * Example: "[||||||....] 60"
     * 
     * @param value Current value (0-100)
     * @return Formatted text bar string
     */
    private fun buildTextBar(value: Float): String {
        val barLength = 10
        val filled = (value / 10).toInt().coerceIn(0, barLength)
        val empty = barLength - filled
        
        val bar = "|".repeat(filled) + ".".repeat(empty)
        return "[${bar}] ${value.toInt()}"
    }
    
    /**
     * Update the displayed stats.
     * 
     * This updates the internal state but does NOT automatically refresh
     * the HUD on the client. Call [updateHud] after this to push the update.
     * 
     * @param hunger New hunger value (0-100)
     * @param thirst New thirst value (0-100)
     * @param energy New energy value (0-100)
     */
    fun updateStats(hunger: Float, thirst: Float, energy: Float) {
        currentStats.set(StatValues(hunger, thirst, energy))
    }
    
    /**
     * Push HUD updates to the client.
     * Call this after updateStats() to refresh the display.
     * 
     * Note: May fail silently if UI elements aren't loaded yet on client.
     * The ticker will retry automatically.
     */
    fun updateHud() {
        val stats = currentStats.get()
        val builder = UICommandBuilder()
        
        // Set the text values for all bars
        builder.set("#HungerBar.Text", buildTextBar(stats.hunger))
        builder.set("#ThirstBar.Text", buildTextBar(stats.thirst))
        builder.set("#EnergyBar.Text", buildTextBar(stats.energy))
        
        // Push update to client
        // Note: update() doesn't throw exceptions - client handles errors
        update(false, builder)
    }
    
    /**
     * Get the current stat values.
     * 
     * @return Current StatValues
     */
    fun getStats(): StatValues {
        return currentStats.get()
    }
    
    /**
     * Format a stat value for display.
     * Shows one decimal place.
     */
    private fun formatStatValue(value: Float): String {
        return String.format("%.1f", value)
    }
    

    
    /**
     * Holds the current stat values.
     */
    data class StatValues(
        val hunger: Float,
        val thirst: Float,
        val energy: Float
    )
    
    companion object {
        /** HUD namespace for metabolism stats */
        const val NAMESPACE = "livinglands:metabolism"
    }
}
