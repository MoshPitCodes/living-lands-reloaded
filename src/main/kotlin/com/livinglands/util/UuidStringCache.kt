package com.livinglands.util

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cache for UUID -> String conversions.
 * 
 * Performance optimization: UUID.toString() allocates a new String on every call.
 * With 100 players at 30 ticks/second, this means 3000+ allocations per second
 * just for UUID conversions. This cache eliminates those allocations.
 * 
 * Usage:
 * ```kotlin
 * // Instead of: playerId.toString()
 * // Use: playerId.toCachedString()
 * ```
 * 
 * Cleanup:
 * Call `UuidStringCache.remove(uuid)` when a player disconnects to prevent memory leaks.
 */
object UuidStringCache {
    
    /**
     * Cache of UUID -> String mappings.
     * Using ConcurrentHashMap for thread-safe access from multiple world threads.
     */
    private val cache = ConcurrentHashMap<UUID, String>()
    
    /**
     * Get the cached string representation of a UUID.
     * If not cached, computes and stores it atomically.
     * 
     * @param uuid The UUID to convert
     * @return The cached string representation
     */
    fun get(uuid: UUID): String {
        return cache.computeIfAbsent(uuid) { it.toString() }
    }
    
    /**
     * Remove a UUID from the cache.
     * Should be called when a player disconnects to prevent memory leaks.
     * 
     * @param uuid The UUID to remove
     * @return The previously cached string, or null if not cached
     */
    fun remove(uuid: UUID): String? {
        return cache.remove(uuid)
    }
    
    /**
     * Clear the entire cache.
     * Useful during server shutdown.
     */
    fun clear() {
        cache.clear()
    }
    
    /**
     * Get the current cache size (for debugging/metrics).
     */
    fun size(): Int = cache.size
}

/**
 * Extension function for convenient UUID -> cached String conversion.
 * 
 * Performance: This avoids allocating a new String on every call by
 * caching the result. Safe to call from any thread.
 * 
 * @return Cached string representation of this UUID
 */
fun UUID.toCachedString(): String = UuidStringCache.get(this)
