package com.livinglands.modules.metabolism

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Unit tests for PlayerMetabolismState depletion modifier system.
 * 
 * Tests the new Tier 3 Professions ability support added for Phase 0 (Professions Prerequisites).
 */
@DisplayName("PlayerMetabolismState - Depletion Modifiers")
class PlayerMetabolismStateTest {
    
    private lateinit var state: PlayerMetabolismState
    
    @BeforeEach
    fun setup() {
        // Create a fresh state before each test
        state = PlayerMetabolismState(
            playerId = "test-player-uuid",
            initialHunger = 100f,
            initialThirst = 100f,
            initialEnergy = 100f
        )
    }
    
    @Test
    @DisplayName("Default combined multiplier is 1.0 (no modifiers)")
    fun testDefaultMultiplier() {
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(1.0, multiplier, 0.0001, "Default multiplier should be 1.0")
    }
    
    @Test
    @DisplayName("Single modifier applies correctly")
    fun testSingleModifier() {
        // Apply Survivalist ability: 15% slower depletion (0.85x)
        state.applyDepletionModifier("professions:survivalist", 0.85)
        
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(0.85, multiplier, 0.0001, "Single modifier should be 0.85")
        
        // Verify it's in active modifiers
        val activeModifiers = state.getActiveModifiers()
        assertTrue(activeModifiers.containsKey("professions:survivalist"))
        assertEquals(0.85, activeModifiers["professions:survivalist"]!!, 0.0001)
    }
    
    @Test
    @DisplayName("Multiple modifiers stack multiplicatively")
    fun testMultipleModifiersStack() {
        // Apply two modifiers
        state.applyDepletionModifier("professions:survivalist", 0.85)  // -15%
        state.applyDepletionModifier("buff:endurance", 0.90)           // -10%
        
        // Should stack: 0.85 * 0.90 = 0.765 (23.5% slower total)
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(0.765, multiplier, 0.0001, "Modifiers should stack multiplicatively")
    }
    
    @Test
    @DisplayName("Three modifiers stack correctly")
    fun testThreeModifiersStack() {
        state.applyDepletionModifier("professions:survivalist", 0.85)  // -15%
        state.applyDepletionModifier("buff:endurance", 0.90)           // -10%
        state.applyDepletionModifier("equipment:ring", 0.95)           // -5%
        
        // Should stack: 0.85 * 0.90 * 0.95 = 0.72675
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(0.72675, multiplier, 0.0001, "Three modifiers should stack")
    }
    
    @Test
    @DisplayName("Removing modifier works correctly")
    fun testRemoveModifier() {
        state.applyDepletionModifier("professions:survivalist", 0.85)
        state.applyDepletionModifier("buff:endurance", 0.90)
        
        // Remove one modifier
        val removed = state.removeDepletionModifier("professions:survivalist")
        assertTrue(removed, "Modifier should be removed successfully")
        
        // Only the second modifier should remain
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(0.90, multiplier, 0.0001, "Only remaining modifier should apply")
        
        // Verify it's not in active modifiers
        val activeModifiers = state.getActiveModifiers()
        assertFalse(activeModifiers.containsKey("professions:survivalist"))
        assertTrue(activeModifiers.containsKey("buff:endurance"))
    }
    
    @Test
    @DisplayName("Removing non-existent modifier returns false")
    fun testRemoveNonExistentModifier() {
        val removed = state.removeDepletionModifier("does-not-exist")
        assertFalse(removed, "Removing non-existent modifier should return false")
    }
    
    @Test
    @DisplayName("Overwriting modifier updates value")
    fun testOverwriteModifier() {
        state.applyDepletionModifier("professions:survivalist", 0.85)
        
        // Apply again with different value (e.g., ability upgraded)
        state.applyDepletionModifier("professions:survivalist", 0.80)
        
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(0.80, multiplier, 0.0001, "Modifier should be overwritten")
    }
    
    @Test
    @DisplayName("Clearing all modifiers resets to default")
    fun testClearModifiers() {
        state.applyDepletionModifier("professions:survivalist", 0.85)
        state.applyDepletionModifier("buff:endurance", 0.90)
        state.applyDepletionModifier("equipment:ring", 0.95)
        
        // Clear all
        state.clearDepletionModifiers()
        
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(1.0, multiplier, 0.0001, "Multiplier should reset to 1.0")
        
        val activeModifiers = state.getActiveModifiers()
        assertTrue(activeModifiers.isEmpty(), "Active modifiers should be empty")
    }
    
    @Test
    @DisplayName("Modifiers greater than 1.0 increase depletion")
    fun testIncreasingModifier() {
        // Apply a debuff that increases depletion by 50%
        state.applyDepletionModifier("debuff:exhaustion", 1.5)
        
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(1.5, multiplier, 0.0001, "Increasing modifier should apply")
    }
    
    @Test
    @DisplayName("Mixed positive and negative modifiers stack correctly")
    fun testMixedModifiers() {
        state.applyDepletionModifier("professions:survivalist", 0.85)  // -15%
        state.applyDepletionModifier("debuff:exhaustion", 1.5)         // +50%
        
        // Should stack: 0.85 * 1.5 = 1.275 (27.5% faster overall)
        val multiplier = state.getCombinedDepletionMultiplier()
        assertEquals(1.275, multiplier, 0.0001, "Mixed modifiers should stack")
    }
    
    @Test
    @DisplayName("getActiveModifiers returns defensive copy")
    fun testActiveModifiersIsCopy() {
        state.applyDepletionModifier("professions:survivalist", 0.85)
        
        val modifiers1 = state.getActiveModifiers()
        val modifiers2 = state.getActiveModifiers()
        
        // Should be equal but not same instance (defensive copy)
        assertEquals(modifiers1, modifiers2)
        assertNotSame(modifiers1, modifiers2, "Should return a copy, not same instance")
    }
    
    @Test
    @DisplayName("Stat restoration methods work with modifiers active")
    fun testStatRestorationWithModifiers() {
        // Apply a modifier
        state.applyDepletionModifier("professions:survivalist", 0.85)
        
        // Deplete stats
        state.setHunger(50f)
        state.setThirst(50f)
        state.setEnergy(50f)
        
        // Restore stats
        state.addHunger(25f)
        state.addThirst(25f)
        state.addEnergy(25f)
        
        // Check restoration worked
        assertEquals(75f, state.hunger, 0.01f)
        assertEquals(75f, state.thirst, 0.01f)
        assertEquals(75f, state.energy, 0.01f)
        
        // Modifier should still be active
        assertEquals(0.85, state.getCombinedDepletionMultiplier(), 0.0001)
    }
}
