package com.livinglands.core

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages world lifecycle and provides WorldContext instances.
 * Each world has isolated player data and persistence.
 */
class WorldRegistry(
    private val dataDir: File,
    private val logger: HytaleLogger
) {
    
    private val worlds = ConcurrentHashMap<UUID, WorldContext>()
    
    /**
     * Reverse lookup: world name (lowercase) → UUID.
     * Updated whenever worlds are added/removed.
     */
    private val nameToUuid = ConcurrentHashMap<String, UUID>()
    
    /**
     * Called when a world is added to the universe.
     */
    fun onWorldAdded(event: AddWorldEvent) {
        val world = event.world
        val worldId = world.worldConfig.uuid
        val worldName = world.name
        
        val context = WorldContext(
            worldId = worldId,
            worldName = worldName,
            dataDir = dataDir,
            logger = logger
        )
        
        worlds[worldId] = context
        nameToUuid[worldName.lowercase()] = worldId  // Case-insensitive lookup
        
        LoggingManager.debug(logger, "core") { "World registered: $worldName ($worldId)" }
    }
    
    /**
     * Called when a world is removed from the universe.
     */
    fun onWorldRemoved(event: RemoveWorldEvent) {
        val world = event.world
        val worldId = world.worldConfig.uuid
        val worldName = world.name
        
        val context = worlds.remove(worldId)
        if (context != null) {
            nameToUuid.remove(worldName.lowercase())
            context.cleanup()
        }
        
        LoggingManager.debug(logger, "core") { "World unregistered: $worldName ($worldId)" }
    }
    
    /**
     * Get WorldContext by world UUID.
     */
    fun getContext(worldId: UUID): WorldContext? = worlds[worldId]
    
    /**
     * Get WorldContext by World instance.
     */
    fun getContext(world: World): WorldContext? = worlds[world.worldConfig.uuid]
    
    /**
     * Find world UUID by name (case-insensitive).
     * Returns null if world doesn't exist.
     */
    fun getWorldIdByName(name: String): UUID? {
        return nameToUuid[name.lowercase()]
    }
    
    /**
     * Find world context by name (case-insensitive).
     * Returns null if world doesn't exist.
     */
    fun getContextByName(name: String): WorldContext? {
        val uuid = nameToUuid[name.lowercase()] ?: return null
        return worlds[uuid]
    }
    
    /**
     * Get all world names (for validation/suggestions).
     */
    fun getAllWorldNames(): Set<String> {
        return worlds.values.map { it.worldName }.toSet()
    }
    
    /**
     * Get or create WorldContext for a world (lazy creation).
     * Used when AddWorldEvent doesn't fire but we need the context.
     */
    fun getOrCreateContext(world: World): WorldContext {
        val worldId = world.worldConfig.uuid
        val worldName = world.name
        return worlds.getOrPut(worldId) {
            LoggingManager.debug(logger, "core") { "Creating lazy WorldContext for $worldName ($worldId)" }
            // Also update name mapping for lazy-created worlds
            nameToUuid[worldName.lowercase()] = worldId
            WorldContext(
                worldId = worldId,
                worldName = worldName,
                dataDir = dataDir,
                logger = logger
            )
        }
    }
    
    /**
     * Get all active world contexts.
     */
    fun getAllContexts(): Collection<WorldContext> = worlds.values
    
    /**
     * Check if a world is registered.
     */
    fun hasWorld(worldId: UUID): Boolean = worlds.containsKey(worldId)
    
    /**
     * Get the count of registered worlds.
     */
    fun getWorldCount(): Int = worlds.size
    
    /**
     * Clear all world contexts (used during shutdown).
     */
    fun clear() {
        worlds.values.forEach { it.cleanup() }
        worlds.clear()
        nameToUuid.clear()
    }
}
