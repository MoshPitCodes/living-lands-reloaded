package com.livinglands.modules.metabolism

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule
import com.livinglands.core.WorldContext
import com.livinglands.core.hud.LivingLandsHudElement
import com.livinglands.modules.metabolism.config.MetabolismConfig
import com.livinglands.core.UuidStringCache
import com.livinglands.core.toCachedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
     *        - Last displayed stats for threshold detection
     * 
     * Note: HUD elements are now stored in CoreModule.hudManager (single source of truth).
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
     * Also triggers re-resolution for all active worlds.
     */
    fun updateConfig(newConfig: MetabolismConfig) {
        config = newConfig
        
        // Re-resolve for all active worlds to pick up override changes
        val worldCount = CoreModule.worlds.getAllContexts().count { worldContext ->
            worldContext.resolveMetabolismConfig(newConfig)
            true
        }
        
        logger.atFine().log("Metabolism config updated globally and re-resolved for $worldCount worlds")
    }
    
    /**
     * Get current global config (for read access).
     */
    fun getConfig(): MetabolismConfig = config
    
    /**
     * Get effective config for a specific world.
     * Returns world-specific merged config if available, otherwise global config.
     * 
     * Performance: O(1) - just a null coalesce.
     * Can be inlined by JIT.
     * 
     * @param worldContext The world context containing resolved config
     * @return World-specific config or global fallback
     */
    @JvmName("getConfigForWorldContext")
    fun getConfigForWorld(worldContext: WorldContext?): MetabolismConfig {
        return worldContext?.metabolismConfig ?: config
    }
    
    /**
     * Get effective config by world UUID.
     * Useful when you only have the world UUID.
     * 
     * Performance: One map lookup + null coalesce.
     * 
     * @param worldId The world UUID
     * @return World-specific config or global fallback
     */
    fun getConfigForWorld(worldId: UUID): MetabolismConfig {
        return CoreModule.worlds.getContext(worldId)?.metabolismConfig ?: config
    }
    
    /**
     * Reset a player's metabolism stats to default values.
     * Called on respawn to restore full hunger/thirst/energy.
     * 
     * **ARCHITECTURE CHANGE:** Now uses global repository.
     * 
     * @param playerId Player's UUID
     * @param repository The global metabolism repository
     */
    suspend fun resetStats(playerId: UUID, repository: MetabolismRepository) {
        // Perform in-memory reset immediately (synchronous)
        resetStatsInMemory(playerId)
        
        // Persist to database asynchronously
        val playerIdStr = playerId.toCachedString()
        val state = playerStates[playerIdStr] ?: return
        
        try {
            val stats = state.toImmutableStats()
            repository.updateStats(stats)
            logger.atFine().log("Persisted reset metabolism stats for $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to persist reset stats for $playerId")
        }
    }
    
    /**
     * Reset metabolism stats to defaults in memory (non-blocking).
     * This updates the cache immediately without waiting for database persistence.
     * Use this for immediate player experience (e.g., respawn), then persist async.
     * 
     * @param playerId Player's UUID
     * @return true if reset succeeded, false if player not in cache
     */
    fun resetStatsInMemory(playerId: UUID): Boolean {
        val playerIdStr = playerId.toCachedString()
        
        // Get current state (if exists)
        val state = playerStates[playerIdStr]
        if (state == null) {
            logger.atWarning().log("Player $playerId not in cache during in-memory reset")
            return false
        }
        
        // Reset to default values (100/100/100)
        val currentTime = System.currentTimeMillis()
        state.updateStats(100f, 100f, 100f, currentTime)
        state.lastDepletionTime = currentTime
        
        logger.atFine().log("Reset metabolism in-memory for player $playerId to defaults (H=100, T=100, E=100)")
        return true
    }
    
    /**
     * Initialize stats for a player from the global database.
     * Called when a player joins/becomes ready.
     * 
     * **ARCHITECTURE CHANGE:** Now uses global database instead of per-world.
     * Stats follow the player across all worlds on the server.
     * 
     * Uses cached UUID string to avoid repeated allocations.
     * 
     * @param playerId Player's UUID
     * @param repository The global metabolism repository
     */
    suspend fun initializePlayer(playerId: UUID, repository: MetabolismRepository) {
        // Use cached string representation (avoids allocation on subsequent calls)
        val playerIdStr = playerId.toCachedString()
        
        logger.atFine().log("🔵 initializePlayer() called: UUID=$playerId, string=$playerIdStr")
        
        // Load or create stats from global database
        val stats = repository.ensureStats(playerIdStr)
        
        // Create consolidated mutable state from immutable stats
        val state = PlayerMetabolismState.fromStats(stats)
        
        // Cache the state (single map entry instead of 4)
        playerStates[playerIdStr] = state
        
        logger.atFine().log("Initialized metabolism for player $playerId: H=${stats.hunger}, T=${stats.thirst}, E=${stats.energy}")
    }
    
    /**
     * Initialize player with default values immediately (non-blocking).
     * Actual stats will be loaded from database asynchronously later.
     * 
     * This allows the HUD to display immediately without blocking the WorldThread.
     * 
     * CRITICAL: Uses putIfAbsent for thread-safe check-and-insert to prevent race
     * conditions when player joins multiple worlds simultaneously or reconnects quickly.
     * 
     * @param playerId Player's UUID
     */
    fun initializePlayerWithDefaults(playerId: UUID) {
        val playerIdStr = playerId.toCachedString()
        
        // Create default stats (100/100/100)
        val defaultStats = MetabolismStats.createDefault(playerIdStr)
        val state = PlayerMetabolismState.fromStats(defaultStats)
        
        // ATOMIC check-and-insert: prevents race condition where two threads
        // both pass containsKey() check and one overwrites the other's data
        val existing = playerStates.putIfAbsent(playerIdStr, state)
        if (existing != null) {
            logger.atFine().log("Player $playerId already in cache, skipping default initialization")
            return
        }
        
        logger.atFine().log("Initialized metabolism with defaults for $playerId (H=100, T=100, E=100)")
    }
    
    /**
     * Update player state with loaded stats from database.
     * Called asynchronously after initializePlayerWithDefaults().
     * 
     * @param playerId Player's UUID
     * @param stats Loaded stats from database
     */
    fun updatePlayerState(playerId: UUID, stats: MetabolismStats) {
        val playerIdStr = playerId.toCachedString()
        val state = playerStates[playerIdStr] ?: return
        
        // Update the mutable state with loaded values
        state.updateStats(stats.hunger, stats.thirst, stats.energy, stats.lastUpdated)
        
        logger.atFine().log("Updated metabolism state from database for $playerId: H=${stats.hunger}, T=${stats.thirst}, E=${stats.energy}")
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
     * Process a metabolism tick for a player using world-specific config.
     * Calculates depletion based on elapsed time and activity multiplier.
     * 
     * Performance: Uses mutable PlayerMetabolismState to avoid allocations.
     * The timestamp is passed in to avoid multiple System.currentTimeMillis() calls.
     * 
     * Depletion modifiers (from Professions Tier 3 abilities) are applied multiplicatively
     * to the final depletion amount. Example: 0.85 modifier = 15% slower depletion.
     * 
     * @param playerId Player's UUID as string
     * @param deltaTimeSeconds Time elapsed since last tick in seconds
     * @param activityState Current activity state of the player
     * @param currentTime Current timestamp (passed in for efficiency)
     * @param worldConfig World-specific metabolism config to use
     */
    fun processTick(
        playerId: String, 
        deltaTimeSeconds: Float, 
        activityState: ActivityState, 
        currentTime: Long,
        worldConfig: MetabolismConfig
    ) {
        if (!worldConfig.enabled) return
        
        val state = playerStates[playerId] ?: return
        
        // Get combined depletion modifier once (applies to all stats)
        val depletionMultiplier = state.getCombinedDepletionMultiplier().toFloat()
        
        // Read current values from mutable state
        var newHunger = state.hunger
        var newThirst = state.thirst
        var newEnergy = state.energy
        
        // Process hunger depletion (use world-specific config + depletion modifier)
        if (worldConfig.hunger.enabled && newHunger > 0f) {
            val activityMultiplier = worldConfig.hunger.getMultiplier(activityState.name).toFloat()
            val depletionPerSecond = 100f / worldConfig.hunger.baseDepletionRateSeconds.toFloat()
            val depletion = depletionPerSecond * deltaTimeSeconds * activityMultiplier * depletionMultiplier
            newHunger = max(0f, newHunger - depletion)
        }
        
        // Process thirst depletion (use world-specific config + depletion modifier)
        if (worldConfig.thirst.enabled && newThirst > 0f) {
            val activityMultiplier = worldConfig.thirst.getMultiplier(activityState.name).toFloat()
            val depletionPerSecond = 100f / worldConfig.thirst.baseDepletionRateSeconds.toFloat()
            val depletion = depletionPerSecond * deltaTimeSeconds * activityMultiplier * depletionMultiplier
            newThirst = max(0f, newThirst - depletion)
        }
        
        // Process energy depletion (use world-specific config + depletion modifier)
        if (worldConfig.energy.enabled && newEnergy > 0f) {
            val activityMultiplier = worldConfig.energy.getMultiplier(activityState.name).toFloat()
            val depletionPerSecond = 100f / worldConfig.energy.baseDepletionRateSeconds.toFloat()
            val depletion = depletionPerSecond * deltaTimeSeconds * activityMultiplier * depletionMultiplier
            newEnergy = max(0f, newEnergy - depletion)
        }
        
        // Update mutable state in-place (zero allocations!)
        state.updateStats(newHunger, newThirst, newEnergy, currentTime)
    }
    
    /**
     * Process tick using the stored last depletion time to calculate delta with world-specific config.
     * This is the preferred method as it handles timing internally.
     * 
     * Performance: Single System.currentTimeMillis() call, timestamp passed through.
     * State access consolidated into single map lookup.
     * 
     * @param playerId Player's UUID as string
     * @param activityState Current activity state
     * @param worldConfig World-specific metabolism config to use
     */
    fun processTickWithDelta(playerId: String, activityState: ActivityState, worldConfig: MetabolismConfig) {
        // Single timestamp capture for entire tick cycle
        val currentTime = System.currentTimeMillis()
        
        // Single map lookup to get state (previously was 2 lookups: lastDepletionTime + statsCache)
        val state = playerStates[playerId] ?: return
        
        val lastTime = state.lastDepletionTime
        val deltaSeconds = (currentTime - lastTime) / 1000f
        
        // Only process if meaningful time has passed
        if (deltaSeconds >= 0.1f) {
            processTick(playerId, deltaSeconds, activityState, currentTime, worldConfig)
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
     * Restore energy for a player by UUID.
     * Used by Professions abilities (e.g., Efficient Miner Tier 2).
     * 
     * @param playerId Player's UUID
     * @param amount Amount to restore (0-100)
     */
    fun restoreEnergy(playerId: UUID, amount: Double) {
        restoreEnergy(playerId.toCachedString(), amount.toFloat())
    }
    
    /**
     * Restore hunger for a player by UUID.
     * Used by Professions abilities (e.g., Hearty Gatherer Tier 2).
     * 
     * @param playerId Player's UUID
     * @param amount Amount to restore (0-100)
     */
    fun restoreHunger(playerId: UUID, amount: Double) {
        restoreHunger(playerId.toCachedString(), amount.toFloat())
    }
    
    /**
     * Restore thirst for a player by UUID.
     * Used by Professions abilities (e.g., Survivalist Tier 2).
     * 
     * @param playerId Player's UUID
     * @param amount Amount to restore (0-100)
     */
    fun restoreThirst(playerId: UUID, amount: Double) {
        restoreThirst(playerId.toCachedString(), amount.toFloat())
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
    
    // ============ Max Stat Management (for Professions Tier 2 abilities) ============
    
    /**
     * Set maximum hunger capacity for a player.
     * Used by Tier 2 Combat ability (Iron Stomach: +15 max hunger).
     * 
     * @param playerId Player's UUID
     * @param maxValue New maximum (default 100, typically 115 with bonus)
     */
    fun setMaxHunger(playerId: UUID, maxValue: Double) {
        setMaxHunger(playerId.toCachedString(), maxValue.toFloat())
    }
    
    /**
     * Set maximum hunger capacity for a player.
     * 
     * @param playerIdStr Player's UUID as cached string
     * @param maxValue New maximum (sanity-bounded to 50-200)
     */
    fun setMaxHunger(playerIdStr: String, maxValue: Float) {
        val state = playerStates[playerIdStr]
        if (state == null) {
            logger.atWarning().log("Cannot set max hunger for $playerIdStr - player not in cache")
            return
        }
        
        state.maxHunger = maxValue.coerceIn(50f, 200f)
        logger.atFine().log("Set max hunger to $maxValue for player $playerIdStr")
    }
    
    /**
     * Set maximum thirst capacity for a player.
     * Used by Tier 2 Mining ability (Desert Nomad: +10 max thirst).
     * 
     * @param playerId Player's UUID
     * @param maxValue New maximum (default 100, typically 110 with bonus)
     */
    fun setMaxThirst(playerId: UUID, maxValue: Double) {
        setMaxThirst(playerId.toCachedString(), maxValue.toFloat())
    }
    
    /**
     * Set maximum thirst capacity for a player.
     * 
     * @param playerIdStr Player's UUID as cached string
     * @param maxValue New maximum (sanity-bounded to 50-200)
     */
    fun setMaxThirst(playerIdStr: String, maxValue: Float) {
        val state = playerStates[playerIdStr]
        if (state == null) {
            logger.atWarning().log("Cannot set max thirst for $playerIdStr - player not in cache")
            return
        }
        
        state.maxThirst = maxValue.coerceIn(50f, 200f)
        logger.atFine().log("Set max thirst to $maxValue for player $playerIdStr")
    }
    
    /**
     * Set maximum energy capacity for a player.
     * Used by Tier 2 Logging ability (Tireless Woodsman: +10 max energy).
     * 
     * @param playerId Player's UUID
     * @param maxValue New maximum (default 100, typically 110 with bonus)
     */
    fun setMaxEnergy(playerId: UUID, maxValue: Double) {
        setMaxEnergy(playerId.toCachedString(), maxValue.toFloat())
    }
    
    /**
     * Set maximum energy capacity for a player.
     * 
     * @param playerIdStr Player's UUID as cached string
     * @param maxValue New maximum (sanity-bounded to 50-200)
     */
    fun setMaxEnergy(playerIdStr: String, maxValue: Float) {
        val state = playerStates[playerIdStr]
        if (state == null) {
            logger.atWarning().log("Cannot set max energy for $playerIdStr - player not in cache")
            return
        }
        
        state.maxEnergy = maxValue.coerceIn(50f, 200f)
        logger.atFine().log("Set max energy to $maxValue for player $playerIdStr")
    }
    
    /**
     * Get current max hunger capacity for a player.
     * 
     * @param playerId Player's UUID
     * @return Max hunger value or 100f if player not cached
     */
    fun getMaxHunger(playerId: UUID): Float {
        return playerStates[playerId.toCachedString()]?.maxHunger ?: 100f
    }
    
    /**
     * Get current max thirst capacity for a player.
     * 
     * @param playerId Player's UUID
     * @return Max thirst value or 100f if player not cached
     */
    fun getMaxThirst(playerId: UUID): Float {
        return playerStates[playerId.toCachedString()]?.maxThirst ?: 100f
    }
    
    /**
     * Get current max energy capacity for a player.
     * 
     * @param playerId Player's UUID
     * @return Max energy value or 100f if player not cached
     */
    fun getMaxEnergy(playerId: UUID): Float {
        return playerStates[playerId.toCachedString()]?.maxEnergy ?: 100f
    }
    
    /**
     * Reset all max stat capacities to defaults.
     * Called when abilities are disabled via config reload.
     * 
     * @param playerId Player's UUID
     */
    fun resetMaxStats(playerId: UUID) {
        val state = playerStates[playerId.toCachedString()]
        if (state == null) {
            logger.atWarning().log("Cannot reset max stats for $playerId - player not in cache")
            return
        }
        
        state.maxHunger = 100f
        state.maxThirst = 100f
        state.maxEnergy = 100f
        logger.atFine().log("Reset max stats to defaults for player $playerId")
    }
    
    // ============ Depletion Modifier Management (for Professions Tier 3 abilities) ============
    
    /**
     * Apply a depletion rate modifier to a player.
     * Used by Professions Tier 3 abilities (e.g., Survivalist: -15% depletion).
     * 
     * Multiple modifiers stack multiplicatively:
     * - Survivalist (0.85) + Endurance Training (0.90) = 0.765 (23.5% slower depletion)
     * 
     * @param playerId Player's UUID
     * @param sourceId Unique modifier ID (e.g., "professions:survivalist")
     * @param multiplier Depletion rate multiplier (0.85 = 15% slower, 1.5 = 50% faster)
     */
    fun applyDepletionModifier(playerId: UUID, sourceId: String, multiplier: Double) {
        val state = playerStates[playerId.toCachedString()]
        if (state == null) {
            logger.atWarning().log("Cannot apply depletion modifier '$sourceId' to $playerId - player not in cache")
            return
        }
        
        state.applyDepletionModifier(sourceId, multiplier)
        logger.atFine().log("Applied depletion modifier '$sourceId' (${multiplier}x) to player $playerId")
    }
    
    /**
     * Remove a depletion rate modifier from a player.
     * 
     * @param playerId Player's UUID
     * @param sourceId The modifier to remove
     * @return true if the modifier was removed, false if it didn't exist
     */
    fun removeDepletionModifier(playerId: UUID, sourceId: String): Boolean {
        val state = playerStates[playerId.toCachedString()]
        if (state == null) {
            logger.atWarning().log("Cannot remove depletion modifier '$sourceId' from $playerId - player not in cache")
            return false
        }
        
        val removed = state.removeDepletionModifier(sourceId)
        if (removed) {
            logger.atFine().log("Removed depletion modifier '$sourceId' from player $playerId")
        }
        return removed
    }
    
    /**
     * Get all active depletion modifiers for a player (for debugging).
     * 
     * @param playerId Player's UUID
     * @return Map of source ID to multiplier, or empty map if player not cached
     */
    fun getActiveModifiers(playerId: UUID): Map<String, Double> {
        return playerStates[playerId.toCachedString()]?.getActiveModifiers() ?: emptyMap()
    }
    
    /**
     * Clear all depletion modifiers for a player (e.g., on death/reset).
     * 
     * @param playerId Player's UUID
     */
    fun clearDepletionModifiers(playerId: UUID) {
        val state = playerStates[playerId.toCachedString()]
        if (state != null) {
            state.clearDepletionModifiers()
            logger.atFine().log("Cleared all depletion modifiers for player $playerId")
        }
    }
    
    /**
     * Save a player's stats to the global database.
     * Called on player disconnect or periodic saves.
     * 
     * **ARCHITECTURE CHANGE:** Now uses global repository.
     * 
     * Creates an immutable MetabolismStats snapshot for persistence.
     * 
     * @param playerId Player's UUID
     * @param repository The global metabolism repository
     */
    suspend fun savePlayer(playerId: UUID, repository: MetabolismRepository) {
        // Use cached string
        val playerIdStr = playerId.toCachedString()
        
        logger.atFine().log("savePlayer() called for $playerId")
        
        val state = playerStates[playerIdStr]
        if (state == null) {
            logger.atWarning().log("No state found in cache for player $playerId - cannot save")
            return
        }
        
        try {
            // Convert mutable state to immutable for database
            val stats = state.toImmutableStats()
            logger.atFine().log("About to save stats for $playerId: H=${stats.hunger}, T=${stats.thirst}, E=${stats.energy}")
            repository.updateStats(stats)
            logger.atFine().log("Successfully saved metabolism for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to save metabolism for player $playerId")
            throw e  // Re-throw so caller knows save failed
        }
    }
    
    /**
     * Save all cached players' stats to the global database.
     * Called on shutdown or periodically.
     * 
     * **ARCHITECTURE CHANGE:** Now uses global repository - all stats saved to one database.
     * 
     * @param repository The global metabolism repository
     */
    suspend fun saveAllPlayers(repository: MetabolismRepository) {
        if (playerStates.isEmpty()) return
        
        // Convert all mutable states to immutable for persistence
        val allStats = playerStates.values.map { it.toImmutableStats() }
        
        try {
            // Save all stats to global database in one transaction
            repository.saveAll(allStats)
            logger.atFine().log("Saved metabolism for ${allStats.size} players to global database")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Failed to save metabolism stats to global database")
        }
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
     * Gets HUD from MultiHudManager (single source of truth).
     * Performance: Two map lookups (state + HUD), no MetabolismStats allocation.
     * 
     * THREAD SAFETY: Synchronizes on state object to prevent race conditions
     * between shouldUpdateHud check and markDisplayed update. Without this,
     * two threads could both pass the threshold check and update HUD twice.
     * 
     * @param playerId Player's UUID as string
     * @param playerUuid Player's UUID (for MultiHudManager lookup)
     * @return true if HUD was updated, false if below threshold
     */
    fun updateHudIfNeeded(playerId: String, playerUuid: UUID): Boolean {
        // Single lookup for all state
        val state = playerStates[playerId] ?: return false
        
        // Synchronize the entire check-and-update to prevent race conditions
        // where two threads both pass shouldUpdateHud and send duplicate HUD updates
        synchronized(state) {
            if (!shouldUpdateHud(state)) {
                return false
            }
            
            // Get unified HUD from MultiHudManager
            val hudElement = CoreModule.hudManager.getHud(playerUuid) ?: return false
            
            // Update the HUD element with current values
            hudElement.updateMetabolism(state.hunger, state.thirst, state.energy)
            
            // Push the update to the client
            hudElement.updateMetabolismHud()
            
            // Record that we displayed these stats (no allocation)
            // This is now atomic with the threshold check above
            state.markDisplayed()
            
            return true
        }
    }
    
    /**
     * Force update the HUD regardless of threshold.
     * Useful for initial display or manual refresh.
     * 
     * @param playerId Player's UUID as string
     * @param playerUuid Player's UUID (for MultiHudManager lookup)
     */
    fun forceUpdateHud(playerId: String, playerUuid: UUID) {
        val state = playerStates[playerId] ?: return
        
        // Get unified HUD from MultiHudManager
        val hudElement = CoreModule.hudManager.getHud(playerUuid) ?: return
        
        hudElement.updateMetabolism(state.hunger, state.thirst, state.energy)
        hudElement.updateMetabolismHud()
        state.markDisplayed()
    }
    
    // ============ System Accessors ============
    // These are needed for commands/UI to access buffs/debuffs
    
    private var buffsSystem: com.livinglands.modules.metabolism.buffs.BuffsSystem? = null
    private var debuffsSystem: com.livinglands.modules.metabolism.buffs.DebuffsSystem? = null
    
    /**
     * Set the buffs system reference.
     * Called by MetabolismModule during initialization.
     */
    fun setBuffsSystem(system: com.livinglands.modules.metabolism.buffs.BuffsSystem) {
        this.buffsSystem = system
    }
    
    /**
     * Set the debuffs system reference.
     * Called by MetabolismModule during initialization.
     */
    fun setDebuffsSystem(system: com.livinglands.modules.metabolism.buffs.DebuffsSystem) {
        this.debuffsSystem = system
    }
    
    /**
     * Get the buffs system for accessing active buff names.
     */
    fun getBuffsSystem(): com.livinglands.modules.metabolism.buffs.BuffsSystem? = buffsSystem
    
    /**
     * Get the debuffs system for accessing active debuff names.
     */
    fun getDebuffsSystem(): com.livinglands.modules.metabolism.buffs.DebuffsSystem? = debuffsSystem
}
