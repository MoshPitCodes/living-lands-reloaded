package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.core.SpeedManager
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.abilities.AdrenalineRushAbility
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ECS system for Combat XP awards.
 * 
 * Awards XP when a player kills an entity.
 * 
 * Event: KillFeedEvent$KillerMessage (ECS event)
 * - Triggered when a player kills another entity (mob, player, etc.)
 * - Provides damage info and target EntityRef
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * 
 * XP Calculation:
 * - Base XP: config.xpRewards.combat.baseXp (default: 10)
 * - Mob multipliers: config.xpRewards.combat.mobMultipliers (boss 5x, default 1x)
 * - Tier 1 bonus: +15% if Warrior ability unlocked
 */
class CombatXpSystem(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry,
    private val abilityEffectService: AbilityEffectService,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, KillFeedEvent.KillerMessage>(KillFeedEvent.KillerMessage::class.java) {
    
    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()
    
    /**
     * Coroutine scope for handling timed effects like Adrenaline Rush.
     * Uses SupervisorJob to prevent single failure from cancelling all jobs.
     */
    private val effectScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Track active Adrenaline Rush effects to prevent stacking.
     * Key: Player UUID, Value: Job for the timed effect removal
     */
    private val activeAdrenalineRush = ConcurrentHashMap<UUID, Job>()
    
    /**
     * Speed multiplier category for Adrenaline Rush.
     * Uses "buff:" prefix so it's treated as a temporary buff by SpeedManager.
     */
    private val ADRENALINE_RUSH_CATEGORY = "buff:adrenaline_rush"
    
    /**
     * Query to match entities - Combat XP only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }
    
    /**
     * Handle a kill event and award Combat XP.
     * 
     * Called by ECS when KillFeedEvent.KillerMessage is triggered on an entity.
     * 
     * @param index Entity index in the archetype chunk
     * @param chunk Archetype chunk containing entity data
     * @param store Entity component store
     * @param buffer Command buffer for ECS modifications
     * @param event The KillFeedEvent.KillerMessage
     */
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: KillFeedEvent.KillerMessage
    ) {
        // Get PlayerRef component from the entity that triggered this event
        val playerRef = chunk.getComponent(index, playerRefType) ?: return
        
        // Get player UUID
        val playerUuid = playerRef.uuid
        
        // Get current Combat level for ability check
        val currentLevel = professionsService.getLevel(playerUuid, Profession.COMBAT)
        
        // Calculate base XP from config
        val baseXp = config.xpRewards.combat.baseXp
        
        // Get mob multiplier (default to 1.0 if not found)
        // TODO: Detect mob type from target EntityRef
        // For now, use "default" multiplier
        val mobMultiplier = config.xpRewards.combat.mobMultipliers["default"] ?: 1.0
        
        // Calculate final XP amount
        val xpAmount = (baseXp * mobMultiplier).toLong()
        
        // Check if player has Warrior ability (Tier 1 +15% XP boost)
        val xpMultiplier = abilityRegistry.getXpMultiplier(
            playerUuid.toString(),
            Profession.COMBAT,
            currentLevel,
            config.abilities.tier1XpBoost
        )
        
        // Award XP (with multiplier if ability unlocked)
        val result = professionsService.awardXpWithMultiplier(
            playerId = playerUuid,
            profession = Profession.COMBAT,
            baseAmount = xpAmount,
            multiplier = xpMultiplier
        )

        // Log multiplier application (INFO level for visibility)
        if (xpMultiplier > 1.0) {
            logger.atFine().log("Applied Tier 1 XP boost for player ${playerUuid}: ${xpMultiplier}x multiplier (base: $xpAmount, final: ${(xpAmount * xpMultiplier).toLong()})")
        }
        
        // Notify HUD elements (panel + notification)
        com.livinglands.core.CoreModule.getModule<com.livinglands.modules.professions.ProfessionsModule>("professions")?.notifyXpGain(
            playerUuid,
            Profession.COMBAT,
            xpAmount,
            result.didLevelUp
        )
        
        // Log level-ups
        if (result.didLevelUp) {
            logger.atFine().log("Player ${playerUuid} leveled up Combat: ${result.oldLevel} → ${result.newLevel}")
        }
        
        // Debug logging
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            logger.atFine().log("Awarded $xpAmount Combat XP to player ${playerUuid} (kill)")
        }
        
        // ========== Tier 3 Ability: Adrenaline Rush ==========
        // Check if player has Adrenaline Rush ability and apply speed buff
        try {
            if (abilityEffectService.hasAdrenalineRush(playerUuid)) {
                applyAdrenalineRush(playerRef, playerUuid, store)
            }
        } catch (e: Exception) {
            logger.atWarning().log("Error applying Adrenaline Rush for player $playerUuid: ${e.message}")
        }
    }
    
    /**
     * Apply Adrenaline Rush speed buff to the player.
     * 
     * Effect: +10% movement speed for 5 seconds on kill.
     * 
     * **Thread Safety:**
     * - SpeedManager is thread-safe (uses ConcurrentHashMap)
     * - Speed application requires world thread (uses world.execute {})
     * - Timed removal uses coroutine with proper cleanup
     * 
     * **Stacking Behavior:**
     * - If already active, refreshes the duration (cancels old job, starts new)
     * - Does NOT stack the effect (still +10%, not +20%)
     * 
     * @param playerRef Player's PlayerRef component
     * @param playerId Player's UUID
     * @param store Entity store for ECS access
     */
    private fun applyAdrenalineRush(playerRef: PlayerRef, playerId: UUID, store: Store<EntityStore>) {
        val speedManager = CoreModule.services.get<SpeedManager>()
        if (speedManager == null) {
            logger.atWarning().log("Cannot apply Adrenaline Rush - SpeedManager not available")
            return
        }
        
        // Cancel existing effect if active (refresh duration)
        activeAdrenalineRush[playerId]?.cancel()
        
        // Apply speed buff (+10%)
        speedManager.setMultiplier(playerId, ADRENALINE_RUSH_CATEGORY, AdrenalineRushAbility.speedMultiplier.toFloat())
        
        // Apply the speed change to player (requires world thread)
        val worldUuid = playerRef.worldUuid ?: return
        val world = Universe.get().getWorld(worldUuid)
        if (world != null) {
            world.execute {
                try {
                    val entityRef = playerRef.reference
                    if (entityRef != null && entityRef.isValid) {
                        speedManager.applySpeed(playerId, entityRef, store)
                        logger.atFine().log("Applied Adrenaline Rush (+10% speed) to player $playerId")
                    }
                } catch (e: Exception) {
                    logger.atWarning().log("Error applying Adrenaline Rush speed for player $playerId: ${e.message}")
                }
            }
        }
        
        // Schedule removal after duration
        val job = effectScope.launch {
            delay(AdrenalineRushAbility.durationMs)
            removeAdrenalineRush(playerRef, playerId, store)
        }
        activeAdrenalineRush[playerId] = job
    }
    
    /**
     * Remove Adrenaline Rush speed buff from the player.
     * 
     * Called automatically after the duration expires, or manually on disconnect.
     * 
     * @param playerRef Player's PlayerRef component
     * @param playerId Player's UUID
     * @param store Entity store for ECS access
     */
    private fun removeAdrenalineRush(playerRef: PlayerRef, playerId: UUID, store: Store<EntityStore>) {
        val speedManager = CoreModule.services.get<SpeedManager>()
        if (speedManager == null) return
        
        // Remove the speed multiplier
        speedManager.removeMultiplier(playerId, ADRENALINE_RUSH_CATEGORY)
        
        // Remove from tracking
        activeAdrenalineRush.remove(playerId)
        
        // Apply the speed change to player (requires world thread)
        val worldUuid = playerRef.worldUuid ?: return
        val world = Universe.get().getWorld(worldUuid)
        if (world != null) {
            world.execute {
                try {
                    val entityRef = playerRef.reference
                    if (entityRef != null && entityRef.isValid) {
                        speedManager.applySpeed(playerId, entityRef, store)
                        logger.atFine().log("Removed Adrenaline Rush effect from player $playerId")
                    }
                } catch (e: Exception) {
                    logger.atFine().log("Error removing Adrenaline Rush speed for player $playerId: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Clean up Adrenaline Rush effect for a player.
     * 
     * Should be called when player disconnects to prevent memory leaks.
     * 
     * @param playerId Player's UUID
     */
    fun cleanupPlayer(playerId: UUID) {
        activeAdrenalineRush[playerId]?.cancel()
        activeAdrenalineRush.remove(playerId)
        
        // Also remove the speed multiplier if SpeedManager is available
        CoreModule.services.get<SpeedManager>()?.removeMultiplier(playerId, ADRENALINE_RUSH_CATEGORY)
    }
    
    /**
     * Shutdown the effect scope.
     * 
     * Should be called when the system is being unregistered.
     */
    fun shutdown() {
        effectScope.cancel()
        activeAdrenalineRush.clear()
    }
}
