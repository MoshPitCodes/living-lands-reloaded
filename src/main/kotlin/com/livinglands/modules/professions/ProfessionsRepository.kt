package com.livinglands.modules.professions

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.persistence.GlobalPersistenceService
import com.livinglands.modules.professions.data.Profession
import com.livinglands.modules.professions.data.ProfessionStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for profession stats persistence.
 * 
 * Uses global database (stats follow player across all worlds).
 * Thread-safe via GlobalPersistenceService.
 * 
 * Table schema:
 * ```sql
 * CREATE TABLE professions_stats (
 *     player_id TEXT NOT NULL,
 *     profession TEXT NOT NULL,
 *     xp INTEGER NOT NULL DEFAULT 0,
 *     level INTEGER NOT NULL DEFAULT 1,
 *     last_updated INTEGER NOT NULL,
 *     PRIMARY KEY (player_id, profession)
 * );
 * CREATE INDEX idx_professions_player ON professions_stats(player_id);
 * ```
 */
class ProfessionsRepository(
    private val persistence: GlobalPersistenceService,
    private val logger: HytaleLogger
) {
    
    companion object {
        private const val SCHEMA_VERSION = 1
        private const val MODULE_ID = "professions"
    }
    
    /**
     * Initialize database schema.
     * Called once during module setup.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val currentVersion = persistence.getModuleSchemaVersion(MODULE_ID)
        
        if (currentVersion == 0) {
            // First-time setup
            LoggingManager.debug(logger, "professions") { "Initializing professions database schema (v$SCHEMA_VERSION)..." }
            
            persistence.transaction { conn ->
                // Create professions_stats table
                conn.createStatement().use { stmt ->
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS professions_stats (
                            player_id TEXT NOT NULL,
                            profession TEXT NOT NULL,
                            xp INTEGER NOT NULL DEFAULT 0,
                            level INTEGER NOT NULL DEFAULT 1,
                            last_updated INTEGER NOT NULL,
                            PRIMARY KEY (player_id, profession)
                        )
                    """.trimIndent())
                    
                    // Create index for faster player lookups
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_professions_player 
                        ON professions_stats(player_id)
                    """.trimIndent())
                }
            }
            
            // Set schema version
            persistence.setModuleSchemaVersion(MODULE_ID, SCHEMA_VERSION)
            
            LoggingManager.debug(logger, "professions") { "Professions database schema initialized successfully" }
        } else if (currentVersion < SCHEMA_VERSION) {
            // Future migrations go here
            LoggingManager.debug(logger, "professions") { "Professions schema at v$currentVersion, migrations not yet implemented" }
        } else {
            LoggingManager.debug(logger, "professions") { "Professions schema up-to-date (v$currentVersion)" }
        }
    }
    
    /**
     * Ensure all 5 professions exist for a player with default values.
     * 
     * If stats don't exist, creates them with XP=0, Level=1.
     * If stats exist, does nothing (does NOT overwrite).
     * 
     * @param playerId Player UUID as cached string
     * @return Map of Profession to ProfessionStats (from DB or defaults)
     */
    suspend fun ensureStats(playerId: String): Map<Profession, ProfessionStats> = withContext(Dispatchers.IO) {
        val existingStats = findAllByPlayer(playerId)
        
        // Check which professions are missing
        val missingProfessions = Profession.all().filter { prof ->
            existingStats.none { it.profession == prof }
        }
        
        if (missingProfessions.isNotEmpty()) {
            // Insert defaults for missing professions
            persistence.transaction { conn ->
                val stmt = conn.prepareStatement("""
                    INSERT OR IGNORE INTO professions_stats 
                    (player_id, profession, xp, level, last_updated)
                    VALUES (?, ?, 0, 1, ?)
                """.trimIndent())
                
                missingProfessions.forEach { profession ->
                    stmt.setString(1, playerId)
                    stmt.setString(2, profession.dbId)
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.executeUpdate()
                }
                
                stmt.close()
            }
            
            LoggingManager.debug(logger, "professions") { "Initialized ${missingProfessions.size} missing professions for player $playerId" }
        }
        
        // Return all stats (existing + newly created)
        return@withContext findAllByPlayer(playerId).associateBy { it.profession }
    }
    
    /**
     * Find all profession stats for a player.
     * 
     * @param playerId Player UUID as cached string
     * @return List of ProfessionStats (may be empty if player is new)
     */
    suspend fun findAllByPlayer(playerId: String): List<ProfessionStats> = withContext(Dispatchers.IO) {
        val stats = mutableListOf<ProfessionStats>()
        
        persistence.execute {
            val stmt = it.prepareStatement("""
                SELECT profession, xp, level, last_updated
                FROM professions_stats
                WHERE player_id = ?
            """.trimIndent())
            
            stmt.setString(1, playerId)
            val rs = stmt.executeQuery()
            
            while (rs.next()) {
                val professionDbId = rs.getString("profession")
                val profession = Profession.fromDbId(professionDbId)
                
                if (profession != null) {
                    stats.add(ProfessionStats(
                        playerId = playerId,
                        profession = profession,
                        xp = rs.getLong("xp"),
                        level = rs.getInt("level"),
                        lastUpdated = rs.getLong("last_updated")
                    ))
                } else {
                    LoggingManager.warn(logger, "professions") { "Unknown profession '$professionDbId' for player $playerId - skipping" }
                }
            }
            
            rs.close()
            stmt.close()
        }
        
        return@withContext stats
    }
    
    /**
     * Find stats for a specific profession.
     * 
     * @param playerId Player UUID as cached string
     * @param profession The profession to query
     * @return ProfessionStats or null if not found
     */
    suspend fun findByProfession(playerId: String, profession: Profession): ProfessionStats? = withContext(Dispatchers.IO) {
        var stats: ProfessionStats? = null
        
        persistence.execute {
            val stmt = it.prepareStatement("""
                SELECT xp, level, last_updated
                FROM professions_stats
                WHERE player_id = ? AND profession = ?
            """.trimIndent())
            
            stmt.setString(1, playerId)
            stmt.setString(2, profession.dbId)
            val rs = stmt.executeQuery()
            
            if (rs.next()) {
                stats = ProfessionStats(
                    playerId = playerId,
                    profession = profession,
                    xp = rs.getLong("xp"),
                    level = rs.getInt("level"),
                    lastUpdated = rs.getLong("last_updated")
                )
            }
            
            rs.close()
            stmt.close()
        }
        
        return@withContext stats
    }
    
    /**
     * Update stats for a single profession.
     * 
     * Uses INSERT OR REPLACE to upsert.
     * 
     * @param stats The stats to save
     */
    suspend fun updateStats(stats: ProfessionStats) = withContext(Dispatchers.IO) {
        persistence.execute {
            val stmt = it.prepareStatement("""
                INSERT OR REPLACE INTO professions_stats 
                (player_id, profession, xp, level, last_updated)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent())
            
            stmt.setString(1, stats.playerId)
            stmt.setString(2, stats.profession.dbId)
            stmt.setLong(3, stats.xp)
            stmt.setInt(4, stats.level)
            stmt.setLong(5, stats.lastUpdated)
            
            val rowsAffected = stmt.executeUpdate()
            
            if (rowsAffected == 0) {
                LoggingManager.warn(logger, "professions") { "DB WRITE FAILED: 0 rows affected when saving profession ${stats.profession.dbId} for player ${stats.playerId}" }
            }
            
            stmt.close()
        }
    }
    
    /**
     * Save all 5 professions for a player in a single transaction.
     * 
     * More efficient than 5 separate updateStats() calls.
     * Used on player disconnect and shutdown.
     * 
     * @param stats List of ProfessionStats to save (typically 5 entries)
     */
    suspend fun saveAll(stats: List<ProfessionStats>) = withContext(Dispatchers.IO) {
        if (stats.isEmpty()) return@withContext
        
        persistence.transaction { conn ->
            val stmt = conn.prepareStatement("""
                INSERT OR REPLACE INTO professions_stats 
                (player_id, profession, xp, level, last_updated)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent())
            
            var failedWrites = 0
            stats.forEach { profStats ->
                stmt.setString(1, profStats.playerId)
                stmt.setString(2, profStats.profession.dbId)
                stmt.setLong(3, profStats.xp)
                stmt.setInt(4, profStats.level)
                stmt.setLong(5, profStats.lastUpdated)
                val rowsAffected = stmt.executeUpdate()
                
                if (rowsAffected == 0) {
                    failedWrites++
                }
            }
            
            stmt.close()
            
            if (failedWrites > 0) {
                LoggingManager.warn(logger, "professions") { "DB WRITE FAILED: $failedWrites out of ${stats.size} profession saves had 0 rows affected for player ${stats.firstOrNull()?.playerId}" }
            }
        }
        
        LoggingManager.debug(logger, "professions") { "Saved ${stats.size} profession stats for player ${stats.firstOrNull()?.playerId}" }
    }
    
    /**
     * Reset a player's stats for a specific profession.
     * 
     * Sets XP=0, Level=1.
     * 
     * @param playerId Player UUID as cached string
     * @param profession The profession to reset
     */
    suspend fun resetProfession(playerId: String, profession: Profession) = withContext(Dispatchers.IO) {
        persistence.execute {
            val stmt = it.prepareStatement("""
                INSERT OR REPLACE INTO professions_stats 
                (player_id, profession, xp, level, last_updated)
                VALUES (?, ?, 0, 1, ?)
            """.trimIndent())
            
            stmt.setString(1, playerId)
            stmt.setString(2, profession.dbId)
            stmt.setLong(3, System.currentTimeMillis())
            
            stmt.executeUpdate()
            stmt.close()
        }
        
        LoggingManager.debug(logger, "professions") { "Reset profession ${profession.displayName} for player $playerId" }
    }
    
    /**
     * Reset all professions for a player.
     * 
     * Sets all 5 professions to XP=0, Level=1.
     * 
     * @param playerId Player UUID as cached string
     */
    suspend fun resetAllProfessions(playerId: String) = withContext(Dispatchers.IO) {
        persistence.execute {
            val stmt = it.prepareStatement("""
                DELETE FROM professions_stats
                WHERE player_id = ?
            """.trimIndent())
            
            stmt.setString(1, playerId)
            stmt.executeUpdate()
            stmt.close()
        }
        
        // Re-initialize with defaults
        ensureStats(playerId)
        
        LoggingManager.debug(logger, "professions") { "Reset all professions for player $playerId" }
    }
    
    /**
     * Delete all data for a player (GDPR compliance).
     * 
     * @param playerId Player UUID as cached string
     */
    suspend fun deletePlayer(playerId: String) = withContext(Dispatchers.IO) {
        persistence.execute {
            val stmt = it.prepareStatement("""
                DELETE FROM professions_stats
                WHERE player_id = ?
            """.trimIndent())
            
            stmt.setString(1, playerId)
            val deleted = stmt.executeUpdate()
            stmt.close()
            
            if (deleted == 0) {
                LoggingManager.warn(logger, "professions") { "DB DELETE: 0 rows affected when deleting profession stats for player $playerId (may not exist)" }
            } else {
                LoggingManager.debug(logger, "professions") { "Deleted $deleted profession records for player $playerId" }
            }
        }
    }
}
