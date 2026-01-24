package com.livinglands.modules.metabolism

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule
import com.livinglands.core.WorldContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.livinglands.modules.metabolism.hud.MetabolismHudElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Service for managing metabolism stats across all players.
 * 
 * Maintains an in-memory cache of stats keyed by player UUID.
 * Stats are loaded from the database on player join and saved on disconnect/shutdown.
 * 
 * Thread-safe using ConcurrentHashMap for the stats cache.
 */
class MetabolismService(
    private var config: MetabolismConfig,
    private val logger: HytaleLogger
) {
    
    /**
     * In-memory cache of player metabolism stats.
     * Key: Player UUID as string
     * Value: Current metabolism stats
     */
    private val statsCache = ConcurrentHashMap<String, MetabolismStats>()
    
    /**
     * Track last depletion time per player for accurate delta calculations.
     * Key: Player UUID as string
     * Value: Timestamp of last depletion tick
     */
    private val lastDepletionTime = ConcurrentHashMap<String, Long>()
    
    /**
     * Track last displayed stats for threshold-based HUD updates.
     * Key: Player UUID as string
     * Value: Last stats that were sent to HUD
     */
    private val lastDisplayedStats = ConcurrentHashMap<String, MetabolismStats>()
    
    /**
     * Track HUD elements for each player.
     * Key: Player UUID as string
     * Value: The player's MetabolismHudElement
     */
    private val hudElements = ConcurrentHashMap<String, MetabolismHudElement>()
    
    /**
     * Coroutine scope for async database operations.
     */
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Update configuration (e.g., after hot-reload).
     */
    fun updateConfig(newConfig: MetabolismConfig) {
        config = newConfig
        logger.atFine().log("Metabolism config updated")
    }
    
    /**
     * Get current config (for read access).
     */
    fun getConfig(): MetabolismConfig = config
    
    /**
     * Initialize stats for a player from the database.
     * Called when a player joins/becomes ready.
     * 
     * @param playerId Player's UUID
     * @param worldContext The world context for database access
     */
    suspend fun initializePlayer(playerId: UUID, worldContext: WorldContext) {
        val playerIdStr = playerId.toString()
        
        // Get or create repository from world context
        val repository = worldContext.getData { 
            MetabolismRepository(worldContext.persistence, logger).also {
                // Initialize schema in a coroutine
                scope.launch { it.initialize() }
            }
        }
        
        // Load or create stats
        val stats = repository.ensureStats(playerIdStr)
        
        // Cache the stats
        statsCache[playerIdStr] = stats
        lastDepletionTime[playerIdStr] = System.currentTimeMillis()
        
        logger.atFine().log("Initialized metabolism for player $playerId: H=${stats.hunger}, T=${stats.thirst}, E=${stats.energy}")
    }
    
    /**
     * Get cached stats for a player.
     * Returns null if player is not in cache (not initialized).
     * 
     * @param playerId Player's UUID as string
     * @return Cached stats or null
     */
    fun getStats(playerId: String): MetabolismStats? {
        return statsCache[playerId]
    }
    
    /**
     * Get cached stats for a player by UUID.
     */
    fun getStats(playerId: UUID): MetabolismStats? {
        return statsCache[playerId.toString()]
    }
    
    /**
     * Process a metabolism tick for a player.
     * Calculates depletion based on elapsed time and activity multiplier.
     * 
     * @param playerId Player's UUID as string
     * @param deltaTimeSeconds Time elapsed since last tick in seconds
     * @param activityState Current activity state of the player
     */
    fun processTick(playerId: String, deltaTimeSeconds: Float, activityState: ActivityState) {
        if (!config.enabled) return
        
        val stats = statsCache[playerId] ?: return
        
        var newHunger = stats.hunger
        var newThirst = stats.thirst
        var newEnergy = stats.energy
        
        // Process hunger depletion
        if (config.hunger.enabled && newHunger > 0f) {
            val multiplier = config.hunger.getMultiplier(activityState.name).toFloat()
            val depletionPerSecond = 100f / config.hunger.baseDepletionRateSeconds.toFloat()
            val depletion = depletionPerSecond * deltaTimeSeconds * multiplier
            newHunger = max(0f, newHunger - depletion)
        }
        
        // Process thirst depletion
        if (config.thirst.enabled && newThirst > 0f) {
            val multiplier = config.thirst.getMultiplier(activityState.name).toFloat()
            val depletionPerSecond = 100f / config.thirst.baseDepletionRateSeconds.toFloat()
            val depletion = depletionPerSecond * deltaTimeSeconds * multiplier
            newThirst = max(0f, newThirst - depletion)
        }
        
        // Process energy depletion
        if (config.energy.enabled && newEnergy > 0f) {
            val multiplier = config.energy.getMultiplier(activityState.name).toFloat()
            val depletionPerSecond = 100f / config.energy.baseDepletionRateSeconds.toFloat()
            val depletion = depletionPerSecond * deltaTimeSeconds * multiplier
            newEnergy = max(0f, newEnergy - depletion)
        }
        
        // Update cache with new values
        statsCache[playerId] = stats.withStats(newHunger, newThirst, newEnergy)
    }
    
    /**
     * Process tick using the stored last depletion time to calculate delta.
     * This is the preferred method as it handles timing internally.
     * 
     * @param playerId Player's UUID as string
     * @param activityState Current activity state
     */
    fun processTickWithDelta(playerId: String, activityState: ActivityState) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastDepletionTime[playerId] ?: currentTime
        val deltaSeconds = (currentTime - lastTime) / 1000f
        
        // Only process if meaningful time has passed
        if (deltaSeconds >= 0.1f) {
            processTick(playerId, deltaSeconds, activityState)
            lastDepletionTime[playerId] = currentTime
        }
    }
    
    /**
     * Restore hunger for a player.
     * 
     * @param playerId Player's UUID as string
     * @param amount Amount to restore (0-100)
     */
    fun restoreHunger(playerId: String, amount: Float) {
        statsCache.computeIfPresent(playerId) { _, stats ->
            stats.withHunger(stats.hunger + amount)
        }
    }
    
    /**
     * Restore thirst for a player.
     * 
     * @param playerId Player's UUID as string
     * @param amount Amount to restore (0-100)
     */
    fun restoreThirst(playerId: String, amount: Float) {
        statsCache.computeIfPresent(playerId) { _, stats ->
            stats.withThirst(stats.thirst + amount)
        }
    }
    
    /**
     * Restore energy for a player.
     * 
     * @param playerId Player's UUID as string
     * @param amount Amount to restore (0-100)
     */
    fun restoreEnergy(playerId: String, amount: Float) {
        statsCache.computeIfPresent(playerId) { _, stats ->
            stats.withEnergy(stats.energy + amount)
        }
    }
    
    /**
     * Set hunger directly (for admin commands).
     */
    fun setHunger(playerId: String, value: Float) {
        statsCache.computeIfPresent(playerId) { _, stats ->
            stats.withHunger(value)
        }
    }
    
    /**
     * Set thirst directly (for admin commands).
     */
    fun setThirst(playerId: String, value: Float) {
        statsCache.computeIfPresent(playerId) { _, stats ->
            stats.withThirst(value)
        }
    }
    
    /**
     * Set energy directly (for admin commands).
     */
    fun setEnergy(playerId: String, value: Float) {
        statsCache.computeIfPresent(playerId) { _, stats ->
            stats.withEnergy(value)
        }
    }
    
    /**
     * Save a single player's stats to the database.
     * Called on disconnect or periodically.
     * 
     * @param playerId Player's UUID
     * @param worldContext World context for database access
     */
    suspend fun savePlayer(playerId: UUID, worldContext: WorldContext) {
        val playerIdStr = playerId.toString()
        val stats = statsCache[playerIdStr] ?: return
        
        val repository = worldContext.getDataOrNull<MetabolismRepository>() ?: return
        
        try {
            repository.updateStats(stats)
            logger.atFine().log("Saved metabolism for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to save metabolism for player $playerId")
        }
    }
    
    /**
     * Save all cached players' stats to the database.
     * Called on shutdown or periodically.
     */
    suspend fun saveAllPlayers() {
        if (statsCache.isEmpty()) return
        
        val allStats = statsCache.values.toList()
        
        // Save to each world's database
        for (worldContext in CoreModule.worlds.getAllContexts()) {
            val repository = worldContext.getDataOrNull<MetabolismRepository>() ?: continue
            
            // Filter stats for players in this world
            val playersInWorld = CoreModule.players.getSessionsInWorld(worldContext.worldId)
                .map { it.playerId.toString() }
                .toSet()
            
            val statsToSave = allStats.filter { it.playerId in playersInWorld }
            
            if (statsToSave.isNotEmpty()) {
                try {
                    repository.saveAll(statsToSave)
                } catch (e: Exception) {
                    logger.atWarning().withCause(e)
                        .log("Failed to save metabolism stats for world ${worldContext.worldId}")
                }
            }
        }
        
        logger.atFine().log("Saved metabolism for ${allStats.size} players")
    }
    
    /**
     * Remove a player from the cache.
     * Called after saving on disconnect.
     * 
     * @param playerId Player's UUID as string
     * @return The removed stats, or null if not cached
     */
    fun removeFromCache(playerId: String): MetabolismStats? {
        lastDepletionTime.remove(playerId)
        return statsCache.remove(playerId)
    }
    
    /**
     * Remove a player from the cache by UUID.
     */
    fun removeFromCache(playerId: UUID): MetabolismStats? {
        return removeFromCache(playerId.toString())
    }
    
    /**
     * Clear the entire stats cache.
     * Called during shutdown after saving.
     */
    fun clearCache() {
        statsCache.clear()
        lastDepletionTime.clear()
        logger.atFine().log("Metabolism cache cleared")
    }
    
    /**
     * Get all cached player IDs.
     */
    fun getCachedPlayerIds(): Set<String> {
        return statsCache.keys.toSet()
    }
    
    /**
     * Get the number of cached players.
     */
    fun getCacheSize(): Int {
        return statsCache.size
    }
    
    /**
     * Check if a player is in the cache.
     */
    fun isPlayerCached(playerId: String): Boolean {
        return statsCache.containsKey(playerId)
    }
    
    // ============ HUD Management ============
    
    /** Threshold for HUD updates - only update if stat changed by more than this amount */
    companion object {
        const val HUD_UPDATE_THRESHOLD = 0.5f
    }
    
    /**
     * Register a HUD element for a player.
     * 
     * @param playerId Player's UUID as string
     * @param hudElement The player's metabolism HUD element
     */
    fun registerHudElement(playerId: String, hudElement: MetabolismHudElement) {
        hudElements[playerId] = hudElement
        // Initialize last displayed stats to trigger first update
        statsCache[playerId]?.let { stats ->
            lastDisplayedStats[playerId] = stats
            hudElement.updateStats(stats.hunger, stats.thirst, stats.energy)
        }
    }
    
    /**
     * Unregister a HUD element for a player.
     * 
     * @param playerId Player's UUID as string
     */
    fun unregisterHudElement(playerId: String) {
        hudElements.remove(playerId)
        lastDisplayedStats.remove(playerId)
    }
    
    /**
     * Get a player's HUD element.
     * 
     * @param playerId Player's UUID as string
     * @return The HUD element, or null if not registered
     */
    fun getHudElement(playerId: String): MetabolismHudElement? {
        return hudElements[playerId]
    }
    
    /**
     * Check if HUD should be updated based on stat changes exceeding threshold.
     * This prevents spamming HUD updates for tiny changes.
     * 
     * @param playerId Player's UUID as string
     * @param currentStats Current metabolism stats
     * @return true if HUD should be updated
     */
    fun shouldUpdateHud(playerId: String, currentStats: MetabolismStats): Boolean {
        val lastStats = lastDisplayedStats[playerId] ?: return true
        
        return abs(currentStats.hunger - lastStats.hunger) > HUD_UPDATE_THRESHOLD ||
               abs(currentStats.thirst - lastStats.thirst) > HUD_UPDATE_THRESHOLD ||
               abs(currentStats.energy - lastStats.energy) > HUD_UPDATE_THRESHOLD
    }
    
    /**
     * Update the HUD if the stats have changed significantly.
     * Uses threshold-based updates to avoid spamming.
     * 
     * @param playerId Player's UUID as string
     * @return true if HUD was updated, false if below threshold
     */
    fun updateHudIfNeeded(playerId: String): Boolean {
        val currentStats = statsCache[playerId] ?: return false
        val hudElement = hudElements[playerId] ?: return false
        
        if (!shouldUpdateHud(playerId, currentStats)) {
            return false
        }
        
        // Update the HUD element with new values
        hudElement.updateStats(currentStats.hunger, currentStats.thirst, currentStats.energy)
        
        // Push the update to the client
        hudElement.updateHud()
        
        // Record that we displayed these stats
        lastDisplayedStats[playerId] = currentStats
        
        return true
    }
    
    /**
     * Force update the HUD regardless of threshold.
     * Useful for initial display or manual refresh.
     * 
     * @param playerId Player's UUID as string
     */
    fun forceUpdateHud(playerId: String) {
        val currentStats = statsCache[playerId] ?: return
        val hudElement = hudElements[playerId] ?: return
        
        hudElement.updateStats(currentStats.hunger, currentStats.thirst, currentStats.energy)
        hudElement.updateHud()
        lastDisplayedStats[playerId] = currentStats
    }
}
