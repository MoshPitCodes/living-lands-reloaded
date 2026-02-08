package com.livinglands.modules.claims

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.modules.claims.cache.ClaimsCache
import com.livinglands.modules.claims.config.ClaimsConfig
import com.livinglands.modules.claims.repository.ClaimsRepository
import java.util.UUID

/**
 * Claims service - business logic for land protection with multi-chunk plots.
 * 
 * **Stateless Design:**
 * - No mutable state (repository and cache are dependencies)
 * - All methods return sealed Result types for better error handling
 * - Async operations use suspend functions
 * 
 * **Performance:**
 * - Hot path (canBuild): O(1) cache lookup only
 * - Cold path (createPlot/removeChunks): Async DB operations via repository
 * - Cache-first strategy: Check cache, fallback to DB if miss
 * 
 * **Multi-Chunk Plot Model:**
 * - One Claim (plot) can span multiple chunks
 * - Players create plots via grid UI (selecting multiple chunks)
 * - Trust is per-plot, not per-chunk
 * - Limits: maxPlotsPerPlayer, maxChunksPerPlot, maxTotalChunks
 * 
 * @param repository Database persistence layer
 * @param cache In-memory cache for hot path lookups
 * @param config Claims configuration
 * @param logger Logger for service operations
 */
class ClaimsService(
    private val repository: ClaimsRepository,
    private val cache: ClaimsCache,
    private val config: ClaimsConfig,
    private val logger: HytaleLogger
) {
    
    /**
     * Create a new plot (multi-chunk claim) for a player.
     * 
     * **Validation:**
     * - All chunks must be unclaimed
     * - Player must not have reached max plots limit
     * - Chunk count must not exceed maxChunksPerPlot
     * - Total chunk count must not exceed maxTotalChunks
     * 
     * **Side Effects:**
     * - Creates claim in database with all chunks
     * - Adds claim to cache
     * 
     * @param playerId UUID of the claiming player
     * @param chunks Set of chunk positions to include in this plot
     * @param name Optional name for the plot
     * @return Success with Claim, or specific error
     */
    suspend fun createPlot(playerId: UUID, chunks: Set<ChunkPosition>, name: String? = null): ClaimResult {
        if (chunks.isEmpty()) {
            return ClaimResult.DatabaseError  // No chunks to claim
        }

        // Check per-plot chunk limit
        if (chunks.size > config.limits.maxChunksPerPlot) {
            return ClaimResult.LimitReached(chunks.size, config.limits.maxChunksPerPlot)
        }

        // Check all chunks are unclaimed (cache first, then DB)
        for (chunk in chunks) {
            val existingClaim = cache.get(chunk) ?: repository.getClaimAt(chunk)
            if (existingClaim != null) {
                return ClaimResult.AlreadyClaimed(existingClaim.owner)
            }
        }
        
        // Check plot limit
        val currentPlotCount = cache.getClaimCount(playerId).takeIf { it > 0 }
            ?: repository.getPlotCount(playerId)
        
        if (currentPlotCount >= config.limits.maxPlotsPerPlayer) {
            return ClaimResult.PlotLimitReached(currentPlotCount, config.limits.maxPlotsPerPlayer)
        }

        // Check total chunk limit
        val currentChunkCount = cache.getChunkCount(playerId).takeIf { it > 0 }
            ?: repository.getTotalChunkCount(playerId)
        
        if (currentChunkCount + chunks.size > config.limits.maxTotalChunks) {
            return ClaimResult.ChunkLimitReached(currentChunkCount, config.limits.maxTotalChunks)
        }
        
        // Create claim
        val claim = Claim(
            id = UUID.randomUUID(),
            owner = playerId,
            chunks = chunks,
            name = name,
            trustedPlayers = emptySet(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // Persist to database
        val success = repository.createClaim(claim)
        if (!success) {
            return ClaimResult.DatabaseError
        }
        
        // Update cache
        cache.put(claim)
        
        LoggingManager.debug(logger, "claims") {
            "Player $playerId created plot ${claim.id} with ${chunks.size} chunks"
        }
        return ClaimResult.Success(claim)
    }

    /**
     * Remove chunks from a plot (multi-chunk unclaim).
     * If all chunks are removed, the entire plot is deleted.
     * 
     * **Validation:**
     * - Claim must exist
     * - Player must be the owner
     * - Chunks must belong to this claim
     * 
     * **Side Effects:**
     * - Removes chunks from database
     * - Updates or removes claim from cache
     * - Deletes plot if no chunks remain
     * 
     * @param playerId UUID of the owner
     * @param claimId UUID of the claim/plot
     * @param chunksToRemove Set of chunk positions to remove
     * @return Success, or specific error
     */
    suspend fun removeChunksFromPlot(
        playerId: UUID,
        claimId: UUID,
        chunksToRemove: Set<ChunkPosition>
    ): UnclaimResult {
        if (chunksToRemove.isEmpty()) {
            return UnclaimResult.Success
        }

        // Find claim (cache first)
        val claim = cache.getById(claimId) ?: repository.getClaimById(claimId)
            ?: return UnclaimResult.NotClaimed
        
        // Verify ownership
        if (claim.owner != playerId) {
            return UnclaimResult.NotOwned(claim.owner)
        }

        // Check which chunks actually belong to this claim
        val validChunks = chunksToRemove.intersect(claim.chunks)
        if (validChunks.isEmpty()) {
            return UnclaimResult.NotClaimed
        }

        val remainingChunks = claim.chunks - validChunks

        if (remainingChunks.isEmpty()) {
            // Delete entire plot
            val success = repository.deleteClaim(claim.id, playerId)
            if (!success) {
                return UnclaimResult.DatabaseError
            }
            cache.remove(claim.id)
            LoggingManager.debug(logger, "claims") {
                "Player $playerId deleted plot ${claim.id} (all chunks removed)"
            }
        } else {
            // Remove only the specified chunks
            val success = repository.removeChunksFromClaim(claim.id, validChunks)
            if (!success) {
                return UnclaimResult.DatabaseError
            }
            // Update cache with reduced chunk set
            val updatedClaim = claim.copy(
                chunks = remainingChunks,
                updatedAt = System.currentTimeMillis()
            )
            cache.update(updatedClaim)
            LoggingManager.debug(logger, "claims") {
                "Player $playerId removed ${validChunks.size} chunks from plot ${claim.id} (${remainingChunks.size} remaining)"
            }
        }

        return UnclaimResult.Success
    }

    /**
     * Check if a player can build at a chunk position (HOT PATH).
     * 
     * **Performance Requirements:**
     * - Must be < 0.1ms
     * - Cache-only lookup (no database access)
     * - O(1) complexity
     * 
     * **Returns:**
     * - `true` if unclaimed (not in cache)
     * - `true` if player is owner or trusted
     * - `false` if claimed by someone else
     */
    fun canBuild(playerId: UUID, position: ChunkPosition): Boolean {
        val claim = cache.get(position) ?: return true  // Unclaimed = can build
        return claim.canBuild(playerId)
    }
    
    /**
     * Add a trusted player to a claim.
     * 
     * @param ownerId UUID of the claim owner (for verification)
     * @param claimId UUID of the claim
     * @param targetPlayerId UUID of player to trust
     */
    suspend fun trustPlayer(ownerId: UUID, claimId: UUID, targetPlayerId: UUID): TrustResult {
        // Find claim (cache first, then database by ID)
        val claim = cache.getById(claimId)
            ?: repository.getClaimById(claimId)
            ?: return TrustResult.ClaimNotFound
        
        // Verify ownership
        if (claim.owner != ownerId) {
            return TrustResult.NotOwner
        }
        
        // Check if already trusted
        if (claim.trustedPlayers.contains(targetPlayerId)) {
            return TrustResult.AlreadyTrusted
        }
        
        // Check limit
        if (claim.trustedPlayers.size >= config.limits.maxTrustedPerClaim) {
            return TrustResult.LimitReached(config.limits.maxTrustedPerClaim)
        }
        
        // Add to database
        val success = repository.addTrustedPlayer(claimId, targetPlayerId)
        if (!success) {
            return TrustResult.DatabaseError
        }
        
        // Update cache
        val updatedClaim = claim.withTrustedPlayer(targetPlayerId)
        cache.update(updatedClaim)
        
        LoggingManager.debug(logger, "claims") { "Added $targetPlayerId to trust list for claim $claimId" }
        return TrustResult.Success
    }
    
    /**
     * Remove a trusted player from a claim.
     * 
     * @param ownerId UUID of the claim owner (for verification)
     * @param claimId UUID of the claim
     * @param targetPlayerId UUID of player to untrust
     */
    suspend fun untrustPlayer(ownerId: UUID, claimId: UUID, targetPlayerId: UUID): UntrustPlayerResult {
        // Find claim (cache first)
        val claim = cache.getById(claimId)
            ?: repository.getClaimById(claimId)
            ?: return UntrustPlayerResult.ClaimNotFound
        
        // Verify ownership
        if (claim.owner != ownerId) {
            return UntrustPlayerResult.NotOwner
        }
        
        // Check if actually trusted
        if (!claim.trustedPlayers.contains(targetPlayerId)) {
            return UntrustPlayerResult.NotTrusted
        }
        
        // Remove from database
        val success = repository.removeTrustedPlayer(claimId, targetPlayerId)
        if (!success) {
            return UntrustPlayerResult.DatabaseError
        }
        
        // Update cache
        val updatedClaim = claim.withoutTrustedPlayer(targetPlayerId)
        cache.update(updatedClaim)
        
        LoggingManager.debug(logger, "claims") { "Removed $targetPlayerId from trust list for claim $claimId" }
        return UntrustPlayerResult.Success
    }
    
    /**
     * Get all claims owned by a player.
     * Falls back to database if not fully cached.
     */
    suspend fun getClaimsByOwner(ownerUuid: UUID): List<Claim> {
        val cachedClaims = cache.getByOwner(ownerUuid)
        
        // If cache has claims, use those
        if (cachedClaims.isNotEmpty()) {
            return cachedClaims
        }
        
        // Otherwise fetch from database
        val dbClaims = repository.getClaimsByOwner(ownerUuid)
        
        // Warm cache
        dbClaims.forEach { cache.put(it) }
        
        return dbClaims
    }
    
    /**
     * Get all claims where a player is trusted.
     */
    suspend fun getClaimsWhereTrusted(trustedUuid: UUID): List<Claim> {
        // No cache index for trusted players, always hit database
        return repository.getClaimsWhereTrusted(trustedUuid)
    }
    
    /**
     * Update a claim's name.
     * 
     * @param ownerId UUID of the claim owner (for verification)
     * @param claimId UUID of the claim
     * @param newName New name (null to clear)
     */
    suspend fun updateClaimName(ownerId: UUID, claimId: UUID, newName: String?): UpdateNameResult {
        // Find claim
        val claim = cache.getById(claimId)
            ?: repository.getClaimById(claimId)
            ?: return UpdateNameResult.ClaimNotFound
        
        // Verify ownership
        if (claim.owner != ownerId) {
            return UpdateNameResult.NotOwner
        }
        
        // Update database
        val success = repository.updateClaimName(claimId, newName)
        if (!success) {
            return UpdateNameResult.DatabaseError
        }
        
        // Update cache
        val updatedClaim = claim.withName(newName)
        cache.update(updatedClaim)
        
        LoggingManager.debug(logger, "claims") { "Updated claim $claimId name to: $newName" }
        return UpdateNameResult.Success
    }
    
    /**
     * Load all claims for a world into cache.
     * Called during world startup.
     */
    suspend fun warmCache(worldId: UUID) {
        val worldClaims = repository.getClaimsByWorld(worldId)
        worldClaims.forEach { cache.put(it) }
        LoggingManager.debug(logger, "claims") {
            "Cache warmed with ${worldClaims.size} claims for world $worldId"
        }
    }

    /**
     * Get a claim by its UUID.
     * Cache-first, then database.
     */
    suspend fun getClaimById(claimId: UUID): Claim? {
        return cache.getById(claimId) ?: repository.getClaimById(claimId)?.also {
            cache.put(it)
        }
    }

    /**
     * Get all claims in a rectangular chunk area for grid UI rendering.
     * Returns a map of grid-relative positions to Claims.
     * Note: Multiple grid cells can map to the same Claim (multi-chunk plot).
     *
     * @param worldId World UUID
     * @param centerChunkX Center chunk X coordinate
     * @param centerChunkZ Center chunk Z coordinate
     * @param radius Half-size of the grid (e.g., 3 for a 6x6 grid)
     * @return Map of grid-relative (x, z) to Claim for claimed chunks
     */
    suspend fun getClaimsInArea(
        worldId: UUID,
        centerChunkX: Int,
        centerChunkZ: Int,
        radius: Int
    ): Map<Pair<Int, Int>, Claim> {
        val minX = centerChunkX - radius
        val maxX = centerChunkX + radius - 1
        val minZ = centerChunkZ - radius
        val maxZ = centerChunkZ + radius - 1

        // Fetch claims from database (deduplicated)
        val areaClaims = repository.getClaimsInArea(worldId, minX, maxX, minZ, maxZ)

        // Warm cache with fetched claims
        areaClaims.forEach { cache.put(it) }

        // Build grid-relative map: for each claim, map each of its chunks that fall in the grid
        val result = mutableMapOf<Pair<Int, Int>, Claim>()
        for (claim in areaClaims) {
            for (chunk in claim.chunks) {
                val gridX = chunk.chunkX - minX
                val gridZ = chunk.chunkZ - minZ
                if (gridX in 0 until (radius * 2) && gridZ in 0 until (radius * 2)) {
                    result[Pair(gridX, gridZ)] = claim
                }
            }
        }

        return result
    }

    // ========== Group Management Methods ==========

    /**
     * Create a new group.
     */
    suspend fun createGroup(ownerId: UUID, name: String): GroupResult {
        // Validate name length
        if (name.length !in 1..32) {
            return GroupResult.InvalidName
        }

        // Check group limit
        val currentCount = repository.getGroupsByOwner(ownerId).size
        if (currentCount >= config.limits.maxGroupsPerPlayer) {
            return GroupResult.LimitReached(currentCount, config.limits.maxGroupsPerPlayer)
        }

        // Create group
        val group = com.livinglands.modules.claims.data.ClaimGroup(
            id = UUID.randomUUID(),
            name = name,
            owner = ownerId,
            members = emptySet(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val success = repository.createGroup(group)
        if (!success) {
            return GroupResult.DatabaseError
        }

        LoggingManager.debug(logger, "claims") { "Player $ownerId created group '${group.name}' (${group.id})" }
        return GroupResult.Success(group)
    }

    /**
     * Delete a group.
     */
    suspend fun deleteGroup(ownerId: UUID, groupId: UUID): DeleteGroupResult {
        val group = repository.getGroup(groupId)
            ?: return DeleteGroupResult.GroupNotFound

        if (group.owner != ownerId) {
            return DeleteGroupResult.NotOwner
        }

        val success = repository.deleteGroup(groupId, ownerId)
        if (!success) {
            return DeleteGroupResult.DatabaseError
        }

        LoggingManager.debug(logger, "claims") { "Player $ownerId deleted group '${group.name}' ($groupId)" }
        return DeleteGroupResult.Success
    }

    /**
     * Add a member to a group.
     */
    suspend fun addGroupMember(ownerId: UUID, groupId: UUID, memberId: UUID): AddMemberResult {
        val group = repository.getGroup(groupId)
            ?: return AddMemberResult.GroupNotFound

        if (group.owner != ownerId) {
            return AddMemberResult.NotOwner
        }

        if (group.isMember(memberId)) {
            return AddMemberResult.AlreadyMember
        }

        if (group.members.size >= config.limits.maxMembersPerGroup) {
            return AddMemberResult.LimitReached(config.limits.maxMembersPerGroup)
        }

        val success = repository.addGroupMember(groupId, memberId)
        if (!success) {
            return AddMemberResult.DatabaseError
        }

        LoggingManager.debug(logger, "claims") { "Added $memberId to group '${group.name}' ($groupId)" }
        return AddMemberResult.Success
    }

    /**
     * Remove a member from a group.
     */
    suspend fun removeMember(ownerId: UUID, groupId: UUID, memberId: UUID): RemoveMemberResult {
        val group = repository.getGroup(groupId)
            ?: return RemoveMemberResult.GroupNotFound

        if (group.owner != ownerId) {
            return RemoveMemberResult.NotOwner
        }

        if (!group.isMember(memberId)) {
            return RemoveMemberResult.NotMember
        }

        val success = repository.removeGroupMember(groupId, memberId)
        if (!success) {
            return RemoveMemberResult.DatabaseError
        }

        LoggingManager.debug(logger, "claims") { "Removed $memberId from group '${group.name}' ($groupId)" }
        return RemoveMemberResult.Success
    }

    /**
     * Trust a group to a claim.
     */
    suspend fun trustGroupToClaim(ownerId: UUID, claimId: UUID, groupId: UUID): TrustGroupResult {
        val claim = cache.getById(claimId)
            ?: repository.getClaimById(claimId)
            ?: return TrustGroupResult.ClaimNotFound

        if (claim.owner != ownerId) {
            return TrustGroupResult.NotClaimOwner
        }

        val group = repository.getGroup(groupId)
            ?: return TrustGroupResult.GroupNotFound

        val success = repository.trustGroup(claimId, groupId)
        if (!success) {
            return TrustGroupResult.DatabaseError
        }

        LoggingManager.debug(logger, "claims") { "Trusted group '${group.name}' to claim $claimId" }
        return TrustGroupResult.Success
    }

    /**
     * Untrust a group from a claim.
     */
    suspend fun untrustGroupFromClaim(ownerId: UUID, claimId: UUID, groupId: UUID): UntrustGroupResult {
        val claim = cache.getById(claimId)
            ?: repository.getClaimById(claimId)
            ?: return UntrustGroupResult.ClaimNotFound

        if (claim.owner != ownerId) {
            return UntrustGroupResult.NotClaimOwner
        }

        val success = repository.untrustGroup(claimId, groupId)
        if (!success) {
            return UntrustGroupResult.DatabaseError
        }

        LoggingManager.debug(logger, "claims") { "Untrusted group $groupId from claim $claimId" }
        return UntrustGroupResult.Success
    }

    /**
     * Get all groups owned by a player.
     */
    suspend fun getGroupsByOwner(ownerId: UUID): List<com.livinglands.modules.claims.data.ClaimGroup> {
        return repository.getGroupsByOwner(ownerId)
    }
}

// ========== Result Types ==========

/**
 * Result of creating a plot (multi-chunk claim).
 */
sealed class ClaimResult {
    data class Success(val claim: Claim) : ClaimResult()
    data class AlreadyClaimed(val ownerUuid: UUID) : ClaimResult()
    data class LimitReached(val current: Int, val max: Int) : ClaimResult()
    data class PlotLimitReached(val current: Int, val max: Int) : ClaimResult()
    data class ChunkLimitReached(val current: Int, val max: Int) : ClaimResult()
    data object DatabaseError : ClaimResult()
}

/**
 * Result of unclaiming chunks from a plot.
 */
sealed class UnclaimResult {
    data object Success : UnclaimResult()
    data object NotClaimed : UnclaimResult()
    data class NotOwned(val ownerUuid: UUID) : UnclaimResult()
    data object DatabaseError : UnclaimResult()
}

/**
 * Result of trusting a player.
 */
sealed class TrustResult {
    data object Success : TrustResult()
    data object ClaimNotFound : TrustResult()
    data object NotOwner : TrustResult()
    data object AlreadyTrusted : TrustResult()
    data class LimitReached(val max: Int) : TrustResult()
    data object DatabaseError : TrustResult()
}

/**
 * Result of untrusting a player.
 */
sealed class UntrustPlayerResult {
    data object Success : UntrustPlayerResult()
    data object ClaimNotFound : UntrustPlayerResult()
    data object NotOwner : UntrustPlayerResult()
    data object NotTrusted : UntrustPlayerResult()
    data object DatabaseError : UntrustPlayerResult()
}

/**
 * Result of updating claim name.
 */
sealed class UpdateNameResult {
    data object Success : UpdateNameResult()
    data object ClaimNotFound : UpdateNameResult()
    data object NotOwner : UpdateNameResult()
    data object DatabaseError : UpdateNameResult()
}

// ========== Group Result Types ==========

sealed class GroupResult {
    data class Success(val group: com.livinglands.modules.claims.data.ClaimGroup) : GroupResult()
    data object InvalidName : GroupResult()
    data class LimitReached(val current: Int, val max: Int) : GroupResult()
    data object DatabaseError : GroupResult()
}

sealed class DeleteGroupResult {
    data object Success : DeleteGroupResult()
    data object GroupNotFound : DeleteGroupResult()
    data object NotOwner : DeleteGroupResult()
    data object DatabaseError : DeleteGroupResult()
}

sealed class AddMemberResult {
    data object Success : AddMemberResult()
    data object GroupNotFound : AddMemberResult()
    data object NotOwner : AddMemberResult()
    data object AlreadyMember : AddMemberResult()
    data class LimitReached(val max: Int) : AddMemberResult()
    data object DatabaseError : AddMemberResult()
}

sealed class RemoveMemberResult {
    data object Success : RemoveMemberResult()
    data object GroupNotFound : RemoveMemberResult()
    data object NotOwner : RemoveMemberResult()
    data object NotMember : RemoveMemberResult()
    data object DatabaseError : RemoveMemberResult()
}

sealed class TrustGroupResult {
    data object Success : TrustGroupResult()
    data object ClaimNotFound : TrustGroupResult()
    data object GroupNotFound : TrustGroupResult()
    data object NotClaimOwner : TrustGroupResult()
    data object DatabaseError : TrustGroupResult()
}

sealed class UntrustGroupResult {
    data object Success : UntrustGroupResult()
    data object ClaimNotFound : UntrustGroupResult()
    data object NotClaimOwner : UntrustGroupResult()
    data object DatabaseError : UntrustGroupResult()
}
