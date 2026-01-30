package com.livinglands.modules.professions

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.toCachedString
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.DeathPenaltyState
import com.livinglands.modules.professions.data.Profession
import com.livinglands.modules.professions.data.PlayerProfessionState
import com.livinglands.modules.professions.data.ProfessionStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing profession stats and XP progression.
 * 
 * Thread-safe with in-memory state using AtomicLong for XP counters.
 * All player data persists globally (follows player across worlds).
 * 
 * Performance optimizations:
 * - In-memory state per player (5 professions each)
 * - AtomicLong XP counters for lock-free updates
 * - Precomputed XP table for O(1) level calculations
 * - Async database operations (non-blocking)
 */
class ProfessionsService(
    private var config: ProfessionsConfig,
    private val xpCalculator: XpCalculator,
    private val logger: HytaleLogger
) {
    
    /**
     * In-memory player state cache.
     * 
     * Outer map: Player UUID (cached string) -> Inner map
     * Inner map: Profession -> PlayerProfessionState (with AtomicLong XP)
     * 
     * Thread-safe via ConcurrentHashMap.
     */
    private val playerStates = ConcurrentHashMap<String, MutableMap<Profession, PlayerProfessionState>>()
    
    /**
     * Death penalty state per player (session-based).
     * 
     * Key: Player UUID (cached string)
     * Value: DeathPenaltyState (tracks deaths, weights, decay)
     * 
     * Cleared on logout, restored on login from database.
     */
    private val deathPenaltyStates = ConcurrentHashMap<String, DeathPenaltyState>()
    
    /**
     * Coroutine scope for async database operations.
     */
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // ============ Configuration Management ============
    
    /**
     * Update configuration (e.g., after hot-reload).
     */
    fun updateConfig(newConfig: ProfessionsConfig) {
        config = newConfig
        logger.atFine().log("Professions config updated")
    }
    
    /**
     * Get current configuration (for read access).
     */
    fun getConfig(): ProfessionsConfig = config
    
    // ============ Player Initialization ============
    
    /**
     * Get progress to next level (0.0 to 1.0).
     * Thread-safe - reads from cache.
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @return Progress (0.0 = start of level, 1.0 = ready to level up)
     */
    fun getProgressToNextLevel(playerId: UUID, profession: Profession): Double {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return 0.0
        val state = stateMap[profession] ?: return 0.0
        
        return xpCalculator.progressToNextLevel(state.xp, state.level)
    }
    
    /**
     * Get XP within current level (how much XP gained in this level).
     * Thread-safe - reads from cache.
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @return XP within current level (0 = just leveled up, N = almost ready for next level)
     */
    fun getXpInCurrentLevel(playerId: UUID, profession: Profession): Long {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return 0L
        val state = stateMap[profession] ?: return 0L
        
        val currentLevelXp = xpCalculator.xpForLevel(state.level)
        return state.xp - currentLevelXp
    }
    
    /**
     * Get XP needed to reach next level from current level.
     * Thread-safe - reads from cache.
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @return XP needed for this level bracket
     */
    fun getXpNeededForNextLevel(playerId: UUID, profession: Profession): Long {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return 0L
        val state = stateMap[profession] ?: return 0L
        
        return xpCalculator.xpForCurrentLevelBracket(state.level)
    }
    
    /**
     * Initialize player with default profession stats (all level 1, 0 XP).
     * Creates in-memory state immediately for non-blocking access.
     *
     * CRITICAL: Checks if player already exists in cache to prevent overwriting
     * existing data on world switch or quick reconnect.
     *
     * @param playerId Player's UUID
     */
    fun initializePlayerWithDefaults(playerId: UUID) {
        val playerIdStr = playerId.toCachedString()

        // Check if player already exists in cache (rejoin or world switch)
        if (playerStates.containsKey(playerIdStr)) {
            logger.atFine().log("Player $playerId already in cache, skipping default initialization")
            return
        }

        // Create default state for all 5 professions
        val professionsMap = mutableMapOf<Profession, PlayerProfessionState>()
        Profession.entries.forEach { profession ->
            professionsMap[profession] = PlayerProfessionState(
                playerId = playerIdStr,
                profession = profession,
                initialXp = 0L,
                initialLevel = 1
            )
        }

        playerStates[playerIdStr] = professionsMap

        logger.atFine().log("Initialized professions with defaults for player $playerId")
    }
    
    /**
     * Load player stats from database asynchronously and update state.
     * Called after initializePlayerWithDefaults().
     * 
     * @param playerId Player's UUID
     * @param repository The professions repository
     */
    fun updatePlayerStateAsync(playerId: UUID, repository: ProfessionsRepository) {
        val playerIdStr = playerId.toCachedString()
        
        scope.launch {
            try {
                // Load all 5 professions from database
                val statsMap = repository.ensureStats(playerIdStr)
                
                // Update in-memory state
                val stateMap = playerStates[playerIdStr]
                if (stateMap != null) {
                    statsMap.forEach { (profession, stats) ->
                        val state = stateMap[profession]
                        if (state != null) {
                            // Update XP and level from database
                            state.setXp(stats.xp, stats.level)
                        }
                    }
                    
                    logger.atFine().log("Loaded profession stats from database for player $playerId")
                } else {
                    logger.atWarning().log("Player $playerId not in cache during async load - already disconnected?")
                }
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to load profession stats for player $playerId")
            }
        }
    }
    
    // ============ XP Award (Hot Path - Thread Safe) ============
    
    /**
     * Award XP to a player in a specific profession.
     * 
     * Thread-safe atomic operation. Handles level-up detection.
     * 
     * @param playerId Player's UUID
     * @param profession The profession to award XP to
     * @param amount Amount of XP to award
     * @return XpAwardResult with level-up information
     */
    fun awardXp(playerId: UUID, profession: Profession, amount: Long): XpAwardResult {
        if (amount <= 0) {
            return XpAwardResult(didLevelUp = false, oldLevel = 1, newLevel = 1, newXp = 0L)
        }
        
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: run {
            logger.atWarning().log("Cannot award XP to $playerId - player not in cache")
            return XpAwardResult(didLevelUp = false, oldLevel = 1, newLevel = 1, newXp = 0L)
        }
        
        val state = stateMap[profession] ?: run {
            logger.atWarning().log("Cannot award XP to $playerId - profession $profession not found")
            return XpAwardResult(didLevelUp = false, oldLevel = 1, newLevel = 1, newXp = 0L)
        }
        
        // CRITICAL: Capture old level BEFORE awarding XP (race condition prevention)
        val oldLevel = state.level
        
        // Award XP atomically
        val newXp = state.awardXp(amount)
        
        // Detect level-up using atomic compareAndSet to prevent race
        val newLevel = state.detectLevelUp(oldLevel) { xp ->
            xpCalculator.calculateLevel(xp)
        }
        
        val didLevelUp = newLevel > oldLevel
        
        if (didLevelUp) {
            logger.atInfo().log("Player $playerId leveled up in ${profession.displayName}: $oldLevel -> $newLevel")
        }
        
        return XpAwardResult(
            didLevelUp = didLevelUp,
            oldLevel = oldLevel,
            newLevel = newLevel,
            newXp = newXp
        )
    }
    
    /**
     * Award XP with automatic multiplier application (e.g., Tier 1 abilities).
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @param baseAmount Base XP amount (before multipliers)
     * @param multiplier XP multiplier (e.g., 1.15 for +15% boost)
     * @return XpAwardResult
     */
    fun awardXpWithMultiplier(
        playerId: UUID,
        profession: Profession,
        baseAmount: Long,
        multiplier: Double = 1.0
    ): XpAwardResult {
        val finalAmount = (baseAmount * multiplier).toLong()
        return awardXp(playerId, profession, finalAmount)
    }
    
    // ============ Stats Query ============
    
    /**
     * Get stats for a specific profession.
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @return Immutable ProfessionStats or null if not cached
     */
    fun getStats(playerId: UUID, profession: Profession): ProfessionStats? {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return null
        val state = stateMap[profession] ?: return null
        
        return state.toImmutableStats()
    }
    
    /**
     * Get stats for all 5 professions.
     * 
     * @param playerId Player's UUID
     * @return Map of Profession to ProfessionStats, or empty map if not cached
     */
    fun getAllStats(playerId: UUID): Map<Profession, ProfessionStats> {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return emptyMap()
        
        return stateMap.mapValues { (_, state) ->
            state.toImmutableStats()
        }
    }
    
    /**
     * Get the current level for a profession.
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @return Current level or 1 if not cached
     */
    fun getLevel(playerId: UUID, profession: Profession): Int {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return 1
        val state = stateMap[profession] ?: return 1
        
        return state.level
    }
    
    // ============ Death Penalty ============
    
    /**
     * Apply death penalty to a player.
     * 
     * Progressive system with decay-based forgiveness:
     * - Base penalty: 10% of current level progress
     * - Increases by 3% per death in session (max 35%)
     * - Adaptive mercy: After 5 deaths, penalty reduced by 50%
     * - Affects 2 HIGHEST professions (not random)
     * - Cannot drop below current level
     * 
     * @param playerId Player's UUID
     * @return Map of affected professions to XP lost, plus death count
     */
    fun applyDeathPenalty(playerId: UUID): DeathPenaltyResult {
        if (!config.deathPenalty.enabled) {
            return DeathPenaltyResult(emptyMap(), 0, 0.0, false)
        }
        
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return DeathPenaltyResult(emptyMap(), 0, 0.0, false)
        
        // Get or create death penalty state
        val deathState = deathPenaltyStates.computeIfAbsent(playerIdStr) {
            DeathPenaltyState(playerId = playerIdStr)
        }
        
        // Record death
        val deathCount = deathState.recordDeath(config.deathPenalty.decay.deathWeight)
        
        // Check if adaptive mercy should activate
        if (config.deathPenalty.adaptiveMercy.enabled && deathState.shouldActivateMercy(config.deathPenalty.adaptiveMercy.deathThreshold)) {
            deathState.mercyActive = true
        }
        
        // Calculate current penalty percentage
        val penaltyPercent = deathState.calculatePenaltyPercent(
            basePercent = config.deathPenalty.baseXpLossPercent,
            progressiveIncrease = config.deathPenalty.progressiveIncreasePercent,
            maxPercent = config.deathPenalty.maxXpLossPercent,
            mercyReduction = if (deathState.mercyActive) config.deathPenalty.adaptiveMercy.penaltyReduction else 0.0
        )
        
        // Select 2 HIGHEST professions to penalize (not random)
        val professionsToAffect = stateMap.entries
            .sortedByDescending { (_, state) -> state.xp }
            .take(config.deathPenalty.affectedProfessions)
            .map { it.key }
        
        val lostXpMap = mutableMapOf<Profession, Long>()
        
        professionsToAffect.forEach { profession ->
            val state = stateMap[profession] ?: return@forEach
            
            // Apply penalty to CURRENT LEVEL PROGRESS ONLY (cannot drop levels)
            val currentXp = state.xp
            val currentLevel = state.level
            val xpForCurrentLevel = xpCalculator.xpForLevel(currentLevel)
            val levelProgress = currentXp - xpForCurrentLevel
            
            // Calculate XP loss (percentage of level progress)
            val lostXp = (levelProgress * penaltyPercent).toLong()
            
            // Apply loss (cannot drop below current level start)
            val newXp = (currentXp - lostXp).coerceAtLeast(xpForCurrentLevel)
            state.setXp(newXp, currentLevel)
            
            lostXpMap[profession] = lostXp
            
            logger.atInfo().log("Applied death penalty to ${profession.displayName} for player $playerId: -$lostXp XP (${(penaltyPercent * 100).toInt()}% penalty, death $deathCount)")
        }
        
        // Send warning messages if thresholds crossed
        sendDeathWarning(playerId, deathCount, penaltyPercent, deathState.mercyActive)
        
        return DeathPenaltyResult(lostXpMap, deathCount, penaltyPercent, deathState.mercyActive)
    }
    
    /**
     * Send death warning messages to player.
     * 
     * @param playerId Player UUID
     * @param deathCount Total deaths this session
     * @param penaltyPercent Current penalty percentage
     * @param mercyActive Whether adaptive mercy is active
     */
    private fun sendDeathWarning(playerId: UUID, deathCount: Int, penaltyPercent: Double, mercyActive: Boolean) {
        if (!config.deathPenalty.warnings.enabled) return
        
        val warningConfig = config.deathPenalty.warnings
        
        when (deathCount) {
            warningConfig.softWarningDeaths -> {
                logger.atInfo().log("⚠️ Soft death warning for player $playerId: $deathCount deaths, ${(penaltyPercent * 100).toInt()}% penalty")
                // TODO: Send in-game message when player messaging API is available
            }
            warningConfig.hardWarningDeaths -> {
                logger.atInfo().log("🚨 Hard death warning for player $playerId: $deathCount deaths, ${(penaltyPercent * 100).toInt()}% penalty")
                // TODO: Send in-game message with actionable advice
            }
            warningConfig.criticalWarningDeaths -> {
                logger.atInfo().log("💀 Critical death warning for player $playerId: $deathCount deaths, ${(penaltyPercent * 100).toInt()}% penalty, mercy active: $mercyActive")
                // TODO: Send in-game message with mercy system preview
            }
        }
    }
    
    /**
     * Apply decay to death penalties for a player.
     * Should be called periodically (every 5 minutes recommended).
     * 
     * @param playerId Player UUID
     * @return Number of deaths that fully decayed
     */
    fun applyDeathPenaltyDecay(playerId: UUID): Int {
        if (!config.deathPenalty.decay.enabled) return 0
        
        val playerIdStr = playerId.toCachedString()
        val deathState = deathPenaltyStates[playerIdStr] ?: return 0
        
        return deathState.applyDecay(config.deathPenalty.decay.decayRatePerHour)
    }
    
    /**
     * Clear death penalty state for a player (on logout).
     * 
     * @param playerId Player UUID
     */
    fun clearDeathPenaltyState(playerId: UUID) {
        val playerIdStr = playerId.toCachedString()
        deathPenaltyStates.remove(playerIdStr)
    }
    
    /**
     * Get death penalty state for a player (for display/debugging).
     * 
     * @param playerId Player UUID
     * @return DeathPenaltyState or null if none
     */
    fun getDeathPenaltyState(playerId: UUID): DeathPenaltyState? {
        val playerIdStr = playerId.toCachedString()
        return deathPenaltyStates[playerIdStr]
    }
    
    // ============ Admin Commands ============
    
    /**
     * Set a player's level in a profession (admin command).
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @param level Target level (1 to maxLevel)
     */
    fun setLevel(playerId: UUID, profession: Profession, level: Int) {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return
        val state = stateMap[profession] ?: return
        
        val clampedLevel = level.coerceIn(1, config.xpCurve.maxLevel)
        val requiredXp = xpCalculator.xpForLevel(clampedLevel)
        
        state.setXp(requiredXp, clampedLevel)
        
        logger.atInfo().log("Set ${profession.displayName} level to $clampedLevel for player $playerId")
    }
    
    /**
     * Add XP to a profession (admin command).
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     * @param amount XP to add
     * @return XpAwardResult
     */
    fun addXp(playerId: UUID, profession: Profession, amount: Long): XpAwardResult {
        return awardXp(playerId, profession, amount)
    }
    
    /**
     * Reset a profession to level 1 (admin command).
     * 
     * @param playerId Player's UUID
     * @param profession The profession
     */
    fun resetProfession(playerId: UUID, profession: Profession) {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr] ?: return
        val state = stateMap[profession] ?: return
        
        state.setXp(0L, 1)
        
        logger.atInfo().log("Reset ${profession.displayName} for player $playerId")
    }
    
    // ============ Persistence ============
    
    /**
     * Save a player's stats to the global database.
     * Called on player disconnect.
     * 
     * @param playerId Player's UUID
     * @param repository The professions repository
     */
    suspend fun savePlayer(playerId: UUID, repository: ProfessionsRepository) {
        val playerIdStr = playerId.toCachedString()
        val stateMap = playerStates[playerIdStr]
        
        if (stateMap == null) {
            logger.atWarning().log("No state found for player $playerId - cannot save")
            return
        }
        
        try {
            // Convert all 5 professions to immutable stats
            val statsList = stateMap.values.map { it.toImmutableStats() }
            
            // Save in batch transaction
            repository.saveAll(statsList)
            
            logger.atInfo().log("Saved profession stats for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to save profession stats for player $playerId")
            throw e
        }
    }
    
    /**
     * Remove a player from the cache.
     * Called after saving on disconnect.
     * 
     * @param playerId Player's UUID
     */
    fun removeFromCache(playerId: UUID) {
        val playerIdStr = playerId.toCachedString()
        playerStates.remove(playerIdStr)
        
        logger.atFine().log("Removed player $playerId from professions cache")
    }
    
    /**
     * Clear the entire cache.
     * Called during shutdown after saving all players.
     */
    fun clearCache() {
        playerStates.clear()
        logger.atFine().log("Professions cache cleared")
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
    fun isPlayerCached(playerId: UUID): Boolean {
        return playerStates.containsKey(playerId.toCachedString())
    }
}

/**
 * Result of an XP award operation.
 */
data class XpAwardResult(
    /** Whether the player leveled up */
    val didLevelUp: Boolean,
    
    /** Level before XP was awarded */
    val oldLevel: Int,
    
    /** Level after XP was awarded */
    val newLevel: Int,
    
    /** New total XP */
    val newXp: Long
)

/**
 * Result of applying death penalty.
 * 
 * @property lostXpMap Map of professions to XP lost
 * @property deathCount Total deaths this session
 * @property penaltyPercent Penalty percentage applied (0.0-1.0)
 * @property mercyActive Whether adaptive mercy is active
 */
data class DeathPenaltyResult(
    val lostXpMap: Map<Profession, Long>,
    val deathCount: Int,
    val penaltyPercent: Double,
    val mercyActive: Boolean
)
