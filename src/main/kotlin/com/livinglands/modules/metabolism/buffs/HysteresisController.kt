package com.livinglands.modules.metabolism.buffs

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic hysteresis controller for managing state transitions with enter/exit thresholds.
 * 
 * **Purpose:** Prevent state flickering when values hover near a threshold boundary.
 * 
 * **How it works:**
 * - State activates when value crosses **enter threshold**
 * - State deactivates when value crosses **exit threshold**
 * - The gap between thresholds creates a "dead zone" that prevents rapid on/off toggling
 * 
 * **Example (Debuff):**
 * ```
 * Enter at 20%, exit at 40%
 * 
 * Value: 50% -> 30% -> 20% -> 19%
 *        OFF    OFF     OFF     ON (crosses enter threshold)
 * 
 * Value: 19% -> 20% -> 30% -> 39% -> 40%
 *        ON     ON     ON     ON     OFF (crosses exit threshold)
 * ```
 * 
 * **Example (Buff):**
 * ```
 * Enter at 90%, exit at 80%
 * 
 * Value: 70% -> 85% -> 90%
 *        OFF    OFF     ON (crosses enter threshold)
 * 
 * Value: 90% -> 85% -> 80% -> 79%
 *        ON     ON     ON     OFF (crosses exit threshold)
 * ```
 * 
 * @property enterThreshold Value must reach this to activate state
 * @property exitThreshold Value must reach this to deactivate state
 * @property compareEnter Comparison function for enter threshold (default: <=)
 * @property compareExit Comparison function for exit threshold (default: >=)
 */
class HysteresisController(
    private val enterThreshold: Double,
    private val exitThreshold: Double,
    private val compareEnter: (Double, Double) -> Boolean = { value, threshold -> value <= threshold },
    private val compareExit: (Double, Double) -> Boolean = { value, threshold -> value >= threshold }
) {
    /**
     * Thread-safe set of players currently in active state.
     */
    private val activePlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    
    /**
     * Check if a player is currently in active state.
     * 
     * @param playerId Player UUID
     * @return True if state is active
     */
    fun isActive(playerId: UUID): Boolean {
        return activePlayers.contains(playerId)
    }
    
    /**
     * Update state based on current value.
     * 
     * **State Transitions:**
     * - INACTIVE → ACTIVE: When value crosses enter threshold
     * - ACTIVE → INACTIVE: When value crosses exit threshold
     * - No change otherwise
     * 
     * @param playerId Player UUID
     * @param currentValue Current stat value to evaluate
     * @return StateTransition indicating what happened
     */
    fun update(playerId: UUID, currentValue: Double): StateTransition {
        val wasActive = activePlayers.contains(playerId)
        
        return when {
            // Not active, check if should activate
            !wasActive && compareEnter(currentValue, enterThreshold) -> {
                activePlayers.add(playerId)
                StateTransition.ACTIVATED
            }
            
            // Active, check if should deactivate
            wasActive && compareExit(currentValue, exitThreshold) -> {
                activePlayers.remove(playerId)
                StateTransition.DEACTIVATED
            }
            
            // No change
            else -> StateTransition.NO_CHANGE
        }
    }
    
    /**
     * Force remove a player from active state (e.g., on disconnect).
     * 
     * @param playerId Player UUID
     */
    fun clear(playerId: UUID) {
        activePlayers.remove(playerId)
    }
    
    /**
     * Get count of players in active state.
     * 
     * @return Number of active players
     */
    fun getActiveCount(): Int {
        return activePlayers.size
    }
    
    /**
     * State transition result from update().
     */
    enum class StateTransition {
        /** State changed from inactive to active */
        ACTIVATED,
        
        /** State changed from active to inactive */
        DEACTIVATED,
        
        /** State remained the same */
        NO_CHANGE
    }
    
    companion object {
        /**
         * Create a controller for a debuff (activates when value is LOW).
         * 
         * Example: Dehydration debuff enters at 20%, exits at 40%
         * 
         * @param enterThreshold Activate when value <= this
         * @param exitThreshold Deactivate when value >= this
         * @return Configured controller
         */
        fun forDebuff(enterThreshold: Double, exitThreshold: Double): HysteresisController {
            return HysteresisController(
                enterThreshold = enterThreshold,
                exitThreshold = exitThreshold,
                compareEnter = { value, threshold -> value <= threshold },
                compareExit = { value, threshold -> value >= threshold }
            )
        }
        
        /**
         * Create a controller for a buff (activates when value is HIGH).
         * 
         * Example: Speed buff enters at 90%, exits at 80%
         * 
         * @param enterThreshold Activate when value >= this
         * @param exitThreshold Deactivate when value <= this
         * @return Configured controller
         */
        fun forBuff(enterThreshold: Double, exitThreshold: Double): HysteresisController {
            return HysteresisController(
                enterThreshold = enterThreshold,
                exitThreshold = exitThreshold,
                compareEnter = { value, threshold -> value >= threshold },
                compareExit = { value, threshold -> value <= threshold }
            )
        }
    }
}
