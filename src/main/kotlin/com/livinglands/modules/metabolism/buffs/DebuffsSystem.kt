package com.livinglands.modules.metabolism.buffs

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismStats
import com.livinglands.modules.metabolism.config.DebuffsConfig
import com.livinglands.core.MessageFormatter
import com.livinglands.core.SpeedManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages metabolism debuffs with 3-stage system.
 * 
 * Uses TieredDebuffController for uniform threshold management.
 * 
 * **3-Stage System (Uniform thresholds):**
 * - Stage 1 (Mild):     ≤ 75% (exits at > 80%)
 * - Stage 2 (Moderate): ≤ 50% (exits at > 55%)
 * - Stage 3 (Severe):   ≤ 25% (exits at > 30%)
 * 
 * **Debuff Types:**
 * - **Hunger:** Peckish → Hungry → Starving (health drain)
 * - **Thirst:** Thirsty → Parched → Dehydrated (stamina drain)
 * - **Energy:** Drowsy → Tired → Exhausted (speed reduction)
 */
class DebuffsSystem(
    private val config: DebuffsConfig,
    private val speedManager: SpeedManager,
    private val logger: HytaleLogger
) {
    
    companion object {
        /** Vanilla base stamina value. TODO: Make configurable in DebuffsConfig if other mods change this */
        const val BASE_STAMINA = 10f
    }
    
    // ========================================
    // Tiered Controllers (1 per stat type)
    // ========================================
    
    private val hungerDebuffs = TieredDebuffController(
        statName = "hunger",
        tierNames = listOf("Peckish", "Hungry", "Starving")
    )
    
    private val thirstDebuffs = TieredDebuffController(
        statName = "thirst",
        tierNames = listOf("Thirsty", "Parched", "Dehydrated")
    )
    
    private val energyDebuffs = TieredDebuffController(
        statName = "energy",
        tierNames = listOf("Drowsy", "Tired", "Exhausted")
    )
    
    // ========================================
    // Effect Timing Tracking
    // ========================================
    
    private val lastHungerDamage = ConcurrentHashMap<UUID, Long>()
    
    // ========================================
    // Effect Values by Tier (index 0=none, 1=mild, 2=moderate, 3=severe)
    // ========================================
    
    private fun getHungerDamage(tier: Int, debuffsConfig: DebuffsConfig): Double = when (tier) {
        1 -> debuffsConfig.hunger.peckishDamage
        2 -> debuffsConfig.hunger.hungryDamage
        3 -> debuffsConfig.hunger.starvingDamage
        else -> 0.0
    }
    
    private fun getThirstMaxStamina(tier: Int, debuffsConfig: DebuffsConfig): Float = when (tier) {
        1 -> debuffsConfig.thirst.thirstyMaxStamina.toFloat()
        2 -> debuffsConfig.thirst.parchedMaxStamina.toFloat()
        3 -> debuffsConfig.thirst.dehydratedMaxStamina.toFloat()
        else -> 1.0f
    }
    
    private fun getEnergySpeed(tier: Int, debuffsConfig: DebuffsConfig): Float = when (tier) {
        1 -> debuffsConfig.energy.drowsySpeed.toFloat()
        2 -> debuffsConfig.energy.tiredSpeed.toFloat()
        3 -> debuffsConfig.energy.exhaustedSpeed.toFloat()
        else -> 1.0f
    }
    
    // ========================================
    // Public API
    // ========================================
    
    fun tick(
        playerId: UUID, 
        stats: MetabolismStats, 
        entityRef: Ref<EntityStore>, 
        store: Store<EntityStore>,
        debuffsConfig: DebuffsConfig
    ) {
        if (!debuffsConfig.enabled) return
        if (!entityRef.isValid) return  // Early validation to ensure consistent state across all sub-ticks
        
        tickHunger(playerId, stats.hunger.toDouble(), entityRef, store, debuffsConfig)
        tickThirst(playerId, stats.thirst.toDouble(), entityRef, store, debuffsConfig)
        tickEnergy(playerId, stats.energy.toDouble(), entityRef, store, debuffsConfig)
    }
    
    fun hasActiveDebuffs(playerId: UUID): Boolean =
        hungerDebuffs.isActive(playerId) ||
        thirstDebuffs.isActive(playerId) ||
        energyDebuffs.isActive(playerId)
    
    fun getActiveDebuffNames(playerId: UUID): List<String> = buildList {
        hungerDebuffs.getActiveDebuffName(playerId)?.let { add("[-] $it") }
        thirstDebuffs.getActiveDebuffName(playerId)?.let { add("[-] $it") }
        energyDebuffs.getActiveDebuffName(playerId)?.let { add("[-] $it") }
    }
    
    fun cleanup(playerId: UUID, entityRef: Ref<EntityStore>? = null, store: Store<EntityStore>? = null) {
        hungerDebuffs.clear(playerId)
        thirstDebuffs.clear(playerId)
        energyDebuffs.clear(playerId)
        
        lastHungerDamage.remove(playerId)
        
        speedManager.removeMultiplier(playerId, "debuff:energy")
        
        // Clean up stamina debuff modifier (prevent memory leak)
        if (entityRef != null && store != null && entityRef.isValid) {
            try {
                val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType())
                if (statMap != null) {
                    val staminaId = DefaultEntityStatTypes.getStamina()
                    statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_debuff_stamina")
                }
            } catch (e: Exception) {
                LoggingManager.debug(logger, "metabolism") { "Could not remove modifiers during cleanup for $playerId: ${e.message}" }
            }
        }
    }
    
    // ========================================
    // Unified Tick Handlers
    // ========================================
    
    private fun tickHunger(playerId: UUID, hungerValue: Double, entityRef: Ref<EntityStore>, store: Store<EntityStore>, debuffsConfig: DebuffsConfig) {
        if (!debuffsConfig.hunger.enabled) return
        
        val result = hungerDebuffs.update(playerId, hungerValue)
        
        // Send transition messages
        result.messages.forEach { (name, activated) ->
            sendDebuffMessage(playerId, name, activated)
        }
        
        // Apply effect for current tier
        if (result.currentTier > 0) {
            // Remove any health buff first to prevent confusion
            removeHealthBuff(entityRef, store)
            
            applyTimedEffect(
                playerId = playerId,
                trackingMap = lastHungerDamage,
                intervalMs = debuffsConfig.hunger.damageIntervalMs
            ) {
                applyHealthDrain(entityRef, store, getHungerDamage(result.currentTier, debuffsConfig))
            }
        } else {
            lastHungerDamage.remove(playerId)
        }
    }
    
    private fun tickThirst(playerId: UUID, thirstValue: Double, entityRef: Ref<EntityStore>, store: Store<EntityStore>, debuffsConfig: DebuffsConfig) {
        if (!debuffsConfig.thirst.enabled) return
        
        val result = thirstDebuffs.update(playerId, thirstValue)
        
        result.messages.forEach { (name, activated) ->
            sendDebuffMessage(playerId, name, activated)
        }
        
        // Apply max stamina modifier (same pattern as BuffsSystem)
        if (result.currentTier > 0) {
            // Remove any stamina buff first to prevent stacking
            removeStaminaBuff(entityRef, store)
            applyMaxStaminaDebuff(entityRef, store, getThirstMaxStamina(result.currentTier, debuffsConfig))
        } else {
            removeMaxStaminaDebuff(entityRef, store)
        }
    }
    
    private fun tickEnergy(playerId: UUID, energyValue: Double, entityRef: Ref<EntityStore>, store: Store<EntityStore>, debuffsConfig: DebuffsConfig) {
        if (!debuffsConfig.energy.enabled) return
        
        val result = energyDebuffs.update(playerId, energyValue)
        
        result.messages.forEach { (name, activated) ->
            sendDebuffMessage(playerId, name, activated)
        }
        
        if (result.currentTier > 0) {
            // Remove any speed buff first (SpeedManager handles this, but be explicit)
            speedManager.removeMultiplier(playerId, "buff:speed")
            
            val speed = getEnergySpeed(result.currentTier, debuffsConfig)
            speedManager.setMultiplier(playerId, "debuff:energy", speed)
            speedManager.applySpeed(playerId, entityRef, store)
        } else {
            speedManager.removeMultiplier(playerId, "debuff:energy")
            speedManager.applySpeed(playerId, entityRef, store)
        }
    }
    
    // ========================================
    // Effect Application Helpers
    // ========================================
    
    private inline fun applyTimedEffect(
        playerId: UUID,
        trackingMap: ConcurrentHashMap<UUID, Long>,
        intervalMs: Long,
        effect: () -> Unit
    ) {
        val now = System.currentTimeMillis()
        val lastTime = trackingMap[playerId] ?: 0L
        
        if (now - lastTime >= intervalMs) {
            effect()
            trackingMap[playerId] = now
        }
    }
    
    private fun applyHealthDrain(entityRef: Ref<EntityStore>, store: Store<EntityStore>, damage: Double) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            statMap.subtractStatValue(DefaultEntityStatTypes.getHealth(), damage.toFloat())
        } catch (e: Exception) {
            LoggingManager.debug(logger, "metabolism") { "Failed to apply health drain: ${e.message}" }
        }
    }
    
    private fun applyMaxStaminaDebuff(entityRef: Ref<EntityStore>, store: Store<EntityStore>, multiplier: Float) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val staminaId = DefaultEntityStatTypes.getStamina()
            
            // Get current max stamina before modifier (for debugging)
            val statValue = statMap.get(staminaId)
            val currentMax = statValue?.getMax() ?: 0f
            val currentValue = statValue?.get() ?: 0f
            
            // Use constant base stamina (vanilla Hytale value)
            // Note: If other mods change base stamina, make this configurable in DebuffsConfig
            val baseStamina = BASE_STAMINA
            
            // Calculate additive modifier amount
            // multiplier = 0.40 (want 40% of base) → target = base * 0.40, modifier = target - base
            // multiplier = 0.65 (want 65% of base) → target = base * 0.65, modifier = target - base  
            // multiplier = 0.85 (want 85% of base) → target = base * 0.85, modifier = target - base
            val targetMax = baseStamina * multiplier
            val additiveModifier = targetMax - baseStamina  // e.g., (10 * 0.85) - 10 = 8.5 - 10 = -1.5
            
            // Apply max stamina reduction using StaticModifier with ADDITIVE calculation
            val modifier = com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier(
                com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget.MAX,
                com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType.ADDITIVE,
                additiveModifier
            )
            
            // Check if there's already a modifier with this key
            val existingModifier = statMap.get(staminaId)?.getModifier("livinglands_debuff_stamina")
            
            // Remove any buff modifier first to ensure clean state (prevents stacking during transitions)
            statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_buff_stamina")
            
            // Use SELF predictable to ensure client receives the stat update
            statMap.putModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_debuff_stamina", modifier)
            
            LoggingManager.debug(logger, "metabolism") { "[DEBUFF] Applying stamina debuff (had existing=${existingModifier != null})" }
            
            // Get new max stamina after modifier (for debugging)
            val newStatValue = statMap.get(staminaId)
            val newMax = newStatValue?.getMax() ?: 0f
            val newValue = newStatValue?.get() ?: 0f
            
            LoggingManager.debug(logger, "metabolism") { "[DEBUFF] Applied stamina debuff: targetPercent=$multiplier, baseStamina=$baseStamina, additive=$additiveModifier, before=$currentValue/$currentMax, after=$newValue/$newMax" }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "metabolism") { "Failed to apply max stamina debuff: ${e.message}" }
            e.printStackTrace()
        }
    }
    
    private fun removeMaxStaminaDebuff(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val staminaId = DefaultEntityStatTypes.getStamina()
            
            // Get stamina before removal (for debugging)
            val beforeValue = statMap.get(staminaId)
            val beforeCurrent = beforeValue?.get() ?: 0f
            val beforeMax = beforeValue?.getMax() ?: 0f
            
            // Remove stamina debuff modifier
            statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_debuff_stamina")
            
            // Get stamina after removal (for debugging)
            val afterValue = statMap.get(staminaId)
            val afterCurrent = afterValue?.get() ?: 0f
            val afterMax = afterValue?.getMax() ?: 0f
            
            LoggingManager.debug(logger, "metabolism") { "[DEBUFF] Removed stamina debuff: before=$beforeCurrent/$beforeMax, after=$afterCurrent/$afterMax" }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "metabolism") { "Failed to remove max stamina debuff: ${e.message}" }
        }
    }
    
    private fun removeStaminaBuff(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val staminaId = DefaultEntityStatTypes.getStamina()
            
            // Remove stamina buff modifier (to prevent stacking with debuff)
            statMap.removeModifier(EntityStatMap.Predictable.SELF, staminaId, "livinglands_buff_stamina")
        } catch (e: Exception) {
            LoggingManager.debug(logger, "metabolism") { "Failed to remove stamina buff: ${e.message}" }
        }
    }
    
    private fun removeHealthBuff(entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        if (!entityRef.isValid) return
        
        try {
            val statMap = store.getComponent(entityRef, EntityStatMap.getComponentType()) ?: return
            val healthId = DefaultEntityStatTypes.getHealth()
            
            // Remove health buff modifier (to prevent confusion with health drain debuff)
            statMap.removeModifier(EntityStatMap.Predictable.SELF, healthId, "livinglands_buff_health")
        } catch (e: Exception) {
            LoggingManager.debug(logger, "metabolism") { "Failed to remove health buff: ${e.message}" }
        }
    }
    
    private fun sendDebuffMessage(playerId: UUID, debuffName: String, activated: Boolean) {
        val session = CoreModule.players.getSession(playerId) ?: return
        
        // No world.execute needed - we're already on the World thread (called from tick)
        // and sendMessage() is thread-safe anyway
        try {
            val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return
            @Suppress("DEPRECATION")
            val playerRef = player.getPlayerRef() ?: return
            
            MessageFormatter.debuff(playerRef, debuffName, activated)
        } catch (e: Exception) {
            LoggingManager.debug(logger, "metabolism") { "Failed to send debuff message to $playerId: ${e.message}" }
        }
    }
}
