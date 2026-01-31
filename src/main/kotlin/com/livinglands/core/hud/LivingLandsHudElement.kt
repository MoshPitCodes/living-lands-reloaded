package com.livinglands.core.hud

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.modules.metabolism.buffs.BuffsSystem
import com.livinglands.modules.metabolism.buffs.DebuffsSystem
import com.livinglands.modules.metabolism.hud.HudPreferences
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.Ability
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.data.Profession
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified HUD element for all Living Lands UI components.
 * 
 * This single HUD element manages ALL Living Lands UI:
 * - Metabolism bars (hunger, thirst, energy)
 * - Buffs/Debuffs display
 * - Professions panel (detailed view with abilities)
 * - Progress panel (compact XP progress bars)
 * - XP notifications
 * 
 * IMPORTANT: Hytale's CustomUI system can only handle ONE append() call per HudElement.
 * All UI must be in a single file: Hud/LivingLandsHud.ui
 * 
 * Modules update their sections by calling methods on this element, which then
 * pushes updates to the client.
 * 
 * @param playerRef The player this HUD is for
 * @param playerId Player's UUID
 * @param buffsSystem Optional buffs system for buff display
 * @param debuffsSystem Optional debuffs system for debuff display
 * @param professionsService Optional professions service for profession data
 * @param abilityRegistry Optional ability registry for ability data
 */
