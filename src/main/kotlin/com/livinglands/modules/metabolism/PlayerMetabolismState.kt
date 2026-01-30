package com.livinglands.modules.metabolism

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Consolidated mutable state container for a player's metabolism data.
 * 
 * Performance optimization: Previously we had 4 separate ConcurrentHashMaps:
 * - statsCache (MetabolismStats)
 * - lastDepletionTime (Long)
 * - lastDisplayedStats (MetabolismStats)
 * - hudElements (now managed by MultiHudManager)
 * 
 * This meant 4 separate hash lookups per tick per player.
 * By consolidating into a single container, we:
 * 1. Reduce from 4 lookups to 1 lookup per tick
 * 2. Reduce memory overhead (4 Entry objects -> 1 Entry object per player)
 * 3. Improve cache locality (all data for a player is in one object)
 * 
 * Note: HUD elements are now managed by the unified LivingLandsHudElement
 * via CoreModule.hudManager (MultiHudManager).
 * 
 * This class uses MUTABLE fields internally for the hot-path stat updates,
 * avoiding object allocation on every tick. Immutable MetabolismStats are
 * only created when needed for persistence.
 * 
 * Thread safety: Individual field access is not synchronized, but the
 * containing ConcurrentHashMap ensures visibility. All tick processing
 * happens on the World Thread, so concurrent modification is not expected.
 */
