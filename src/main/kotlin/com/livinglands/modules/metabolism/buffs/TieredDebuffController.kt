package com.livinglands.modules.metabolism.buffs

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a 3-tier debuff for a single stat type with uniform thresholds.
 * 
 * **Thresholds (10-point hysteresis gap):**
 * - Tier 1 (Mild):     enter ≤ 75%, exit > 85%
 * - Tier 2 (Moderate): enter ≤ 50%, exit > 60%
 * - Tier 3 (Severe):   enter ≤ 25%, exit > 35%
 * 
 * **10-point gap prevents flickering:**
 * - Buffs use 10-point gaps (enter 90%, exit 80%)
 * - Debuffs now use 10-point gaps to match (enter 75%, exit 85%)
 * - Prevents rapid on/off transitions during natural stat fluctuation
 *
 * @property statName Name for logging (e.g., "hunger", "thirst", "energy")
 * @property tierNames Display names for each tier: [mild, moderate, severe]
 */
class TieredDebuffController(
    private val statName: String,
    private val tierNames: List<String> // e.g., ["Peckish", "Hungry", "Starving"]
) {
    init {
        require(tierNames.size == 3) { "Must provide exactly 3 tier names" }
    }

    // Uniform thresholds for all debuff types (10-point hysteresis gap to match buffs)
    private val controllers = listOf(
        HysteresisController.forDebuff(enterThreshold = 75.0, exitThreshold = 85.0),  // Tier 1
        HysteresisController.forDebuff(enterThreshold = 50.0, exitThreshold = 60.0),  // Tier 2
        HysteresisController.forDebuff(enterThreshold = 25.0, exitThreshold = 35.0)   // Tier 3
    )

    /** Previous worst tier (0 = none, 1-3 = tier) */
    private val previousWorstTier = ConcurrentHashMap<UUID, Int>()

    /**
     * Result of a tick update.
     */
    data class TickResult(
        /** Current worst active tier (0 = none, 1-3 = tier) */
        val currentTier: Int,
        /** Messages to send: Pair(debuffName, activated) */
        val messages: List<Pair<String, Boolean>>
    )

    /**
     * Update all tier controllers and compute state transitions.
     * Returns the worst active tier and any messages to send.
     */
    fun update(playerId: UUID, statValue: Double): TickResult {
        // Update all controllers (must always track state)
        val transitions = controllers.map { it.update(playerId, statValue) }
        
        // Determine worst active tier (higher = worse, 0 = none)
        val currentTier = when {
            controllers[2].isActive(playerId) -> 3
            controllers[1].isActive(playerId) -> 2
            controllers[0].isActive(playerId) -> 1
            else -> 0
        }
        
        val prevTier = previousWorstTier[playerId] ?: 0
        previousWorstTier[playerId] = currentTier
        
        // Generate messages for tier changes
        val messages = mutableListOf<Pair<String, Boolean>>()
        
        when {
            // Escalated to worse tier
            currentTier > prevTier -> {
                if (prevTier > 0) {
                    messages.add(tierNames[prevTier - 1] to false)  // Deactivate old
                }
                messages.add(tierNames[currentTier - 1] to true)   // Activate new
            }
            // De-escalated to better tier
            currentTier < prevTier && currentTier > 0 -> {
                messages.add(tierNames[prevTier - 1] to false)     // Deactivate old
                messages.add(tierNames[currentTier - 1] to true)   // Activate new
            }
            // Fully recovered
            currentTier == 0 && prevTier > 0 -> {
                messages.add(tierNames[prevTier - 1] to false)     // Deactivate old
            }
        }
        
        return TickResult(currentTier, messages)
    }

    /**
     * Get display name for current worst tier.
     */
    fun getActiveDebuffName(playerId: UUID): String? = when {
        controllers[2].isActive(playerId) -> tierNames[2]
        controllers[1].isActive(playerId) -> tierNames[1]
        controllers[0].isActive(playerId) -> tierNames[0]
        else -> null
    }

    fun isActive(playerId: UUID): Boolean = controllers.any { it.isActive(playerId) }

    fun getCurrentTier(playerId: UUID): Int = when {
        controllers[2].isActive(playerId) -> 3
        controllers[1].isActive(playerId) -> 2
        controllers[0].isActive(playerId) -> 1
        else -> 0
    }

    fun clear(playerId: UUID) {
        controllers.forEach { it.clear(playerId) }
        previousWorstTier.remove(playerId)
    }
}
