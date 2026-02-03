package com.livinglands.modules.claims

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.modules.claims.cache.ClaimsCache
import com.livinglands.modules.claims.config.ClaimsConfig
import com.livinglands.modules.claims.repository.ClaimsRepository
import java.util.UUID

/**
 * Claims service - business logic for land protection.
 * 
 * **Stateless Design:**
 * - No mutable state (repository and cache are dependencies)
 * - All methods return sealed Result types for better error handling
 * - Async operations use suspend functions
 * 
 * **Performance:**
 * - Hot path (canBuild): O(1) cache lookup only
 * - Cold path (claim/unclaim): Async DB operations via repository
 * - Cache-first strategy: Check cache, fallback to DB if miss
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
     * Attempt to claim a chunk for a player.
     * 
     * **Validation:**
     * - Chunk must not be already claimed
     * - Player must not have reached max claims limit
     * 
     * **Side Effects:**
     * - Creates claim in database
     * - Adds claim to cache
     * - Increments player's claim count
     * 
     * @return Success with Claim, or specific error
     */
    suspend fun claimChunk(playerId: UUID, position: ChunkPosition): ClaimResult {
        // Check if chunk already claimed (cache first)
        val existingClaim = cache.get(position) ?: repository.getClaimAt(position)
        if (existingClaim != null) {
            return ClaimResult.AlreadyClaimed(existingClaim.owner)
        }
        
        // Check claim limit
        val currentCount = cache.getClaimCount(playerId).takeIf { it > 0 } 
            ?: repository.getClaimCount(playerId)
        
        if (currentCount >= config.limits.maxClaimsPerPlayer) {
            return ClaimResult.LimitReached(currentCount, config.limits.maxClaimsPerPlayer)
        }
        
        // Create claim
        val claim = Claim(
            id = UUID.randomUUID(),
            owner = playerId,
            position = position,
            name = null,
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
        
        logger.atFine().log("Player $playerId claimed chunk at ${position.chunkX}, ${position.chunkZ}")
        return ClaimResult.Success(claim)
    }
    
    /**
     * Unclaim a chunk owned by a player.
     * 
     * **Validation:**
     * - Chunk must be claimed by this player
     * 
     * **Side Effects:**
     * - Removes claim from database
     * - Removes claim from cache
     * - Decrements player's claim count
     * 
     * @return Success, NotOwned, or DatabaseError
     */
    suspend fun unclaimChunk(playerId: UUID, position: ChunkPosition): UnclaimResult {
        // Find claim (cache first)
        val claim = cache.get(position) ?: repository.getClaimAt(position)
            ?: return UnclaimResult.NotClaimed
        
        // Verify ownership
        if (claim.owner != playerId) {
            return UnclaimResult.NotOwned(claim.owner)
        }
        
        // Delete from database
        val success = repository.deleteClaim(claim.id, playerId)
        if (!success) {
            return UnclaimResult.DatabaseError
        }
        
        // Remove from cache
        cache.remove(claim.id)
        
        logger.atFine().log("Player $playerId unclaimed chunk at ${position.chunkX}, ${position.chunkZ}")
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
     * 
     * **Admin Bypass:**
     * - Not implemented yet (TODO: check player permissions)
     */
    fun canBuild(playerId: UUID, position: ChunkPosition): Boolean {
        val claim = cache.get(position) ?: return true  // Unclaimed = can build
        return claim.canBuild(playerId)
    }
    
    /**
     * Add a trusted player to a claim.
     * 
     * **Validation:**
     * - Claim must exist
     * - Caller must be the owner
     * - Target player not already trusted
     * - Not exceeding max trusted players
     * 
     * @param ownerId UUID of the claim owner (for verification)
     * @param claimId UUID of the claim
     * @param targetPlayerId UUID of player to trust
     */
    suspend fun trustPlayer(ownerId: UUID, claimId: UUID, targetPlayerId: UUID): TrustResult {
        // Find claim (cache first)
        val claim = cache.getById(claimId) 
            ?: repository.getClaimAt(ChunkPosition(UUID.randomUUID(), 0, 0))  // This is a hack - need getById
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
        
        logger.atFine().log("Added $targetPlayerId to trust list for claim $claimId")
        return TrustResult.Success
    }
    
    /**
     * Remove a trusted player from a claim.
     * 
     * @param ownerId UUID of the claim owner (for verification)
     * @param claimId UUID of the claim
     * @param targetPlayerId UUID of player to untrust
     */
    suspend fun untrustPlayer(ownerId: UUID, claimId: UUID, targetPlayerId: UUID): UntrustResult {
        // Find claim (cache first)
        val claim = cache.getById(claimId)
            ?: return UntrustResult.ClaimNotFound
        
        // Verify ownership
        if (claim.owner != ownerId) {
            return UntrustResult.NotOwner
        }
        
        // Check if actually trusted
        if (!claim.trustedPlayers.contains(targetPlayerId)) {
            return UntrustResult.NotTrusted
        }
        
        // Remove from database
        val success = repository.removeTrustedPlayer(claimId, targetPlayerId)
        if (!success) {
            return UntrustResult.DatabaseError
        }
        
        // Update cache
        val updatedClaim = claim.withoutTrustedPlayer(targetPlayerId)
        cache.update(updatedClaim)
        
        logger.atFine().log("Removed $targetPlayerId from trust list for claim $claimId")
        return UntrustResult.Success
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
        
        logger.atFine().log("Updated claim $claimId name to: $newName")
        return UpdateNameResult.Success
    }
    
    /**
     * Load all claims for a world into cache.
     * Called during world startup.
     */
    suspend fun warmCache(worldId: UUID) {
        // TODO: Implement once we have a way to query all claims by world
        logger.atFine().log("Cache warming not yet implemented for world $worldId")
    }
}

// ========== Result Types ==========

/**
 * Result of claiming a chunk.
 */
sealed class ClaimResult {
    data class Success(val claim: Claim) : ClaimResult()
    data class AlreadyClaimed(val ownerUuid: UUID) : ClaimResult()
    data class LimitReached(val current: Int, val max: Int) : ClaimResult()
    data object DatabaseError : ClaimResult()
}

/**
 * Result of unclaiming a chunk.
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
sealed class UntrustResult {
    data object Success : UntrustResult()
    data object ClaimNotFound : UntrustResult()
    data object NotOwner : UntrustResult()
    data object NotTrusted : UntrustResult()
    data object DatabaseError : UntrustResult()
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
