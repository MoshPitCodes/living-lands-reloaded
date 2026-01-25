package com.livinglands.modules.metabolism

import com.livinglands.modules.metabolism.hud.MetabolismHudElement
import kotlin.math.max

/**
 * Consolidated mutable state container for a player's metabolism data.
 * 
 * Performance optimization: Previously we had 4 separate ConcurrentHashMaps:
 * - statsCache (MetabolismStats)
 * - lastDepletionTime (Long)
 * - lastDisplayedStats (MetabolismStats)
 * - hudElements (MetabolismHudElement)
 * 
 * This meant 4 separate hash lookups per tick per player.
 * By consolidating into a single container, we:
 * 1. Reduce from 4 lookups to 1 lookup per tick
 * 2. Reduce memory overhead (4 Entry objects -> 1 Entry object per player)
 * 3. Improve cache locality (all data for a player is in one object)
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
    // ============ Mutable Stat Fields (hot path - no allocations) ============
    
    /** Current hunger level (0.0 - 100.0). Mutable to avoid copy() allocations. */
    @Volatile var hunger: Float = initialHunger
        private set
    
    /** Current thirst level (0.0 - 100.0). Mutable to avoid copy() allocations. */
    @Volatile var thirst: Float = initialThirst
        private set
    
    /** Current energy level (0.0 - 100.0). Mutable to avoid copy() allocations. */
    @Volatile var energy: Float = initialEnergy
        private set
    
    /** Timestamp of last stat update. */
    @Volatile var lastUpdated: Long = initialLastUpdated
        private set
    
    // ============ Timing Data (previously in separate map) ============
    
    /** Timestamp of last depletion tick for accurate delta calculations. */
    @Volatile var lastDepletionTime: Long = System.currentTimeMillis()
    
    // ============ HUD Data (previously in separate maps) ============
    
    /** The player's HUD element, or null if not registered. */
    @Volatile var hudElement: MetabolismHudElement? = null
    
    /** Last hunger value displayed on HUD (for threshold-based updates). */
    @Volatile var lastDisplayedHunger: Float = initialHunger
        private set
    
    /** Last thirst value displayed on HUD (for threshold-based updates). */
    @Volatile var lastDisplayedThirst: Float = initialThirst
        private set
    
    /** Last energy value displayed on HUD (for threshold-based updates). */
    @Volatile var lastDisplayedEnergy: Float = initialEnergy
        private set
    
    // ============ Stat Update Methods (mutable - no allocations) ============
    
    /**
     * Update all stats at once with a shared timestamp.
     * This is the primary tick update method - zero allocations.
     * 
     * @param newHunger New hunger value (will be clamped)
     * @param newThirst New thirst value (will be clamped)
     * @param newEnergy New energy value (will be clamped)
     * @param timestamp Current time in millis (passed in to avoid multiple System.currentTimeMillis() calls)
     */
    fun updateStats(newHunger: Float, newThirst: Float, newEnergy: Float, timestamp: Long) {
        hunger = newHunger.coerceIn(0f, 100f)
        thirst = newThirst.coerceIn(0f, 100f)
        energy = newEnergy.coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
    /**
     * Update hunger value only.
     * 
     * @param value New hunger value (will be clamped)
     * @param timestamp Current time in millis
     */
    fun setHunger(value: Float, timestamp: Long = System.currentTimeMillis()) {
        hunger = value.coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
    /**
     * Update thirst value only.
     * 
     * @param value New thirst value (will be clamped)
     * @param timestamp Current time in millis
     */
    fun setThirst(value: Float, timestamp: Long = System.currentTimeMillis()) {
        thirst = value.coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
    /**
     * Update energy value only.
     * 
     * @param value New energy value (will be clamped)
     * @param timestamp Current time in millis
     */
    fun setEnergy(value: Float, timestamp: Long = System.currentTimeMillis()) {
        energy = value.coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
    /**
     * Add to hunger value (for restoration).
     * 
     * @param amount Amount to add (can be negative)
     * @param timestamp Current time in millis
     */
    fun addHunger(amount: Float, timestamp: Long = System.currentTimeMillis()) {
        hunger = (hunger + amount).coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
    /**
     * Add to thirst value (for restoration).
     * 
     * @param amount Amount to add (can be negative)
     * @param timestamp Current time in millis
     */
    fun addThirst(amount: Float, timestamp: Long = System.currentTimeMillis()) {
        thirst = (thirst + amount).coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
    /**
     * Add to energy value (for restoration).
     * 
     * @param amount Amount to add (can be negative)
     * @param timestamp Current time in millis
     */
    fun addEnergy(amount: Float, timestamp: Long = System.currentTimeMillis()) {
        energy = (energy + amount).coerceIn(0f, 100f)
        lastUpdated = timestamp
    }
    
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
