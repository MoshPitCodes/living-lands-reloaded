package com.livinglands.core

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.UUID

/**
 * Represents an active player session.
 * Tracks player's current world, entity reference, and store access.
 */
data class PlayerSession(
    val playerId: UUID,
    val entityRef: Ref<EntityStore>,
    val store: Store<EntityStore>,
    val worldId: UUID
)
