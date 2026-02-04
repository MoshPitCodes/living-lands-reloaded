package com.livinglands.modules.metabolism

/**
 * Represents the metabolism stats for a single player.
 * 
 * This is an IMMUTABLE data class used for:
 * - Database persistence (loading/saving)
 * - Snapshots for external callers
 * - Initial state transfer
 * 
 * For hot-path tick updates, use PlayerMetabolismState which provides
 * mutable fields to avoid allocations on every tick.
 * 
 * Current stat values range from 0.0 to maxValue.
 * Max stat values are set by Tier 2 abilities (default 100, up to ~135 with bonuses).
 */
data class MetabolismStats(
    /** Player UUID as string (matches database schema) */
    val playerId: String,
    
    /** Current hunger level (0.0 - maxHunger) */
    val hunger: Float = 100f,
    
    /** Current thirst level (0.0 - maxThirst) */
    val thirst: Float = 100f,
    
    /** Current energy level (0.0 - maxEnergy) */
    val energy: Float = 100f,
    
    /** Maximum hunger capacity (default 100, modified by Iron Stomach Tier 2 ability) */
    val maxHunger: Float = 100f,
    
    /** Maximum thirst capacity (default 100, modified by Desert Nomad Tier 2 ability) */
    val maxThirst: Float = 100f,
    
    /** Maximum energy capacity (default 100, modified by Tireless Woodsman Tier 2 ability) */
    val maxEnergy: Float = 100f,
    
    /** Timestamp of last update (milliseconds since epoch) */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    
    /**
     * Create a copy with updated hunger value (clamped to 0-100).
     * 
     * Note: For high-frequency updates, prefer PlayerMetabolismState.setHunger()
     * which mutates in-place without allocations.
     * 
     * @param value New hunger value
     * @param timestamp Optional timestamp (defaults to current time, but passing it avoids extra System call)
     */
    fun withHunger(value: Float, timestamp: Long = System.currentTimeMillis()): MetabolismStats {
        return copy(
            hunger = value.coerceIn(0f, 100f),
            lastUpdated = timestamp
        )
    }
    
    /**
     * Create a copy with updated thirst value (clamped to 0-100).
     * 
     * @param value New thirst value
     * @param timestamp Optional timestamp (defaults to current time)
     */
    fun withThirst(value: Float, timestamp: Long = System.currentTimeMillis()): MetabolismStats {
        return copy(
            thirst = value.coerceIn(0f, 100f),
            lastUpdated = timestamp
        )
    }
    
    /**
     * Create a copy with updated energy value (clamped to 0-100).
     * 
     * @param value New energy value
     * @param timestamp Optional timestamp (defaults to current time)
     */
    fun withEnergy(value: Float, timestamp: Long = System.currentTimeMillis()): MetabolismStats {
        return copy(
            energy = value.coerceIn(0f, 100f),
            lastUpdated = timestamp
        )
    }
    
    /**
     * Create a copy with all stats updated.
     * 
     * @param hunger New hunger value
     * @param thirst New thirst value
     * @param energy New energy value
     * @param timestamp Optional timestamp (defaults to current time)
     */
    fun withStats(
        hunger: Float,
        thirst: Float,
        energy: Float,
        timestamp: Long = System.currentTimeMillis()
    ): MetabolismStats {
        return copy(
            hunger = hunger.coerceIn(0f, 100f),
            thirst = thirst.coerceIn(0f, 100f),
            energy = energy.coerceIn(0f, 100f),
            lastUpdated = timestamp
        )
    }
    
    /**
     * Create default stats for a new player.
     * 
     * @param playerId Player UUID as string
     * @param timestamp Optional timestamp (defaults to current time)
     */
    companion object {
        fun createDefault(playerId: String, timestamp: Long = System.currentTimeMillis()): MetabolismStats {
            return MetabolismStats(
                playerId = playerId,
                hunger = 100f,
                thirst = 100f,
                energy = 100f,
                maxHunger = 100f,
                maxThirst = 100f,
                maxEnergy = 100f,
                lastUpdated = timestamp
            )
        }
    }
}
