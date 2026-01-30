package com.livinglands.modules.metabolism

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.core.toCachedString
import com.livinglands.modules.metabolism.buffs.BuffsSystem
import com.livinglands.modules.metabolism.buffs.DebuffsSystem
import com.livinglands.modules.professions.ProfessionsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ECS system that detects player respawns and resets metabolism stats.
 * 
 * This system tracks whether players have the DeathComponent:
 * - When DeathComponent is present: player is dead, mark them as "was dead"
 * - When DeathComponent is removed: player respawned, trigger metabolism reset
 * 
 * On respawn, this system:
 * 1. Resets metabolism stats to defaults (100/100/100) - immediately in memory
 * 2. Removes all active debuffs (health drain, stamina reduction, speed penalty)
 * 3. Removes all active buffs (to avoid unfair advantage)
 * 4. Updates the HUD to show reset values
 * 5. Persists the reset to the global database asynchronously (non-blocking)
 * 
 * **PERFORMANCE:** Uses optimistic reset pattern - memory update is immediate,
 * database persistence is async to avoid blocking the WorldThread during ECS tick.
 */
class RespawnResetSystem(
    private val metabolismService: MetabolismService,
    private val metabolismRepository: MetabolismRepository,
    private val debuffsSystem: DebuffsSystem?,
    private val buffsSystem: BuffsSystem?,
    private val logger: HytaleLogger
) : EntityTickingSystem<EntityStore>() {
    
    /**
     * Track which players were dead in the previous tick.
     * When a player transitions from dead -> alive, we trigger reset.
     */
    private val wasDeadLastTick = ConcurrentHashMap<UUID, Boolean>()
    
    /**
     * Coroutine scope for async persistence operations.
     * Uses IO dispatcher for database operations.
     */
    private val persistenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Query for Player entities.
     */
    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }
    
    /**
     * Called every tick for each player entity.
     * Detects death -> alive transitions and triggers metabolism reset.
     */
    override fun tick(
        deltaTime: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val ref: Ref<EntityStore> = chunk.getReferenceTo(index)
        if (!ref.isValid) return
        
        // Get Player component
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        
        // Get player UUID
        @Suppress("DEPRECATION")
        val playerId = player.uuid ?: return
        
        // Check if player has DeathComponent (is currently dead)
        val deathComponent = store.getComponent(ref, DeathComponent.getComponentType())
        val isDead = deathComponent != null
        
        // Get previous state
        val wasDead = wasDeadLastTick[playerId] ?: false
        
        // Update current state for next tick
        wasDeadLastTick[playerId] = isDead
        
        // Detect respawn: was dead last tick, now alive
        if (wasDead && !isDead) {
            handleRespawn(playerId, ref, store)
        }
    }
    
    /**
     * Handle player respawn - reset metabolism and cleanup effects.
     * 
     * Uses optimistic reset pattern:
     * 1. Immediate in-memory reset (synchronous, fast)
     * 2. Async database persistence (non-blocking)
     * 3. Immediate cleanup of buffs/debuffs
     * 4. Immediate HUD update
     * 
     * This ensures no WorldThread blocking during ECS tick.
     */
    private fun handleRespawn(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        logger.atInfo().log("Player respawned: $playerId - resetting metabolism")
        
        try {
            // 1. Immediate in-memory reset (synchronous, no blocking)
            // This updates the cache immediately so player sees reset stats
            val resetSuccess = metabolismService.resetStatsInMemory(playerId)
            
            if (!resetSuccess) {
                logger.atWarning().log("Player $playerId not in cache during respawn reset - skipping")
                return
            }
            
            // 2. Async database persistence (fire-and-forget, non-blocking)
            // We don't wait for this to complete - it happens in background
            persistenceScope.launch {
                try {
                    val state = metabolismService.getState(playerId.toCachedString())
                    if (state != null) {
                        val stats = state.toImmutableStats()
                        metabolismRepository.updateStats(stats)
                        logger.atFine().log("Persisted respawn reset for player $playerId")
                    }
                } catch (e: Exception) {
                    logger.atWarning().withCause(e).log("Failed to persist respawn reset for $playerId")
                }
            }
            
            // 3. Trigger death penalty for professions (async)
            CoreModule.getModule<ProfessionsModule>("professions")?.handlePlayerRespawn(playerId)
            
            // 4. Cleanup buffs and debuffs immediately (synchronous)
            debuffsSystem?.cleanup(playerId, entityRef, store)
            buffsSystem?.cleanup(playerId, entityRef, store)
            
            // 5. Force HUD update immediately (synchronous)
            metabolismService.forceUpdateHud(playerId.toCachedString(), playerId)
            
            logger.atInfo().log("Metabolism reset complete for player $playerId (persisting async)")
            
        } catch (e: Exception) {
            logger.atSevere().withCause(e)
                .log("Failed to reset metabolism on respawn for $playerId")
        }
    }
    
    /**
     * Clean up tracking for a player (called on disconnect).
     */
    fun cleanup(playerId: UUID) {
        wasDeadLastTick.remove(playerId)
    }
}
