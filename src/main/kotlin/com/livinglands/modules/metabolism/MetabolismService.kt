package com.livinglands.modules.metabolism

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule
import com.livinglands.core.WorldContext
import com.livinglands.modules.metabolism.config.MetabolismConfig
import com.livinglands.modules.metabolism.hud.MetabolismHudElement
import com.livinglands.util.UuidStringCache
import com.livinglands.util.toCachedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Service for managing metabolism stats across all players.
 * 
 * Performance Optimizations (v1.0.1):
 * 1. Consolidated state: Single ConcurrentHashMap with PlayerMetabolismState instead of 4 separate maps
 *    - Reduces hash lookups from 4 per tick to 1
 *    - Better cache locality for player data
 *    - Reduced memory overhead (1 Entry per player instead of 4)
 * 
 * 2. UUID string caching: Using toCachedString() extension instead of UUID.toString()
 *    - Eliminates ~3000 String allocations per second with 100 players
 *    - Cached strings are cleaned up on player disconnect
 * 
 * 3. Mutable state container: PlayerMetabolismState uses mutable fields
 *    - Zero allocations during tick updates (no copy() calls)
 *    - Immutable MetabolismStats only created for persistence
 * 
 * 4. Timestamp reuse: Single System.currentTimeMillis() call per tick cycle
 *    - Passed through all update methods to avoid multiple system calls
 * 
 * Thread-safe using ConcurrentHashMap for the player state map.
 */
