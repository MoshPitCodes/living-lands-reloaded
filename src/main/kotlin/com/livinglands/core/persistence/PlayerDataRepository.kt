package com.livinglands.core.persistence

import com.hypixel.hytale.logger.HytaleLogger
import java.sql.Connection

/**
 * Repository for player data persistence operations.
 * Handles CRUD operations for player records.
 * 
 * Implements the Repository interface with String as the ID type
 * (player UUID stored as string).
 */
class PlayerDataRepository(
    private val persistence: PersistenceService,
    private val logger: HytaleLogger
) : Repository<PlayerData, String> {
    
    /**
     * Ensure player record exists in database.
     * Creates new record if player doesn't exist, updates name if changed.
     * 
     * @param playerId Player's UUID as string
     * @param playerName Player's current name
     */
    suspend fun ensurePlayer(playerId: String, playerName: String) {
        persistence.execute { conn ->
            // Check if player exists
            val existing = findByIdInternal(conn, playerId)
            
            if (existing == null) {
                // Insert new player
                val now = System.currentTimeMillis()
                conn.prepareStatement("""
                    INSERT INTO players (player_id, player_name, first_seen, last_seen)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, playerId)
                    stmt.setString(2, playerName)
                    stmt.setLong(3, now)
                    stmt.setLong(4, now)
                    stmt.executeUpdate()
                }
                logger.atFine().log("Created player record: $playerId ($playerName)")
            } else if (existing.playerName != playerName) {
                // Update player name if changed
                conn.prepareStatement("""
                    UPDATE players SET player_name = ? WHERE player_id = ?
                """.trimIndent()).use { stmt ->
                    stmt.setString(1, playerName)
                    stmt.setString(2, playerId)
                    stmt.executeUpdate()
                }
                logger.atFine().log("Updated player name: $playerId -> $playerName")
            }
        }
    }
    
    /**
     * Find player by ID.
     * 
     * @param id Player's UUID as string
     * @return PlayerData if found, null otherwise
     */
    override suspend fun findById(id: String): PlayerData? {
        return persistence.execute { conn ->
            findByIdInternal(conn, id)
        }
    }
    
    /**
     * Internal method to find player by ID (for use within connections).
     */
    private fun findByIdInternal(conn: Connection, playerId: String): PlayerData? {
        return conn.prepareStatement("""
            SELECT player_id, player_name, first_seen, last_seen
            FROM players
            WHERE player_id = ?
        """.trimIndent()).use { stmt ->
            stmt.setString(1, playerId)
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
    
    /**
     * Update player's last seen timestamp.
     * 
     * @param playerId Player's UUID as string
     * @param timestamp Unix timestamp in milliseconds
     */
    suspend fun updateLastSeen(playerId: String, timestamp: Long) {
        persistence.execute { conn ->
            conn.prepareStatement("""
                UPDATE players SET last_seen = ? WHERE player_id = ?
            """.trimIndent()).use { stmt ->
                stmt.setLong(1, timestamp)
                stmt.setString(2, playerId)
                val updated = stmt.executeUpdate()
                
                if (updated == 0) {
                    logger.atFine().log("Player not found for last seen update: $playerId")
                }
            }
        }
    }
    
    /**
     * Get all players in the database.
     * Useful for administrative commands or analytics.
     * 
     * @return List of all player records
     */
    suspend fun findAll(): List<PlayerData> {
        return persistence.execute { conn ->
            conn.prepareStatement("""
                SELECT player_id, player_name, first_seen, last_seen
                FROM players
                ORDER BY last_seen DESC
            """.trimIndent()).use { stmt ->
                stmt.executeQuery().useRows { rs ->
                    val players = mutableListOf<PlayerData>()
                    while (rs.next()) {
                        players.add(PlayerData(
                            playerId = rs.getString("player_id"),
                            playerName = rs.getString("player_name"),
                            firstSeen = rs.getLong("first_seen"),
                            lastSeen = rs.getLong("last_seen")
                        ))
                    }
                    players
                }
            }
        }
    }
    
    /**
     * Count total players in database.
     * 
     * @return Total player count
     */
    suspend fun count(): Int {
        return persistence.execute { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM players").use { stmt ->
                stmt.executeQuery().useRows { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }
    
    // Repository interface implementation
    
    /**
     * Save player data (insert or update).
     * Uses UPSERT pattern for idempotent saves.
     * 
     * @param entity PlayerData to save
     */
    override suspend fun save(entity: PlayerData) {
        persistence.execute { conn ->
            conn.prepareStatement("""
                INSERT INTO players (player_id, player_name, first_seen, last_seen)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET 
                    player_name = ?,
                    last_seen = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, entity.playerId)
                stmt.setString(2, entity.playerName)
                stmt.setLong(3, entity.firstSeen)
                stmt.setLong(4, entity.lastSeen)
                stmt.setString(5, entity.playerName)
                stmt.setLong(6, entity.lastSeen)
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Delete player record by ID.
     * 
     * @param id Player's UUID as string
     */
    override suspend fun delete(id: String) {
        persistence.execute { conn ->
            conn.prepareStatement("DELETE FROM players WHERE player_id = ?").use { stmt ->
                stmt.setString(1, id)
                val deleted = stmt.executeUpdate()
                if (deleted > 0) {
                    logger.atFine().log("Deleted player record: $id")
                }
            }
        }
    }
    
    /**
     * Check if a player exists by ID.
     * 
     * @param id Player's UUID as string
     * @return true if player exists, false otherwise
     */
    override suspend fun existsById(id: String): Boolean {
        return persistence.execute { conn ->
            conn.prepareStatement("SELECT 1 FROM players WHERE player_id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    rs.next()
                }
            }
        }
    }
}
