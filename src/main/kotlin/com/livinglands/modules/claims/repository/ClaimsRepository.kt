package com.livinglands.modules.claims.repository

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.modules.claims.Claim
import com.livinglands.modules.claims.ChunkPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID

/**
 * Repository for persisting claim data to SQLite database.
 * 
 * **Database Schema:**
 * - `land_claims` - Main claims table with chunk positions and ownership
 * - `claim_trust` - Trust relationships between claims and players
 * - `claim_counts` - Cached claim counts per player for quick limit checks
 * 
 * **Performance:**
 * - All operations are async (Dispatchers.IO)
 * - Prepared statements prevent SQL injection
 * - Indices on (world_id, chunk_x, chunk_z) for O(1) lookups
 * - Indices on owner_uuid for efficient "my claims" queries
 * 
 * **Thread Safety:**
 * - All methods use `withContext(Dispatchers.IO)` for safe DB access
 * - Connection pool not needed (single-world, SQLite serializes writes)
 * 
 * @param dbPath Path to SQLite database file (per-world)
 * @param logger Logger for SQL errors
 */
class ClaimsRepository(
    private val dbPath: String,
    private val logger: HytaleLogger
) {
    
    /**
     * Initialize database schema.
     * Creates tables and indices if they don't exist.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    // Main claims table
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS land_claims (
                            claim_id TEXT PRIMARY KEY,
                            owner_uuid TEXT NOT NULL,
                            world_id TEXT NOT NULL,
                            chunk_x INTEGER NOT NULL,
                            chunk_z INTEGER NOT NULL,
                            claim_name TEXT DEFAULT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            UNIQUE(world_id, chunk_x, chunk_z)
                        )
                    """.trimIndent())
                    
                    // Trust relationships
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS claim_trust (
                            claim_id TEXT NOT NULL,
                            trusted_uuid TEXT NOT NULL,
                            permission_level INTEGER NOT NULL DEFAULT 1,
                            added_at INTEGER NOT NULL,
                            PRIMARY KEY (claim_id, trusted_uuid),
                            FOREIGN KEY (claim_id) REFERENCES land_claims(claim_id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    
                    // Claim count cache (for max claims enforcement)
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS claim_counts (
                            owner_uuid TEXT PRIMARY KEY,
                            claim_count INTEGER NOT NULL DEFAULT 0,
                            last_updated INTEGER NOT NULL
                        )
                    """.trimIndent())
                    
                    // Indices for performance
                    stmt.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_chunk_lookup 
                        ON land_claims(world_id, chunk_x, chunk_z)
                    """.trimIndent())
                    
                    stmt.executeUpdate("""
                        CREATE INDEX IF NOT EXISTS idx_owner_claims 
                        ON land_claims(owner_uuid)
                    """.trimIndent())
                    
                    logger.atFine().log("Claims database initialized at: $dbPath")
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to initialize claims database")
            throw e
        }
    }
    
    /**
     * Get claim at a specific chunk position.
     * Returns null if no claim exists.
     */
    suspend fun getClaimAt(position: ChunkPosition): Claim? = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT c.claim_id, c.owner_uuid, c.world_id, c.chunk_x, c.chunk_z, 
                           c.claim_name, c.created_at, c.updated_at,
                           GROUP_CONCAT(t.trusted_uuid) as trusted_players
                    FROM land_claims c
                    LEFT JOIN claim_trust t ON c.claim_id = t.claim_id
                    WHERE c.world_id = ? AND c.chunk_x = ? AND c.chunk_z = ?
                    GROUP BY c.claim_id
                """.trimIndent()
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, position.worldId.toString())
                    stmt.setInt(2, position.chunkX)
                    stmt.setInt(3, position.chunkZ)
                    
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.toClaim()
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to get claim at $position")
            null
        }
    }
    
    /**
     * Get all claims owned by a player.
     */
    suspend fun getClaimsByOwner(ownerUuid: UUID): List<Claim> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT c.claim_id, c.owner_uuid, c.world_id, c.chunk_x, c.chunk_z, 
                           c.claim_name, c.created_at, c.updated_at,
                           GROUP_CONCAT(t.trusted_uuid) as trusted_players
                    FROM land_claims c
                    LEFT JOIN claim_trust t ON c.claim_id = t.claim_id
                    WHERE c.owner_uuid = ?
                    GROUP BY c.claim_id
                    ORDER BY c.created_at DESC
                """.trimIndent()
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.toClaim())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to get claims for owner $ownerUuid")
            emptyList()
        }
    }
    
    /**
     * Get all claims where a player is trusted.
     */
    suspend fun getClaimsWhereTrusted(trustedUuid: UUID): List<Claim> = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    SELECT c.claim_id, c.owner_uuid, c.world_id, c.chunk_x, c.chunk_z, 
                           c.claim_name, c.created_at, c.updated_at,
                           GROUP_CONCAT(t2.trusted_uuid) as trusted_players
                    FROM land_claims c
                    INNER JOIN claim_trust t ON c.claim_id = t.claim_id
                    LEFT JOIN claim_trust t2 ON c.claim_id = t2.claim_id
                    WHERE t.trusted_uuid = ?
                    GROUP BY c.claim_id
                    ORDER BY c.created_at DESC
                """.trimIndent()
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, trustedUuid.toString())
                    
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.toClaim())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to get trusted claims for $trustedUuid")
            emptyList()
        }
    }
    
    /**
     * Create a new claim.
     * Returns true if successful, false if chunk already claimed.
     */
    suspend fun createClaim(claim: Claim): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    // Insert claim
                    val claimSql = """
                        INSERT INTO land_claims 
                        (claim_id, owner_uuid, world_id, chunk_x, chunk_z, claim_name, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                    
                    conn.prepareStatement(claimSql).use { stmt ->
                        stmt.setString(1, claim.id.toString())
                        stmt.setString(2, claim.owner.toString())
                        stmt.setString(3, claim.position.worldId.toString())
                        stmt.setInt(4, claim.position.chunkX)
                        stmt.setInt(5, claim.position.chunkZ)
                        stmt.setString(6, claim.name)
                        stmt.setLong(7, claim.createdAt)
                        stmt.setLong(8, claim.updatedAt)
                        stmt.executeUpdate()
                    }
                    
                    // Insert trust relationships
                    if (claim.trustedPlayers.isNotEmpty()) {
                        val trustSql = """
                            INSERT INTO claim_trust (claim_id, trusted_uuid, permission_level, added_at)
                            VALUES (?, ?, 1, ?)
                        """.trimIndent()
                        
                        conn.prepareStatement(trustSql).use { stmt ->
                            val now = System.currentTimeMillis()
                            claim.trustedPlayers.forEach { trustedUuid ->
                                stmt.setString(1, claim.id.toString())
                                stmt.setString(2, trustedUuid.toString())
                                stmt.setLong(3, now)
                                stmt.addBatch()
                            }
                            stmt.executeBatch()
                        }
                    }
                    
                    // Update claim count
                    incrementClaimCount(conn, claim.owner)
                    
                    conn.commit()
                    logger.atFine().log("Created claim ${claim.id} at ${claim.position}")
                    true
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to create claim ${claim.id}")
            false
        }
    }
    
    /**
     * Delete a claim.
     * CASCADE delete removes trust relationships automatically.
     */
    suspend fun deleteClaim(claimId: UUID, ownerUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                conn.autoCommit = false
                try {
                    val sql = "DELETE FROM land_claims WHERE claim_id = ?"
                    
                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setString(1, claimId.toString())
                        val rowsAffected = stmt.executeUpdate()
                        
                        if (rowsAffected > 0) {
                            decrementClaimCount(conn, ownerUuid)
                            conn.commit()
                            logger.atFine().log("Deleted claim $claimId")
                            true
                        } else {
                            conn.rollback()
                            false
                        }
                    }
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to delete claim $claimId")
            false
        }
    }
    
    /**
     * Add a trusted player to a claim.
     */
    suspend fun addTrustedPlayer(claimId: UUID, trustedUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    INSERT OR IGNORE INTO claim_trust (claim_id, trusted_uuid, permission_level, added_at)
                    VALUES (?, ?, 1, ?)
                """.trimIndent()
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.setString(2, trustedUuid.toString())
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to add trusted player $trustedUuid to claim $claimId")
            false
        }
    }
    
    /**
     * Remove a trusted player from a claim.
     */
    suspend fun removeTrustedPlayer(claimId: UUID, trustedUuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = "DELETE FROM claim_trust WHERE claim_id = ? AND trusted_uuid = ?"
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.setString(2, trustedUuid.toString())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to remove trusted player $trustedUuid from claim $claimId")
            false
        }
    }
    
    /**
     * Get claim count for a player.
     */
    suspend fun getClaimCount(ownerUuid: UUID): Int = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = "SELECT claim_count FROM claim_counts WHERE owner_uuid = ?"
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt("claim_count") else 0
                    }
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to get claim count for $ownerUuid")
            0
        }
    }
    
    /**
     * Update claim name.
     */
    suspend fun updateClaimName(claimId: UUID, name: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = """
                    UPDATE land_claims 
                    SET claim_name = ?, updated_at = ? 
                    WHERE claim_id = ?
                """.trimIndent()
                
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, name)
                    stmt.setLong(2, System.currentTimeMillis())
                    stmt.setString(3, claimId.toString())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to update claim name for $claimId")
            false
        }
    }
    
    // ========== Private Helpers ==========
    
    private fun getConnection(): Connection {
        return DriverManager.getConnection("jdbc:sqlite:$dbPath")
    }
    
    private fun ResultSet.toClaim(): Claim {
        val trustedPlayersStr = getString("trusted_players")
        val trustedPlayers = if (trustedPlayersStr != null) {
            trustedPlayersStr.split(",").mapNotNull { 
                try { UUID.fromString(it) } catch (e: Exception) { null }
            }.toSet()
        } else {
            emptySet()
        }
        
        return Claim(
            id = UUID.fromString(getString("claim_id")),
            owner = UUID.fromString(getString("owner_uuid")),
            position = ChunkPosition(
                worldId = UUID.fromString(getString("world_id")),
                chunkX = getInt("chunk_x"),
                chunkZ = getInt("chunk_z")
            ),
            name = getString("claim_name"),
            trustedPlayers = trustedPlayers,
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at")
        )
    }
    
    private fun incrementClaimCount(conn: Connection, ownerUuid: UUID) {
        val sql = """
            INSERT INTO claim_counts (owner_uuid, claim_count, last_updated)
            VALUES (?, 1, ?)
            ON CONFLICT(owner_uuid) DO UPDATE SET 
                claim_count = claim_count + 1,
                last_updated = excluded.last_updated
        """.trimIndent()
        
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, ownerUuid.toString())
            stmt.setLong(2, System.currentTimeMillis())
            stmt.executeUpdate()
        }
    }
    
    private fun decrementClaimCount(conn: Connection, ownerUuid: UUID) {
        val sql = """
            UPDATE claim_counts 
            SET claim_count = MAX(0, claim_count - 1), last_updated = ?
            WHERE owner_uuid = ?
        """.trimIndent()
        
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, System.currentTimeMillis())
            stmt.setString(2, ownerUuid.toString())
            stmt.executeUpdate()
        }
    }
}
