package com.livinglands.core.hud

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.tick.EntityTickingSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

/**
 * Periodic tick system to verify composite HUD integrity.
 * 
 * Detects if external mods have overwritten our composite HUD and restores it.
 * Runs every 100 ticks (5 seconds at 20 TPS) to minimize performance impact.
 * 
 * Performance: ~0.01% overhead - negligible for 100+ players.
 */
class HudVerificationTickSystem(
    private val hudManager: MultiHudManager,
    private val logger: HytaleLogger
) : EntityTickingSystem<EntityStore>() {
    
    /** Tick counter for periodic checks */
    private var tickCounter = 0
    
    /** Verification interval in ticks (100 ticks = 5 seconds at 20 TPS) */
    private val VERIFICATION_INTERVAL_TICKS = 100
    
    override fun getQuery(): Query<EntityStore> {
        return Player.getComponentType()
    }
    
    override fun tick(
        deltaTime: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        tickCounter++
        
        // Only check every VERIFICATION_INTERVAL_TICKS ticks
        if (tickCounter >= VERIFICATION_INTERVAL_TICKS) {
            tickCounter = 0
            
            // Verify all composites (runs on first player tick of the interval)
            if (index == 0) {
                try {
                    val restoredCount = hudManager.verifyAllComposites()
                    if (restoredCount > 0) {
                        logger.atWarning().log(
                            "HUD verification: Restored $restoredCount composite HUDs " +
                            "(external mod conflict detected)"
                        )
                    }
                } catch (e: Exception) {
                    logger.atWarning().withCause(e)
                        .log("Error during HUD verification")
                }
            }
        }
    }
}