class LivingLandsHudElement(
    playerRef: PlayerRef,
    private val playerId: UUID,
    private var buffsSystem: BuffsSystem? = null,
    private var debuffsSystem: DebuffsSystem? = null,
    private var professionsService: ProfessionsService? = null,
    private var abilityRegistry: AbilityRegistry? = null
) : CustomUIHud(playerRef) {
    
    companion object {
        /** HUD namespace for Living Lands unified HUD */
        const val NAMESPACE = "livinglands:hud"
        
        /** Maximum number of buffs/debuffs to display */
        const val MAX_BUFFS = 3
        const val MAX_DEBUFFS = 3
        
        private val logger = HytaleLogger.getLogger()
    }
    
    // ============ Metabolism State ============
    
    /** Current metabolism stat values */
    private val metabolismStats = AtomicReference(MetabolismStats(100f, 100f, 100f))
    
    /** Current active buffs for display */
    private val currentBuffs = AtomicReference<List<String>>(emptyList())
    
    /** Current active debuffs for display */
    private val currentDebuffs = AtomicReference<List<String>>(emptyList())
    
    /** HUD visibility preferences for metabolism section */
    @Volatile
    var metabolismPreferences = HudPreferences()
    
    // ============ Professions State ============
    
    /** Whether professions panel is visible */
    private val professionsPanelVisible = AtomicBoolean(false)
    
    /** Whether progress panel is visible */
    private val progressPanelVisible = AtomicBoolean(false)
    
    /** Flag to track if professions panel needs refresh */
    @Volatile
    private var professionsPanelNeedsRefresh = true
    
    /** Flag to track if progress panel needs refresh */
    @Volatile
    private var progressPanelNeedsRefresh = true
    
    // ============ Build Method ============
    
    /**
     * Build the unified HUD.
     * 
     * This appends a SINGLE UI file containing ALL Living Lands UI elements.
     * The UI structure is loaded here, but content updates happen via update() methods.
     * 
     * IMPORTANT: Do NOT call set() in build()! The UI elements don't exist until
     * after the client processes the append() command. All data population must
     * happen in update methods (updateMetabolismHud(), etc.) which are called
     * after the HUD is shown.
     */
    override fun build(builder: UICommandBuilder) {
        // SINGLE append - all UI in one file
        // Path is relative to Common/UI/Custom/ (per hytale-basic-uis pattern)
        builder.append("Hud/LivingLandsHud.ui")
        
        // DO NOT call set() here! UI elements aren't loaded yet.
        // Data population happens in:
        // - updateMetabolismHud() for metabolism stats/buffs/debuffs
        // - updateProfessionsPanel() for professions panel
        // - updateProgressPanel() for progress panel
        //
        // These are called after show() when the HUD is first displayed.
    }
    
    // ============ Metabolism Section ============
    
    /**
     * Build the metabolism section (stats, buffs, debuffs).
     * Uses vanilla-styled layered progress bars matching Hytale's experience bar design.
     */
    private fun buildMetabolismSection(builder: UICommandBuilder) {
        // Set stats visibility
        builder.set("#MetabolismBars.Visible", metabolismPreferences.statsVisible)
        
        // Set all three bars with vanilla-styled progress
        val stats = metabolismStats.get()
        updateProgressBar(builder, "Hunger", stats.hunger, "#27ae60")
        updateProgressBar(builder, "Thirst", stats.thirst, "#3498db")
        updateProgressBar(builder, "Energy", stats.energy, "#f39c12")
        
        // Set buffs/debuffs (respecting visibility preferences)
        if (metabolismPreferences.buffsVisible) {
            updateBuffsDisplay(builder, currentBuffs.get())
        } else {
            for (i in 1..MAX_BUFFS) {
                builder.set("#Buff${i}Container.Visible", false)
            }
        }
        
        if (metabolismPreferences.debuffsVisible) {
            updateDebuffsDisplay(builder, currentDebuffs.get())
        } else {
            for (i in 1..MAX_DEBUFFS) {
                builder.set("#Debuff${i}Container.Visible", false)
            }
        }
    }
    
    /**
     * Update a text-based progress bar (v2.8.0 style).
     * Uses Unicode block characters to create visual progress bars.
     * 
     * @param builder UI command builder
     * @param barName Bar name (e.g., "Hunger", "Thirst", "Energy")
     * @param value Current stat value (0-100)
     * @param baseColor Base color for healthy state (unused in text mode, kept for compatibility)
     */
    private fun updateProgressBar(builder: UICommandBuilder, barName: String, value: Float, baseColor: String) {
        // Create text-based progress bar using Unicode blocks
        val barText = buildTextProgressBar(value)
        
        // Update the bar label (e.g., #HungerBar, #ThirstBar, #EnergyBar)
        builder.set("#${barName}Bar.Text", barText)
    }
    
    /**
     * Build a text-based progress bar using simple ASCII characters.
     * Example: "[||||||||||] 100%" or "[|||||.....] 50%"
     * 
     * @param value Current stat value (0-100)
     * @return Text representation of progress bar
     */
    private fun buildTextProgressBar(value: Float): String {
        val barLength = 10 // Number of characters in the bar
        val filledBlocks = ((value / 100.0f) * barLength).toInt().coerceIn(0, barLength)
        val emptyBlocks = barLength - filledBlocks
        
        val filled = "|".repeat(filledBlocks)
        val empty = ".".repeat(emptyBlocks)
        val percentage = value.toInt()
        
        return "[$filled$empty] $percentage%"
    }
    
    /**
     * Calculate progress bar width in pixels.
     * Vanilla-inspired: uses exact pixel values, not percentages.
     * 
     * @param value Current stat value (0-100)
     * @param maxWidth Maximum bar width in pixels (default 200)
     * @return Width in pixels (1-maxWidth), or 0 if value is 0
     */
    private fun calculateBarWidth(value: Float, maxWidth: Int = 200): Int {
        val percentage = (value / 100.0f).coerceIn(0.0f, 1.0f)
        val width = (percentage * maxWidth).toInt()
        // Return minimum 1px if value > 0 for visibility, otherwise 0
        return if (value > 0.0f) width.coerceAtLeast(1) else 0
    }
    
    /**
     * Get stat color based on value thresholds.
     * Transitions from green (healthy) → orange (warning) → red (critical).
     * 
     * @param value Current stat value (0-100)
     * @param baseColor Base color for healthy state (>70%)
     * @return Hex color string
     */
    private fun getStatColor(value: Float, baseColor: String): String {
        return when {
            value >= 70.0f -> baseColor           // Healthy: use base color
            value >= 40.0f -> "#e67e22"          // Warning: orange
            value >= 20.0f -> "#e74c3c"          // Critical: red
            else -> "#c0392b"                     // Danger: dark red
        }
    }
    
    /**
     * Update the buffs display labels.
     */
    private fun updateBuffsDisplay(builder: UICommandBuilder, buffs: List<String>) {
        for (i in 1..MAX_BUFFS) {
            val selector = "#Buff$i"
            val containerSelector = "${selector}Container"
            
            if (i <= buffs.size) {
                builder.set("$selector.Text", buffs[i - 1])
                builder.set("$containerSelector.Visible", true)
            } else {
                builder.set("$selector.Text", "")
                builder.set("$containerSelector.Visible", false)
            }
        }
    }
    
    /**
     * Update the debuffs display labels.
     */
    private fun updateDebuffsDisplay(builder: UICommandBuilder, debuffs: List<String>) {
        for (i in 1..MAX_DEBUFFS) {
            val selector = "#Debuff$i"
            val containerSelector = "${selector}Container"
            
            if (i <= debuffs.size) {
                builder.set("$selector.Text", debuffs[i - 1])
                builder.set("$containerSelector.Visible", true)
            } else {
                builder.set("$selector.Text", "")
                builder.set("$containerSelector.Visible", false)
            }
        }
    }
    
    /**
     * Update metabolism stats.
     * Call updateHud() after this to push changes to client.
     */
    fun updateMetabolism(hunger: Float, thirst: Float, energy: Float) {
        metabolismStats.set(MetabolismStats(hunger, thirst, energy))
        
        // Get actual buff/debuff names from the systems
        val buffs = buffsSystem?.getActiveBuffNames(playerId) ?: emptyList()
        val debuffs = debuffsSystem?.getActiveDebuffNames(playerId) ?: emptyList()
        
        currentBuffs.set(buffs)
        currentDebuffs.set(debuffs)
    }
    
    /**
     * Push metabolism HUD updates to the client.
     */
    fun updateMetabolismHud() {
        val stats = metabolismStats.get()
        val buffs = currentBuffs.get()
        val debuffs = currentDebuffs.get()
        val builder = UICommandBuilder()
        
        // Set the progress bars (only if stats visible)
        if (metabolismPreferences.statsVisible) {
            updateProgressBar(builder, "Hunger", stats.hunger, "#27ae60")
            updateProgressBar(builder, "Thirst", stats.thirst, "#3498db")
            updateProgressBar(builder, "Energy", stats.energy, "#f39c12")
        }
        
        // Update buffs/debuffs display
        if (metabolismPreferences.buffsVisible) {
            updateBuffsDisplay(builder, buffs)
        }
        
        if (metabolismPreferences.debuffsVisible) {
            updateDebuffsDisplay(builder, debuffs)
        }
        
        update(false, builder)
    }
    
    // ============ Metabolism Toggle Methods ============
    
    /**
     * Toggle stats visibility (hunger/thirst/energy bars).
     * Returns the new visibility state.
     */
    fun toggleStats(): Boolean {
        metabolismPreferences.statsVisible = !metabolismPreferences.statsVisible
        val builder = UICommandBuilder()
        builder.set("#MetabolismBars.Visible", metabolismPreferences.statsVisible)
        update(false, builder)
        return metabolismPreferences.statsVisible
    }
    
    /**
     * Toggle buffs visibility.
     * Returns the new visibility state.
     */
    fun toggleBuffs(): Boolean {
        metabolismPreferences.buffsVisible = !metabolismPreferences.buffsVisible
        val builder = UICommandBuilder()
        
        if (metabolismPreferences.buffsVisible) {
            updateBuffsDisplay(builder, currentBuffs.get())
        } else {
            for (i in 1..MAX_BUFFS) {
                builder.set("#Buff${i}Container.Visible", false)
            }
        }
        
        update(false, builder)
        return metabolismPreferences.buffsVisible
    }
    
    /**
     * Toggle debuffs visibility.
     * Returns the new visibility state.
     */
    fun toggleDebuffs(): Boolean {
        metabolismPreferences.debuffsVisible = !metabolismPreferences.debuffsVisible
        val builder = UICommandBuilder()
        
        if (metabolismPreferences.debuffsVisible) {
            updateDebuffsDisplay(builder, currentDebuffs.get())
        } else {
            for (i in 1..MAX_DEBUFFS) {
                builder.set("#Debuff${i}Container.Visible", false)
            }
        }
        
        update(false, builder)
        return metabolismPreferences.debuffsVisible
    }
    
    // ============ Professions Panel Section ============
    
    /**
     * Build the professions panel section.
     */
    private fun buildProfessionsPanelSection(builder: UICommandBuilder) {
        builder.set("#ProfessionsPanel.Visible", professionsPanelVisible.get())
        
        // Always populate data if needed (even when hidden, so it's ready when shown)
        if (professionsPanelNeedsRefresh && professionsService != null) {
            populateProfessionsData(builder)
            professionsPanelNeedsRefresh = false
        }
    }
    
    /**
     * Populate all profession data into the UI.
     */
    private fun populateProfessionsData(builder: UICommandBuilder) {
        val service = professionsService ?: run {
            logger.atFine().log("populateProfessionsData: professionsService is null")
            return
        }
        val registry = abilityRegistry
        
        logger.atFine().log("populateProfessionsData: Starting population for player $playerId")
        
        var totalXp = 0L
        var populatedCount = 0
        
        Profession.values().forEach { profession ->
            val stats = service.getStats(playerId, profession)
            if (stats == null) {
                logger.atFine().log("populateProfessionsData: Stats null for ${profession.name}")
                return@forEach
            }
            
            populatedCount++
            
            val level = stats.level
            val professionTotalXp = stats.xp
            
            val xpInLevel = service.getXpInCurrentLevel(playerId, profession)
            val xpNeeded = service.getXpNeededForNextLevel(playerId, profession)
            val progress = service.getProgressToNextLevel(playerId, profession)
            
            totalXp += professionTotalXp
            
            val professionName = profession.name.lowercase().replaceFirstChar { it.uppercase() }
            val prefix = professionName
            
            // Set level and XP text
            val progressPercent = (progress * 100).toInt()
            val levelText = "Lv $level | ${formatXp(xpInLevel)}/${formatXp(xpNeeded)} XP ($progressPercent%)"
            logger.atFine().log("Setting #${prefix}Level.Text = $levelText")
            builder.set("#${prefix}Level.Text", levelText)
            
            // Get abilities for this profession
            if (registry != null) {
                val abilities = registry.getAbilitiesForProfession(profession)
                
                abilities.forEachIndexed { index, ability ->
                    val abilityNum = index + 1
                    val unlocked = level >= ability.requiredLevel
                    
                    val abilityName = if (unlocked) {
                        "* ${ability.name}"
                    } else {
                        "? ${ability.name} (Lv ${ability.requiredLevel})"
                    }
                    
                    builder.set("#${prefix}Ability${abilityNum}.Text", abilityName)
                    builder.set("#${prefix}Ability${abilityNum}Desc.Text", ability.description)
                    
                    val color = if (unlocked) "#b5bdc4" else "#484f58"
                    builder.set("#${prefix}Ability${abilityNum}.Style.TextColor", color)
                }
            }
        }
        
        logger.atFine().log("populateProfessionsData: Populated $populatedCount professions, total XP: $totalXp")
        builder.set("#ProfessionsTotalXp.Text", "Total XP Earned: ${formatXp(totalXp)}")
    }
    
    /**
     * Toggle the professions panel visibility.
     * Returns the new visibility state.
     */
    fun toggleProfessionsPanel(): Boolean {
        val newState = !professionsPanelVisible.get()
        professionsPanelVisible.set(newState)
        
        val builder = UICommandBuilder()
        builder.set("#ProfessionsPanel.Visible", newState)
        
        // If opening professions panel, close progress panel (mutual exclusivity)
        if (newState && progressPanelVisible.get()) {
            progressPanelVisible.set(false)
            builder.set("#ProgressPanel.Visible", false)
        }
        
        // If opening, populate data now
        if (newState && professionsService != null) {
            populateProfessionsData(builder)
            professionsPanelNeedsRefresh = false
        }
        
        update(false, builder)
        return newState
    }
    
    /**
     * Check if professions panel is visible.
     */
    fun isProfessionsPanelVisible(): Boolean = professionsPanelVisible.get()
    
    /**
     * Mark professions panel as needing refresh.
     */
    fun refreshProfessionsPanel() {
        professionsPanelNeedsRefresh = true
    }
    
    // ============ Progress Panel Section ============
    
    /**
     * Build the progress panel section.
     */
    private fun buildProgressPanelSection(builder: UICommandBuilder) {
        builder.set("#ProgressPanel.Visible", progressPanelVisible.get())
        
        // Always populate data if needed (even when hidden, so it's ready when shown)
        if (progressPanelNeedsRefresh && professionsService != null) {
            populateProgressData(builder)
            progressPanelNeedsRefresh = false
        }
    }
    
    /**
     * Populate progress panel with profession data.
     */
    private fun populateProgressData(builder: UICommandBuilder) {
        val service = professionsService ?: run {
            logger.atFine().log("populateProgressData: professionsService is null")
            return
        }
        
        logger.atFine().log("populateProgressData: Starting population for player $playerId")
        
        var populatedCount = 0
        
        Profession.values().forEach { profession ->
            val stats = service.getStats(playerId, profession)
            if (stats == null) {
                logger.atFine().log("populateProgressData: Stats null for ${profession.name}")
                return@forEach
            }
            
            populatedCount++
            
            val level = stats.level
            val xpInLevel = service.getXpInCurrentLevel(playerId, profession)
            val xpNeeded = service.getXpNeededForNextLevel(playerId, profession)
            val progress = service.getProgressToNextLevel(playerId, profession)
            
            val professionName = profession.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Set level text (fix: use ProgressLevel to match UI element IDs)
            val levelText = "Lv $level"
            logger.atFine().log("Setting #${professionName}ProgressLevel.Text = $levelText")
            builder.set("#${professionName}ProgressLevel.Text", levelText)
            
            // Set progress percentage
            val progressPercent = (progress * 100).toInt()
            builder.set("#${professionName}Percent.Text", "$progressPercent%")
            
            // Set progress bar as text (can't dynamically resize Group anchors)
            val progressBar = buildTextProgressBar((progress * 100).toFloat())
            builder.set("#${professionName}Bar.Text", progressBar)
            
            // Set XP text
            val xpText = "${formatXp(xpInLevel)} / ${formatXp(xpNeeded)} XP"
            builder.set("#${professionName}Xp.Text", xpText)
        }
        
        logger.atFine().log("populateProgressData: Populated $populatedCount professions")
    }
    
    /**
     * Toggle the progress panel visibility.
     * Returns the new visibility state.
     */
    fun toggleProgressPanel(): Boolean {
        val newState = !progressPanelVisible.get()
        progressPanelVisible.set(newState)
        
        val builder = UICommandBuilder()
        builder.set("#ProgressPanel.Visible", newState)
        
        // If opening progress panel, close professions panel (mutual exclusivity)
        if (newState && professionsPanelVisible.get()) {
            professionsPanelVisible.set(false)
            builder.set("#ProfessionsPanel.Visible", false)
        }
        
        // If opening, populate data now
        if (newState && professionsService != null) {
            populateProgressData(builder)
            progressPanelNeedsRefresh = false
        }
        
        update(false, builder)
        return newState
    }
    
    /**
     * Check if progress panel is visible.
     */
    fun isProgressPanelVisible(): Boolean = progressPanelVisible.get()
    
    /**
     * Mark progress panel as needing refresh.
     */
    fun refreshProgressPanel() {
        progressPanelNeedsRefresh = true
    }
    
    /**
     * Update the professions panel with current data.
     * Call this to refresh the panel content.
     */
    fun updateProfessionsPanel() {
        if (!professionsPanelVisible.get() || professionsService == null) return
        
        val builder = UICommandBuilder()
        populateProfessionsData(builder)
        professionsPanelNeedsRefresh = false
        update(false, builder)
    }
    
    /**
     * Update the progress panel with current data.
     * Call this to refresh the panel content.
     */
    fun updateProgressPanel() {
        if (!progressPanelVisible.get() || professionsService == null) return
        
        val builder = UICommandBuilder()
        populateProgressData(builder)
        progressPanelNeedsRefresh = false
        update(false, builder)
    }
    
    // ============ Service Management ============
    
    /**
     * Update profession services after they become available.
     * This is called by ProfessionsModule when it starts up.
     */
    fun setProfessionServices(service: ProfessionsService, registry: AbilityRegistry) {
        logger.atFine().log("setProfessionServices called for player $playerId")
        this.professionsService = service
        this.abilityRegistry = registry
        
        // Mark panels as needing refresh
        professionsPanelNeedsRefresh = true
        progressPanelNeedsRefresh = true
        
        // Data will be populated when the panel is toggled visible via /ll profession
        logger.atFine().log("setProfessionServices: Profession services ready, panel will populate on toggle")
    }
    
    /**
     * Clear profession services when the professions module is disabled.
     * This disables the profession panels but keeps the rest of the HUD active.
     * Called when professions module is disabled via config hot-reload.
     */
    fun clearProfessionServices() {
        logger.atFine().log("clearProfessionServices called for player $playerId")
        this.professionsService = null
        this.abilityRegistry = null
        
        // Hide profession panels if they're visible
        if (professionsPanelVisible.get()) {
            professionsPanelVisible.set(false)
            logger.atFine().log("Hidden professions panel due to module disable")
        }
        if (progressPanelVisible.get()) {
            progressPanelVisible.set(false)
            logger.atFine().log("Hidden progress panel due to module disable")
        }
        
        // Mark panels as needing refresh to clear their content
        professionsPanelNeedsRefresh = true
        progressPanelNeedsRefresh = true
    }
    
    /**
     * Clear metabolism services when the metabolism module is disabled.
     * This hides the metabolism stats, buffs, and debuffs but keeps the rest of the HUD active.
     * Called when metabolism module is disabled via config hot-reload.
     */
    fun clearMetabolismServices() {
        logger.atFine().log("clearMetabolismServices called for player $playerId")
        this.buffsSystem = null
        this.debuffsSystem = null
        
        // Hide all metabolism UI elements
        metabolismPreferences.statsVisible = false
        metabolismPreferences.buffsVisible = false
        metabolismPreferences.debuffsVisible = false
        
        // Clear current buff/debuff lists
        currentBuffs.set(emptyList())
        currentDebuffs.set(emptyList())
        
        logger.atFine().log("Hidden metabolism UI due to module disable")
    }
    
    // ============ Utility Methods ============
    
    /**
     * Format XP number with K/M suffix.
     */
    private fun formatXp(xp: Long): String {
        return when {
            xp >= 1_000_000 -> String.format("%.1fM", xp / 1_000_000.0)
            xp >= 1_000 -> String.format("%.1fK", xp / 1_000.0)
            else -> xp.toString()
        }
    }
    
    /**
     * Refresh both professions panels if they're visible.
     */
    fun refreshAllProfessionsPanels() {
        refreshProfessionsPanel()
        refreshProgressPanel()
        
        if (professionsPanelVisible.get() || progressPanelVisible.get()) {
            val builder = UICommandBuilder()
            
            if (professionsPanelVisible.get() && professionsService != null) {
                populateProfessionsData(builder)
            }
            
            if (progressPanelVisible.get() && professionsService != null) {
                populateProgressData(builder)
            }
            
            update(false, builder)
        }
    }
    
    // ============ Data Classes ============
    
    /**
     * Holds the current metabolism stat values.
     */
    data class MetabolismStats(
        val hunger: Float,
        val thirst: Float,
        val energy: Float
    )
}