class MetabolismService(
    private var config: MetabolismConfig,
    private val logger: HytaleLogger
) {
    
    /**
     * Consolidated player state cache.
     * 
     * Key: Player UUID as cached string (via toCachedString())
     * Value: Mutable PlayerMetabolismState containing all per-player data:
     *        - Current stats (hunger, thirst, energy)
     *        - Last depletion timestamp
     *        - HUD element reference
     *        - Last displayed stats for threshold detection
     * 
     * This replaces the previous 4 separate maps (statsCache, lastDepletionTime,
     * lastDisplayedStats, hudElements) reducing lookups from 4 to 1 per tick.
     */
    private val playerStates = ConcurrentHashMap<String, PlayerMetabolismState>()
    
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
     * Uses cached UUID string to avoid repeated allocations.
     * 
     * @param playerId Player's UUID
     * @param worldContext The world context for database access
     */
    suspend fun initializePlayer(playerId: UUID, worldContext: WorldContext) {
        // Use cached string representation (avoids allocation on subsequent calls)
        val playerIdStr = playerId.toCachedString()
        
        logger.atInfo().log("🔵 initializePlayer() called: UUID=$playerId, string=$playerIdStr")
        
        // Get or create repository from world context
        val repository = worldContext.getData { 
            MetabolismRepository(worldContext.persistence, logger).also {
                // Initialize schema in a coroutine
                scope.launch { it.initialize() }
            }
        }
        
        // Load or create stats from database
        val stats = repository.ensureStats(playerIdStr)
        
        // Create consolidated mutable state from immutable stats
        val state = PlayerMetabolismState.fromStats(stats)
        
        // Cache the state (single map entry instead of 4)
        playerStates[playerIdStr] = state
        
        logger.atInfo().log("Initialized metabolism for player $playerId: H=${stats.hunger}, T=${stats.thirst}, E=${stats.energy}")
    }
    
    /**
     * Get cached stats for a player.
     * Returns null if player is not in cache (not initialized).
     * 
     * Note: This creates an immutable MetabolismStats snapshot.
     * For high-frequency access, use getState() to access the mutable container directly.
     * 
     * @param playerId Player's UUID as string
     * @return Immutable stats snapshot or null
     */
    fun getStats(playerId: String): MetabolismStats? {
        return playerStates[playerId]?.toImmutableStats()
    }
    
    /**
     * Get cached stats for a player by UUID.
     * Uses cached UUID string for efficiency.
     */
    fun getStats(playerId: UUID): MetabolismStats? {
        return playerStates[playerId.toCachedString()]?.toImmutableStats()
    }
    
    /**
     * Get the mutable state container for a player.
     * Prefer this for high-frequency access to avoid creating snapshots.
     * 
     * @param playerId Player's UUID as string
     * @return Mutable state or null if not cached
     */
    fun getState(playerId: String): PlayerMetabolismState? {
        return playerStates[playerId]
    }
    
    /**
     * Process a metabolism tick for a player.
     * Calculates depletion based on elapsed time and activity multiplier.
     * 
     * Performance: Uses mutable PlayerMetabolismState to avoid allocations.
     * The timestamp is passed in to avoid multiple System.currentTimeMillis() calls.
     * 
     * @param playerId Player's UUID as string
     * @param deltaTimeSeconds Time elapsed since last tick in seconds
     * @param activityState Current activity state of the player
     * @param currentTime Current timestamp (passed in for efficiency)
     */
    fun processTick(playerId: String, deltaTimeSeconds: Float, activityState: ActivityState, currentTime: Long) {
        if (!config.enabled) return
        
        val state = playerStates[playerId] ?: return
        
        // Read current values from mutable state
        var newHunger = state.hunger
        var newThirst = state.thirst
        var newEnergy = state.energy
        
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
        
        // Update mutable state in-place (zero allocations!)
        state.updateStats(newHunger, newThirst, newEnergy, currentTime)
    }
    
    /**
     * Process tick using the stored last depletion time to calculate delta.
     * This is the preferred method as it handles timing internally.
     * 
     * Performance: Single System.currentTimeMillis() call, timestamp passed through.
     * State access consolidated into single map lookup.
     * 
     * @param playerId Player's UUID as string
     * @param activityState Current activity state
     */
    fun processTickWithDelta(playerId: String, activityState: ActivityState) {
        // Single timestamp capture for entire tick cycle
        val currentTime = System.currentTimeMillis()
        
        // Single map lookup to get state (previously was 2 lookups: lastDepletionTime + statsCache)
        val state = playerStates[playerId] ?: return
        
        val lastTime = state.lastDepletionTime
        val deltaSeconds = (currentTime - lastTime) / 1000f
        
        // Only process if meaningful time has passed
        if (deltaSeconds >= 0.1f) {
            processTick(playerId, deltaSeconds, activityState, currentTime)
            state.lastDepletionTime = currentTime
        }
    }
    
    /**
     * Restore hunger for a player.
     * Uses mutable state - no allocations.
     * 
     * @param playerId Player's UUID as string
     * @param amount Amount to restore (0-100)
     */
    fun restoreHunger(playerId: String, amount: Float) {
        playerStates[playerId]?.addHunger(amount)
    }
    
    /**
     * Restore thirst for a player.
     * Uses mutable state - no allocations.
     * 
     * @param playerId Player's UUID as string
     * @param amount Amount to restore (0-100)
     */
    fun restoreThirst(playerId: String, amount: Float) {
        playerStates[playerId]?.addThirst(amount)
    }
    
    /**
     * Restore energy for a player.
     * Uses mutable state - no allocations.
     * 
     * @param playerId Player's UUID as string
     * @param amount Amount to restore (0-100)
     */
    fun restoreEnergy(playerId: String, amount: Float) {
        playerStates[playerId]?.addEnergy(amount)
    }
    
    /**
     * Restore multiple stats at once (used by food consumption system).
     * 
     * @param playerId Player's UUID
     * @param hunger Amount to restore to hunger (0-100)
     * @param thirst Amount to restore to thirst (0-100)
     * @param energy Amount to restore to energy (0-100)
     */
    fun restoreStats(playerId: UUID, hunger: Double = 0.0, thirst: Double = 0.0, energy: Double = 0.0) {
        val playerIdStr = playerId.toCachedString()
        val state = playerStates[playerIdStr] ?: return
        
        if (hunger > 0.0) state.addHunger(hunger.toFloat())
        if (thirst > 0.0) state.addThirst(thirst.toFloat())
        if (energy > 0.0) state.addEnergy(energy.toFloat())
    }
    
    /**
     * Set hunger directly (for admin commands).
     * Uses mutable state - no allocations.
     */
    fun setHunger(playerId: String, value: Float) {
        playerStates[playerId]?.setHunger(value)
    }
    
    /**
     * Set thirst directly (for admin commands).
     * Uses mutable state - no allocations.
     */
    fun setThirst(playerId: String, value: Float) {
        playerStates[playerId]?.setThirst(value)
    }
    
    /**
     * Set energy directly (for admin commands).
     * Uses mutable state - no allocations.
     */
    fun setEnergy(playerId: String, value: Float) {
        playerStates[playerId]?.setEnergy(value)
    }
    
    /**
     * Save a single player's stats to the database.
     * Called on disconnect or periodically.
     * 
     * Creates an immutable MetabolismStats snapshot for persistence.
     * 
     * @param playerId Player's UUID
     * @param worldContext World context for database access
     */
    suspend fun savePlayer(playerId: UUID, worldContext: WorldContext) {
        // Use cached string
        val playerIdStr = playerId.toCachedString()
        
        logger.atInfo().log("savePlayer() called for $playerId")
        
        val state = playerStates[playerIdStr]
        if (state == null) {
            logger.atWarning().log("No state found in cache for player $playerId - cannot save")
            return
        }
        
        val repository = worldContext.getDataOrNull<MetabolismRepository>()
        if (repository == null) {
            logger.atWarning().log("No MetabolismRepository found for world ${worldContext.worldId} - cannot save player $playerId")
            return
        }
        
        try {
            // Convert mutable state to immutable for database
            val stats = state.toImmutableStats()
            logger.atInfo().log("About to save stats for $playerId: H=${stats.hunger}, T=${stats.thirst}, E=${stats.energy}")
            repository.updateStats(stats)
            logger.atInfo().log("Successfully saved metabolism for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to save metabolism for player $playerId")
            throw e  // Re-throw so caller knows save failed
        }
    }
    
    /**
     * Save all cached players' stats to the database.
     * Called on shutdown or periodically.
     */
    suspend fun saveAllPlayers() {
        if (playerStates.isEmpty()) return
        
        // Convert all mutable states to immutable for persistence
        val allStats = playerStates.values.map { it.toImmutableStats() }
        
        // Save to each world's database
        for (worldContext in CoreModule.worlds.getAllContexts()) {
            val repository = worldContext.getDataOrNull<MetabolismRepository>() ?: continue
            
            // Filter stats for players in this world (use cached strings)
            val playersInWorld = CoreModule.players.getSessionsInWorld(worldContext.worldId)
                .map { it.playerId.toCachedString() }
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
     * Also cleans up the UUID string cache to prevent memory leaks.
     * 
     * @param playerId Player's UUID as string
     * @return The removed stats as immutable snapshot, or null if not cached
     */
    fun removeFromCache(playerId: String): MetabolismStats? {
        val state = playerStates.remove(playerId)
        return state?.toImmutableStats()
    }
    
    /**
     * Remove a player from the cache by UUID.
     * Cleans up the UUID string cache as well.
     */
    fun removeFromCache(playerId: UUID): MetabolismStats? {
        val playerIdStr = playerId.toCachedString()
        val result = removeFromCache(playerIdStr)
        // Clean up UUID string cache to prevent memory leak
        UuidStringCache.remove(playerId)
        return result
    }
    
    /**
     * Clear the entire stats cache.
     * Called during shutdown after saving.
     * Also clears the UUID string cache.
     */
    fun clearCache() {
        playerStates.clear()
        UuidStringCache.clear()
        logger.atFine().log("Metabolism cache cleared")
    }
    
    /**
     * Get all cached player IDs.
     */
    fun getCachedPlayerIds(): Set<String> {
        return playerStates.keys.toSet()
    }
    
    /**
     * Get the number of cached players.
     */
    fun getCacheSize(): Int {
        return playerStates.size
    }
    
    /**
     * Check if a player is in the cache.
     */
    fun isPlayerCached(playerId: String): Boolean {
        return playerStates.containsKey(playerId)
    }
    
    // ============ HUD Management ============
    
    /** Threshold for HUD updates - only update if stat changed by more than this amount */
    companion object {
        const val HUD_UPDATE_THRESHOLD = 0.5f
    }
    
    /**
     * Register a HUD element for a player.
     * Stores the element in the consolidated PlayerMetabolismState.
     * 
     * @param playerId Player's UUID as string
     * @param hudElement The player's metabolism HUD element
     */
    fun registerHudElement(playerId: String, hudElement: MetabolismHudElement) {
        val state = playerStates[playerId] ?: return
        
        // Store HUD element in consolidated state
        state.hudElement = hudElement
        
        // Initialize last displayed stats and send first update
        state.markDisplayed()
        hudElement.updateStats(state.hunger, state.thirst, state.energy)
    }
    
    /**
     * Unregister a HUD element for a player.
     * 
     * @param playerId Player's UUID as string
     */
    fun unregisterHudElement(playerId: String) {
        playerStates[playerId]?.hudElement = null
    }
    
    /**
     * Get a player's HUD element.
     * 
     * @param playerId Player's UUID as string
     * @return The HUD element, or null if not registered
     */
    fun getHudElement(playerId: String): MetabolismHudElement? {
        return playerStates[playerId]?.hudElement
    }
    
    /**
     * Check if HUD should be updated based on stat changes exceeding threshold.
     * This prevents spamming HUD updates for tiny changes.
     * 
     * Uses fields directly from PlayerMetabolismState - no allocations.
     * 
     * @param state Player's metabolism state
     * @return true if HUD should be updated
     */
    private fun shouldUpdateHud(state: PlayerMetabolismState): Boolean {
        return abs(state.hunger - state.lastDisplayedHunger) > HUD_UPDATE_THRESHOLD ||
               abs(state.thirst - state.lastDisplayedThirst) > HUD_UPDATE_THRESHOLD ||
               abs(state.energy - state.lastDisplayedEnergy) > HUD_UPDATE_THRESHOLD
    }
    
    /**
     * Update the HUD if the stats have changed significantly.
     * Uses threshold-based updates to avoid spamming.
     * 
     * Performance: Single map lookup, no MetabolismStats allocation.
     * 
     * @param playerId Player's UUID as string
     * @return true if HUD was updated, false if below threshold
     */
    fun updateHudIfNeeded(playerId: String): Boolean {
        // Single lookup for all state
        val state = playerStates[playerId] ?: return false
        val hudElement = state.hudElement ?: return false
        
        if (!shouldUpdateHud(state)) {
            return false
        }
        
        // Update the HUD element with current values (direct field access)
        hudElement.updateStats(state.hunger, state.thirst, state.energy)
        
        // Push the update to the client
        hudElement.updateHud()
        
        // Record that we displayed these stats (no allocation)
        state.markDisplayed()
        
        return true
    }
    
    /**
     * Force update the HUD regardless of threshold.
     * Useful for initial display or manual refresh.
     * 
     * @param playerId Player's UUID as string
     */
    fun forceUpdateHud(playerId: String) {
        val state = playerStates[playerId] ?: return
        val hudElement = state.hudElement ?: return
        
        hudElement.updateStats(state.hunger, state.thirst, state.energy)
        hudElement.updateHud()
        state.markDisplayed()
    }
}
