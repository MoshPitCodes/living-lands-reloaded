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
 * - LRU eviction when max size reached
 * - Spatial index: ChunkPosition -> Claim
 * - Owner index: UUID -> Set<ClaimId>
 * - Thread-safe via ConcurrentHashMap
 * 
 * **Eviction Policy:**
 * - Max 1000 claims per world
 * - Removes least recently accessed claims
 * - Access tracking via LinkedHashMap (access order)
 * 
 * @param maxSize Maximum number of claims to cache (default: 1000)
 */
class ClaimsCache(
    private val maxSize: Int = 1000
) {
    
    /**
     * Spatial index: ChunkPosition -> Claim
     * Used for hot path permission checks.
     */
    private val spatialIndex = ConcurrentHashMap<ChunkPosition, Claim>()
    
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
     */
    fun getById(claimId: UUID): Claim? {
        return spatialIndex.values.find { it.id == claimId }?.also {
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
            spatialIndex.values.find { it.id == claimId }
        }.also { claims ->
            // Update access times
            val now = System.nanoTime()
            claims.forEach { accessOrder[it.id] = now }
        }
    }
    
    /**
     * Put a claim into the cache.
     * Triggers LRU eviction if max size exceeded.
     */
    fun put(claim: Claim) {
        // Evict if needed
        if (spatialIndex.size >= maxSize && !spatialIndex.containsKey(claim.position)) {
            evictLRU()
        }
        
        // Add to spatial index
        spatialIndex[claim.position] = claim
        
        // Add to owner index
        ownerIndex.compute(claim.owner) { _, existing ->
            (existing ?: mutableSetOf()).apply { add(claim.id) }
        }
        
        // Update access time
        accessOrder[claim.id] = System.nanoTime()
    }
    
    /**
     * Update an existing claim in cache.
     * If claim is not cached, this is a no-op.
     */
    fun update(claim: Claim) {
        if (spatialIndex.containsKey(claim.position)) {
            spatialIndex[claim.position] = claim
            accessOrder[claim.id] = System.nanoTime()
        }
    }
    
    /**
     * Remove a claim from cache.
     */
    fun remove(claimId: UUID) {
        // Find and remove from spatial index
        val claim = spatialIndex.values.find { it.id == claimId }
        if (claim != null) {
            spatialIndex.remove(claim.position)
            
            // Remove from owner index
            ownerIndex[claim.owner]?.remove(claimId)
            if (ownerIndex[claim.owner]?.isEmpty() == true) {
                ownerIndex.remove(claim.owner)
            }
            
            // Remove from access order
            accessOrder.remove(claimId)
        }
    }
    
    /**
     * Remove all claims by a specific owner.
     * Used when player disconnects or during cleanup.
     */
    fun removeByOwner(ownerUuid: UUID) {
        val claimIds = ownerIndex.remove(ownerUuid) ?: return
        claimIds.forEach { claimId ->
            val claim = spatialIndex.values.find { it.id == claimId }
            if (claim != null) {
                spatialIndex.remove(claim.position)
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
     * Get claim count for a player (from cache only).
     * Returns accurate count if all claims are cached.
     */
    fun getClaimCount(ownerUuid: UUID): Int {
        return ownerIndex[ownerUuid]?.size ?: 0
    }
    
    /**
     * Clear all cached claims.
     * Used during shutdown or world unload.
     */
    fun clear() {
        spatialIndex.clear()
        ownerIndex.clear()
        accessOrder.clear()
    }
    
    /**
     * Get cache statistics for monitoring.
     */
    fun getStats(): CacheStats {
        return CacheStats(
            size = spatialIndex.size,
            maxSize = maxSize,
            ownerCount = ownerIndex.size,
            utilizationPercent = (spatialIndex.size.toDouble() / maxSize * 100).toInt()
        )
    }
    
    // ========== Private Methods ==========
    
    /**
     * Evict the least recently used claim.
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
    val size: Int,
    val maxSize: Int,
    val ownerCount: Int,
    val utilizationPercent: Int
)
