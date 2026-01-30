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
import kotlinx.coroutines.runBlocking
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
 * 1. Resets metabolism stats to defaults (100/100/100)
 * 2. Removes all active debuffs (health drain, stamina reduction, speed penalty)
 * 3. Removes all active buffs (to avoid unfair advantage)
 * 4. Updates the HUD to show reset values
 * 5. Persists the reset to the global database
 * 
 * **ARCHITECTURE CHANGE:** Now uses global repository instead of per-world.
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
     */
    private fun handleRespawn(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        logger.atInfo().log("Player respawned: $playerId - resetting metabolism")
        
        try {
            // Reset metabolism stats in global database (blocking - must complete before player continues)
            runBlocking {
                metabolismService.resetStats(playerId, metabolismRepository)
            }
            
            // Trigger death penalty for professions
            CoreModule.getModule<ProfessionsModule>("professions")?.handlePlayerRespawn(playerId)
            
            // Cleanup buffs and debuffs (remove all active effects)
            debuffsSystem?.cleanup(playerId, entityRef, store)
            buffsSystem?.cleanup(playerId, entityRef, store)
            
            // Force HUD update to show reset values
            metabolismService.forceUpdateHud(playerId.toCachedString(), playerId)
            
            logger.atInfo().log("Metabolism reset complete for player $playerId")
            
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
