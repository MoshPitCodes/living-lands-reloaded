package com.livinglands.modules.professions

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Unit tests for XpCalculator.
 * 
 * Tests the precomputed XP table and level calculation logic.
 */
@DisplayName("XpCalculator")
class XpCalculatorTest {
    
    private lateinit var calculator: XpCalculator
    
    @BeforeEach
    fun setup() {
        // Use default values: baseXp=100, multiplier=1.15, maxLevel=100
        calculator = XpCalculator()
    }
    
    @Test
    @DisplayName("Level 1 requires 0 XP")
    fun testLevel1RequiresZeroXp() {
        assertEquals(1, calculator.calculateLevel(0L))
        assertEquals(0L, calculator.xpForLevel(1))
    }
    
    @Test
    @DisplayName("Level 2 requires baseXp (100) XP")
    fun testLevel2RequiresBaseXp() {
        assertEquals(2, calculator.calculateLevel(100L))
        assertEquals(100L, calculator.xpForLevel(2))
    }
    
    @Test
    @DisplayName("Level calculation is accurate for known values")
    fun testKnownLevels() {
        // With baseXp=100, multiplier=1.15:
        // Level 2: 100 XP
        // Level 3: 100 + 115 = 215 XP
        // Level 4: 215 + 132 = 347 XP (approximately)
        
        assertEquals(1, calculator.calculateLevel(0L))
        assertEquals(1, calculator.calculateLevel(99L))
        assertEquals(2, calculator.calculateLevel(100L))
        assertEquals(2, calculator.calculateLevel(214L))
        assertEquals(3, calculator.calculateLevel(215L))
    }
    
    @Test
    @DisplayName("Calculate level with exact XP threshold")
    fun testExactThresholds() {
        val level2Xp = calculator.xpForLevel(2)
        val level3Xp = calculator.xpForLevel(3)
        val level10Xp = calculator.xpForLevel(10)
        
        // At exact threshold, should be that level
        assertEquals(2, calculator.calculateLevel(level2Xp))
        assertEquals(3, calculator.calculateLevel(level3Xp))
        assertEquals(10, calculator.calculateLevel(level10Xp))
        
        // One below threshold, should be previous level
        assertEquals(1, calculator.calculateLevel(level2Xp - 1))
        assertEquals(2, calculator.calculateLevel(level3Xp - 1))
        assertEquals(9, calculator.calculateLevel(level10Xp - 1))
    }
    
    @Test
    @DisplayName("XP for next level calculation")
    fun testXpForNextLevel() {
        val level1NextXp = calculator.xpForNextLevel(1)
        val level2NextXp = calculator.xpForNextLevel(2)
        
        // Level 1 -> Level 2 requires baseXp
        assertEquals(100L, level1NextXp)
        
        // Level 2 -> Level 3 requires more XP
        assertTrue(level2NextXp > level1NextXp)
    }
    
    @Test
    @DisplayName("XP for max level returns 0 for next level")
    fun testMaxLevelNextXp() {
        val nextXp = calculator.xpForNextLevel(100)
        assertEquals(0L, nextXp, "Max level should have no next level XP")
    }
    
    @Test
    @DisplayName("Progress to next level calculation")
    fun testProgressToNextLevel() {
        val level2Xp = calculator.xpForLevel(2)
        val level3Xp = calculator.xpForLevel(3)
        
        // At start of level 2, progress = 0%
        val progressAtStart = calculator.progressToNextLevel(level2Xp, 2)
        assertEquals(0.0, progressAtStart, 0.01)
        
        // At end of level 2 (just before level 3), progress = ~100%
        val progressAtEnd = calculator.progressToNextLevel(level3Xp - 1, 2)
        assertTrue(progressAtEnd > 0.99)
        
        // Halfway through level 2
        val midpoint = (level2Xp + level3Xp) / 2
        val progressMid = calculator.progressToNextLevel(midpoint, 2)
        assertEquals(0.5, progressMid, 0.05)
    }
    
