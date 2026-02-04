package com.livinglands.core

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized manager for player movement speed modifications.
 * 
 * **Problem Solved:**
 * Multiple systems (BuffsSystem, DebuffsSystem, abilities) were independently modifying
 * player speed, causing race conditions and flickering. SpeedManager provides a single
 * source of truth for speed calculations.
 * 
 * **Architecture:**
 * ```
 * SpeedManager.setMultiplier(playerId, "debuff:thirst", 0.6f)
 * SpeedManager.setMultiplier(playerId, "debuff:energy", 0.8f)
 * SpeedManager.setMultiplier(playerId, "buff:speed", 1.15f)
 * 
 * Combined = 0.6 * 0.8 * 1.15 = 0.552 (55.2% of original speed)
 * ```
 * 
 * **Categories (applied in order):**
 * 1. Debuff multipliers (always applied, reduce speed)
 * 2. Permanent buff multipliers (always applied, increase speed)
 * 3. Temporary buff multipliers (suppressed if heavily debuffed, increase speed)
 * 
 * @property logger Logger for debugging
 */
class SpeedManager(private val logger: HytaleLogger) {
    
    /**
     * Original base speed for each player (captured on first modification).
     */
    private val originalBaseSpeeds = ConcurrentHashMap<UUID, Float>()
    
    /**
     * Active speed multipliers by category.
     * Key: playerId -> category -> multiplier
     */
    private val multipliers = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Float>>()
    
    /**
     * Last applied combined multiplier (to avoid redundant updates).
     */
    private val lastAppliedMultipliers = ConcurrentHashMap<UUID, Float>()
    
    /**
     * Threshold below which temporary buffs are suppressed.
     * If combined debuff multiplier < 0.5, don't apply temporary buffs.
     */
    private val TEMP_BUFF_SUPPRESSION_THRESHOLD = 0.5f
    
    /**
     * Minimum change threshold to trigger an update (1% difference).
     */
    private val UPDATE_THRESHOLD = 0.01f
    
    /**
     * Set a speed multiplier for a specific category.
     * 
     * **Categories:**
     * - `debuff:thirst`, `debuff:energy` - Debuffs (always applied)
     * - `buff:permanent` - Permanent buffs from abilities (always applied)
     * - `buff:speed`, `buff:ability` - Temporary buffs (suppressed if heavily debuffed)
     * 
     * @param playerId Player UUID
     * @param category Multiplier category (e.g., "debuff:thirst", "buff:speed")
     * @param multiplier Speed multiplier (1.0 = normal, <1.0 = slower, >1.0 = faster)
     */
    fun setMultiplier(playerId: UUID, category: String, multiplier: Float) {
        val playerMultipliers = multipliers.computeIfAbsent(playerId) { ConcurrentHashMap() }
        playerMultipliers[category] = multiplier
    }
    
    /**
     * Remove a speed multiplier category.
     * 
     * @param playerId Player UUID
     * @param category Multiplier category to remove
     */
    fun removeMultiplier(playerId: UUID, category: String) {
        multipliers[playerId]?.remove(category)
    }
    
    /**
     * Calculate combined speed multiplier for a player.
     * 
     * **Algorithm:**
     * 1. Apply all debuff multipliers (multiplicative)
     * 2. Apply all permanent buff multipliers (multiplicative)
     * 3. If not heavily debuffed (>= 0.5x), apply temporary buff multipliers
     * 
     * @param playerId Player UUID
     * @return Combined multiplier
     */
    fun getCombinedMultiplier(playerId: UUID): Float {
        val playerMultipliers = multipliers[playerId] ?: return 1.0f
        
        var combined = 1.0f
        
        // 1. Apply debuffs (always applied, reduce speed)
        for ((category, multiplier) in playerMultipliers) {
            if (category.startsWith("debuff:")) {
                combined *= multiplier
            }
        }
        
        // 2. Apply permanent buffs (always applied, increase speed)
        for ((category, multiplier) in playerMultipliers) {
            if (category.startsWith("buff:permanent")) {
                combined *= multiplier
            }
        }
        
        // 3. Apply temporary buffs (only if not heavily debuffed)
        if (combined >= TEMP_BUFF_SUPPRESSION_THRESHOLD) {
            for ((category, multiplier) in playerMultipliers) {
                if (category.startsWith("buff:") && !category.startsWith("buff:permanent")) {
                    combined *= multiplier
                }
            }
        }
        
        return combined
    }
    
