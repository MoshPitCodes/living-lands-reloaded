package com.livinglands.modules.claims.cache

import com.livinglands.modules.claims.Claim
import com.livinglands.modules.claims.ChunkPosition
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance claims cache with O(1) lookups.
 * 
 * **Performance Requirements:**
 * - Block interaction checks: < 0.1ms (hot path)
 * - Cache hit rate: > 95% for active areas
 * - Supports 100 players * 100 block interactions/sec = 10,000 lookups/sec
 * 
 * **Cache Strategy:**
 * - LRU eviction when max size reached (evicts entire claim with all chunks)
 * - Spatial index: ChunkPosition -> Claim (many:1, multiple positions map to same Claim)
 * - Claims-by-ID index: UUID -> Claim (O(1) ID lookup)
 * - Owner index: UUID -> Set<ClaimId>
 * - Thread-safe via ConcurrentHashMap
 * 
 * **Eviction Policy:**
 * - Max claims tracked (not chunk count)
 * - Removes least recently accessed claim (all its chunks removed together)
 * 
 * @param maxSize Maximum number of claims (plots) to cache (default: 1000)
 */
class ClaimsCache(
    private val maxSize: Int = 1000
) {
    
    /**
     * Spatial index: ChunkPosition -> Claim
     * Used for hot path permission checks.
     * Multiple ChunkPositions can map to the same Claim (many:1).
     */
    private val spatialIndex = ConcurrentHashMap<ChunkPosition, Claim>()
    
    /**
     * Claims-by-ID index: ClaimId -> Claim
     * Used for O(1) ID lookup (trust/untrust, name updates, etc.)
     */
    private val claimsById = ConcurrentHashMap<UUID, Claim>()
    
    /**
     * Owner index: UUID -> Set<ClaimId>
     * Used for "my claims" queries and count enforcement.
     */
    private val ownerIndex = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    
    /**
     * Access order tracking for LRU eviction.
     * Key: ClaimId, Value: Last access timestamp
     */
    private val accessOrder = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Get claim at a chunk position (hot path).
     * Returns null if not cached.
     * 
     * **Performance:** O(1) lookup
     */
    fun get(position: ChunkPosition): Claim? {
        val claim = spatialIndex[position]
        if (claim != null) {
            // Update access time for LRU
            accessOrder[claim.id] = System.nanoTime()
        }
        return claim
    }
    
    /**
     * Get claim by ID.
     * Returns null if not cached.
     * 
     * **Performance:** O(1) lookup via claimsById index
     */
    fun getById(claimId: UUID): Claim? {
        return claimsById[claimId]?.also {
            accessOrder[it.id] = System.nanoTime()
        }
    }
    
    /**
     * Get all claims owned by a player (from cache only).
     * Returns empty list if not cached.
     */
    fun getByOwner(ownerUuid: UUID): List<Claim> {
        val claimIds = ownerIndex[ownerUuid] ?: return emptyList()
        return claimIds.mapNotNull { claimId ->
            claimsById[claimId]
        }.also { claims ->
            // Update access times
            val now = System.nanoTime()
            claims.forEach { accessOrder[it.id] = now }
        }
    }
    
    /**
     * Put a claim into the cache.
     * Inserts ALL chunk positions into the spatial index.
     * Triggers LRU eviction if max size exceeded.
     */
    fun put(claim: Claim) {
        // If this claim already exists, remove old entries first
        val existing = claimsById[claim.id]
        if (existing != null) {
            // Remove old spatial entries (chunks may have changed)
            existing.chunks.forEach { spatialIndex.remove(it) }
        }

        // Evict if needed (check claim count, not chunk count)
        if (existing == null && claimsById.size >= maxSize) {
            evictLRU()
        }
        
        // Add to claims-by-ID index
        claimsById[claim.id] = claim

        // Add ALL chunks to spatial index
        claim.chunks.forEach { chunkPos ->
            spatialIndex[chunkPos] = claim
        }
        
        // Add to owner index
        ownerIndex.compute(claim.owner) { _, existing ->
            (existing ?: mutableSetOf()).apply { add(claim.id) }
        }
        
        // Update access time
        accessOrder[claim.id] = System.nanoTime()
    }
    
    /**
     * Update an existing claim in cache.
     * Handles chunk set changes (removes old chunk entries, adds new ones).
     * If claim is not cached, this is a no-op.
     */
    fun update(claim: Claim) {
        val existing = claimsById[claim.id] ?: return

        // Remove spatial entries for chunks that are no longer in the claim
        val removedChunks = existing.chunks - claim.chunks
        removedChunks.forEach { spatialIndex.remove(it) }

        // Add spatial entries for new chunks
        val addedChunks = claim.chunks - existing.chunks
        addedChunks.forEach { spatialIndex[it] = claim }

        // Update existing chunk entries to point to the new claim object
        val keptChunks = existing.chunks.intersect(claim.chunks)
        keptChunks.forEach { spatialIndex[it] = claim }

        // Update claims-by-ID
        claimsById[claim.id] = claim

        // Update access time
        accessOrder[claim.id] = System.nanoTime()
    }
    
    /**
     * Remove a claim from cache.
     * Removes ALL chunk entries for this claim.
     */
    fun remove(claimId: UUID) {
        val claim = claimsById.remove(claimId) ?: return

        // Remove all spatial index entries
        claim.chunks.forEach { spatialIndex.remove(it) }
        
        // Remove from owner index
        ownerIndex[claim.owner]?.remove(claimId)
        if (ownerIndex[claim.owner]?.isEmpty() == true) {
            ownerIndex.remove(claim.owner)
        }
        
        // Remove from access order
        accessOrder.remove(claimId)
    }
    
    /**
     * Remove all claims by a specific owner.
     * Used when player disconnects or during cleanup.
     */
    fun removeByOwner(ownerUuid: UUID) {
        val claimIds = ownerIndex.remove(ownerUuid) ?: return
        claimIds.forEach { claimId ->
            val claim = claimsById.remove(claimId)
            if (claim != null) {
                claim.chunks.forEach { spatialIndex.remove(it) }
            }
            accessOrder.remove(claimId)
        }
    }
    
    /**
     * Check if a chunk position is claimed (hot path).
     * Returns true if cached and claimed.
     * 
     * **Performance:** O(1) lookup
     */
    fun isClaimed(position: ChunkPosition): Boolean {
        return spatialIndex.containsKey(position)
    }
    
    /**
     * Get claim count (number of plots) for a player (from cache only).
     */
    fun getClaimCount(ownerUuid: UUID): Int {
        return ownerIndex[ownerUuid]?.size ?: 0
    }

    /**
     * Get total chunk count across all plots for a player (from cache only).
     * Used for maxTotalChunks limit enforcement.
     */
    fun getChunkCount(ownerUuid: UUID): Int {
        val claimIds = ownerIndex[ownerUuid] ?: return 0
        return claimIds.sumOf { claimId ->
            claimsById[claimId]?.chunks?.size ?: 0
        }
    }
    
    /**
     * Clear all cached claims.
     * Used during shutdown or world unload.
     */
    fun clear() {
        spatialIndex.clear()
        claimsById.clear()
        ownerIndex.clear()
        accessOrder.clear()
    }
    
    /**
     * Get cache statistics for monitoring.
     */
    fun getStats(): CacheStats {
        return CacheStats(
            claimCount = claimsById.size,
            chunkCount = spatialIndex.size,
            maxSize = maxSize,
            ownerCount = ownerIndex.size,
            utilizationPercent = (claimsById.size.toDouble() / maxSize * 100).toInt()
        )
    }
    
    // ========== Private Methods ==========
    
    /**
     * Evict the least recently used claim (entire claim with all chunks).
     */
    private fun evictLRU() {
        // Find claim with oldest access time
        val lruClaimId = accessOrder.entries.minByOrNull { it.value }?.key
        if (lruClaimId != null) {
            remove(lruClaimId)
        }
    }
}

/**
 * Cache statistics for monitoring and debugging.
 */
data class CacheStats(
    val claimCount: Int,
    val chunkCount: Int,
    val maxSize: Int,
    val ownerCount: Int,
    val utilizationPercent: Int
)
