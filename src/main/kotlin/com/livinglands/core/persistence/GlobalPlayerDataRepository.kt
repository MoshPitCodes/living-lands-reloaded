package com.livinglands.core.persistence

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import java.util.UUID

/**
 * Global repository for player data using GlobalPersistenceService.
 * Provides server-wide player lookups for admin commands.
 */
class GlobalPlayerDataRepository(
    private val persistence: GlobalPersistenceService,
    private val logger: HytaleLogger
) {

    /**
     * Find player by name (case-insensitive).
     * 
     * @param name Player's display name
     * @return PlayerData if found, null otherwise
     */
    suspend fun findByName(name: String): PlayerData? {
        return persistence.execute { conn ->
            conn.prepareStatement("""
                SELECT player_id, player_name, first_seen, last_seen
                FROM players
                WHERE LOWER(player_name) = LOWER(?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().useRows { rs ->
                    if (rs.next()) {
                        PlayerData(
                            playerId = rs.getString("player_id"),
                            playerName = rs.getString("player_name"),
                            firstSeen = rs.getLong("first_seen"),
                            lastSeen = rs.getLong("last_seen")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Find player by UUID.
     * 
     * @param playerId Player's UUID
     * @return PlayerData if found, null otherwise
     */
    suspend fun findById(playerId: UUID): PlayerData? {
        return persistence.execute { conn ->
            conn.prepareStatement("""
                SELECT player_id, player_name, first_seen, last_seen
                FROM players
                WHERE player_id = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, playerId.toString())
                stmt.executeQuery().useRows { rs ->
                    if (rs.next()) {
                        PlayerData(
                            playerId = rs.getString("player_id"),
                            playerName = rs.getString("player_name"),
                            firstSeen = rs.getLong("first_seen"),
                            lastSeen = rs.getLong("last_seen")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Ensure player record exists in global database.
     * Creates new record if player doesn't exist, updates name if changed.
     * 
     * @param playerId Player's UUID
     * @param playerName Player's current name
     */
    suspend fun ensurePlayer(playerId: UUID, playerName: String) {
        persistence.execute { conn ->
            val existing = findByIdInternal(conn, playerId)
            
            if (existing == null) {
                val now = System.currentTimeMillis()
                conn.prepareStatement("""
                    INSERT INTO players (player_id, player_name, first_seen, last_seen)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, playerId.toString())
                    stmt.setString(2, playerName)
                    stmt.setLong(3, now)
                    stmt.setLong(4, now)
                    stmt.executeUpdate()
                }
                LoggingManager.debug(logger, "core") { "Created global player record: $playerId ($playerName)" }
            } else if (existing.playerName != playerName) {
                conn.prepareStatement("""
                    UPDATE players SET player_name = ? WHERE player_id = ?
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, playerName)
                    stmt.setString(2, playerId.toString())
                    stmt.executeUpdate()
                }
                LoggingManager.debug(logger, "core") { "Updated global player name: $playerId -> $playerName" }
            }
        }
    }

    /**
     * Update player's last seen timestamp.
     * 
     * @param playerId Player's UUID
     * @param timestamp Unix timestamp in milliseconds
     */
    suspend fun updateLastSeen(playerId: UUID, timestamp: Long) {
        persistence.execute { conn ->
            conn.prepareStatement("""
                UPDATE players SET last_seen = ? WHERE player_id = ?
            """.trimIndent()).use { stmt ->
                stmt.setLong(1, timestamp)
                stmt.setString(2, playerId.toString())
                val updated = stmt.executeUpdate()
                
                if (updated == 0) {
                    LoggingManager.debug(logger, "core") { "Player not found for last seen update: $playerId" }
                }
            }
        }
    }

    /**
     * Internal method to find player by ID (for use within connections).
     */
    private fun findByIdInternal(conn: java.sql.Connection, playerId: UUID): PlayerData? {
        return conn.prepareStatement("""
            SELECT player_id, player_name, first_seen, last_seen
            FROM players
            WHERE player_id = ?
        """.trimIndent()).use { stmt ->
            stmt.setString(1, playerId.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    PlayerData(
                        playerId = rs.getString("player_id"),
                        playerName = rs.getString("player_name"),
                        firstSeen = rs.getLong("first_seen"),
                        lastSeen = rs.getLong("last_seen")
                    )
                } else {
                    null
                }
            }
        }
    }
}