    /**
     * Apply speed modifications to a player's MovementManager.
     * 
     * **Important:** Must be called from world thread (use world.execute { }).
     * 
     * After modifying the settings, we must call `movementManager.update(packetHandler)`
     * to sync the movement settings to the client. Without this call, the server-side
     * speed change won't be visible to the player.
     * 
     * @param playerId Player UUID
     * @param entityRef Player's entity reference
     * @param store Entity store
     */
    fun applySpeed(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        try {
            if (!entityRef.isValid) return
            
            val player = store.getComponent(entityRef, Player.getComponentType()) ?: return
            val movementManager = store.getComponent(entityRef, MovementManager.getComponentType()) ?: return
            val settings = movementManager.getSettings()
            
            // Capture original speed on first modification
            val originalSpeed = originalBaseSpeeds.putIfAbsent(playerId, settings.baseSpeed)
                ?: settings.baseSpeed
            
            // Calculate combined multiplier
            val combinedMultiplier = getCombinedMultiplier(playerId)
            
            // Check if update is needed (> 1% change)
            val lastApplied = lastAppliedMultipliers[playerId]
            if (lastApplied != null && Math.abs(lastApplied - combinedMultiplier) < UPDATE_THRESHOLD) {
                return // No significant change
            }
            
            // Apply new speed
            val newSpeed = originalSpeed * combinedMultiplier
            settings.baseSpeed = newSpeed
            
            // CRITICAL: Sync movement settings to the client!
            // Without this call, the speed change only exists server-side and
            // the player won't see or feel any difference in movement speed.
            val playerRef = store.getComponent(entityRef, PlayerRef.getComponentType())
            val packetHandler = playerRef?.getPacketHandler()
            if (packetHandler != null) {
                movementManager.update(packetHandler)
            }
            
            // Track last applied multiplier
            lastAppliedMultipliers[playerId] = combinedMultiplier
            
            LoggingManager.debug(logger, "core") { "Applied speed to player $playerId: original=${"%.2f".format(originalSpeed)}, multiplier=${"%.2f".format(combinedMultiplier)}, new=${"%.2f".format(newSpeed)}" }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "core") { "Failed to apply speed for player $playerId: ${e.message}" }
        }
    }
    
    /**
     * Restore a player's original speed and clear all multipliers.
     * 
     * **Important:** Must be called from world thread (use world.execute { }).
     * 
     * @param playerId Player UUID
     * @param entityRef Player's entity reference
     * @param store Entity store
     */
    fun restoreOriginalSpeed(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
        try {
            if (!entityRef.isValid) return
            
            val originalSpeed = originalBaseSpeeds.remove(playerId) ?: return
            val player = store.getComponent(entityRef, Player.getComponentType()) ?: return
            val movementManager = store.getComponent(entityRef, MovementManager.getComponentType()) ?: return
            val settings = movementManager.getSettings()
            
            settings.baseSpeed = originalSpeed
            
            // CRITICAL: Sync movement settings to the client!
            val playerRef = store.getComponent(entityRef, PlayerRef.getComponentType())
            val packetHandler = playerRef?.getPacketHandler()
            if (packetHandler != null) {
                movementManager.update(packetHandler)
            }
            
            // Clear tracking
            multipliers.remove(playerId)
            lastAppliedMultipliers.remove(playerId)
            
            LoggingManager.debug(logger, "core") { "Restored original speed for player $playerId: $originalSpeed" }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "core") { "Failed to restore speed for player $playerId: ${e.message}" }
        }
    }
    
    /**
     * Clean up tracking for a player (e.g., on disconnect).
     * Does NOT modify player speed, just clears internal state.
     * 
     * @param playerId Player UUID
     */
    fun cleanup(playerId: UUID) {
        originalBaseSpeeds.remove(playerId)
        multipliers.remove(playerId)
        lastAppliedMultipliers.remove(playerId)
    }
}
