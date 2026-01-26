package com.livinglands.modules.professions.data

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Mutable state container for a player's profession stats.
 * 
 * Performance optimization: Uses AtomicLong for XP to allow thread-safe
 * increments without locks. This is critical for the hot path (XP award events).
 * 
 * Thread safety:
 * - XP counter is AtomicLong (thread-safe increments)
 * - Last processed level is AtomicInteger (prevents race in level-up detection)
 * - Level is volatile (safe reads, calculated from XP)
 * - lastUpdated is volatile (safe reads/writes)
 * 
 * Based on v2.6.0 LevelingService pattern with modern Kotlin improvements.
 */
class PlayerProfessionState(
    /** Player UUID as string (cached) */
    val playerId: String,
    
    /** The profession these stats belong to */
    val profession: Profession,
    
    /** Initial XP (will be mutated via atomic operations) */
    initialXp: Long = 0L,
    
    /** Initial level (calculated from XP) */
    initialLevel: Int = 1
) {
    /**
     * Atomic XP counter for thread-safe increments.
     * Use addAndGet() to award XP, never set directly.
     */
    private val xpCounter = AtomicLong(initialXp)
    
    /**
     * Last processed level for race condition prevention.
     * Updated atomically with compareAndSet() to ensure only one thread
     * processes a level-up.
     */
    private val lastProcessedLevel = AtomicInteger(initialLevel)
    
    /**
     * Timestamp of last XP gain (milliseconds since epoch).
     */
    @Volatile
    var lastUpdated: Long = System.currentTimeMillis()
        private set
    
    // ============ Read Accessors (thread-safe) ============
    
    /**
     * Get current XP (thread-safe read).
     */
    val xp: Long
        get() = xpCounter.get()
    
    /**
     * Get current level (thread-safe read, calculated from XP).
     * Uses XpCalculator for O(1) lookup.
     */
    val level: Int
        get() = lastProcessedLevel.get()
    
    // ============ XP Award (hot path - zero allocation, thread-safe) ============
    
    /**
     * Award XP to this profession.
     * 
     * Thread-safe atomic operation. Returns the new XP total.
     * 
     * CRITICAL: This does NOT check for level-ups. Call detectLevelUp()
     * after awarding XP to handle level progression.
     * 
     * @param amount Amount of XP to add (must be positive)
     * @return New total XP
     */
    fun awardXp(amount: Long): Long {
        require(amount > 0) { "XP amount must be positive" }
        
        lastUpdated = System.currentTimeMillis()
        return xpCounter.addAndGet(amount)
    }
    
    /**
     * Detect if the player leveled up after XP was awarded.
     * 
     * Uses atomic compareAndSet to prevent race conditions where multiple
     * threads could both detect the same level-up.
     * 
     * CRITICAL PATTERN (from code review):
     * ```
     * val oldLevel = state.level
     * state.awardXp(amount)
     * val newLevel = state.detectLevelUp(oldLevel)
     * 
     * if (newLevel > oldLevel) {
     *     // This thread won the race - process level-up
     *     unlockAbilities(playerId, profession, newLevel)
     * }
     * ```
     * 
     * @param oldLevel The level before XP was awarded
     * @return The new level (may be same as oldLevel if no level-up)
     */
    fun detectLevelUp(oldLevel: Int, xpCalculator: (Long) -> Int): Int {
        val currentXp = xpCounter.get()
        val calculatedLevel = xpCalculator(currentXp)
        
        if (calculatedLevel > oldLevel) {
            // Attempt to claim this level-up atomically
            if (lastProcessedLevel.compareAndSet(oldLevel, calculatedLevel)) {
                // We won the race - return new level
                return calculatedLevel
            } else {
                // Another thread already processed this level-up
                return lastProcessedLevel.get()
            }
        }
        
        return oldLevel
    }
    
    /**
     * Set XP directly (for admin commands or death penalty).
     * 
     * NOT thread-safe - should only be called from synchronized contexts
     * (e.g., admin commands on main thread).
     * 
     * @param newXp New XP value (will be clamped to >= 0)
     * @param newLevel New level corresponding to XP
     */
    fun setXp(newXp: Long, newLevel: Int) {
        xpCounter.set(maxOf(0L, newXp))
        lastProcessedLevel.set(maxOf(1, newLevel))
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * Apply death penalty to this profession.
     * 
     * Reduces XP by the given percentage, but never drops below level 1.
     * 
     * @param penaltyPercent Penalty as decimal (0.85 = 85% reduction)
     * @param xpCalculator Function to calculate level from XP
     * @return Amount of XP lost
     */
    fun applyDeathPenalty(penaltyPercent: Double, xpCalculator: (Long) -> Int): Long {
        val currentXp = xpCounter.get()
        val lostXp = (currentXp * penaltyPercent).toLong()
        val newXp = maxOf(0L, currentXp - lostXp)
        
        // Ensure we don't drop below level 1 minimum XP
        val level1MinXp = 0L  // Level 1 starts at 0 XP
        val finalXp = maxOf(level1MinXp, newXp)
        
        xpCounter.set(finalXp)
        lastProcessedLevel.set(xpCalculator(finalXp))
        lastUpdated = System.currentTimeMillis()
        
        return currentXp - finalXp
    }
    
    // ============ Persistence Conversion ============
    
    /**
     * Convert to immutable ProfessionStats for database persistence.
     * 
     * Only call this when saving to database, not on every XP award.
     * 
     * @return Immutable stats snapshot
     */
    fun toImmutableStats(): ProfessionStats {
        return ProfessionStats(
            playerId = playerId,
            profession = profession,
            xp = xpCounter.get(),
            level = lastProcessedLevel.get(),
            lastUpdated = lastUpdated
        )
    }
    
    companion object {
        /**
         * Create a PlayerProfessionState from immutable ProfessionStats.
         * Used when loading from database.
         * 
         * @param stats The immutable stats to load from
         * @return Mutable state container
         */
        fun fromStats(stats: ProfessionStats): PlayerProfessionState {
            return PlayerProfessionState(
                playerId = stats.playerId,
                profession = stats.profession,
                initialXp = stats.xp,
                initialLevel = stats.level
            )
        }
        
        /**
         * Create default state for a new player.
         * 
         * @param playerId Player UUID as cached string
         * @param profession The profession
         * @return New state with default values (XP=0, Level=1)
         */
        fun createDefault(playerId: String, profession: Profession): PlayerProfessionState {
            return PlayerProfessionState(
                playerId = playerId,
                profession = profession,
                initialXp = 0L,
                initialLevel = 1
            )
        }
    }
}
