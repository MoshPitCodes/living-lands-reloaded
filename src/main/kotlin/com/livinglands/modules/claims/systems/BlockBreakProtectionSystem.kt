package com.livinglands.modules.claims.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.claims.ChunkPosition
import com.livinglands.modules.claims.ClaimsService

/**
 * ECS system for protecting blocks from unauthorized breaking.
 *
 * Prevents players from breaking blocks in claimed chunks unless they own
 * the claim or are trusted by the owner.
 *
 * Event: BreakBlockEvent (ECS event)
 * - Triggered when a player breaks a block
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * - Can cancel the event to prevent the block break
 */
class BlockBreakProtectionSystem(
    private val claimsService: ClaimsService,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {

    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()

    /**
     * Query to match entities - protection only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }

    /**
     * Handle a block break event and prevent it if unauthorized.
     *
     * @param index Entity index in the archetype chunk
     * @param chunk Archetype chunk containing entity data
     * @param store Entity component store
     * @param buffer Command buffer for ECS modifications
     * @param event The BreakBlockEvent
     */
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent
    ) {
        // Get PlayerRef component from the entity that triggered this event
        val playerRef = chunk.getComponent(index, playerRefType) ?: return

        // Get player UUID
        val playerId = playerRef.uuid

        // Get block position from event
        val blockPosition = event.targetBlock

        // Calculate chunk position from block position
        val chunkX = blockPosition.x shr 4
        val chunkZ = blockPosition.z shr 4

        // Get world UUID from PlayerRef
        val worldId = playerRef.worldUuid ?: return

        val chunkPosition = ChunkPosition(worldId, chunkX, chunkZ)

        // Check if player can build in this chunk
        if (!claimsService.canBuild(playerId, chunkPosition)) {
            // Cancel the event to prevent block break
            event.setCancelled(true)

            LoggingManager.debug(logger, "claims") {
                "Blocked block break by $playerId at chunk ($chunkX, $chunkZ)"
            }
        }
    }
}
