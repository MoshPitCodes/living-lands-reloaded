package com.livinglands.modules.metabolism.hud

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.UUID
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
    playerRef: PlayerRef,
    private val playerId: UUID,
    private val buffsSystem: com.livinglands.modules.metabolism.buffs.BuffsSystem? = null,
    private val debuffsSystem: com.livinglands.modules.metabolism.buffs.DebuffsSystem? = null
) : CustomUIHud(playerRef) {
    
    /** Current stat values, updated atomically for thread safety */
    private val currentStats = AtomicReference(StatValues(100f, 100f, 100f))
    
    /** Current active buffs/debuffs for display */
    private val currentBuffs = AtomicReference<List<String>>(emptyList())
    private val currentDebuffs = AtomicReference<List<String>>(emptyList())
    
    /** HUD visibility preferences (loaded from database) */
    @Volatile
    var preferences = HudPreferences()
    
    /** Flag indicating if this is the first build (need to append UI template) */
    @Volatile
    private var firstBuild = true
    
    companion object {
        /** HUD namespace for metabolism stats */
        const val NAMESPACE = "livinglands:metabolism"
        
        /** Maximum number of buffs/debuffs to display */
        const val MAX_BUFFS = 3
        const val MAX_DEBUFFS = 3
    }
    
    /**
     * Build the metabolism HUD UI.
     * 
     * On first build, appends the UI template file.
     * Then sets all the current stat values.
     */
    override fun build(builder: UICommandBuilder) {
        // Append the UI file every time (Hytale might need this)
        builder.append("Hud/MetabolismHud.ui")
        
        // Set stats visibility
        builder.set("#MetabolismBars.Visible", preferences.statsVisible)
        
        // Set all three bars
        val stats = currentStats.get()
        builder.set("#HungerBar.Text", buildTextBar(stats.hunger))
        builder.set("#ThirstBar.Text", buildTextBar(stats.thirst))
        builder.set("#EnergyBar.Text", buildTextBar(stats.energy))
        
        // Set buffs/debuffs (respecting visibility preferences)
        if (preferences.buffsVisible) {
            updateBuffsDisplay(builder, currentBuffs.get())
        } else {
            // Hide all buff containers
            for (i in 1..MAX_BUFFS) {
                builder.set("#Buff${i}Container.Visible", false)
            }
        }
        
        if (preferences.debuffsVisible) {
            updateDebuffsDisplay(builder, currentDebuffs.get())
        } else {
            // Hide all debuff containers
            for (i in 1..MAX_DEBUFFS) {
                builder.set("#Debuff${i}Container.Visible", false)
            }
        }
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
        
        // Get actual buff/debuff names from the systems (source of truth)
        val buffs = buffsSystem?.getActiveBuffNames(playerId) ?: emptyList()
        val debuffs = debuffsSystem?.getActiveDebuffNames(playerId) ?: emptyList()
        
        currentBuffs.set(buffs)
        currentDebuffs.set(debuffs)
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
        val buffs = currentBuffs.get()
        val debuffs = currentDebuffs.get()
        val builder = UICommandBuilder()
        
        // Set the text values for all bars (only if stats visible)
        if (preferences.statsVisible) {
            builder.set("#HungerBar.Text", buildTextBar(stats.hunger))
            builder.set("#ThirstBar.Text", buildTextBar(stats.thirst))
            builder.set("#EnergyBar.Text", buildTextBar(stats.energy))
        }
        
        // Update buffs/debuffs display (respecting visibility preferences)
        if (preferences.buffsVisible) {
            updateBuffsDisplay(builder, buffs)
        }
        
        if (preferences.debuffsVisible) {
            updateDebuffsDisplay(builder, debuffs)
        }
        
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
     * Update the buffs display labels.
     * Controls container visibility - hidden when empty.
     */
    private fun updateBuffsDisplay(builder: UICommandBuilder, buffs: List<String>) {
        for (i in 1..MAX_BUFFS) {
            val selector = "#Buff$i"
            val containerSelector = "${selector}Container"
            
            if (i <= buffs.size) {
                builder.set("$selector.Text", buffs[i - 1])
                builder.set("$containerSelector.Visible", true)
            } else {
                // Clear text and hide when not in use
                builder.set("$selector.Text", "")
                builder.set("$containerSelector.Visible", false)
            }
        }
    }
    
    /**
     * Update the debuffs display labels.
     * Controls container visibility - hidden when empty.
     */
    private fun updateDebuffsDisplay(builder: UICommandBuilder, debuffs: List<String>) {
        for (i in 1..MAX_DEBUFFS) {
            val selector = "#Debuff$i"
            val containerSelector = "${selector}Container"
            
            if (i <= debuffs.size) {
                builder.set("$selector.Text", debuffs[i - 1])
                builder.set("$containerSelector.Visible", true)
            } else {
                // Clear text and hide when not in use
                builder.set("$selector.Text", "")
                builder.set("$containerSelector.Visible", false)
            }
        }
    }
    
    // ============ Toggle Methods ============
    
    /**
     * Toggle stats visibility (hunger/thirst/energy bars).
     * Returns the new visibility state.
     */
    fun toggleStats(): Boolean {
        preferences.statsVisible = !preferences.statsVisible
        val builder = UICommandBuilder()
        builder.set("#MetabolismBars.Visible", preferences.statsVisible)
        update(false, builder)
        return preferences.statsVisible
    }
    
    /**
     * Toggle buffs visibility.
     * Returns the new visibility state.
     */
    fun toggleBuffs(): Boolean {
        preferences.buffsVisible = !preferences.buffsVisible
        val builder = UICommandBuilder()
        
        if (preferences.buffsVisible) {
            // Show current buffs
            updateBuffsDisplay(builder, currentBuffs.get())
        } else {
            // Hide all buff containers
            for (i in 1..MAX_BUFFS) {
                builder.set("#Buff${i}Container.Visible", false)
            }
        }
        
        update(false, builder)
        return preferences.buffsVisible
    }
    
    /**
     * Toggle debuffs visibility.
     * Returns the new visibility state.
     */
    fun toggleDebuffs(): Boolean {
        preferences.debuffsVisible = !preferences.debuffsVisible
        val builder = UICommandBuilder()
        
        if (preferences.debuffsVisible) {
            // Show current debuffs
            updateDebuffsDisplay(builder, currentDebuffs.get())
        } else {
            // Hide all debuff containers
            for (i in 1..MAX_DEBUFFS) {
                builder.set("#Debuff${i}Container.Visible", false)
            }
        }
        
        update(false, builder)
        return preferences.debuffsVisible
    }
    
    /**
     * Holds the current stat values.
     */
    data class StatValues(
        val hunger: Float,
        val thirst: Float,
        val energy: Float
    )
}
