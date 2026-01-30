package com.livinglands.core

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.persistence.GlobalPlayerDataRepository
import com.livinglands.core.persistence.PersistenceService
import com.livinglands.modules.metabolism.config.MetabolismConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Per-world context containing world-specific data.
 * Each world has its own persistence for world-specific data (claims, etc.)
 * while player identity and global stats are stored in the global database.
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
    
    // Persistence layer for world-specific data (lazy initialized)
    // Note: Player identity is stored globally, not here
    private val _persistence: Lazy<PersistenceService> = lazy {
        persistenceInitialized = true
        PersistenceService(worldId, dataDir, logger)
    }
    
    val persistence: PersistenceService
        get() = _persistence.value
    
    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Coroutine scope for persistence operations (fire-and-forget)
    private val persistenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Cached world-specific metabolism config.
     * Pre-merged from global + world overrides.
     * Updated on config reload.
     * 
     * @Volatile ensures visibility across world threads.
     */
    @Volatile
    var metabolismConfig: MetabolismConfig? = null
        internal set
    
    /**
     * Resolve and cache metabolism config for this world.
     * Called on world creation and config reload.
     */
    internal fun resolveMetabolismConfig(globalConfig: MetabolismConfig) {
        // Check for both name-based and UUID-based overrides
        val byName = globalConfig.worldOverrides.entries.firstOrNull { 
            it.key.equals(worldName, ignoreCase = true) 
        }?.value
        
        val byId = globalConfig.worldOverrides[worldId.toString()]
        
        // Warn if both exist and are different (ambiguous configuration)
        if (byName != null && byId != null && byName != byId) {
            logger.atWarning().log(
                "World '$worldName' ($worldId) has conflicting overrides by name and UUID. " +
                "Using name-based override. Consider removing one to avoid confusion."
            )
        }
        
        // Prefer name-based override, fall back to UUID-based, then global config
        val resolved = (byName ?: byId)?.let { override ->
            globalConfig.mergeOverride(override)
        } ?: globalConfig
        
        metabolismConfig = resolved
        logger.atFine().log(
            "Resolved metabolism config for world $worldName: " +
            "hunger.rate=${resolved.hunger.baseDepletionRateSeconds}, " +
            "thirst.rate=${resolved.thirst.baseDepletionRateSeconds}, " +
            "energy.rate=${resolved.energy.baseDepletionRateSeconds}"
        )
    }
    
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
     * Ensures player record exists in the GLOBAL database asynchronously.
     * Player identity is stored globally, not per-world.
     * 
     * This method returns immediately (fire-and-forget pattern) to avoid
     * blocking the WorldThread during database operations.
     */
    fun onPlayerJoin(playerId: String, playerName: String) {
        // Fire-and-forget: Launch async operation without blocking
        persistenceScope.launch {
            try {
                val globalPlayerRepo = CoreModule.services.get<GlobalPlayerDataRepository>()
                    ?: throw IllegalStateException("GlobalPlayerDataRepository not registered")
                
                val uuid = UUID.fromString(playerId)
                globalPlayerRepo.ensurePlayer(uuid, playerName)
                globalPlayerRepo.updateLastSeen(uuid, System.currentTimeMillis())
                
                logger.atFine().log("Player $playerName ($playerId) persisted to global database")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error persisting player join for $playerId")
            }
        }
    }
    
    /**
     * Handle player leaving the world.
     * Updates last seen timestamp in the GLOBAL database asynchronously.
     * 
     * This method returns immediately (fire-and-forget pattern) to avoid
     * blocking during player disconnect.
     */
    fun onPlayerLeave(playerId: String) {
        // Fire-and-forget: Launch async operation without blocking
        persistenceScope.launch {
            try {
                val globalPlayerRepo = CoreModule.services.get<GlobalPlayerDataRepository>()
                    ?: throw IllegalStateException("GlobalPlayerDataRepository not registered")
                
                globalPlayerRepo.updateLastSeen(UUID.fromString(playerId), System.currentTimeMillis())
                logger.atFine().log("Updated last seen for player $playerId")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error updating last seen for $playerId")
            }
        }
    }
    
    /**
     * Cleanup world context when world is removed.
     * Closes database connection and clears all module data.
     * 
     * IMPORTANT: This method gives coroutines a brief grace period (100ms) to complete
     * before forcing cancellation. This prevents data loss from in-flight writes.
     */
    fun cleanup() {
        try {
            // Give coroutines a brief grace period (100ms) to complete their work
            // This prevents data loss from in-flight database writes
            try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(100) {
                        // Wait for persistence operations to complete
                        persistenceScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { 
                            it.join() 
                        }
                        // Wait for general operations to complete
                        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { 
                            it.join() 
                        }
                    }
                }
            } catch (e: Exception) {
                logger.atFine().log("Cleanup timeout for world $worldId, forcing close")
            }
            
            // Cancel scopes to prevent new operations from starting
            scope.cancel("WorldContext cleanup")
            persistenceScope.cancel("WorldContext cleanup")
            
            // Close persistence if initialized - this flushes any pending writes
            if (persistenceInitialized && _persistence.isInitialized()) {
                persistence.close()
            }
            
            // Clear all module data
            moduleData.clear()
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Error during WorldContext cleanup for world $worldId")
        }
    }
}
