package com.livinglands.modules.metabolism.food

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent
import com.livinglands.core.PlayerSession
import com.livinglands.modules.metabolism.food.modded.ItemTierDetector
import com.livinglands.modules.metabolism.food.modded.ModdedConsumablesRegistry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects food consumption by monitoring entity effects.
 * 
 * This detector tracks active effects on players and identifies when new food-related
 * effects are applied, which indicates food consumption.
 * 
 * **Detection Strategy:**
 * - Monitors EffectControllerComponent for new active effects
 * - Compares current effect indexes with previous tick
 * - Checks modded consumables registry FIRST (if enabled)
 * - Falls back to vanilla detection patterns
 * - Identifies new food/consumable effects by ID prefix
 * - Prevents duplicate detection with processed effect tracking
 * 
 * **Performance:**
 * - O(n) per player where n = active effect count (typically 1-5)
 * - Batched processing (10 players per tick)
 * - Cleanup every 6 ticks (200ms) to prevent memory buildup
 * 
 * **Thread Safety:**
 * - All ECS access wrapped in `world.execute { }`
 * - Concurrent collections for cross-tick state
 * 
 * @property logger Logger for debugging
 * @property moddedRegistry Optional registry for modded consumables (null = vanilla only)
 */
class FoodEffectDetector(
    private val logger: HytaleLogger,
    private val moddedRegistry: ModdedConsumablesRegistry? = null
) {
    /**
     * Tracks effect indexes from previous tick for each player.
     * Used to detect new effects by comparing with current tick.
     */
    private val previousEffects = ConcurrentHashMap<UUID, Set<Int>>()
    
    /**
     * Tracks recently processed effect indexes to prevent duplicate detection.
     * 
     * Key: Player UUID
     * Value: Map of effect index -> timestamp when processed
     * 
     * Effects are removed from this map when:
     * 1. The effect is no longer active on the player
     * 2. Cleanup runs and effect hasn't been seen in 5 seconds
     */
    private val processedEffectIndexes = ConcurrentHashMap<UUID, MutableMap<Int, Long>>()
    
    /**
     * Timestamp of last cleanup operation (milliseconds).
     */
    @Volatile
    private var lastCleanupTime = System.currentTimeMillis()
    
    /**
     * Interval between cleanup operations (5 seconds).
     */
    private val cleanupIntervalMs = 5000L
    
    /**
     * How long to remember a processed effect (2 seconds).
     * After this time, the same effect can be detected again.
     * Reduced from 5s since previousEffects handles long-term deduplication.
     */
    private val processedEffectTTL = 2000L
    
    /**
     * Check for new food/consumable effects on a player.
     * 
     * This method compares the current active effects with the previous tick's effects
     * to identify newly applied effects. It then filters for food/consumable effects
     * and returns the detected consumptions.
     * 
     * **IMPORTANT:** This method accesses ECS components and MUST be called from
     * within a `world.execute { }` block to ensure WorldThread compliance.
     * 
     * @param playerId The player's UUID
     * @param session The player's session (contains world, entityRef, store)
     * @return List of detected food consumptions (may be empty)
     */
    fun checkForNewFoodEffects(playerId: UUID, session: PlayerSession): List<FoodDetection> {
        // Get effect controller component (must be on world thread)
        val effectController = try {
            session.store.getComponent(session.entityRef, EffectControllerComponent.getComponentType())
        } catch (e: Exception) {
            logger.atFine().log("Failed to get EffectControllerComponent for player $playerId: ${e.message}")
            return emptyList()
        }
        
        // Get all active effects (may be null)
        val activeEffects = effectController?.getAllActiveEntityEffects()
        if (activeEffects == null || activeEffects.isEmpty()) {
            // No effects active - update tracking and return
            previousEffects[playerId] = emptySet()
            return emptyList()
        }
        
        // Extract current effect indexes
        val currentIndexes = activeEffects.map { it.getEntityEffectIndex() }.toSet()
        
        // Get previous indexes for comparison
        val prevIndexes = previousEffects[playerId] ?: emptySet()
        
        // Get processed effect map
        val processed = processedEffectIndexes.getOrPut(playerId) { ConcurrentHashMap() }
        
        // Remove processed effects that are no longer active
        // This allows re-detection when effects expire and are reapplied
        val processedKeys = processed.keys.toList()
        for (index in processedKeys) {
            if (index !in currentIndexes) {
                processed.remove(index)
            }
        }
        
        // Find new effect indexes (current - previous)
        // We only subtract processed.keys from the list of effects we actually check,
        // not from the "new" detection. This prevents missing re-applied effects.
        val newIndexes = currentIndexes - prevIndexes
        
        // Update tracking for next tick
        previousEffects[playerId] = currentIndexes
        
        // No new effects - return early
        if (newIndexes.isEmpty()) {
            return emptyList()
        }
        
        // Check each new effect to see if it's a food/consumable
        val detections = mutableListOf<FoodDetection>()
        
        for (effect in activeEffects) {
            val index = effect.getEntityEffectIndex()
            
            // Skip if not a new effect
            if (index !in newIndexes) {
                continue
            }
            
            // Get effect ID using the asset map lookup by index
            // This is the v2.6.0 pattern since entityEffectId field is protected
            val effectId = try {
                val assetMap = EntityEffect.getAssetMap()
                val entityEffect = assetMap.getAsset(index)
                entityEffect?.id
            } catch (e: Exception) {
                logger.atFine().log("Failed to get effect ID for index $index: ${e.message}")
                null
            }
            
            if (effectId == null || effectId.isBlank()) continue
            
            // Try to detect consumable: modded registry first, then vanilla patterns
            val detection = detectFoodType(effectId)
            if (detection != null) {
                detections.add(detection)
                
                // Log if this is a re-detection (useful for debugging)
                if (index in processed) {
                    logger.atFine().log("Re-detected effect index $index: $effectId (same food consumed again)")
                }
                
                // Mark as processed with current timestamp to prevent duplicate detection
                processed[index] = System.currentTimeMillis()
                
                val source = if (moddedRegistry?.findByEffectId(effectId) != null) "modded" else "vanilla"
                logger.atFine().log("Detected food consumption [$source]: $effectId (tier ${detection.tier}, type ${detection.foodType})")
            }
        }
        
        // Periodic cleanup of old processed effects
        cleanupIfNeeded()
        
        return detections
    }
    
    /**
     * Clean up old processed effect indexes periodically.
     * 
     * Removes effects that haven't been seen for processedEffectTTL milliseconds.
     * This allows rapid consecutive consumption while preventing duplicates.
     * 
     * Also removes empty player effect maps to prevent memory leaks.
     */
    private fun cleanupIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCleanupTime >= cleanupIntervalMs) {
            // Remove effects older than TTL
            val playersToRemove = mutableListOf<UUID>()
            
            for ((playerId, playerEffects) in processedEffectIndexes) {
                // Remove expired effect indexes
                val toRemove = mutableListOf<Int>()
                for ((index, timestamp) in playerEffects) {
                    if (now - timestamp > processedEffectTTL) {
                        toRemove.add(index)
                    }
                }
                toRemove.forEach { playerEffects.remove(it) }
                
                // Mark player for removal if they have no active tracked effects
                if (playerEffects.isEmpty()) {
                    playersToRemove.add(playerId)
                }
            }
            
            // Remove players with no tracked effects
            playersToRemove.forEach { playerId ->
                processedEffectIndexes.remove(playerId)
            }
            
            // Also clean up previousEffects map for players with no effects
            // This prevents memory leaks from offline players
            val previousToRemove = mutableListOf<UUID>()
            for ((playerId, effects) in previousEffects) {
                if (effects.isEmpty() && !processedEffectIndexes.containsKey(playerId)) {
                    previousToRemove.add(playerId)
                }
            }
            previousToRemove.forEach { playerId ->
                previousEffects.remove(playerId)
            }
            
            lastCleanupTime = now
            
            // Log cleanup stats if significant cleanup occurred
            if (playersToRemove.size > 0 || previousToRemove.size > 0) {
                logger.atFine().log(
                    "FoodEffectDetector cleanup: removed ${playersToRemove.size} processed maps, " +
                    "${previousToRemove.size} previous effect maps"
                )
            }
        }
    }
    
    /**
     * Initialize tracking for a player who just joined.
     * 
     * This prevents false detections of pre-existing effects (e.g., buffs from previous session)
     * by marking all currently active effects as "already seen".
     * 
     * **IMPORTANT:** This method accesses ECS components and MUST be called from
     * within a `world.execute { }` block to ensure WorldThread compliance.
     * 
     * @param playerId The player's UUID
     * @param session The player's session (contains world, entityRef, store)
     */
    fun initializePlayer(playerId: UUID, session: PlayerSession) {
        // Get effect controller component (must be on world thread)
        val effectController = try {
            session.store.getComponent(session.entityRef, EffectControllerComponent.getComponentType())
        } catch (e: Exception) {
            logger.atFine().log("Failed to get EffectControllerComponent for player $playerId during init: ${e.message}")
            return
        }
        
        // Get all currently active effects
        val activeEffects = effectController?.getAllActiveEntityEffects()
        if (activeEffects != null && activeEffects.isNotEmpty()) {
            // Extract current effect indexes and mark them as "previous"
            // This prevents them from being detected as new consumptions
            val currentIndexes = activeEffects.map { it.getEntityEffectIndex() }.toSet()
            previousEffects[playerId] = currentIndexes
            
            logger.atFine().log("Initialized food detection for player $playerId with ${currentIndexes.size} existing effects")
        } else {
            // No active effects, initialize with empty set
            previousEffects[playerId] = emptySet()
            logger.atFine().log("Initialized food detection for player $playerId with no existing effects")
        }
    }
    
    /**
     * Remove a player from tracking when they disconnect.
     * 
     * @param playerId The player's UUID
     */
    fun removePlayer(playerId: UUID) {
        previousEffects.remove(playerId)
        processedEffectIndexes.remove(playerId)
    }
    
    /**
     * Alias for removePlayer() for consistency with other systems.
     * 
     * @param playerId The player's UUID
     */
    fun cleanup(playerId: UUID) {
        removePlayer(playerId)
    }
    
    /**
     * Detect food type from effect ID.
     * 
     * Checks modded consumables registry FIRST (if enabled),
     * then falls back to vanilla detection patterns.
     * 
     * @param effectId The effect ID to analyze
     * @return FoodDetection with type and tier, or null if not a consumable
     */
    private fun detectFoodType(effectId: String): FoodDetection? {
        // Check modded registry first (if enabled)
        if (moddedRegistry != null && moddedRegistry.isEnabled()) {
            val moddedEntry = moddedRegistry.findByEffectId(effectId)
            if (moddedEntry != null) {
                // Use category from modded config
                val foodType = mapCategoryToFoodType(moddedEntry.category)
                
                // Use tier from config (already validated, non-null with default of 1)
                val tier = moddedEntry.tier
                
                return FoodDetection(effectId, foodType, tier)
            }
        }
        
        // Fallback to vanilla detection
        return FoodDetectionUtils.parseEffect(effectId)
    }
    
    /**
     * Map a modded consumable category string to a FoodType enum.
     * 
     * @param category The category string from config
     * @return The corresponding FoodType
     */
    private fun mapCategoryToFoodType(category: String): FoodType {
        return when (category.uppercase()) {
            "MEAT" -> FoodType.MEAT
            "FRUIT_VEGGIE" -> FoodType.FRUIT_VEGGIE
            "BREAD" -> FoodType.BREAD
            "WATER" -> FoodType.WATER
            "MILK" -> FoodType.MILK
            "INSTANT_HEAL" -> FoodType.INSTANT_HEAL
            "HEALTH_REGEN" -> FoodType.HEALTH_REGEN
            "HEALTH_BOOST" -> FoodType.HEALTH_BOOST
            "STAMINA_BOOST" -> FoodType.STAMINA_BOOST
            "HEALTH_POTION" -> FoodType.HEALTH_POTION
            "MANA_POTION" -> FoodType.MANA_POTION
            "STAMINA_POTION" -> FoodType.STAMINA_POTION
            "GENERIC" -> FoodType.GENERIC
            else -> FoodType.GENERIC
        }
    }
}
