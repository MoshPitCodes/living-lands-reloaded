package com.livinglands.core

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.persistence.PersistenceService
import com.livinglands.core.persistence.PlayerDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Per-world context containing world-specific data.
 * Each world has completely isolated player data and persistence.
 */
class WorldContext(
    val worldId: UUID,
    val worldName: String,
    private val dataDir: File,
    private val logger: HytaleLogger
) {
    // Module-specific data storage (lazy initialized)
    @PublishedApi
    internal val moduleData = ConcurrentHashMap<KClass<*>, Any>()
    
    // Track whether persistence was initialized
    private var persistenceInitialized = false
    
    // Persistence layer (lazy initialized)
    private val _persistence: Lazy<PersistenceService> = lazy {
        persistenceInitialized = true
        PersistenceService(worldId, dataDir, logger)
    }
    
    val persistence: PersistenceService
        get() = _persistence.value
    
    // Player data repository (lazy initialized)
    val playerRepository: PlayerDataRepository by lazy {
        PlayerDataRepository(persistence, logger)
    }
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default)
    
    /**
     * Get or create module-specific data.
     */
    inline fun <reified T : Any> getData(factory: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return moduleData.getOrPut(T::class) {
            factory()
        } as T
    }
    
    /**
     * Get module-specific data if it exists.
     */
    inline fun <reified T : Any> getDataOrNull(): T? {
        @Suppress("UNCHECKED_CAST")
        return moduleData[T::class] as? T
    }
    
    /**
     * Check if module data exists.
     */
    inline fun <reified T : Any> hasData(): Boolean {
        return moduleData.containsKey(T::class)
    }
    
    /**
     * Remove module data.
     */
    inline fun <reified T : Any> removeData(): T? {
        @Suppress("UNCHECKED_CAST")
        return moduleData.remove(T::class) as? T
    }
    
    /**
     * Handle player joining the world.
     * Ensures player record exists and updates last seen.
     */
    fun onPlayerJoin(playerId: String, playerName: String) {
        // TODO: Make this async again once coroutines issue is fixed
        try {
            runBlocking {
                playerRepository.ensurePlayer(playerId, playerName)
                playerRepository.updateLastSeen(playerId, System.currentTimeMillis())
            }
            logger.atInfo().log("Player $playerName ($playerId) persisted to database")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Error handling player join for $playerId")
        }
    }
    
    /**
     * Handle player leaving the world.
     * Updates last seen timestamp.
     */
    fun onPlayerLeave(playerId: String) {
        // TODO: Make this async again once coroutines issue is fixed
        try {
            runBlocking {
                playerRepository.updateLastSeen(playerId, System.currentTimeMillis())
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Error handling player leave for $playerId")
        }
    }
    
    /**
     * Cleanup world context when world is removed.
     * Closes database connection and clears all module data.
     */
    fun cleanup() {
        // Wait for all pending coroutines to complete
        runBlocking {
            scope.coroutineContext[Job]?.children?.forEach { it.join() }
        }
        
        // Cancel the scope to prevent new operations
        scope.cancel()
        
        // Close persistence if initialized
        if (persistenceInitialized && _persistence.isInitialized()) {
            persistence.close()
        }
        
        // Clear all module data
        moduleData.clear()
    }
}
