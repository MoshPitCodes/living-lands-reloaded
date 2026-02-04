package com.livinglands.modules.metabolism.buffs

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismStats
import com.livinglands.modules.metabolism.config.BuffsConfig
import com.livinglands.core.MessageFormatter
import com.livinglands.core.SpeedManager
import java.util.UUID

/**
 * Manages metabolism buffs (bonuses for high stats).
 * 
 * **Buffs:**
 * 1. **Speed Buff** (energy >= 90%): +13.2% movement speed
 * 2. **Health Buff** (hunger >= 90%): +13.2% max health
 * 3. **Stamina Buff** (thirst >= 90%): +13.2% max stamina
 * 
 * **Hysteresis Thresholds:**
 * - Buffs activate when stat >= 90%
 * - Buffs deactivate when stat < 80%
 * - 10-point gap prevents flickering
 * 
 * **Debuff Suppression:**
 * - ALL buffs are completely suppressed when ANY debuff is active
 * - Prevents conflicting effects (e.g., speed buff + speed debuff)
 * 
 * @property config Buffs configuration
 * @property debuffsSystem Reference to debuffs system (for suppression check)
 * @property speedManager Centralized speed modification manager
 * @property logger Logger for debugging
 */
class BuffsSystem(
    private val config: BuffsConfig,
    private val debuffsSystem: DebuffsSystem,
    private val speedManager: SpeedManager,
    private val logger: HytaleLogger
) {
    
    // ========================================
    // Hysteresis Controllers
    // ========================================
    
    private val speedBuffController = HysteresisController.forBuff(
        enterThreshold = 90.0,  // Activate when energy >= 90
        exitThreshold = 80.0    // Deactivate when energy < 80
    )
    
    private val defenseBuffController = HysteresisController.forBuff(
        enterThreshold = 90.0,  // Activate when hunger >= 90
        exitThreshold = 80.0    // Deactivate when hunger < 80
    )
    
    private val staminaBuffController = HysteresisController.forBuff(
        enterThreshold = 90.0,  // Activate when thirst >= 90
        exitThreshold = 80.0    // Deactivate when thirst < 80
    )
    
    // ========================================
    // Public API
    // ========================================
    
    /**
     * Tick the buffs system for a player using world-specific config.
     * Checks thresholds and applies/removes buffs as needed.
     * 
     * **Important:** Must be called from world thread.
     * 
     * @param playerId Player UUID
     * @param stats Current metabolism stats
     * @param entityRef Player's entity reference
     * @param store Entity store
     * @param buffsConfig World-specific buffs configuration to use
     */
    fun tick(
        playerId: UUID, 
        stats: MetabolismStats, 
        entityRef: Ref<EntityStore>, 
        store: Store<EntityStore>,
        buffsConfig: BuffsConfig
    ) {
        if (!buffsConfig.enabled) return
        if (!entityRef.isValid) return  // Early validation to ensure consistent state across all sub-ticks
        
        try {
            // If player has any active debuffs, suppress ALL buffs
            if (debuffsSystem.hasActiveDebuffs(playerId)) {
                removeAllBuffs(playerId, entityRef, store)
                return
            }
            
            // Check speed buff (energy >= 90%)
            tickSpeedBuff(playerId, stats, entityRef, store, buffsConfig)
            
            // Check defense buff (hunger >= 90%)
            tickDefenseBuff(playerId, stats, entityRef, store, buffsConfig)
            
            // Check stamina buff (thirst >= 90%)
            tickStaminaBuff(playerId, stats, entityRef, store, buffsConfig)
            
        } catch (e: Exception) {
            LoggingManager.warn(logger, "metabolism") { "Error ticking buffs for player $playerId: ${e.message}" }
        }
    }
    
    /**
     * Clean up tracking for a player (e.g., on disconnect).
     * 
     * @param playerId Player UUID
     * @param entityRef Player's entity reference (optional, for modifier cleanup)
     * @param store Entity store (optional, for modifier cleanup)
     */
    fun cleanup(playerId: UUID, entityRef: Ref<EntityStore>? = null, store: Store<EntityStore>? = null) {
        speedBuffController.clear(playerId)
        defenseBuffController.clear(playerId)
        staminaBuffController.clear(playerId)
        
        // Clean up speed modifications
        speedManager.removeMultiplier(playerId, "buff:speed")
        
        // Clean up EntityStatMap modifiers (prevent memory leak)
        if (entityRef != null && store != null && entityRef.isValid) {
            try {
                val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType())
                if (statMap != null) {
                    val healthId = DefaultEntityStatTypes.getHealth()
                    val staminaId = DefaultEntityStatTypes.getStamina()
                    statMap.removeModifier(EntityStatMap.Predictable.SELF, healthId, "livinglands_buff_health")
                    statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_buff_stamina")
                }
            } catch (e: Exception) {
                LoggingManager.debug(logger, "metabolism") { "Could not remove modifiers during cleanup for $playerId: ${e.message}" }
            }
        }
    }
    
    // ========================================
    // Speed Buff (Energy >= 90%)
    // ========================================
    
    private fun tickSpeedBuff(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>, buffsConfig: BuffsConfig) {
        val transition = speedBuffController.update(playerId, stats.energy.toDouble())
        
        when (transition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                speedManager.setMultiplier(playerId, "buff:speed", buffsConfig.speedBuff.multiplier.toFloat())
                speedManager.applySpeed(playerId, entityRef, store)
                sendBuffMessage(playerId, "Energized", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                speedManager.removeMultiplier(playerId, "buff:speed")
                speedManager.applySpeed(playerId, entityRef, store)
                sendBuffMessage(playerId, "Energized", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Nothing to do
            }
        }
    }
    
    // ========================================
    // Health Buff (Hunger >= 90%)
    // ========================================
    
    private fun tickDefenseBuff(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>, buffsConfig: BuffsConfig) {
        val transition = defenseBuffController.update(playerId, stats.hunger.toDouble())
        
        when (transition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                applyDefenseBuff(playerId, entityRef, store, true, buffsConfig)
                sendBuffMessage(playerId, "Well-Fed", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                applyDefenseBuff(playerId, entityRef, store, false, buffsConfig)
                sendBuffMessage(playerId, "Well-Fed", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Nothing to do
            }
        }
    }
    
    private fun applyDefenseBuff(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>, apply: Boolean, buffsConfig: BuffsConfig) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val healthId = DefaultEntityStatTypes.getHealth()
            
            if (apply) {
                // Apply max health buff using StaticModifier with MULTIPLICATIVE calculation
                val modifier = StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.MULTIPLICATIVE,
                    buffsConfig.defenseBuff.multiplier.toFloat()  // e.g., 1.132 for +13.2%
                )
                
                // Use SELF predictable to ensure client receives the stat update
                statMap.putModifier(EntityStatMap.Predictable.SELF, healthId, "livinglands_buff_health", modifier)
                
                LoggingManager.debug(logger, "metabolism") { "Applied defense buff to player $playerId: +${((buffsConfig.defenseBuff.multiplier - 1.0) * 100).toInt()}% max health" }
            } else {
                // Remove health buff modifier
                statMap.removeModifier(EntityStatMap.Predictable.SELF, healthId, "livinglands_buff_health")
                
                LoggingManager.debug(logger, "metabolism") { "Removed defense buff from player $playerId" }
            }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "metabolism") { "Failed to apply defense buff for player $playerId: ${e.message}" }
        }
    }
    
    // ========================================
    // Stamina Buff (Thirst >= 90%)
    // ========================================
    
    private fun tickStaminaBuff(playerId: UUID, stats: MetabolismStats, entityRef: Ref<EntityStore>, store: Store<EntityStore>, buffsConfig: BuffsConfig) {
        val transition = staminaBuffController.update(playerId, stats.thirst.toDouble())
        
        when (transition) {
            HysteresisController.StateTransition.ACTIVATED -> {
                applyStaminaBuff(playerId, entityRef, store, true, buffsConfig)
                sendBuffMessage(playerId, "Hydrated", true)
            }
            
            HysteresisController.StateTransition.DEACTIVATED -> {
                applyStaminaBuff(playerId, entityRef, store, false, buffsConfig)
                sendBuffMessage(playerId, "Hydrated", false)
            }
            
            HysteresisController.StateTransition.NO_CHANGE -> {
                // Nothing to do
            }
        }
    }
    
    private fun applyStaminaBuff(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>, apply: Boolean, buffsConfig: BuffsConfig) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val staminaId = DefaultEntityStatTypes.getStamina()
            
            if (apply) {
                // Apply max stamina buff using StaticModifier with MULTIPLICATIVE calculation
                val modifier = StaticModifier(
                    Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.MULTIPLICATIVE,
                    buffsConfig.staminaBuff.multiplier.toFloat()  // e.g., 1.132 for +13.2%
                )
                
                // Use SELF predictable to ensure client receives the stat update
                statMap.putModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_buff_stamina", modifier)
                
                LoggingManager.debug(logger, "metabolism") { "Applied stamina buff to player $playerId: +${((buffsConfig.staminaBuff.multiplier - 1.0) * 100).toInt()}% max stamina" }
            } else {
                // Remove stamina buff modifier
                statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_buff_stamina")
                
                LoggingManager.debug(logger, "metabolism") { "Removed stamina buff from player $playerId" }
            }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "metabolism") { "Failed to apply stamina buff for player $playerId: ${e.message}" }
        }
    }
    
    // ========================================
    // Helper Methods
    // ========================================
    
    /**
     * Remove all buffs from a player (called when debuffs become active).
     */
    private fun removeAllBuffs(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        // Remove speed buff if active
        if (speedBuffController.isActive(playerId)) {
            speedManager.removeMultiplier(playerId, "buff:speed")
            speedManager.applySpeed(playerId, entityRef, store)
            speedBuffController.clear(playerId)
        }
        
        // Remove defense buff if active
        if (defenseBuffController.isActive(playerId)) {
            applyDefenseBuff(playerId, entityRef, store, false, config)  // Use instance config for removal
            defenseBuffController.clear(playerId)
        }
        
        // Remove stamina buff if active
        if (staminaBuffController.isActive(playerId)) {
            applyStaminaBuff(playerId, entityRef, store, false, config)  // Use instance config for removal
            staminaBuffController.clear(playerId)
        }
    }
    
    // ========================================
    // HUD Integration
    // ========================================
    
    /**
     * Get list of active buff names for HUD display.
     * Returns formatted strings like "[+] Energized", "[+] Well-Fed", etc.
     */
    fun getActiveBuffNames(playerId: UUID): List<String> {
        val buffs = mutableListOf<String>()
        
        // Check each buff controller (use actual buff names from chat messages)
        if (speedBuffController.isActive(playerId)) {
            buffs.add("[+] Energized")
        }
        if (defenseBuffController.isActive(playerId)) {
            buffs.add("[+] Well-Fed")
        }
        if (staminaBuffController.isActive(playerId)) {
            buffs.add("[+] Hydrated")
        }
        
        return buffs
    }
    
    // ========================================
    // Messaging
    // ========================================
    
    private fun sendBuffMessage(playerId: UUID, buffName: String, activated: Boolean) {
        val session = CoreModule.players.getSession(playerId) ?: return
        
        // No world.execute needed - we're already on the World thread (called from tick)
        // and sendMessage() is thread-safe anyway
        try {
            val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return
            @Suppress("DEPRECATION")
            val playerRef = player.getPlayerRef() ?: return
            
            MessageFormatter.buff(playerRef, buffName, activated)
        } catch (e: Exception) {
            LoggingManager.debug(logger, "metabolism") { "Failed to send buff message to player $playerId: ${e.message}" }
        }
    }
}
