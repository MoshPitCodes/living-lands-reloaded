package com.livinglands.modules.claims.repository

import com.livinglands.core.logging.LoggingManager
import com.livinglands.core.persistence.GlobalPersistenceService
import com.livinglands.core.persistence.Repository
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.modules.claims.Claim
import com.livinglands.modules.claims.ChunkPosition
import com.livinglands.modules.claims.data.ClaimGroup
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

/**
 * Repository for persisting claim data to SQLite database.
 *
 * **ARCHITECTURE NOTE:**
 * Claims are GLOBAL (server-wide), not per-world.
 * Uses GlobalPersistenceService for shared data across worlds.
 *
 * **Database Schema (v4 - Multi-Chunk Plots):**
 * - `land_claims` - Main claims table (one row per plot, no chunk columns)
 * - `claim_chunks` - Chunk positions belonging to each plot (many:1)
 * - `claim_trust` - Trust relationships between claims and players
 * - `claim_groups` - Player-defined groups for organizing trusted players
 * - `group_members` - Players who are members of groups
 * - `claim_group_trust` - Groups trusted to claims
 *
 * **Migration:**
 * v3→v4: Moves chunk data from land_claims to separate claim_chunks table,
 *         drops claim_counts table (use COUNT queries instead).
 *
 * **Performance:**
 * - All operations are async via GlobalPersistenceService
 * - Prepared statements prevent SQL injection
 * - Index on claim_chunks(world_id, chunk_x, chunk_z) for O(1) lookups
 * - Index on claim_chunks(claim_id) for efficient chunk-to-claim joins
 * - Index on land_claims(owner_uuid) for efficient "my claims" queries
 *
 * **Thread Safety:**
 * - GlobalPersistenceService handles thread-safe DB access
 * - Connection pool not needed (SQLite serializes writes)
 *
 * @param persistence Global persistence service
 * @param logger Logger for SQL errors
 */
