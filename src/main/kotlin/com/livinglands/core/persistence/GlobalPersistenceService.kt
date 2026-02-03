package com.livinglands.core.persistence

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQLite-backed persistence service for server-wide (global) data.
 * Used for data that should persist across all worlds on the server.
 * 
 * Examples:
 * - Metabolism stats (hunger/thirst/energy follow player between worlds)
 * - Player preferences (HUD visibility, etc.)
 * - Achievement progress
 * 
 * Thread-safe with async operations using Kotlin coroutines.
 */
class GlobalPersistenceService(
    private val dataDir: File,
    private val logger: HytaleLogger
) {
    
    private val dbFile: File
    private var connection: Connection? = null
    private val closed = AtomicBoolean(false)
    
    init {
        // Create global data directory: data/global/
        val globalDir = File(dataDir, "global")
        if (!globalDir.exists()) {
            globalDir.mkdirs()
            LoggingManager.debug(logger, "core") { "Created global data directory: ${globalDir.absolutePath}" }
        }
        
        dbFile = File(globalDir, "livinglands.db")
        
        // Initialize database connection and schema
        try {
            initializeDatabase()
        } catch (e: Exception) {
            LoggingManager.error(logger, "core", e) { "Failed to initialize global database" }
            throw e
        }
    }
    
    /**
     * Initialize database connection and create core schema.
     */
    private fun initializeDatabase() {
        // Explicitly load SQLite JDBC driver
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (e: ClassNotFoundException) {
            LoggingManager.error(logger, "core") { "SQLite JDBC driver not found! Check if sqlite-jdbc is included in JAR." }
            throw e
        }
        
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        
        // Enable WAL mode for better concurrency
        connection?.createStatement()?.use { stmt ->
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA synchronous=NORMAL")
            stmt.execute("PRAGMA foreign_keys=ON")
        }
        
        // Create core players table (global player registry)
        connection?.createStatement()?.use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    player_id TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    first_seen INTEGER NOT NULL,
                    last_seen INTEGER NOT NULL
                )
            """.trimIndent())
            
            // Create index for name lookups
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_players_name 
                ON players(player_name)
            """.trimIndent())
            
            // Create module schema tracking table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS module_schemas (
                    module_id TEXT PRIMARY KEY,
                    schema_version INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
        }
        
        LoggingManager.debug(logger, "core") { "Global database initialized: ${dbFile.absolutePath}" }
    }
    
    /**
     * Execute a database operation asynchronously.
     * Uses Dispatchers.IO for non-blocking execution.
     * 
     * @param block Database operation to execute
     * @return Result of the operation
     */
    suspend fun <T> execute(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        checkClosed()
        val conn = connection ?: throw IllegalStateException("Global database connection not initialized")
        
        try {
            synchronized(conn) {
                block(conn)
            }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "core") { "Global database operation failed" }
            throw e
        }
    }
    
    /**
     * Execute a database transaction asynchronously.
     * Automatically commits on success or rolls back on failure.
     * 
     * @param block Transaction operations to execute
     * @return Result of the transaction
     */
    suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        checkClosed()
        val conn = connection ?: throw IllegalStateException("Global database connection not initialized")
        
        synchronized(conn) {
            val originalAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                try {
                    conn.rollback()
                } catch (rollbackException: Exception) {
                    LoggingManager.warn(logger, "core") { "Rollback failed" }
                }
                LoggingManager.warn(logger, "core") { "Transaction failed" }
                throw e
            } finally {
                conn.autoCommit = originalAutoCommit
            }
        }
    }
    
    /**
     * Get the schema version for a module.
     * Returns 0 if the module has no schema version recorded.
     * 
     * @param moduleId Module identifier
     * @return Current schema version, or 0 if not set
     */
    suspend fun getModuleSchemaVersion(moduleId: String): Int {
        return execute { conn ->
            conn.prepareStatement(
                "SELECT schema_version FROM module_schemas WHERE module_id = ?"
            ).use { stmt ->
                stmt.setString(1, moduleId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }
    
    /**
     * Set the schema version for a module.
     * Uses UPSERT pattern to insert or update.
     * 
     * @param moduleId Module identifier
     * @param version Schema version to set
     */
    suspend fun setModuleSchemaVersion(moduleId: String, version: Int) {
        execute { conn ->
            conn.prepareStatement("""
                INSERT INTO module_schemas (module_id, schema_version) 
                VALUES (?, ?)
                ON CONFLICT(module_id) DO UPDATE SET schema_version = ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, moduleId)
                stmt.setInt(2, version)
                stmt.setInt(3, version)
                stmt.executeUpdate()
            }
        }
    }
    
    /**
     * Execute raw SQL statement.
     * Used for schema migrations and DDL operations.
     * 
     * @param sql SQL statement to execute
     */
    suspend fun executeRaw(sql: String) {
        execute { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate(sql)
            }
        }
    }
    
    /**
     * Close database connection.
     * Called during plugin shutdown.
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                connection?.let { conn ->
                    synchronized(conn) {
                        conn.close()
                    }
                }
                LoggingManager.debug(logger, "core") { "Global database connection closed" }
            } catch (e: Exception) {
                LoggingManager.warn(logger, "core") { "Error closing global database connection" }
            } finally {
                connection = null
            }
        }
    }
    
    /**
     * Check if the service is closed.
     */
    private fun checkClosed() {
        if (closed.get()) {
            throw IllegalStateException("GlobalPersistenceService is closed")
        }
    }
    
    /**
     * Check if the database connection is open.
     */
    fun isOpen(): Boolean {
        if (closed.get()) return false
        val conn = connection ?: return false
        return try {
            !conn.isClosed
        } catch (e: Exception) {
            false
        }
    }
}
