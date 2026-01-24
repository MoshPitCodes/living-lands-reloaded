package com.livinglands.modules.metabolism

/**
 * Represents the metabolism stats for a single player.
 * Stored in the database and cached in memory.
 * 
 * All stat values range from 0.0 to 100.0.
 */
data class MetabolismStats(
    /** Player UUID as string (matches database schema) */
    val playerId: String,
    
    /** Current hunger level (0.0 - 100.0) */
    val hunger: Float = 100f,
    
    /** Current thirst level (0.0 - 100.0) */
    val thirst: Float = 100f,
    
    /** Current energy level (0.0 - 100.0) */
    val energy: Float = 100f,
    
    /** Timestamp of last update (milliseconds since epoch) */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    
    /**
     * Create a copy with updated hunger value (clamped to 0-100).
     */
    fun withHunger(value: Float): MetabolismStats {
        return copy(
            hunger = value.coerceIn(0f, 100f),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with updated thirst value (clamped to 0-100).
     */
    fun withThirst(value: Float): MetabolismStats {
        return copy(
            thirst = value.coerceIn(0f, 100f),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with updated energy value (clamped to 0-100).
     */
    fun withEnergy(value: Float): MetabolismStats {
        return copy(
            energy = value.coerceIn(0f, 100f),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with all stats updated.
     */
    fun withStats(hunger: Float, thirst: Float, energy: Float): MetabolismStats {
        return copy(
            hunger = hunger.coerceIn(0f, 100f),
            thirst = thirst.coerceIn(0f, 100f),
            energy = energy.coerceIn(0f, 100f),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Create default stats for a new player.
     */
    companion object {
        fun createDefault(playerId: String): MetabolismStats {
            return MetabolismStats(
                playerId = playerId,
                hunger = 100f,
                thirst = 100f,
                energy = 100f,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