class ClaimsRepository(
    private val persistence: GlobalPersistenceService,
    private val logger: HytaleLogger
) : Repository<Claim, UUID> {

    companion object {
        private const val MODULE_ID = "claims"
        private const val SCHEMA_VERSION = 4
    }
    
    /**
     * Initialize database schema.
     * Creates tables and indices if they don't exist.
     * Migrates from v3 to v4 if needed.
     */
    suspend fun initialize() {
        val currentVersion = persistence.getModuleSchemaVersion(MODULE_ID)

        if (currentVersion < SCHEMA_VERSION) {
            persistence.execute { conn ->
                if (currentVersion >= 3) {
                    // Migrate v3 -> v4
                    migrateV3ToV4(conn)
                } else {
                    // Fresh install - create v4 schema directly
                    createV4Schema(conn)
                }

                LoggingManager.debug(logger, "claims") { "Claims database schema at version $SCHEMA_VERSION" }
            }

            persistence.setModuleSchemaVersion(MODULE_ID, SCHEMA_VERSION)
            LoggingManager.info(logger, "claims") { "Claims repository initialized at schema version $SCHEMA_VERSION" }
        }
    }

    /**
     * Migrate from schema v3 (chunk data in land_claims) to v4 (separate claim_chunks table).
     *
     * Steps:
     * 1. Create claim_chunks table
     * 2. Copy chunk data from land_claims to claim_chunks
     * 3. Recreate land_claims without chunk columns (SQLite doesn't support DROP COLUMN cleanly)
     * 4. Drop claim_counts table (no longer needed)
     */
    private fun migrateV3ToV4(conn: Connection) {
        conn.createStatement().use { stmt ->
            LoggingManager.info(logger, "claims") { "Migrating claims schema v3 -> v4 (multi-chunk plots)..." }

            // CRITICAL: Disable foreign keys during migration.
            // SQLite ALTER TABLE RENAME updates FK references in OTHER tables
            // (claim_trust, claim_group_trust) to point to the renamed table.
            // We must disable FK enforcement so we can safely drop+recreate.
            stmt.executeUpdate("PRAGMA foreign_keys = OFF")

            // Step 1: Create metadata backup from original land_claims (if it still exists)
            // Check if original land_claims or its backup exists
            val hasOriginalTable = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='land_claims'"
            ).use { rs -> rs.next() }

            val hasBackupTable = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='land_claims_v3_backup'"
            ).use { rs -> rs.next() }

            val sourceTable = when {
                hasBackupTable -> "land_claims_v3_backup"  // Previous failed migration left this
                hasOriginalTable -> "land_claims"
                else -> null
            }

            if (sourceTable != null) {
                // Check if source table has chunk columns (v3 schema)
                val hasChunkColumns = stmt.executeQuery(
                    "SELECT COUNT(*) FROM pragma_table_info('$sourceTable') WHERE name='chunk_x'"
                ).use { rs -> rs.next() && rs.getInt(1) > 0 }

                if (hasChunkColumns) {
                    // Source is v3 format - extract chunks and recreate
                    LoggingManager.info(logger, "claims") { "Found v3 data in '$sourceTable', migrating..." }

                    // Create claim_chunks from v3 data
                    stmt.executeUpdate("DROP TABLE IF EXISTS claim_chunks")
                    stmt.executeUpdate("""
                        CREATE TABLE claim_chunks (
                            claim_id TEXT NOT NULL,
                            world_id TEXT NOT NULL,
                            chunk_x INTEGER NOT NULL,
                            chunk_z INTEGER NOT NULL,
                            PRIMARY KEY (world_id, chunk_x, chunk_z)
                        )
                    """.trimIndent())

                    stmt.executeUpdate("""
                        INSERT OR IGNORE INTO claim_chunks (claim_id, world_id, chunk_x, chunk_z)
                        SELECT claim_id, world_id, chunk_x, chunk_z FROM $sourceTable
                    """.trimIndent())

                    val migratedCount = stmt.executeQuery("SELECT COUNT(*) FROM claim_chunks").use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                    LoggingManager.info(logger, "claims") { "Migrated $migratedCount chunk entries to claim_chunks" }

                    // Save claim metadata to temp table
                    stmt.executeUpdate("DROP TABLE IF EXISTS land_claims_metadata_tmp")
                    stmt.executeUpdate("""
                        CREATE TABLE land_claims_metadata_tmp AS
                        SELECT DISTINCT claim_id, owner_uuid, claim_name, created_at, updated_at
                        FROM $sourceTable
                    """.trimIndent())

                    // Save trust data
                    stmt.executeUpdate("DROP TABLE IF EXISTS claim_trust_tmp")
                    val hasTrustTable = stmt.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='claim_trust'"
                    ).use { rs -> rs.next() }
                    if (hasTrustTable) {
                        stmt.executeUpdate("CREATE TABLE claim_trust_tmp AS SELECT * FROM claim_trust")
                    }

                    // Save group trust data
                    stmt.executeUpdate("DROP TABLE IF EXISTS claim_group_trust_tmp")
                    val hasGroupTrustTable = stmt.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='claim_group_trust'"
                    ).use { rs -> rs.next() }
                    if (hasGroupTrustTable) {
                        stmt.executeUpdate("CREATE TABLE claim_group_trust_tmp AS SELECT * FROM claim_group_trust")
                    }

                    // Now drop everything and recreate clean
                    stmt.executeUpdate("DROP TABLE IF EXISTS claim_trust")
                    stmt.executeUpdate("DROP TABLE IF EXISTS claim_group_trust")
                    stmt.executeUpdate("DROP TABLE IF EXISTS $sourceTable")
                    if (sourceTable != "land_claims") {
                        stmt.executeUpdate("DROP TABLE IF EXISTS land_claims")
                    }
                    stmt.executeUpdate("DROP TABLE IF EXISTS claim_counts")

                    // Create fresh v4 land_claims
                    stmt.executeUpdate("""
                        CREATE TABLE land_claims (
                            claim_id TEXT PRIMARY KEY,
                            owner_uuid TEXT NOT NULL,
                            claim_name TEXT DEFAULT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // Restore claim metadata
                    stmt.executeUpdate("""
                        INSERT INTO land_claims (claim_id, owner_uuid, claim_name, created_at, updated_at)
                        SELECT claim_id, owner_uuid, claim_name, created_at, updated_at
                        FROM land_claims_metadata_tmp
                    """.trimIndent())
                    stmt.executeUpdate("DROP TABLE land_claims_metadata_tmp")

                    // Recreate claim_trust with proper FK
                    stmt.executeUpdate("""
                        CREATE TABLE claim_trust (
                            claim_id TEXT NOT NULL,
                            trusted_uuid TEXT NOT NULL,
                            permission_level INTEGER NOT NULL DEFAULT 1,
                            added_at INTEGER NOT NULL,
                            PRIMARY KEY (claim_id, trusted_uuid),
                            FOREIGN KEY (claim_id) REFERENCES land_claims(claim_id) ON DELETE CASCADE
                        )
                    """.trimIndent())

                    // Restore trust data
                    if (hasTrustTable) {
                        stmt.executeUpdate("""
                            INSERT OR IGNORE INTO claim_trust
                            SELECT * FROM claim_trust_tmp
                        """.trimIndent())
                        stmt.executeUpdate("DROP TABLE claim_trust_tmp")
                    }

                    // Add FK to claim_chunks now that land_claims exists
                    // SQLite doesn't support ALTER TABLE ADD CONSTRAINT, so recreate
                    stmt.executeUpdate("ALTER TABLE claim_chunks RENAME TO claim_chunks_tmp")
                    stmt.executeUpdate("""
                        CREATE TABLE claim_chunks (
                            claim_id TEXT NOT NULL,
                            world_id TEXT NOT NULL,
                            chunk_x INTEGER NOT NULL,
                            chunk_z INTEGER NOT NULL,
                            PRIMARY KEY (world_id, chunk_x, chunk_z),
                            FOREIGN KEY (claim_id) REFERENCES land_claims(claim_id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    stmt.executeUpdate("INSERT INTO claim_chunks SELECT * FROM claim_chunks_tmp")
                    stmt.executeUpdate("DROP TABLE claim_chunks_tmp")

                    LoggingManager.info(logger, "claims") { "Claims metadata and trust data restored" }
                } else {
                    // Source is already v4 format (no chunk columns) - just ensure clean state
                    LoggingManager.info(logger, "claims") { "Found v4-format data in '$sourceTable'" }
                    if (sourceTable == "land_claims_v3_backup") {
                        // Rename backup back to land_claims
                        stmt.executeUpdate("DROP TABLE IF EXISTS land_claims")
                        stmt.executeUpdate("ALTER TABLE land_claims_v3_backup RENAME TO land_claims")
                    }
                }
            } else {
                // No existing data - fresh install
                LoggingManager.info(logger, "claims") { "No existing claims data found, creating fresh v4 schema" }
            }

            // Ensure all v4 tables exist (idempotent)
            createV4Schema(conn)

            // Restore group trust if backed up
            val hasGroupTrustTmp = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='claim_group_trust_tmp'"
            ).use { rs -> rs.next() }
            if (hasGroupTrustTmp) {
                stmt.executeUpdate("INSERT OR IGNORE INTO claim_group_trust SELECT * FROM claim_group_trust_tmp")
                stmt.executeUpdate("DROP TABLE claim_group_trust_tmp")
            }

            // Clean up any leftover temp/backup tables
            stmt.executeUpdate("DROP TABLE IF EXISTS land_claims_v3_backup")
            stmt.executeUpdate("DROP TABLE IF EXISTS claim_trust_backup")
            stmt.executeUpdate("DROP TABLE IF EXISTS claim_group_trust_backup")
            stmt.executeUpdate("DROP TABLE IF EXISTS claim_counts")

            // Re-enable foreign keys
            stmt.executeUpdate("PRAGMA foreign_keys = ON")

            // Create indices
            createV4Indices(stmt)

            LoggingManager.info(logger, "claims") { "Claims schema migration v3 -> v4 complete" }
        }
    }

    /**
     * Create fresh v4 schema (for new installations).
     */
    private fun createV4Schema(conn: Connection) {
        conn.createStatement().use { stmt ->
            // Main claims table (no chunk columns)
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS land_claims (
                    claim_id TEXT PRIMARY KEY,
                    owner_uuid TEXT NOT NULL,
                    claim_name TEXT DEFAULT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())

            // Chunk positions belonging to claims
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS claim_chunks (
                    claim_id TEXT NOT NULL,
                    world_id TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    PRIMARY KEY (world_id, chunk_x, chunk_z),
                    FOREIGN KEY (claim_id) REFERENCES land_claims(claim_id) ON DELETE CASCADE
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

            // Create indices
            createV4Indices(stmt)

            // Group tables
            createGroupTables(stmt)

            LoggingManager.debug(logger, "claims") { "Claims v4 database schema created" }
        }
    }

    /**
     * Create v4 indices for performance.
     */
    private fun createV4Indices(stmt: java.sql.Statement) {
        stmt.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_claim_chunks_claim_id
            ON claim_chunks(claim_id)
        """.trimIndent())

        stmt.executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_owner_claims
            ON land_claims(owner_uuid)
        """.trimIndent())
    }

    /**
     * Create group system tables (shared between v3 and v4).
     */
    private fun createGroupTables(stmt: java.sql.Statement) {
        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS claim_groups (
                group_id TEXT PRIMARY KEY,
                owner_uuid TEXT NOT NULL,
                group_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS group_members (
                group_id TEXT NOT NULL,
                member_uuid TEXT NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY (group_id, member_uuid),
                FOREIGN KEY (group_id) REFERENCES claim_groups(group_id) ON DELETE CASCADE
            )
        """.trimIndent())

        stmt.executeUpdate("""
            CREATE TABLE IF NOT EXISTS claim_group_trust (
                claim_id TEXT NOT NULL,
                group_id TEXT NOT NULL,
                granted_at INTEGER NOT NULL,
                PRIMARY KEY (claim_id, group_id),
                FOREIGN KEY (claim_id) REFERENCES land_claims(claim_id) ON DELETE CASCADE,
                FOREIGN KEY (group_id) REFERENCES claim_groups(group_id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Group indices
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_groups_owner ON claim_groups(owner_uuid)")
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_members_group ON group_members(group_id)")
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_members_player ON group_members(member_uuid)")
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_group_trust_claim ON claim_group_trust(claim_id)")
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_group_trust_group ON claim_group_trust(group_id)")
    }

    // ========== Claim CRUD ==========
    
    /**
     * Get claim at a specific chunk position.
     * Queries claim_chunks first, then joins to land_claims.
     * Returns null if no claim exists.
     */
    suspend fun getClaimAt(position: ChunkPosition): Claim? {
        return try {
            persistence.execute { conn ->
                // Find the claim_id for this chunk
                val findSql = """
                    SELECT claim_id FROM claim_chunks
                    WHERE world_id = ? AND chunk_x = ? AND chunk_z = ?
                """.trimIndent()

                val claimId = conn.prepareStatement(findSql).use { stmt ->
                    stmt.setString(1, position.worldId.toString())
                    stmt.setInt(2, position.chunkX)
                    stmt.setInt(3, position.chunkZ)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("claim_id") else null
                    }
                } ?: return@execute null

                // Load full claim with all chunks and trust
                loadClaimById(conn, UUID.fromString(claimId))
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get claim at $position" }
            null
        }
    }
    
    /**
     * Get all claims owned by a player.
     */
    suspend fun getClaimsByOwner(ownerUuid: UUID): List<Claim> {
        return try {
            persistence.execute { conn ->
                val sql = "SELECT claim_id FROM land_claims WHERE owner_uuid = ? ORDER BY created_at DESC"

                val claimIds = conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(UUID.fromString(rs.getString("claim_id")))
                            }
                        }
                    }
                }

                claimIds.mapNotNull { loadClaimById(conn, it) }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get claims for owner $ownerUuid" }
            emptyList()
        }
    }
    
    /**
     * Get all claims where a player is trusted.
     */
    suspend fun getClaimsWhereTrusted(trustedUuid: UUID): List<Claim> {
        return try {
            persistence.execute { conn ->
                val sql = """
                    SELECT DISTINCT t.claim_id
                    FROM claim_trust t
                    INNER JOIN land_claims c ON t.claim_id = c.claim_id
                    WHERE t.trusted_uuid = ?
                    ORDER BY c.created_at DESC
                """.trimIndent()

                val claimIds = conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, trustedUuid.toString())
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(UUID.fromString(rs.getString("claim_id")))
                            }
                        }
                    }
                }

                claimIds.mapNotNull { loadClaimById(conn, it) }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get trusted claims for $trustedUuid" }
            emptyList()
        }
    }
    
    /**
     * Create a new claim (plot) with one or more chunks.
     * Returns true if successful, false if any chunk is already claimed.
     */
    suspend fun createClaim(claim: Claim): Boolean {
        return try {
            persistence.transaction { conn ->
                // Insert claim row
                val claimSql = """
                    INSERT INTO land_claims (claim_id, owner_uuid, claim_name, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(claimSql).use { stmt ->
                    stmt.setString(1, claim.id.toString())
                    stmt.setString(2, claim.owner.toString())
                    stmt.setString(3, claim.name)
                    stmt.setLong(4, claim.createdAt)
                    stmt.setLong(5, claim.updatedAt)
                    stmt.executeUpdate()
                }

                // Batch-insert all chunks
                val chunkSql = """
                    INSERT INTO claim_chunks (claim_id, world_id, chunk_x, chunk_z)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(chunkSql).use { stmt ->
                    for (chunk in claim.chunks) {
                        stmt.setString(1, claim.id.toString())
                        stmt.setString(2, chunk.worldId.toString())
                        stmt.setInt(3, chunk.chunkX)
                        stmt.setInt(4, chunk.chunkZ)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
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

                LoggingManager.debug(logger, "claims") {
                    "Created claim ${claim.id} with ${claim.chunks.size} chunks"
                }
                true
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to create claim ${claim.id}" }
            false
        }
    }
    
    /**
     * Delete a claim (plot).
     * CASCADE delete removes chunks, trust relationships automatically.
     */
    suspend fun deleteClaim(claimId: UUID, ownerUuid: UUID): Boolean {
        return try {
            persistence.transaction { conn ->
                // Delete chunks first (for DBs without CASCADE support)
                conn.prepareStatement("DELETE FROM claim_chunks WHERE claim_id = ?").use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.executeUpdate()
                }

                val sql = "DELETE FROM land_claims WHERE claim_id = ?"

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    val rowsAffected = stmt.executeUpdate()

                    if (rowsAffected > 0) {
                        LoggingManager.debug(logger, "claims") { "Deleted claim $claimId" }
                        true
                    } else {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to delete claim $claimId" }
            false
        }
    }

    /**
     * Add chunks to an existing claim (plot).
     *
     * @param claimId UUID of the claim
     * @param chunks Set of chunk positions to add
     * @return true if all chunks added successfully
     */
    suspend fun addChunksToClaim(claimId: UUID, chunks: Set<ChunkPosition>): Boolean {
        if (chunks.isEmpty()) return true
        return try {
            persistence.transaction { conn ->
                val sql = """
                    INSERT INTO claim_chunks (claim_id, world_id, chunk_x, chunk_z)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    for (chunk in chunks) {
                        stmt.setString(1, claimId.toString())
                        stmt.setString(2, chunk.worldId.toString())
                        stmt.setInt(3, chunk.chunkX)
                        stmt.setInt(4, chunk.chunkZ)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }

                // Update timestamp
                updateClaimTimestamp(conn, claimId)

                LoggingManager.debug(logger, "claims") {
                    "Added ${chunks.size} chunks to claim $claimId"
                }
                true
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to add chunks to claim $claimId" }
            false
        }
    }

    /**
     * Remove chunks from an existing claim (plot).
     *
     * @param claimId UUID of the claim
     * @param chunks Set of chunk positions to remove
     * @return true if all chunks removed successfully
     */
    suspend fun removeChunksFromClaim(claimId: UUID, chunks: Set<ChunkPosition>): Boolean {
        if (chunks.isEmpty()) return true
        return try {
            persistence.transaction { conn ->
                val sql = "DELETE FROM claim_chunks WHERE claim_id = ? AND world_id = ? AND chunk_x = ? AND chunk_z = ?"

                conn.prepareStatement(sql).use { stmt ->
                    for (chunk in chunks) {
                        stmt.setString(1, claimId.toString())
                        stmt.setString(2, chunk.worldId.toString())
                        stmt.setInt(3, chunk.chunkX)
                        stmt.setInt(4, chunk.chunkZ)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }

                // Update timestamp
                updateClaimTimestamp(conn, claimId)

                LoggingManager.debug(logger, "claims") {
                    "Removed ${chunks.size} chunks from claim $claimId"
                }
                true
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to remove chunks from claim $claimId" }
            false
        }
    }

    /**
     * Get chunk count for a specific claim.
     */
    suspend fun getChunkCountForClaim(claimId: UUID): Int {
        return try {
            persistence.execute { conn ->
                val sql = "SELECT COUNT(*) FROM claim_chunks WHERE claim_id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get chunk count for claim $claimId" }
            0
        }
    }

    /**
     * Get total chunk count across all claims for a player.
     */
    suspend fun getTotalChunkCount(ownerUuid: UUID): Int {
        return try {
            persistence.execute { conn ->
                val sql = """
                    SELECT COUNT(*) FROM claim_chunks cc
                    INNER JOIN land_claims lc ON cc.claim_id = lc.claim_id
                    WHERE lc.owner_uuid = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get total chunk count for $ownerUuid" }
            0
        }
    }

    /**
     * Get plot count for a player.
     */
    suspend fun getPlotCount(ownerUuid: UUID): Int {
        return try {
            persistence.execute { conn ->
                val sql = "SELECT COUNT(*) FROM land_claims WHERE owner_uuid = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get plot count for $ownerUuid" }
            0
        }
    }

    // ========== Trust Management ==========

    /**
     * Add a trusted player to a claim.
     */
    suspend fun addTrustedPlayer(claimId: UUID, trustedUuid: UUID): Boolean {
        return try {
            persistence.execute { conn ->
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
            LoggingManager.error(logger, "claims", e) { "Failed to add trusted player $trustedUuid to claim $claimId" }
            false
        }
    }
    
    /**
     * Remove a trusted player from a claim.
     */
    suspend fun removeTrustedPlayer(claimId: UUID, trustedUuid: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = "DELETE FROM claim_trust WHERE claim_id = ? AND trusted_uuid = ?"

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.setString(2, trustedUuid.toString())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to remove trusted player $trustedUuid from claim $claimId" }
            false
        }
    }
    
    /**
     * Update claim name.
     */
    suspend fun updateClaimName(claimId: UUID, name: String?): Boolean {
        return try {
            persistence.execute { conn ->
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
            LoggingManager.error(logger, "claims", e) { "Failed to update claim name for $claimId" }
            false
        }
    }

    /**
     * Get a claim by its UUID.
     */
    suspend fun getClaimById(claimId: UUID): Claim? {
        return try {
            persistence.execute { conn ->
                loadClaimById(conn, claimId)
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get claim by id $claimId" }
            null
        }
    }

    /**
     * Get all claims in a rectangular chunk area for a given world.
     *
     * Used by the grid UI to render claim ownership for a 6x6 chunk area.
     * Queries claim_chunks for matching positions, then loads full claims.
     * Note: Multiple grid cells can map to the same claim (multi-chunk plot).
     *
     * @param worldId World UUID
     * @param minChunkX Minimum chunk X coordinate (inclusive)
     * @param maxChunkX Maximum chunk X coordinate (inclusive)
     * @param minChunkZ Minimum chunk Z coordinate (inclusive)
     * @param maxChunkZ Maximum chunk Z coordinate (inclusive)
     * @return List of claims with chunks in the area (deduplicated)
     */
    suspend fun getClaimsInArea(
        worldId: UUID,
        minChunkX: Int,
        maxChunkX: Int,
        minChunkZ: Int,
        maxChunkZ: Int
    ): List<Claim> {
        return try {
            persistence.execute { conn ->
                // Find distinct claim IDs that have chunks in this area
                val sql = """
                    SELECT DISTINCT claim_id FROM claim_chunks
                    WHERE world_id = ?
                      AND chunk_x >= ? AND chunk_x <= ?
                      AND chunk_z >= ? AND chunk_z <= ?
                """.trimIndent()

                val claimIds = conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, worldId.toString())
                    stmt.setInt(2, minChunkX)
                    stmt.setInt(3, maxChunkX)
                    stmt.setInt(4, minChunkZ)
                    stmt.setInt(5, maxChunkZ)

                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(UUID.fromString(rs.getString("claim_id")))
                            }
                        }
                    }
                }

                // Load full claims
                claimIds.mapNotNull { loadClaimById(conn, it) }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) {
                "Failed to get claims in area ($minChunkX,$minChunkZ)-($maxChunkX,$maxChunkZ) for world $worldId"
            }
            emptyList()
        }
    }

    /**
     * Get all claims in a world.
     * Used for cache warming on world load.
     */
    suspend fun getClaimsByWorld(worldId: UUID): List<Claim> {
        return try {
            persistence.execute { conn ->
                // Find distinct claim IDs that have chunks in this world
                val sql = "SELECT DISTINCT claim_id FROM claim_chunks WHERE world_id = ?"

                val claimIds = conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, worldId.toString())
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(UUID.fromString(rs.getString("claim_id")))
                            }
                        }
                    }
                }

                claimIds.mapNotNull { loadClaimById(conn, it) }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get claims for world $worldId" }
            emptyList()
        }
    }

    // ========== Group Management Methods ==========

    /**
     * Create a new group.
     */
    suspend fun createGroup(group: ClaimGroup): Boolean {
        return try {
            persistence.transaction { conn ->
                val groupSql = """
                    INSERT INTO claim_groups (group_id, owner_uuid, group_name, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(groupSql).use { stmt ->
                    stmt.setString(1, group.id.toString())
                    stmt.setString(2, group.owner.toString())
                    stmt.setString(3, group.name)
                    stmt.setLong(4, group.createdAt)
                    stmt.setLong(5, group.updatedAt)
                    stmt.executeUpdate()
                }

                if (group.members.isNotEmpty()) {
                    val memberSql = """
                        INSERT INTO group_members (group_id, member_uuid, added_at)
                        VALUES (?, ?, ?)
                    """.trimIndent()

                    conn.prepareStatement(memberSql).use { stmt ->
                        val now = System.currentTimeMillis()
                        group.members.forEach { memberUuid ->
                            stmt.setString(1, group.id.toString())
                            stmt.setString(2, memberUuid.toString())
                            stmt.setLong(3, now)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }

                LoggingManager.debug(logger, "claims") { "Created group ${group.id} (${group.name})" }
                true
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to create group ${group.id}" }
            false
        }
    }

    /**
     * Get a group by ID.
     */
    suspend fun getGroup(groupId: UUID): ClaimGroup? {
        return try {
            persistence.execute { conn ->
                val sql = """
                    SELECT g.group_id, g.owner_uuid, g.group_name, g.created_at, g.updated_at,
                           GROUP_CONCAT(m.member_uuid) as members
                    FROM claim_groups g
                    LEFT JOIN group_members m ON g.group_id = m.group_id
                    WHERE g.group_id = ?
                    GROUP BY g.group_id
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, groupId.toString())

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            rs.toClaimGroup()
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get group $groupId" }
            null
        }
    }

    /**
     * Get all groups owned by a player.
     */
    suspend fun getGroupsByOwner(ownerUuid: UUID): List<ClaimGroup> {
        return try {
            persistence.execute { conn ->
                val sql = """
                    SELECT g.group_id, g.owner_uuid, g.group_name, g.created_at, g.updated_at,
                           GROUP_CONCAT(m.member_uuid) as members
                    FROM claim_groups g
                    LEFT JOIN group_members m ON g.group_id = m.group_id
                    WHERE g.owner_uuid = ?
                    GROUP BY g.group_id
                    ORDER BY g.created_at DESC
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())

                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.toClaimGroup())
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to get groups for owner $ownerUuid" }
            emptyList()
        }
    }

    /**
     * Update a group (currently only name can be updated).
     */
    suspend fun updateGroup(group: ClaimGroup): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = """
                    UPDATE claim_groups
                    SET group_name = ?, updated_at = ?
                    WHERE group_id = ?
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, group.name)
                    stmt.setLong(2, System.currentTimeMillis())
                    stmt.setString(3, group.id.toString())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to update group ${group.id}" }
            false
        }
    }

    /**
     * Delete a group.
     * CASCADE delete removes members and trust relationships automatically.
     */
    suspend fun deleteGroup(groupId: UUID, ownerUuid: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = "DELETE FROM claim_groups WHERE group_id = ? AND owner_uuid = ?"

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, groupId.toString())
                    stmt.setString(2, ownerUuid.toString())
                    val rowsAffected = stmt.executeUpdate()

                    if (rowsAffected > 0) {
                        LoggingManager.debug(logger, "claims") { "Deleted group $groupId" }
                        true
                    } else {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to delete group $groupId" }
            false
        }
    }

    suspend fun addGroupMember(groupId: UUID, memberUuid: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = """
                    INSERT OR IGNORE INTO group_members (group_id, member_uuid, added_at)
                    VALUES (?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, groupId.toString())
                    stmt.setString(2, memberUuid.toString())
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to add member $memberUuid to group $groupId" }
            false
        }
    }

    suspend fun removeGroupMember(groupId: UUID, memberUuid: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = "DELETE FROM group_members WHERE group_id = ? AND member_uuid = ?"

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, groupId.toString())
                    stmt.setString(2, memberUuid.toString())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to remove member $memberUuid from group $groupId" }
            false
        }
    }

    suspend fun trustGroup(claimId: UUID, groupId: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = """
                    INSERT OR IGNORE INTO claim_group_trust (claim_id, group_id, granted_at)
                    VALUES (?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.setString(2, groupId.toString())
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to trust group $groupId to claim $claimId" }
            false
        }
    }

    suspend fun untrustGroup(claimId: UUID, groupId: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = "DELETE FROM claim_group_trust WHERE claim_id = ? AND group_id = ?"

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, claimId.toString())
                    stmt.setString(2, groupId.toString())
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to untrust group $groupId from claim $claimId" }
            false
        }
    }

    // ========== Repository Interface Implementation ==========

    /**
     * Find a claim by its UUID.
     */
    override suspend fun findById(id: UUID): Claim? {
        return getClaimById(id)
    }

    /**
     * Save (insert or update) a claim.
     */
    override suspend fun save(entity: Claim) {
        try {
            persistence.transaction { conn ->
                // Upsert the claim row
                val sql = """
                    INSERT INTO land_claims (claim_id, owner_uuid, claim_name, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(claim_id) DO UPDATE SET
                        owner_uuid = excluded.owner_uuid,
                        claim_name = excluded.claim_name,
                        updated_at = excluded.updated_at
                """.trimIndent()

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, entity.id.toString())
                    stmt.setString(2, entity.owner.toString())
                    stmt.setString(3, entity.name)
                    stmt.setLong(4, entity.createdAt)
                    stmt.setLong(5, entity.updatedAt)
                    stmt.executeUpdate()
                }

                // Replace all chunks: delete old, insert new
                conn.prepareStatement("DELETE FROM claim_chunks WHERE claim_id = ?").use { stmt ->
                    stmt.setString(1, entity.id.toString())
                    stmt.executeUpdate()
                }

                val chunkSql = """
                    INSERT INTO claim_chunks (claim_id, world_id, chunk_x, chunk_z)
                    VALUES (?, ?, ?, ?)
                """.trimIndent()

                conn.prepareStatement(chunkSql).use { stmt ->
                    for (chunk in entity.chunks) {
                        stmt.setString(1, entity.id.toString())
                        stmt.setString(2, chunk.worldId.toString())
                        stmt.setInt(3, chunk.chunkX)
                        stmt.setInt(4, chunk.chunkZ)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to save claim ${entity.id}" }
            throw e
        }
    }

    /**
     * Delete a claim by its UUID.
     */
    override suspend fun delete(id: UUID) {
        try {
            persistence.transaction { conn ->
                // Delete chunks first
                conn.prepareStatement("DELETE FROM claim_chunks WHERE claim_id = ?").use { stmt ->
                    stmt.setString(1, id.toString())
                    stmt.executeUpdate()
                }

                conn.prepareStatement("DELETE FROM land_claims WHERE claim_id = ?").use { stmt ->
                    stmt.setString(1, id.toString())
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to delete claim $id" }
            throw e
        }
    }

    /**
     * Check if a claim exists by its UUID.
     */
    override suspend fun existsById(id: UUID): Boolean {
        return try {
            persistence.execute { conn ->
                val sql = "SELECT 1 FROM land_claims WHERE claim_id = ?"

                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, id.toString())
                    stmt.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "claims", e) { "Failed to check if claim exists: $id" }
            false
        }
    }

    // ========== Private Helpers ==========

    /**
     * Load a complete Claim object by ID (claim row + chunks + trust).
     * Used internally by all query methods to ensure consistent loading.
     */
    private fun loadClaimById(conn: Connection, claimId: UUID): Claim? {
        // Load claim row
        val claimSql = """
            SELECT claim_id, owner_uuid, claim_name, created_at, updated_at
            FROM land_claims WHERE claim_id = ?
        """.trimIndent()

        val claimRow = conn.prepareStatement(claimSql).use { stmt ->
            stmt.setString(1, claimId.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    ClaimRow(
                        id = UUID.fromString(rs.getString("claim_id")),
                        owner = UUID.fromString(rs.getString("owner_uuid")),
                        name = rs.getString("claim_name"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at")
                    )
                } else {
                    null
                }
            }
        } ?: return null

        // Load chunks
        val chunksSql = "SELECT world_id, chunk_x, chunk_z FROM claim_chunks WHERE claim_id = ?"
        val chunks = conn.prepareStatement(chunksSql).use { stmt ->
            stmt.setString(1, claimId.toString())
            stmt.executeQuery().use { rs ->
                buildSet {
                    while (rs.next()) {
                        add(ChunkPosition(
                            worldId = UUID.fromString(rs.getString("world_id")),
                            chunkX = rs.getInt("chunk_x"),
                            chunkZ = rs.getInt("chunk_z")
                        ))
                    }
                }
            }
        }

        // Load trusted players
        val trustSql = "SELECT trusted_uuid FROM claim_trust WHERE claim_id = ?"
        val trustedPlayers = conn.prepareStatement(trustSql).use { stmt ->
            stmt.setString(1, claimId.toString())
            stmt.executeQuery().use { rs ->
                buildSet {
                    while (rs.next()) {
                        try {
                            add(UUID.fromString(rs.getString("trusted_uuid")))
                        } catch (_: Exception) { /* skip invalid UUIDs */ }
                    }
                }
            }
        }

        return Claim(
            id = claimRow.id,
            owner = claimRow.owner,
            chunks = chunks,
            name = claimRow.name,
            trustedPlayers = trustedPlayers,
            createdAt = claimRow.createdAt,
            updatedAt = claimRow.updatedAt
        )
    }

    /**
     * Update the updated_at timestamp for a claim.
     */
    private fun updateClaimTimestamp(conn: Connection, claimId: UUID) {
        val sql = "UPDATE land_claims SET updated_at = ? WHERE claim_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, System.currentTimeMillis())
            stmt.setString(2, claimId.toString())
            stmt.executeUpdate()
        }
    }

    /**
     * Helper to convert a ResultSet row to a ClaimGroup.
     */
    private fun ResultSet.toClaimGroup(): ClaimGroup {
        val membersStr = getString("members")
        val members = if (membersStr != null) {
            membersStr.split(",").mapNotNull {
                try { UUID.fromString(it) } catch (_: Exception) { null }
            }.toSet()
        } else {
            emptySet()
        }

        return ClaimGroup(
            id = UUID.fromString(getString("group_id")),
            name = getString("group_name"),
            owner = UUID.fromString(getString("owner_uuid")),
            members = members,
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at")
        )
    }

    /**
     * Internal data holder for claim row (without chunks/trust).
     */
    private data class ClaimRow(
        val id: UUID,
        val owner: UUID,
        val name: String?,
        val createdAt: Long,
        val updatedAt: Long
    )
}
