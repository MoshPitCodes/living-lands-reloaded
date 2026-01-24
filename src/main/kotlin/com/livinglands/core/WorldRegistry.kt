package com.livinglands.core

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
     * Called when a world is added to the universe.
     */
    fun onWorldAdded(event: AddWorldEvent) {
        val world = event.world
        val worldId = world.worldConfig.uuid
        
        val context = WorldContext(
            worldId = worldId,
            worldName = world.name,
            dataDir = dataDir,
            logger = logger
        )
        
        worlds[worldId] = context
        
        logger.atFine().log("World registered: ${world.name} ($worldId)")
    }
    
    /**
     * Called when a world is removed from the universe.
     */
    fun onWorldRemoved(event: RemoveWorldEvent) {
        val world = event.world
        val worldId = world.worldConfig.uuid
        
        val context = worlds.remove(worldId)
        context?.cleanup()
        
        logger.atFine().log("World unregistered: ${world.name} ($worldId)")
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
     * Get or create WorldContext for a world (lazy creation).
     * Used when AddWorldEvent doesn't fire but we need the context.
     */
    fun getOrCreateContext(world: World): WorldContext {
        val worldId = world.worldConfig.uuid
        return worlds.getOrPut(worldId) {
            logger.atInfo().log("Creating lazy WorldContext for ${world.name} ($worldId)")
            WorldContext(
                worldId = worldId,
                worldName = world.name,
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
    }
}
