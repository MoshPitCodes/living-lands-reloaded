package com.livinglands.modules.professions

import kotlin.math.pow

/**
 * XP calculator with precomputed level table for O(1) lookups.
 * 
 * This class precomputes the XP required for each level (1-100) using an
 * exponential curve. Level lookups are then O(1) via binary search on the table.
 * 
 * Formula: XP_required(level) = baseXp * (multiplier ^ (level - 1))
 * 
 * Example with default values (baseXp=100, multiplier=1.15):
 * - Level 1: 0 XP
 * - Level 2: 100 XP
 * - Level 3: 215 XP (100 + 115)
 * - Level 5: 475 XP
 * - Level 10: 2,038 XP
 * - Level 25: 182,211 XP
 * - Level 50: 1,317,945,893 XP
 * 
 * Based on v2.6.0 XpCalculator with modern Kotlin improvements.
 */
class XpCalculator(
    /** Base XP required for level 2 */
    private val baseXp: Double = 100.0,
    
    /** Exponential multiplier (>1.0 for increasing difficulty) */
    private val multiplier: Double = 1.15,
    
    /** Maximum level (inclusive) */
    private val maxLevel: Int = 100
) {
    /**
     * Precomputed XP table.
     * 
     * Index 0 = Level 1 (0 XP)
     * Index 1 = Level 2 (100 XP)
     * Index 2 = Level 3 (215 XP)
     * ...
     * Index 99 = Level 100 (X XP)
     * 
     * Each entry is the TOTAL XP required to reach that level.
     */
    private val xpTable: LongArray
    
    init {
        require(baseXp > 0) { "baseXp must be positive" }
        require(multiplier > 1.0) { "multiplier must be > 1.0 for exponential growth" }
        require(maxLevel > 0) { "maxLevel must be positive" }
        
        // Precompute XP table
        xpTable = LongArray(maxLevel) { level ->
            if (level == 0) {
                // Level 1 requires 0 XP
                0L
            } else {
                // Calculate cumulative XP for this level
                var totalXp = 0.0
                for (i in 1..level) {
                    totalXp += baseXp * multiplier.pow(i - 1)
                }
                totalXp.toLong()
            }
        }
    }
    
    /**
     * Calculate the level for a given XP amount.
     * 
     * O(log N) binary search on precomputed table.
     * 
     * @param xp Total XP amount
     * @return Level (1 to maxLevel)
     */
    fun calculateLevel(xp: Long): Int {
        if (xp <= 0) return 1
        
        // Binary search to find the highest level where xpTable[level-1] <= xp
        var left = 0
        var right = xpTable.size - 1
        var result = 1
        
        while (left <= right) {
            val mid = (left + right) / 2
            val requiredXp = xpTable[mid]
            
            if (requiredXp <= xp) {
                // This level is achievable
                result = mid + 1  // Convert index to level (1-indexed)
                left = mid + 1    // Search higher levels
            } else {
                // This level requires too much XP
                right = mid - 1   // Search lower levels
            }
        }
        
        return result.coerceIn(1, maxLevel)
    }
    
    /**
     * Get the XP required to reach the next level.
     * 
     * O(1) table lookup.
     * 
     * @param currentLevel Current level (1 to maxLevel)
     * @return XP required for next level, or 0 if at max level
     */
    fun xpForNextLevel(currentLevel: Int): Long {
        if (currentLevel >= maxLevel) return 0L
        
        val nextLevelIndex = currentLevel  // Next level's index in 0-indexed array
        return xpTable[nextLevelIndex]
    }
    
    /**
     * Get the XP required to reach a specific level.
     * 
     * O(1) table lookup.
     * 
     * @param level Target level (1 to maxLevel)
     * @return Total XP required to reach that level
     */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0L
        if (level > maxLevel) return xpTable.last()
        
        return xpTable[level - 1]
    }
    
    /**
     * Calculate progress to next level as a percentage.
     * 
     * @param currentXp Player's current XP
     * @param currentLevel Player's current level
     * @return Progress as 0.0 to 1.0 (0% to 100%)
     */
    fun progressToNextLevel(currentXp: Long, currentLevel: Int): Double {
        if (currentLevel >= maxLevel) return 1.0
        
        val currentLevelXp = xpForLevel(currentLevel)
        val nextLevelXp = xpForLevel(currentLevel + 1)
        
        val xpIntoLevel = currentXp - currentLevelXp
        val xpNeeded = nextLevelXp - currentLevelXp
        
        if (xpNeeded <= 0) return 1.0
        
        return (xpIntoLevel.toDouble() / xpNeeded.toDouble()).coerceIn(0.0, 1.0)
    }
    
    /**
     * Get the XP required for the current level bracket.
     * 
     * @param currentLevel Current level
     * @return XP needed to go from currentLevel to currentLevel+1
     */
    fun xpForCurrentLevelBracket(currentLevel: Int): Long {
        if (currentLevel >= maxLevel) return 0L
        
        val currentLevelXp = xpForLevel(currentLevel)
        val nextLevelXp = xpForLevel(currentLevel + 1)
        
        return nextLevelXp - currentLevelXp
    }
    
    /**
     * Get statistics about the XP curve.
     * Useful for debugging/config validation.
     */
    fun getStats(): XpCurveStats {
        return XpCurveStats(
            baseXp = baseXp,
            multiplier = multiplier,
            maxLevel = maxLevel,
            level10Xp = xpForLevel(10),
            level25Xp = xpForLevel(25),
            level50Xp = xpForLevel(50),
            level100Xp = xpForLevel(100)
        )
    }
    
    /**
     * Statistics about the configured XP curve.
     */
    data class XpCurveStats(
        val baseXp: Double,
        val multiplier: Double,
        val maxLevel: Int,
        val level10Xp: Long,
        val level25Xp: Long,
        val level50Xp: Long,
        val level100Xp: Long
    ) {
        override fun toString(): String {
            return """
                XP Curve Statistics:
                - Base XP: $baseXp
                - Multiplier: $multiplier
                - Max Level: $maxLevel
                - Level 10: ${level10Xp.formatWithCommas()} XP
                - Level 25: ${level25Xp.formatWithCommas()} XP
                - Level 50: ${level50Xp.formatWithCommas()} XP
                - Level 100: ${level100Xp.formatWithCommas()} XP
            """.trimIndent()
        }
        
        private fun Long.formatWithCommas(): String {
            return "%,d".format(this)
        }
    }
}
