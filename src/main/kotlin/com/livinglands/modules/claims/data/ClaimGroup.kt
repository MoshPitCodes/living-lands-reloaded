package com.livinglands.modules.claims.data

import java.util.UUID

/**
 * Represents a player-defined group for managing claim permissions.
 *
 * **Immutable Design:**
 * - All collections are immutable Sets to prevent concurrent modification
 * - Use helper methods to create modified copies
 * - Thread-safe by design (no mutable state)
 *
 * Groups allow players to organize trusted players and grant permissions
 * to multiple players at once across multiple claims.
 *
 * @property id Unique group identifier
 * @property name Group name (player-defined)
 * @property owner UUID of the player who owns this group
 * @property members Set of player UUIDs who are members of this group (immutable)
 * @property createdAt Unix timestamp when group was created
 * @property updatedAt Unix timestamp when group was last modified
 */
data class ClaimGroup(
    val id: UUID,
    val name: String,
    val owner: UUID,
    val members: Set<UUID> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Create a copy with an additional member.
     *
     * @param memberId UUID of player to add to group
     * @return New ClaimGroup with member added
     */
    fun withMember(memberId: UUID): ClaimGroup {
        return copy(
            members = members + memberId,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy with a member removed.
     *
     * @param memberId UUID of player to remove from group
     * @return New ClaimGroup with member removed
     */
    fun withoutMember(memberId: UUID): ClaimGroup {
        return copy(
            members = members - memberId,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy with a new name.
     *
     * @param newName New group name
     * @return New ClaimGroup with updated name
     */
    fun withName(newName: String): ClaimGroup {
        return copy(
            name = newName,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Check if a player is a member of this group.
     *
     * @param playerId UUID of player to check
     * @return true if player is a member, false otherwise
     */
    fun isMember(playerId: UUID): Boolean {
        return members.contains(playerId)
    }

    /**
     * Check if a player owns this group.
     *
     * @param playerId UUID of player to check
     * @return true if player is the owner, false otherwise
     */
    fun isOwner(playerId: UUID): Boolean {
        return playerId == owner
    }
}
