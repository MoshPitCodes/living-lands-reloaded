package com.livinglands.modules.metabolism.food

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.modules.metabolism.config.FoodConsumptionConfig
import com.livinglands.core.toCachedString
import java.util.UUID

/**
 * ECS system that detects food consumption by monitoring entity effects.
 * 
 * **Timing:** 30 TPS = 33.33ms per tick
 * - Runs every N ticks (configurable, default 2 ticks = 66.66ms)
 * - Processes M players per tick (configurable, default 10 players)
 * - Instant heal effects last ~100ms (3 ticks), so 2-tick detection catches them
 * 
 * **Batching Strategy:**
 * With 100 players, batch size 10, and 2-tick interval:
 * - 10 batches needed to process all players
 * - Full cycle = 10 batches × 2 ticks = 20 ticks = 666ms
 * - Still fast enough to catch 100ms instant effects
 * 
 * **Performance:**
 * - Batched processing prevents O(n) overhead every tick
 * - Only processes active players (those in session registry)
 * - ECS access wrapped in world.execute for thread safety
 * 
 * @property foodEffectDetector Detects new food effects on players
 * @property foodConsumptionProcessor Processes detected food and restores stats
 * @property config Food consumption configuration
 * @property logger Logger for debugging
 */
class FoodDetectionTickSystem(
    private val foodEffectDetector: FoodEffectDetector,
    private val foodConsumptionProcessor: FoodConsumptionProcessor,
    private val config: FoodConsumptionConfig,
    private val logger: HytaleLogger
) : EntityTickingSystem<EntityStore>() {
    
    /**
     * Tick counter for detection interval.
     * Increments every tick, resets when reaching detectionTickInterval.
     */
    private var tickCounter = 0
    
    /**
     * Current player batch offset.
     * Tracks which players were processed last time.
     */
    private var batchOffset = 0
    
    /**
     * List of player IDs to process.
     * Rebuilt each detection cycle from active sessions.
     */
    private var playerList = mutableListOf<UUID>()
    
    /**
     * Query for Player component to iterate over all players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }
    
    /**
     * Called every tick.
     * Implements tick counting and batched processing.
     * 
     * Unlike MetabolismTickSystem which processes every player every tick,
     * this system only processes a batch of players every N ticks.
     */
    override fun tick(
        deltaTime: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        // Only process on master entity (index 0) to avoid redundant work
        if (index != 0) return
        
        // Increment tick counter
        tickCounter++
        
        // Only process on detection interval (e.g., every 2 ticks)
        if (tickCounter < config.detectionTickInterval) {
            return
        }
        
        // Reset tick counter
        tickCounter = 0
        
        // Rebuild player list if starting a new cycle
        if (batchOffset == 0) {
            playerList.clear()
            playerList.addAll(CoreModule.players.getAllSessions().map { it.playerId })
            
            // Shuffle to distribute load (optional)
            // playerList.shuffle()
        }
        
        // Calculate batch range
        val totalPlayers = playerList.size
        if (totalPlayers == 0) return
        
        val batchSize = config.batchSize
        val start = batchOffset
        val end = minOf(start + batchSize, totalPlayers)
        
        // Process batch
        for (i in start until end) {
            val playerId = playerList[i]
            processPlayer(playerId)
        }
        
        // Update offset for next tick
        batchOffset = end
        if (batchOffset >= totalPlayers) {
            batchOffset = 0 // Reset for next cycle
        }
    }
    
    /**
     * Process a single player for food consumption detection.
     * 
     * **IMPORTANT:** All ECS access is wrapped in world.execute() for thread safety.
     * 
     * @param playerId Player's UUID
     */
    private fun processPlayer(playerId: UUID) {
        // Get player session
        val session = CoreModule.players.getSession(playerId) ?: return
        
        // Execute ECS access on world thread
        session.world.execute {
            try {
                // Check for new food effects
                val detections = foodEffectDetector.checkForNewFoodEffects(playerId, session)
                
                // Process each detected consumption
                for (detection in detections) {
                    foodConsumptionProcessor.processConsumption(playerId, detection)
                }
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error processing food consumption for player $playerId")
            }
        }
    }
}