    @Test
    @DisplayName("Progress at max level is 100%")
    fun testProgressAtMaxLevel() {
        val progress = calculator.progressToNextLevel(Long.MAX_VALUE, 100)
        assertEquals(1.0, progress, 0.01)
    }
    
    @Test
    @DisplayName("XP curve is exponential")
    fun testExponentialCurve() {
        val level5Xp = calculator.xpForLevel(5)
        val level10Xp = calculator.xpForLevel(10)
        val level20Xp = calculator.xpForLevel(20)
        
        // XP should grow exponentially (more than linearly)
        // Level 10 should require more than 2x the XP of level 5
        assertTrue(level10Xp > level5Xp * 2, "Level 10 XP ($level10Xp) should be > 2x Level 5 XP ($level5Xp)")
        
        // Level 20 should require significantly more than Level 10
        // (With multiplier 1.15, it's about 5-6x, not 10x)
        assertTrue(level20Xp > level10Xp * 4, "Level 20 XP ($level20Xp) should be > 4x Level 10 XP ($level10Xp)")
    }
    
    @Test
    @DisplayName("XP for current level bracket")
    fun testXpForCurrentLevelBracket() {
        val bracket1 = calculator.xpForCurrentLevelBracket(1)  // 1 -> 2
        val bracket2 = calculator.xpForCurrentLevelBracket(2)  // 2 -> 3
        
        // First bracket is baseXp
        assertEquals(100L, bracket1)
        
        // Second bracket is larger (exponential growth)
        assertTrue(bracket2 > bracket1)
    }
    
    @Test
    @DisplayName("Custom XP curve parameters")
    fun testCustomCurve() {
        // Steeper curve: baseXp=200, multiplier=1.25
        val steepCalculator = XpCalculator(baseXp = 200.0, multiplier = 1.25)
        
        val level2Xp = steepCalculator.xpForLevel(2)
        val level10Xp = steepCalculator.xpForLevel(10)
        
        // Level 2 should require 200 XP
        assertEquals(200L, level2Xp)
        
        // Level 10 should require more than default curve
        val defaultLevel10 = calculator.xpForLevel(10)
        assertTrue(level10Xp > defaultLevel10)
    }
    
    @Test
    @DisplayName("Get XP curve statistics")
    fun testGetStats() {
        val stats = calculator.getStats()
        
        assertEquals(100.0, stats.baseXp, 0.01)
        assertEquals(1.15, stats.multiplier, 0.01)
        assertEquals(100, stats.maxLevel)
        
        // Verify milestone XP values are reasonable (exponential growth)
        // With baseXp=100 and multiplier=1.15:
        // - Level 10 requires ~2,000 XP
        // - Level 25 requires ~100,000 XP (50x level 10)
        // - Level 50 requires ~7,000,000 XP (70x level 25)
        assertTrue(stats.level10Xp > 1000L, "Level 10 should require > 1000 XP")
        assertTrue(stats.level25Xp > stats.level10Xp * 5, "Level 25 should require > 5x Level 10 XP")
        assertTrue(stats.level50Xp > stats.level25Xp * 20, "Level 50 should require > 20x Level 25 XP")
    }
    
    @Test
    @DisplayName("Negative XP returns level 1")
    fun testNegativeXp() {
        assertEquals(1, calculator.calculateLevel(-100L))
        assertEquals(1, calculator.calculateLevel(-1L))
    }
    
    @Test
    @DisplayName("Very large XP caps at max level")
    fun testVeryLargeXp() {
        val level = calculator.calculateLevel(Long.MAX_VALUE)
        assertEquals(100, level)
    }
    
    @Test
    @DisplayName("Binary search efficiency test")
    fun testBinarySearchEfficiency() {
        // Test many XP values to ensure binary search works correctly
        for (level in 1..100) {
            val xpForLevel = calculator.xpForLevel(level)
            val calculatedLevel = calculator.calculateLevel(xpForLevel)
            
            assertEquals(level, calculatedLevel, "Failed for level $level")
        }
    }
}
