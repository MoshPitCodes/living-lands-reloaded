package com.livinglands.modules.professions.data

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks death penalty state for a single player session.
 * 
 * Thread-safe using atomic operations.
 * 
 * Session = login to logout. Death counter and weights reset on logout
 * (but decay system means logout doesn't instantly forgive deaths).
 */
data class DeathPenaltyState(
    /** Player UUID string */
    val playerId: String,
    
    /** Session start timestamp (Unix millis) */
    val sessionStartTime: Long = System.currentTimeMillis(),
    
    /** List of death timestamps and weights (mutable for decay) */
    val deaths: MutableList<DeathEntry> = mutableListOf(),
    
    /** Last decay check timestamp */
    @Volatile
    var lastDecayCheck: Long = System.currentTimeMillis(),
    
    /** Total deaths this session (for warnings) */
    val totalDeaths: AtomicInteger = AtomicInteger(0),
    
    /** Adaptive mercy active flag */
    @Volatile
    var mercyActive: Boolean = false
) {
    
    /**
     * Record a new death.
     * 
     * @param weight Initial weight for this death (default 1.0)
     * @return New total death count
     */
    fun recordDeath(weight: Double = 1.0): Int {
        val now = System.currentTimeMillis()
        synchronized(deaths) {
            deaths.add(DeathEntry(timestamp = now, weight = weight))
        }
        return totalDeaths.incrementAndGet()
    }
    
    /**
     * Calculate current death weight (sum of all non-decayed deaths).
     * 
     * @return Total weight (0.0 = no deaths, 5.0 = 5 recent deaths)
     */
    fun getCurrentWeight(): Double {
        synchronized(deaths) {
            return deaths.sumOf { it.weight }
        }
    }
    
    /**
     * Apply decay to all deaths based on time elapsed.
     * 
     * @param decayRatePerHour Weight lost per hour alive
     * @param now Current timestamp (for testing)
     * @return Number of deaths that fully decayed (weight <= 0)
     */
    fun applyDecay(decayRatePerHour: Double, now: Long = System.currentTimeMillis()): Int {
        val hoursElapsed = (now - lastDecayCheck) / 3_600_000.0
        val decayAmount = decayRatePerHour * hoursElapsed
        
        var fullyDecayed = 0
        
        synchronized(deaths) {
            val iterator = deaths.iterator()
            while (iterator.hasNext()) {
                val death = iterator.next()
                death.weight -= decayAmount
                
                if (death.weight <= 0.0) {
                    iterator.remove()
                    fullyDecayed++
                }
            }
        }
        
        lastDecayCheck = now
        return fullyDecayed
    }
    
    /**
     * Check if adaptive mercy should be activated.
     * 
     * @param threshold Death threshold for mercy (default 5)
     * @return True if mercy should activate
     */
    fun shouldActivateMercy(threshold: Int = 5): Boolean {
        return totalDeaths.get() >= threshold
    }
    
    /**
     * Calculate current death penalty percentage.
     * 
     * @param basePercent Base penalty (0.10 = 10%)
     * @param progressiveIncrease Per-death increase (0.03 = 3%)
     * @param maxPercent Maximum penalty cap (0.35 = 35%)
     * @param mercyReduction Mercy reduction multiplier (0.5 = 50% reduction)
     * @return Penalty percentage (0.0 - 1.0)
     */
    fun calculatePenaltyPercent(
        basePercent: Double,
        progressiveIncrease: Double,
        maxPercent: Double,
        mercyReduction: Double = 0.0
    ): Double {
        val currentWeight = getCurrentWeight()
        
        // Calculate penalty: base + (weight * progressive increase)
        var penalty = basePercent + (currentWeight * progressiveIncrease)
        
        // Apply mercy reduction if active
        if (mercyActive && mercyReduction > 0.0) {
            penalty *= mercyReduction
        }
        
        // Cap at maximum
        return penalty.coerceAtMost(maxPercent)
    }
    
    /**
     * Get time until next decay check.
     * 
     * @param checkIntervalSeconds Interval between checks
     * @return Milliseconds until next check
     */
    fun getTimeUntilNextDecay(checkIntervalSeconds: Int): Long {
        val intervalMs = checkIntervalSeconds * 1000L
        val timeSinceLastCheck = System.currentTimeMillis() - lastDecayCheck
        return (intervalMs - timeSinceLastCheck).coerceAtLeast(0)
    }
    
    /**
     * Create immutable snapshot for persistence.
     */
    fun toSnapshot(): DeathPenaltySnapshot {
        synchronized(deaths) {
            return DeathPenaltySnapshot(
                playerId = playerId,
                sessionStartTime = sessionStartTime,
                deaths = deaths.map { it.copy() },
                lastDecayCheck = lastDecayCheck,
                totalDeaths = totalDeaths.get(),
                mercyActive = mercyActive
            )
        }
    }
    
    companion object {
        /**
         * Create state from snapshot (on player login).
         */
        fun fromSnapshot(snapshot: DeathPenaltySnapshot): DeathPenaltyState {
            return DeathPenaltyState(
                playerId = snapshot.playerId,
                sessionStartTime = snapshot.sessionStartTime,
                deaths = snapshot.deaths.toMutableList(),
                lastDecayCheck = snapshot.lastDecayCheck,
                totalDeaths = AtomicInteger(snapshot.totalDeaths),
                mercyActive = snapshot.mercyActive
            )
        }
    }
}

/**
 * Single death entry with timestamp and decaying weight.
 */
data class DeathEntry(
    /** Death timestamp (Unix millis) */
    val timestamp: Long,
    
    /** Current weight (1.0 = fresh death, 0.0 = fully decayed) */
    var weight: Double
)

/**
 * Immutable snapshot of death penalty state for persistence.
 * 
 * Saved to database on logout, loaded on login.
 */
data class DeathPenaltySnapshot(
    val playerId: String,
    val sessionStartTime: Long,
    val deaths: List<DeathEntry>,
    val lastDecayCheck: Long,
    val totalDeaths: Int,
    val mercyActive: Boolean
)
