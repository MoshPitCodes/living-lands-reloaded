package com.livinglands.core.persistence

/**
 * Player data model for database persistence.
 * Represents core player information across sessions.
 */
data class PlayerData(
    val playerId: String,
    val playerName: String,
    val firstSeen: Long,
    val lastSeen: Long
)
