package com.livinglands.modules.claims.cache

import com.livinglands.modules.claims.data.ClaimGroup
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for ClaimGroup data.
 *
 * **Thread-Safe Design:**
 * - ConcurrentHashMap for all maps
 * - ConcurrentHashMap.newKeySet() for thread-safe sets
 * - Atomic operations for consistency
 *
 * **Purpose:**
 * - Fast group lookups by ID (O(1))
 * - Fast "my groups" queries by owner (O(1))
 * - Reduces database round-trips for hot paths
 *
 * **Cache Strategy:**
 * - Write-through: Update cache after DB operation
 * - No auto-eviction (groups are small, long-lived data)
 * - Manual invalidation on group delete
 *
 * @see ClaimGroup
 */
class GroupsCache {

    /** Groups indexed by group ID */
    private val groupsById = ConcurrentHashMap<UUID, ClaimGroup>()

    /** Group IDs indexed by owner UUID (for "my groups" queries) */
    private val groupsByOwner = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    /**
     * Add or update a group in the cache.
     *
     * **Updates indexes:**
     * - groupsById
     * - groupsByOwner
     *
     * @param group The group to cache
     */
    fun put(group: ClaimGroup) {
        groupsById[group.id] = group

        // Update owner index
        groupsByOwner.computeIfAbsent(group.owner) {
            ConcurrentHashMap.newKeySet()
        }.add(group.id)
    }

    /**
     * Get a group by ID.
     *
     * @param groupId UUID of the group
     * @return ClaimGroup if found, null otherwise
     */
    fun get(groupId: UUID): ClaimGroup? {
        return groupsById[groupId]
    }

    /**
     * Get all groups owned by a player.
     *
     * @param ownerId UUID of the owner
     * @return List of ClaimGroups (empty if none)
     */
    fun getByOwner(ownerId: UUID): List<ClaimGroup> {
        val groupIds = groupsByOwner[ownerId] ?: return emptyList()
        return groupIds.mapNotNull { groupsById[it] }
    }

    /**
     * Remove a group from the cache.
     *
     * **Updates indexes:**
     * - groupsById
     * - groupsByOwner
     *
     * @param groupId UUID of the group to remove
     * @return The removed ClaimGroup if found, null otherwise
     */
    fun remove(groupId: UUID): ClaimGroup? {
        val group = groupsById.remove(groupId) ?: return null

        // Update owner index
        groupsByOwner[group.owner]?.remove(groupId)

        return group
    }

    /**
     * Update an existing group in the cache.
     *
     * This is an alias for put() since we use a write-through strategy.
     *
     * @param group The updated group
     */
    fun update(group: ClaimGroup) {
        put(group)
    }

    /**
     * Invalidate (remove) a group from the cache.
     *
     * Alias for remove() for consistency with other cache classes.
     *
     * @param groupId UUID of the group to invalidate
     */
    fun invalidate(groupId: UUID) {
        remove(groupId)
    }

    /**
     * Clear all cached groups.
     *
     * Used during module shutdown.
     */
    fun clear() {
        groupsById.clear()
        groupsByOwner.clear()
    }

    /**
     * Get count of cached groups.
     *
     * Useful for diagnostics and logging.
     *
     * @return Number of groups in cache
     */
    fun size(): Int {
        return groupsById.size
    }

    /**
     * Check if cache contains a group.
     *
     * @param groupId UUID of the group
     * @return true if cached, false otherwise
     */
    fun contains(groupId: UUID): Boolean {
        return groupsById.containsKey(groupId)
    }

    /**
     * Get count of groups owned by a player.
     *
     * @param ownerId UUID of the owner
     * @return Number of groups owned
     */
    fun getGroupCount(ownerId: UUID): Int {
        return groupsByOwner[ownerId]?.size ?: 0
    }
}
