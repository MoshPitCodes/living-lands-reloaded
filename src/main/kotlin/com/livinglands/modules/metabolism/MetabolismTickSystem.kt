package com.livinglands.modules.metabolism

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.util.toCachedString

/**
 * ECS system that processes metabolism ticks for all players.
 * 
 * Runs every game tick (~30 ticks/sec) for each player entity.
 * Rate limiting is handled per-player in MetabolismService.processTickWithDelta().
 * 
 * For each player:
 * 1. Checks if player is in Creative mode (skip if true)
 * 2. Detects their current activity state from MovementStatesComponent
 * 3. Delegates to MetabolismService for delta calculation and stat depletion
 * 
 * Performance optimizations (v1.0.1):
 * - UUID.toString() replaced with toCachedString() to avoid allocations
 * - Single service call handles timing, stats, and HUD updates
 * - Removed unused lastTickTimeMs/tickIntervalMs fields (timing is per-player in service)
 * 
 * Metabolism is paused when:
 * - Player is in Creative mode (both singleplayer and servers)
 * - Note: ESC menu pause in singleplayer doesn't affect server-side ticks
 */
class MetabolismTickSystem(
    private val metabolismService: MetabolismService,
    private val logger: HytaleLogger
) : EntityTickingSystem<EntityStore>() {
    
    // Note: Removed unused lastTickTimeMs and tickIntervalMs fields.
    // Rate limiting is now handled per-player in MetabolismService.processTickWithDelta()
    // using PlayerMetabolismState.lastDepletionTime for accurate per-player timing.
    
    /**
     * Query for Player component to iterate over all players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }
    
    /**
     * Called every tick for each entity matching the query.
     * 
     * Performance: Uses cached UUID string to avoid allocations.
     * Rate limiting is handled internally by MetabolismService.
     */
    override fun tick(
        deltaTime: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Get entity reference first
        val ref: Ref<EntityStore> = chunk.getReferenceTo(index)
        if (!ref.isValid) return
        
        // Get Player component
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        
        // Get player UUID and use cached string (avoids allocation every tick!)
        @Suppress("DEPRECATION")
        val playerId = player.getUuid() ?: return
        val playerIdStr = playerId.toCachedString()
        
        // Skip if player not in metabolism cache
        if (!metabolismService.isPlayerCached(playerIdStr)) return
        
        // Skip metabolism processing if player is in Creative mode
        // BUT update lastDepletionTime to prevent huge delta when returning to survival
        val gameMode = player.gameMode
        if (gameMode == GameMode.Creative) {
            // Update timestamp to current time so delta is small when resuming
            val state = metabolismService.getState(playerIdStr)
            if (state != null) {
                state.lastDepletionTime = System.currentTimeMillis()
            }
            
            // Force HUD update to show current (frozen) values
            // This ensures the HUD displays the correct values while in creative mode
            if (metabolismService.updateHudIfNeeded(playerIdStr)) {
                val hudElement = metabolismService.getHudElement(playerIdStr)
                hudElement?.show()
            }
            
            return
        }
        
        // Get MovementStatesComponent to detect activity
        val movementStates = store.getComponent(ref, MovementStatesComponent.getComponentType())
        
        // Determine activity state
        val activityState = ActivityState.fromMovementStates(movementStates)
        
        // Process metabolism tick using the service's internal delta tracking
        // This handles timing correctly per-player with consolidated state
        try {
            metabolismService.processTickWithDelta(playerIdStr, activityState)
            
            // Update HUD if stats changed significantly
            if (metabolismService.updateHudIfNeeded(playerIdStr)) {
                // HUD was updated - trigger show() to push changes to client
                val hudElement = metabolismService.getHudElement(playerIdStr)
                hudElement?.show()
            }
        } catch (e: Exception) {
            // Log but don't crash - metabolism is not critical
            if (CoreModule.isDebug()) {
                logger.atFine().log("Error processing metabolism tick for $playerId: ${e.message}")
            }
        }
    }
}
