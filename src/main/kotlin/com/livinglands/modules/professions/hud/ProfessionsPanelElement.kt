package com.livinglands.modules.professions.hud

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.Ability
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.data.Profession
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HUD panel element for displaying all professions with abilities and XP progress.
 * 
 * Toggleable via /ll profession command.
 * Shows:
 * - All 5 professions (Combat, Mining, Logging, Building, Gathering)
 * - Current level and XP progress for each
 * - 3 abilities per profession (Tier 1, 2, 3)
 * - Locked/unlocked status and descriptions
 * 
 * @param playerRef The player this panel is for
 * @param playerId Player's UUID
 * @param professionsService Service for fetching profession data
 * @param abilityRegistry Registry for fetching ability data
 */
class ProfessionsPanelElement(
    playerRef: PlayerRef,
    private val playerId: UUID,
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry
) : CustomUIHud(playerRef) {
    
    /** Panel visibility state */
    private val visible = AtomicBoolean(false)
    
    /** Flag to track if we need to refresh the panel on next build */
    @Volatile
    private var needsRefresh = true
    
    companion object {
        /** HUD namespace for professions panel */
        const val NAMESPACE = "livinglands:professions"
        
        /** Profession colors (matching UI template) */
        private val PROFESSION_COLORS = mapOf(
            Profession.COMBAT to "#e74c3c",
            Profession.MINING to "#3498db",
            Profession.LOGGING to "#27ae60",
            Profession.BUILDING to "#f39c12",
            Profession.GATHERING to "#9b59b6"
        )
    }
    
    /**
     * Build the professions panel UI.
     * 
     * Appends the UI template and populates all profession data.
     */
    override fun build(builder: UICommandBuilder) {
        // Append the UI file every time
        builder.append("Hud/ProfessionsPanel.ui")
        
        // Set panel visibility
        builder.set("#ProfessionsPanel.Visible", visible.get())
        
        // If visible, populate all data
        if (visible.get() && needsRefresh) {
            populateProfessionsData(builder)
            needsRefresh = false
        }
    }
    
    /**
     * Toggle the panel visibility.
     * 
     * @return New visibility state
     */
    fun togglePanel(): Boolean {
        val newState = !visible.get()
        visible.set(newState)
        needsRefresh = true
        return newState
    }
    
    /**
     * Show the panel.
     */
    fun showPanel() {
        visible.set(true)
        needsRefresh = true
    }
    
    /**
     * Hide the panel.
     */
    fun hidePanel() {
        visible.set(false)
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
     * Populate all profession data into the UI.
     */
    private fun populateProfessionsData(builder: UICommandBuilder) {
        var totalXp = 0L
        
        // Populate each profession
        Profession.values().forEach { profession ->
            val stats = professionsService.getStats(playerId, profession)
            if (stats == null) {
                // Stats not loaded yet, skip this profession
                return@forEach
            }
            
            val level = stats.level
            val professionTotalXp = stats.xp
            
            // Get current level progress
            val xpInLevel = professionsService.getXpInCurrentLevel(playerId, profession)
            val xpNeeded = professionsService.getXpNeededForNextLevel(playerId, profession)
            val progress = professionsService.getProgressToNextLevel(playerId, profession)
            
            totalXp += professionTotalXp
            
            val professionName = profession.name.lowercase().replaceFirstChar { it.uppercase() }
            val prefix = professionName
            
            // Set level and XP text with current level progress
            val progressPercent = (progress * 100).toInt()
            val levelText = "Lv $level • ${formatXp(xpInLevel)}/${formatXp(xpNeeded)} XP ($progressPercent%)"
            builder.set("#${prefix}Level.Text", levelText)
            
            // Get abilities for this profession
            val abilities = abilityRegistry.getAbilitiesForProfession(profession)
            
            // Populate abilities (3 per profession)
            abilities.forEachIndexed { index, ability ->
                val abilityNum = index + 1
                val unlocked = level >= ability.requiredLevel
                
                // Ability name with lock/unlock indicator
                val abilityName = if (unlocked) {
                    "✓ ${ability.name}"
                } else {
                    "⚠ ${ability.name} (Lv ${ability.requiredLevel})"
                }
                
                // Ability description with effect details
                val abilityDesc = formatAbilityDescription(ability)
                
                builder.set("#${prefix}Ability${abilityNum}.Text", abilityName)
                builder.set("#${prefix}Ability${abilityNum}Desc.Text", abilityDesc)
                
                // Change color based on unlock status
                val color = if (unlocked) "#b5bdc4" else "#484f58"
                builder.set("#${prefix}Ability${abilityNum}.Style.TextColor", color)
            }
        }
        
        // Set total XP summary
        builder.set("#TotalXpSummary.Text", "Total XP Earned: ${formatXp(totalXp)}")
    }
    
    /**
     * Format ability description with effect details.
     * 
     * Uses the ability's own description since specific properties vary per ability.
     */
    private fun formatAbilityDescription(ability: Ability): String {
        // Each ability has a description, just use that for simplicity
        return ability.description
    }
    
    /**
     * Format XP number with commas for readability.
     */
    private fun formatXp(xp: Long): String {
        return when {
            xp >= 1_000_000 -> String.format("%.1fM", xp / 1_000_000.0)
            xp >= 1_000 -> String.format("%.1fK", xp / 1_000.0)
            else -> xp.toString()
        }
    }
}