class PlayerMetabolismState(
    /** Player UUID as string (cached to avoid repeated UUID.toString() calls) */
    val playerId: String,
    
    /** Initial hunger level (will be mutated during ticks) */
    initialHunger: Float = 100f,
    
    /** Initial thirst level (will be mutated during ticks) */
    initialThirst: Float = 100f,
    
    /** Initial energy level (will be mutated during ticks) */
    initialEnergy: Float = 100f,
    
    /** Initial timestamp for last update */
    initialLastUpdated: Long = System.currentTimeMillis()
) {
    // ============ Max Stat Capacity (Tier 2 abilities) ============
    
    /**
     * Maximum hunger capacity (default 100, modified by Iron Stomach ability).
     * Used by Tier 2 Combat ability to permanently increase max hunger.
     */
    @Volatile var maxHunger: Float = 100f
    
    /**
     * Maximum thirst capacity (default 100, modified by Desert Nomad ability).
     * Used by Tier 2 Mining ability to permanently increase max thirst.
     */
    @Volatile var maxThirst: Float = 100f
    
    /**
     * Maximum energy capacity (default 100, modified by Tireless Woodsman ability).
     * Used by Tier 2 Logging ability to permanently increase max energy.
     */
    @Volatile var maxEnergy: Float = 100f
    
    // ============ Mutable Stat Fields (hot path - no allocations) ============
    
    /** Current hunger level (0.0 - maxHunger). Mutable to avoid copy() allocations. */
    @Volatile var hunger: Float = initialHunger
        private set
    
    /** Current thirst level (0.0 - maxThirst). Mutable to avoid copy() allocations. */
    @Volatile var thirst: Float = initialThirst
        private set
    
    /** Current energy level (0.0 - maxEnergy). Mutable to avoid copy() allocations. */
    @Volatile var energy: Float = initialEnergy
        private set
    
    /** Timestamp of last stat update. */
    @Volatile var lastUpdated: Long = initialLastUpdated
        private set
    
    // ============ Timing Data (previously in separate map) ============
    
    /** Timestamp of last depletion tick for accurate delta calculations. */
    @Volatile var lastDepletionTime: Long = System.currentTimeMillis()
    
    // ============ HUD Data (for threshold-based updates) ============
    // Note: HUD element itself is now stored in MultiHudManager (single source of truth)
    
    /** Last hunger value displayed on HUD (for threshold-based updates). */
    @Volatile var lastDisplayedHunger: Float = initialHunger
        private set
    
    /** Last thirst value displayed on HUD (for threshold-based updates). */
    @Volatile var lastDisplayedThirst: Float = initialThirst
        private set
    
    /** Last energy value displayed on HUD (for threshold-based updates). */
    @Volatile var lastDisplayedEnergy: Float = initialEnergy
        private set
    
    // ============ Depletion Modifier System (for Professions Tier 3 abilities) ============
    
    /**
     * Map of active depletion modifiers (source ID -> multiplier).
     * Multiple modifiers stack multiplicatively.
     * 
     * Example: Survivalist Tier 3 applies "professions:survivalist" with 0.85 (15% slower depletion)
     * 
     * Thread-safe using ConcurrentHashMap.
     */
    private val depletionModifiers = ConcurrentHashMap<String, Double>()
    
    // ============ Stat Update Methods (mutable - no allocations) ============
    
    /**
     * Update all stats at once with a shared timestamp.
     * This is the primary tick update method - zero allocations.
     * 
     * Respects max stat capacity from Tier 2 abilities.
     * 
     * @param newHunger New hunger value (will be clamped to 0..maxHunger)
     * @param newThirst New thirst value (will be clamped to 0..maxThirst)
     * @param newEnergy New energy value (will be clamped to 0..maxEnergy)
     * @param timestamp Current time in millis (passed in to avoid multiple System.currentTimeMillis() calls)
     */
    fun updateStats(newHunger: Float, newThirst: Float, newEnergy: Float, timestamp: Long) {
        hunger = newHunger.coerceIn(0f, maxHunger)
        thirst = newThirst.coerceIn(0f, maxThirst)
        energy = newEnergy.coerceIn(0f, maxEnergy)
        lastUpdated = timestamp
    }
    
    /**
     * Update hunger value only.
     * Respects max stat capacity.
     * 
     * @param value New hunger value (will be clamped to 0..maxHunger)
     * @param timestamp Current time in millis
     */
    fun setHunger(value: Float, timestamp: Long = System.currentTimeMillis()) {
        hunger = value.coerceIn(0f, maxHunger)
        lastUpdated = timestamp
    }
    
    /**
     * Update thirst value only.
     * Respects max stat capacity.
     * 
     * @param value New thirst value (will be clamped to 0..maxThirst)
     * @param timestamp Current time in millis
     */
    fun setThirst(value: Float, timestamp: Long = System.currentTimeMillis()) {
        thirst = value.coerceIn(0f, maxThirst)
        lastUpdated = timestamp
    }
    
    /**
     * Update energy value only.
     * Respects max stat capacity.
     * 
     * @param value New energy value (will be clamped to 0..maxEnergy)
     * @param timestamp Current time in millis
     */
    fun setEnergy(value: Float, timestamp: Long = System.currentTimeMillis()) {
        energy = value.coerceIn(0f, maxEnergy)
        lastUpdated = timestamp
    }
    
    /**
     * Add to hunger value (for restoration).
     * Respects max stat capacity.
     * 
     * @param amount Amount to add (can be negative)
     * @param timestamp Current time in millis
     */
    fun addHunger(amount: Float, timestamp: Long = System.currentTimeMillis()) {
        hunger = (hunger + amount).coerceIn(0f, maxHunger)
        lastUpdated = timestamp
    }
    
    /**
     * Add to thirst value (for restoration).
     * Respects max stat capacity.
     * 
     * @param amount Amount to add (can be negative)
     * @param timestamp Current time in millis
     */
    fun addThirst(amount: Float, timestamp: Long = System.currentTimeMillis()) {
        thirst = (thirst + amount).coerceIn(0f, maxThirst)
        lastUpdated = timestamp
    }
    
    /**
     * Add to energy value (for restoration).
     * Respects max stat capacity.
     * 
     * @param amount Amount to add (can be negative)
     * @param timestamp Current time in millis
     */
    fun addEnergy(amount: Float, timestamp: Long = System.currentTimeMillis()) {
        energy = (energy + amount).coerceIn(0f, maxEnergy)
        lastUpdated = timestamp
    }
    
    // ============ Percentage-Based Stat Access (for HUD display) ============
    
    /**
     * Get hunger as percentage of max capacity (0.0 - 100.0).
     * Accounts for Tier 2 max stat bonuses.
     */
    fun getHungerPercent(): Float = if (maxHunger > 0f) (hunger / maxHunger) * 100f else 0f
    
    /**
     * Get thirst as percentage of max capacity (0.0 - 100.0).
     * Accounts for Tier 2 max stat bonuses.
     */
    fun getThirstPercent(): Float = if (maxThirst > 0f) (thirst / maxThirst) * 100f else 0f
    
    /**
     * Get energy as percentage of max capacity (0.0 - 100.0).
     * Accounts for Tier 2 max stat bonuses.
     */
    fun getEnergyPercent(): Float = if (maxEnergy > 0f) (energy / maxEnergy) * 100f else 0f
    
    // ============ HUD Methods ============
    
    /**
     * Record that these stats were just displayed on the HUD.
     * Used for threshold-based update detection.
     */
    fun markDisplayed() {
        lastDisplayedHunger = hunger
        lastDisplayedThirst = thirst
        lastDisplayedEnergy = energy
    }
    
    // ============ Depletion Modifier Methods ============
    
    /**
     * Apply a depletion rate modifier.
     * Multiple modifiers stack multiplicatively.
     * 
     * Example: Survivalist Tier 3 applies "professions:survivalist" with 0.85 (15% slower depletion)
     * 
     * @param sourceId Unique identifier for this modifier (e.g., "professions:survivalist")
     * @param multiplier Depletion rate multiplier (0.85 = 15% slower, 1.5 = 50% faster)
     */
    fun applyDepletionModifier(sourceId: String, multiplier: Double) {
        depletionModifiers[sourceId] = multiplier
    }
    
    /**
     * Remove a depletion rate modifier.
     * 
     * @param sourceId The modifier to remove
     * @return true if the modifier was removed, false if it didn't exist
     */
    fun removeDepletionModifier(sourceId: String): Boolean {
        return depletionModifiers.remove(sourceId) != null
    }
    
    /**
     * Get the combined depletion multiplier from all active modifiers.
     * Modifiers stack multiplicatively (0.85 * 0.90 = 0.765 = 23.5% slower).
     * 
     * @return Combined multiplier (1.0 if no modifiers active)
     */
    fun getCombinedDepletionMultiplier(): Double {
        if (depletionModifiers.isEmpty()) return 1.0
        return depletionModifiers.values.fold(1.0) { acc, mult -> acc * mult }
    }
    
    /**
     * Get all active depletion modifiers (for debugging/inspection).
     * 
     * @return Map of source ID to multiplier
     */
    fun getActiveModifiers(): Map<String, Double> {
        return depletionModifiers.toMap()
    }
    
    /**
     * Clear all depletion modifiers (e.g., on death/reset).
     */
    fun clearDepletionModifiers() {
        depletionModifiers.clear()
    }
    
    // ============ Persistence Conversion ============
    
    /**
     * Convert to immutable MetabolismStats for database persistence.
     * Only call this when saving to database, not on every tick.
     * 
     * @return Immutable MetabolismStats snapshot
     */
    fun toImmutableStats(): MetabolismStats {
        return MetabolismStats(
            playerId = playerId,
            hunger = hunger,
            thirst = thirst,
            energy = energy,
            lastUpdated = lastUpdated
        )
    }
    
    companion object {
        /**
         * Create a PlayerMetabolismState from immutable MetabolismStats.
         * Used when loading from database.
         * 
         * @param stats The immutable stats to load from
         * @return Mutable state container
         */
        fun fromStats(stats: MetabolismStats): PlayerMetabolismState {
            return PlayerMetabolismState(
                playerId = stats.playerId,
                initialHunger = stats.hunger,
                initialThirst = stats.thirst,
                initialEnergy = stats.energy,
                initialLastUpdated = stats.lastUpdated
            )
        }
        
        /**
         * Create default state for a new player.
         * 
         * @param playerId Player UUID as cached string
         * @return New state with default values (100 for all stats)
         */
        fun createDefault(playerId: String): PlayerMetabolismState {
            return PlayerMetabolismState(playerId = playerId)
        }
    }
}
