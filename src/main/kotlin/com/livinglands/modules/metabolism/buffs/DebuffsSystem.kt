package com.livinglands.modules.metabolism.buffs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismStats
import com.livinglands.modules.metabolism.config.DebuffsConfig
import com.livinglands.util.MessageFormatter
import com.livinglands.util.SpeedManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Manages metabolism debuffs (penalties for low stats).
 * 
 * **Debuffs:**
 * 1. **Starvation** (hunger = 0): Progressive damage over time
 * 2. **Dehydration** (thirst < 30): Speed and stamina penalties, damage at 0
 * 3. **Exhaustion** (energy < 30): Speed penalty and stamina consumption increase, stamina drain at 0
 * 
 * **Hysteresis Thresholds:**
 * - Debuffs activate when stat crosses ENTER threshold
 * - Debuffs deactivate when stat crosses EXIT threshold
 * - Gap between thresholds prevents flickering
 * 
 * @property config Debuffs configuration
 * @property speedManager Centralized speed modification manager
 * @property logger Logger for debugging
 */
class DebuffsSystem(
    private val config: DebuffsConfig,
    private val speedManager: SpeedManager,
    private val logger: Logger
) {
    
    // ========================================
    // Hysteresis Controllers
    // ========================================
    
    private val starvationController = HysteresisController.forDebuff(
        enterThreshold = 0.0,   // Activate when hunger <= 0
        exitThreshold = 30.0    // Deactivate when hunger >= 30
    )
    
    private val dehydrationDamageController = HysteresisController.forDebuff(
        enterThreshold = 0.0,   // Activate when thirst <= 0
        exitThreshold = 30.0    // Deactivate when thirst >= 30
    )
    
    private val parchedController = HysteresisController.forDebuff(
        enterThreshold = 30.0,  // Activate when thirst <= 30
        exitThreshold = 30.0    // Deactivate when thirst >= 30 (same threshold for parched)
    )
    
    private val exhaustedController = HysteresisController.forDebuff(
        enterThreshold = 0.0,   // Activate when energy <= 0
        exitThreshold = 50.0    // Deactivate when energy >= 50 (larger gap)
    )
    
    private val tiredController = HysteresisController.forDebuff(
        enterThreshold = 30.0,  // Activate when energy <= 30
        exitThreshold = 30.0    // Deactivate when energy >= 30
    )
    
    // ========================================
    // Damage Tracking
    // ========================================
    
    /**
     * Starvation damage tick counter (increases damage over time).
     */
    private val starvationTicks = ConcurrentHashMap<UUID, Int>()
    
    /**
     * Last time starvation damage was applied (milliseconds).
     */
    private val lastStarvationDamage = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Last time dehydration damage was applied (milliseconds).
     */
    private val lastDehydrationDamage = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Last time exhaustion stamina drain was applied (milliseconds).
     */
    private val lastExhaustionDrain = ConcurrentHashMap<UUID, Long>()
    
    // ========================================
    // Public API
    // ========================================
    
    /**
     * Tick the debuffs system for a player.
     * Checks thresholds and applies/removes debuffs as needed.
     * 
     * **Important:** Must be called from world thread.
     * 
     * @param playerId Player UUID
     * @param stats Current metabolism stats
     * @param entityRef Player's entity reference
     * @param store Entity store
     * @param world World instance
     */
    fun tick(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        if (!config.enabled) return
        
        try {
            // Check starvation (hunger = 0)
            tickStarvation(playerId, stats, entityRef, store)
            
            // Check dehydration (thirst < 30)
            tickDehydration(playerId, stats, entityRef, store)
            
            // Check exhaustion (energy < 30)
            tickExhaustion(playerId, stats, entityRef, store)
            
        } catch (e: Exception) {
            logger.warning("Error ticking debuffs for player $playerId: ${e.message}")
        }
    }
    
    /**
     * Check if a player has any active debuffs.
     * Used by BuffsSystem to suppress buffs when debuffed.
     * 
     * @param playerId Player UUID
     * @return True if any debuff is active
     */
    fun hasActiveDebuffs(playerId: UUID): Boolean {
        return starvationController.isActive(playerId) ||
               dehydrationDamageController.isActive(playerId) ||
               parchedController.isActive(playerId) ||
               exhaustedController.isActive(playerId) ||
               tiredController.isActive(playerId)
    }
    
    /**
     * Clean up tracking for a player (e.g., on disconnect).
     * 
     * @param playerId Player UUID
     */
    fun cleanup(playerId: UUID) {
        starvationController.clear(playerId)
        dehydrationDamageController.clear(playerId)
        parchedController.clear(playerId)
        exhaustedController.clear(playerId)
        tiredController.clear(playerId)
        
        starvationTicks.remove(playerId)
        lastStarvationDamage.remove(playerId)
        lastDehydrationDamage.remove(playerId)
        lastExhaustionDrain.remove(playerId)
        
        // Clean up speed modifications
        speedManager.removeMultiplier(playerId, "debuff:thirst")
        speedManager.removeMultiplier(playerId, "debuff:energy")
    }
    
    // ========================================
    // Starvation (Hunger = 0)
    // ========================================
    
    private fun tickStarvation(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        val transition = starvationController.update(playerId, stats.hunger.toDouble())
        
        when (transition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                starvationTicks[playerId] = 0
                sendDebuffMessage(playerId, "Starvation", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                starvationTicks.remove(playerId)
                lastStarvationDamage.remove(playerId)
                sendDebuffMessage(playerId, "Starvation", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Apply damage if starving
                if (starvationController.isActive(playerId)) {
                    applyStarvationDamage(playerId, entityRef, store)
                }
            }
        }
    }
    
    private fun applyStarvationDamage(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        val now = System.currentTimeMillis()
        val lastDamage = lastStarvationDamage[playerId] ?: 0L
        
        // Apply damage every 3 seconds (3000ms)
        if (now - lastDamage < config.starvation.damageIntervalMs) return
        
        // Calculate progressive damage
        val ticks = starvationTicks.getOrDefault(playerId, 0)
        val damage = minOf(
            config.starvation.initialDamage + (ticks * config.starvation.damageIncreasePerTick),
            config.starvation.maxDamage
        )
        
        // Apply damage
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val healthId = DefaultEntityStatTypes.getHealth()
            
            // Subtract health
            statMap.subtractStatValue(healthId, damage.toFloat())
            
            lastStarvationDamage[playerId] = now
            starvationTicks[playerId] = ticks + 1
            
            logger.fine("Applied starvation damage to player $playerId: $damage HP (tick $ticks)")
        } catch (e: Exception) {
            logger.warning("Failed to apply starvation damage to player $playerId: ${e.message}")
        }
    }
    
    // ========================================
    // Dehydration (Thirst < 30)
    // ========================================
    
    private fun tickDehydration(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Two-tier system: Parched (< 30) and Dehydrated (= 0)
        
        // Tier 1: Parched (gradual speed/stamina reduction)
        val parchedTransition = parchedController.update(playerId, stats.thirst.toDouble())
        when (parchedTransition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                sendDebuffMessage(playerId, "Parched", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                speedManager.removeMultiplier(playerId, "debuff:thirst")
                speedManager.applySpeed(playerId, entityRef, store)
                sendDebuffMessage(playerId, "Parched", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Update speed/stamina based on thirst level
                if (parchedController.isActive(playerId)) {
                    applyThirstSpeedPenalty(playerId, stats.thirst.toDouble(), entityRef, store)
                }
            }
        }
        
        // Tier 2: Dehydrated (critical damage)
        val dehydratedTransition = dehydrationDamageController.update(playerId, stats.thirst.toDouble())
        when (dehydratedTransition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                sendDebuffMessage(playerId, "Dehydrated", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                lastDehydrationDamage.remove(playerId)
                sendDebuffMessage(playerId, "Dehydrated", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Apply damage if dehydrated
                if (dehydrationDamageController.isActive(playerId)) {
                    applyDehydrationDamage(playerId, entityRef, store)
                }
            }
        }
    }
    
    private fun applyThirstSpeedPenalty(playerId: UUID, thirst: Double, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Gradual speed reduction from 100% (at 30 thirst) to 40.5% (at 0 thirst)
        val debuffRatio = 1.0 - (thirst / 30.0)  // 0 at 30%, 1.0 at 0%
        val speedMultiplier = (1.0f - ((1.0f - config.dehydration.minSpeed.toFloat()) * debuffRatio.toFloat()))
        
        speedManager.setMultiplier(playerId, "debuff:thirst", speedMultiplier)
        speedManager.applySpeed(playerId, entityRef, store)
    }
    
    private fun applyDehydrationDamage(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        val now = System.currentTimeMillis()
        val lastDamage = lastDehydrationDamage[playerId] ?: 0L
        
        // Apply damage every 4 seconds (4000ms)
        if (now - lastDamage < config.dehydration.damageIntervalMs) return
        
        val damage = config.dehydration.damage
        
        // Apply damage
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val healthId = DefaultEntityStatTypes.getHealth()
            
            // Subtract health
            statMap.subtractStatValue(healthId, damage.toFloat())
            
            lastDehydrationDamage[playerId] = now
            
            logger.fine("Applied dehydration damage to player $playerId: $damage HP")
        } catch (e: Exception) {
            logger.warning("Failed to apply dehydration damage to player $playerId: ${e.message}")
        }
    }
    
    // ========================================
    // Exhaustion (Energy < 30)
    // ========================================
    
    private fun tickExhaustion(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Two-tier system: Tired (< 30) and Exhausted (= 0)
        
        // Tier 1: Tired (gradual speed reduction and stamina consumption increase)
        val tiredTransition = tiredController.update(playerId, stats.energy.toDouble())
        when (tiredTransition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                sendDebuffMessage(playerId, "Tired", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                speedManager.removeMultiplier(playerId, "debuff:energy")
                speedManager.applySpeed(playerId, entityRef, store)
                sendDebuffMessage(playerId, "Tired", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Update speed based on energy level
                if (tiredController.isActive(playerId)) {
                    applyEnergySpeedPenalty(playerId, stats.energy.toDouble(), entityRef, store)
                }
            }
        }
        
        // Tier 2: Exhausted (active stamina drain)
        val exhaustedTransition = exhaustedController.update(playerId, stats.energy.toDouble())
        when (exhaustedTransition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                sendDebuffMessage(playerId, "Exhausted", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                lastExhaustionDrain.remove(playerId)
                sendDebuffMessage(playerId, "Exhausted", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Apply stamina drain if exhausted
                if (exhaustedController.isActive(playerId)) {
                    applyExhaustionStaminaDrain(playerId, entityRef, store)
                }
            }
        }
    }
    
    private fun applyEnergySpeedPenalty(playerId: UUID, energy: Double, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Gradual speed reduction from 100% (at 30 energy) to 54% (at 0 energy)
        val debuffRatio = 1.0 - (energy / 30.0)  // 0 at 30%, 1.0 at 0%
        val speedMultiplier = (1.0f - ((1.0f - config.exhaustion.minSpeed.toFloat()) * debuffRatio.toFloat()))
        
        speedManager.setMultiplier(playerId, "debuff:energy", speedMultiplier)
        speedManager.applySpeed(playerId, entityRef, store)
    }
    
    private fun applyExhaustionStaminaDrain(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        val now = System.currentTimeMillis()
        val lastDrain = lastExhaustionDrain[playerId] ?: 0L
        
        // Drain stamina every 1 second (1000ms)
        if (now - lastDrain < config.exhaustion.drainIntervalMs) return
        
        val drain = config.exhaustion.staminaDrain
        
        // Apply stamina drain
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val staminaId = DefaultEntityStatTypes.getStamina()
            
            // Subtract stamina
            statMap.subtractStatValue(staminaId, drain.toFloat())
            
            lastExhaustionDrain[playerId] = now
            
            logger.fine("Applied exhaustion stamina drain to player $playerId: $drain stamina")
        } catch (e: Exception) {
            logger.warning("Failed to apply exhaustion stamina drain to player $playerId: ${e.message}")
        }
    }
    
    // ========================================
    // Messaging
    // ========================================
    
    private fun sendDebuffMessage(playerId: UUID, debuffName: String, activated: Boolean) {
        val session = CoreModule.players.getSession(playerId) ?: return
        
        session.world.execute {
            try {
                val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return@execute
                @Suppress("DEPRECATION")
                val playerRef = player.getPlayerRef() ?: return@execute
                
                MessageFormatter.debuff(playerRef, debuffName, activated)
            } catch (e: Exception) {
                logger.fine("Failed to send debuff message to player $playerId: ${e.message}")
            }
        }
    }
}
