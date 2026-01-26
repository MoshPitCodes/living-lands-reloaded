package com.livinglands.modules.professions.hud

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.data.Profession
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compact HUD element showing XP progress for all professions.
 * 
 * Displays a condensed view with:
 * - Profession name
 * - Current level (e.g., "Lv 5")
 * - Progress bar (visual)
 * - Progress percentage (e.g., "24%")
 * - XP numbers (e.g., "1.2K / 5.0K XP")
 * 
 * Toggled by /ll progress command.
 * 
 * @param playerRef The player this HUD is for
 * @param playerId Player's UUID
 * @param professionsService Service for fetching profession stats
 */
class ProfessionsProgressElement(
    playerRef: PlayerRef,
    private val playerId: UUID,
    private val professionsService: ProfessionsService
) : CustomUIHud(playerRef) {
    
    /** Whether the progress panel is currently visible */
    private val visible = AtomicBoolean(false)
    
    /** Whether data needs to be refreshed on next build */
    @Volatile
    private var needsRefresh = false
    
    companion object {
        /** HUD namespace for progress panel */
        const val NAMESPACE = "livinglands:progress"
    }
    
    /**
     * Build the progress panel UI.
     * 
     * Appends the UI template and populates with current profession data.
     */
    override fun build(builder: UICommandBuilder) {
        // Append the UI file every time
        builder.append("Common/UI/Custom/Hud/ProfessionsProgressPanel.ui")
        
        // Set panel visibility
        builder.set("#ProgressPanel.Visible", visible.get())
        
        // If visible, populate with current data
        if (visible.get() && needsRefresh) {
            populateProgressData(builder)
            needsRefresh = false
        } else if (visible.get()) {
            populateProgressData(builder)
        }
    }
    
    /**
     * Toggle the progress panel visibility.
     * 
     * @return New visibility state (true = showing, false = hidden)
     */
    fun togglePanel(): Boolean {
        val newState = !visible.get()
        visible.set(newState)
        needsRefresh = true
        return newState
    }
    
    /**
     * Check if panel is currently visible.
     */
    fun isVisible(): Boolean = visible.get()
    
    /**
     * Mark the panel as needing refresh (call after XP gain or level up).
     */
    fun refresh() {
        needsRefresh = true
    }
    
    /**
     * Populate all profession progress data into the UI.
     */
    private fun populateProgressData(builder: UICommandBuilder) {
        // Populate each profession
        Profession.values().forEach { profession ->
            val stats = professionsService.getStats(playerId, profession)
            if (stats == null) {
                // Stats not loaded yet, skip this profession
                return@forEach
            }
            
            val level = stats.level
            
            // Get current level progress
            val xpInLevel = professionsService.getXpInCurrentLevel(playerId, profession)
            val xpNeeded = professionsService.getXpNeededForNextLevel(playerId, profession)
            val progress = professionsService.getProgressToNextLevel(playerId, profession)
            
            val professionName = profession.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Set level text
            builder.set("#${professionName}Level.Text", "Lv $level")
            
            // Set progress percentage
            val progressPercent = (progress * 100).toInt()
            builder.set("#${professionName}Percent.Text", "$progressPercent%")
            
            // Set progress bar width
            val barWidth = (progress * 100).toInt().coerceIn(0, 100)
            builder.set("#${professionName}Bar.Anchor.Width", "$barWidth%")
            
            // Set XP text
            val xpText = "${formatXp(xpInLevel)} / ${formatXp(xpNeeded)} XP"
            builder.set("#${professionName}Xp.Text", xpText)
        }
    }
    
    /**
     * Format XP number with K/M suffix.
     * 
     * Examples:
     * - 500 → "500"
     * - 1,250 → "1.3K"
     * - 50,000 → "50K"
     * - 1,200,000 → "1.2M"
     */
    private fun formatXp(xp: Long): String {
        return when {
            xp >= 1_000_000 -> String.format("%.1fM", xp / 1_000_000.0)
            xp >= 1_000 -> String.format("%.1fK", xp / 1_000.0)
            else -> xp.toString()
        }
    }
}
