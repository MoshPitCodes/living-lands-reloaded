package com.livinglands.modules.metabolism

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule

/**
 * ECS system that processes metabolism ticks for all players.
 * 
 * Runs every tick but only processes metabolism every 1 second
 * to avoid excessive computation.
 * 
 * For each player:
 * 1. Detects their current activity state from MovementStatesComponent
 * 2. Calculates time delta since last metabolism tick
 * 3. Calls MetabolismService to deplete stats
 */
class MetabolismTickSystem(
    private val metabolismService: MetabolismService,
    private val logger: HytaleLogger
) : EntityTickingSystem<EntityStore>() {
    
    /**
     * Track last tick time for rate limiting.
     * We only process metabolism every 1 second.
     */
    @Volatile
    private var lastTickTimeMs = System.currentTimeMillis()
    
    /**
     * Minimum interval between metabolism ticks in milliseconds.
     */
    private val tickIntervalMs = 1000L
    
    /**
     * Query for Player component to iterate over all players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }
    
    /**
     * Called every tick for each entity matching the query.
     * We rate-limit internally to only process every 1 second.
     */
    override fun tick(
        deltaTime: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val currentTime = System.currentTimeMillis()
        
        // Check if we should process this tick
        val timeSinceLastTick = currentTime - lastTickTimeMs
        
        // Only process if at least 1 second has passed
        if (timeSinceLastTick < tickIntervalMs) {
            return
        }
        
        // Get entity reference
        val ref: Ref<EntityStore> = chunk.getReferenceTo(index)
        if (!ref.isValid) return
        
        // Get Player component
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        
        // Get player UUID
        @Suppress("DEPRECATION")
        val playerId = player.getUuid() ?: return
        val playerIdStr = playerId.toString()
        
        // Skip if player not in metabolism cache
        if (!metabolismService.isPlayerCached(playerIdStr)) return
        
        // Get MovementStatesComponent to detect activity
        val movementStates = store.getComponent(ref, MovementStatesComponent.getComponentType())
        
        // Determine activity state
        val activityState = ActivityState.fromMovementStates(movementStates)
        
        // Calculate delta time in seconds
        val deltaTimeSeconds = timeSinceLastTick / 1000f
        
        // Process metabolism tick
        try {
            metabolismService.processTick(playerIdStr, deltaTimeSeconds, activityState)
            
            // Update HUD if stats changed significantly
            if (metabolismService.updateHudIfNeeded(playerIdStr)) {
                // HUD was updated - trigger show() to push changes to client
                val hudElement = metabolismService.getHudElement(playerIdStr)
                hudElement?.show()
            }
            
            // Update last tick time only on first entity processed this interval
            // This ensures consistent timing for all players
            if (index == 0) {
                lastTickTimeMs = currentTime
            }
        } catch (e: Exception) {
            // Log but don't crash - metabolism is not critical
            if (CoreModule.isDebug()) {
                logger.atFine().log("Error processing metabolism tick for $playerId: ${e.message}")
            }
        }
    }
}
