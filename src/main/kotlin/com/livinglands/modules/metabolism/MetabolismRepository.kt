package com.livinglands.modules.metabolism

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.persistence.PersistenceService
import com.livinglands.core.persistence.Repository
import com.livinglands.core.persistence.useRows
import java.sql.Connection

/**
 * Repository for metabolism stats persistence.
 * Each world has its own instance via WorldContext.
 * 
 * Database schema:
 * CREATE TABLE IF NOT EXISTS metabolism_stats (
 *     player_id TEXT PRIMARY KEY,
 *     hunger REAL NOT NULL DEFAULT 100.0,
 *     thirst REAL NOT NULL DEFAULT 100.0,
 *     energy REAL NOT NULL DEFAULT 100.0,
 *     last_updated INTEGER NOT NULL,
 *     FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE
 * )
 */
class MetabolismRepository(
    private val persistence: PersistenceService,
    private val logger: HytaleLogger
) : Repository<MetabolismStats, String> {
    
    companion object {
        private const val MODULE_ID = "metabolism"
        private const val SCHEMA_VERSION = 1
    }
    
    /**
     * Initialize the repository schema.
     * Creates the metabolism_stats table if it doesn't exist.
     */
    suspend fun initialize() {
        val currentVersion = persistence.getModuleSchemaVersion(MODULE_ID)
        
        if (currentVersion < SCHEMA_VERSION) {
            persistence.execute { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS metabolism_stats (
                            player_id TEXT PRIMARY KEY,
                            hunger REAL NOT NULL DEFAULT 100.0,
                            thirst REAL NOT NULL DEFAULT 100.0,
                            energy REAL NOT NULL DEFAULT 100.0,
                            last_updated INTEGER NOT NULL,
                            FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    
                    // Create index for faster lookups
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_metabolism_player 
                        ON metabolism_stats(player_id)
                    """.trimIndent())
                }
            }
            
            persistence.setModuleSchemaVersion(MODULE_ID, SCHEMA_VERSION)
            logger.atFine().log("Metabolism schema initialized (version $SCHEMA_VERSION)")
        }
    }
    
    /**
     * Ensure stats exist for a player, creating default if not present.
     * 
     * @param playerId Player's UUID as string
     * @return The existing or newly created stats
     */
    suspend fun ensureStats(playerId: String): MetabolismStats {
        return persistence.execute { conn ->
            val existing = findByIdInternal(conn, playerId)
            
            if (existing != null) {
                logger.atInfo().log("Loaded existing metabolism stats for $playerId: H=${existing.hunger}, T=${existing.thirst}, E=${existing.energy}")
                existing
            } else {
                // Create default stats
                val stats = MetabolismStats.createDefault(playerId)
                insertInternal(conn, stats)
                logger.atInfo().log("Created default metabolism stats for player: $playerId (H=100, T=100, E=100)")
                stats
            }
        }
    }
    
    /**
     * Find stats by player ID.
     */
    override suspend fun findById(id: String): MetabolismStats? {
        return persistence.execute { conn ->
            findByIdInternal(conn, id)
        }
    }
    
    /**
     * Internal find by ID (for use within connection scope).
     */
    private fun findByIdInternal(conn: Connection, playerId: String): MetabolismStats? {
        return conn.prepareStatement("""
            SELECT player_id, hunger, thirst, energy, last_updated
            FROM metabolism_stats
            WHERE player_id = ?
        """.trimIndent()).use { stmt ->
            stmt.setString(1, playerId)
            stmt.executeQuery().useRows { rs ->
                if (rs.next()) {
                    MetabolismStats(
                        playerId = rs.getString("player_id"),
                        hunger = rs.getFloat("hunger"),
                        thirst = rs.getFloat("thirst"),
                        energy = rs.getFloat("energy"),
                        lastUpdated = rs.getLong("last_updated")
                    )
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * Save stats (insert or update).
     */
    override suspend fun save(entity: MetabolismStats) {
        persistence.execute { conn ->
            conn.prepareStatement("""
                INSERT INTO metabolism_stats (player_id, hunger, thirst, energy, last_updated)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET 
                    hunger = ?,
                    thirst = ?,
                    energy = ?,
                    last_updated = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, entity.playerId)
                stmt.setFloat(2, entity.hunger)
                stmt.setFloat(3, entity.thirst)
                stmt.setFloat(4, entity.energy)
                stmt.setLong(5, entity.lastUpdated)
                // For update clause
                stmt.setFloat(6, entity.hunger)
                stmt.setFloat(7, entity.thirst)
                stmt.setFloat(8, entity.energy)
                stmt.setLong(9, entity.lastUpdated)
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Update stats (only if player exists).
     * More efficient than save() when we know the record exists.
     */
    suspend fun updateStats(stats: MetabolismStats) {
        persistence.execute { conn ->
            conn.prepareStatement("""
                UPDATE metabolism_stats 
                SET hunger = ?, thirst = ?, energy = ?, last_updated = ?
                WHERE player_id = ?
            """.trimIndent()).use { stmt ->
                stmt.setFloat(1, stats.hunger)
                stmt.setFloat(2, stats.thirst)
                stmt.setFloat(3, stats.energy)
                stmt.setLong(4, stats.lastUpdated)
                stmt.setString(5, stats.playerId)
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Internal insert (for use within connection scope).
     */
    private fun insertInternal(conn: Connection, stats: MetabolismStats) {
        conn.prepareStatement("""
            INSERT INTO metabolism_stats (player_id, hunger, thirst, energy, last_updated)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()).use { stmt ->
            stmt.setString(1, stats.playerId)
            stmt.setFloat(2, stats.hunger)
            stmt.setFloat(3, stats.thirst)
            stmt.setFloat(4, stats.energy)
            stmt.setLong(5, stats.lastUpdated)
            stmt.executeUpdate()
        }
    }
    
    /**
     * Delete stats by player ID.
     */
    override suspend fun delete(id: String) {
        persistence.execute { conn ->
            conn.prepareStatement("DELETE FROM metabolism_stats WHERE player_id = ?").use { stmt ->
                stmt.setString(1, id)
                val deleted = stmt.executeUpdate()
                if (deleted > 0) {
                    logger.atFine().log("Deleted metabolism stats for player: $id")
                }
            }
        }
    }
    
    /**
     * Check if stats exist for a player.
     */
    override suspend fun existsById(id: String): Boolean {
        return persistence.execute { conn ->
            conn.prepareStatement("SELECT 1 FROM metabolism_stats WHERE player_id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
    }
    
    /**
     * Get all metabolism stats in the database.
     * Useful for bulk operations or admin commands.
     */
    suspend fun getAllStats(): List<MetabolismStats> {
        return persistence.execute { conn ->
            conn.prepareStatement("""
                SELECT player_id, hunger, thirst, energy, last_updated
                FROM metabolism_stats
                ORDER BY last_updated DESC
            """.trimIndent()).use { stmt ->
                stmt.executeQuery().useRows { rs ->
                    val stats = mutableListOf<MetabolismStats>()
                    while (rs.next()) {
                        stats.add(MetabolismStats(
                            playerId = rs.getString("player_id"),
                            hunger = rs.getFloat("hunger"),
                            thirst = rs.getFloat("thirst"),
                            energy = rs.getFloat("energy"),
                            lastUpdated = rs.getLong("last_updated")
                        ))
                    }
                    stats
                }
            }
        }
    }
    
    /**
     * Bulk save multiple stats records.
     * More efficient than calling save() multiple times.
     */
    suspend fun saveAll(statsList: Collection<MetabolismStats>) {
        if (statsList.isEmpty()) return
        
        persistence.transaction { conn ->
            conn.prepareStatement("""
                INSERT INTO metabolism_stats (player_id, hunger, thirst, energy, last_updated)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET 
                    hunger = ?,
                    thirst = ?,
                    energy = ?,
                    last_updated = ?
            """.trimIndent()).use { stmt ->
                for (stats in statsList) {
                    stmt.setString(1, stats.playerId)
                    stmt.setFloat(2, stats.hunger)
                    stmt.setFloat(3, stats.thirst)
                    stmt.setFloat(4, stats.energy)
                    stmt.setLong(5, stats.lastUpdated)
                    stmt.setFloat(6, stats.hunger)
                    stmt.setFloat(7, stats.thirst)
                    stmt.setFloat(8, stats.energy)
                    stmt.setLong(9, stats.lastUpdated)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
        
        logger.atFine().log("Bulk saved ${statsList.size} metabolism stats records")
    }
}
