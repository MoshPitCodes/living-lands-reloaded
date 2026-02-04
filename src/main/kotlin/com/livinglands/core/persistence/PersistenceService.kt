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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SQLite-backed persistence service for world-specific data.
 * Each world has its own isolated database file.
 * 
 * Thread-safe with async operations using Kotlin coroutines.
 */
class PersistenceService(
    private val worldId: UUID,
    private val dataDir: File,
    private val logger: HytaleLogger
) {
    
    private val dbFile: File
    private var connection: Connection? = null
    private val closed = AtomicBoolean(false)
    
    init {
        // Create data directory structure: data/{world-uuid}/
        val worldDir = File(dataDir, worldId.toString())
        if (!worldDir.exists()) {
            worldDir.mkdirs()
            LoggingManager.debug(logger, "core") { "Created data directory: ${worldDir.absolutePath}" }
        }
        
        dbFile = File(worldDir, "livinglands.db")
        
        // Initialize database connection and schema
        try {
            initializeDatabase()
        } catch (e: Exception) {
            LoggingManager.debug(logger, "core") { "Failed to initialize database for world $worldId: ${e.message}" }
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
        
        // Create module schema tracking table
        // Note: Player identity is stored in the global database, not per-world
        connection?.createStatement()?.use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS module_schemas (
                    module_id TEXT PRIMARY KEY,
                    schema_version INTEGER NOT NULL DEFAULT 1
                )
            """.trimIndent())
        }
        
        LoggingManager.debug(logger, "core") { "Database initialized: ${dbFile.absolutePath}" }
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
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
        try {
            synchronized(conn) {
                block(conn)
            }
        } catch (e: Exception) {
            LoggingManager.debug(logger, "core") { "Database operation failed: ${e.message}" }
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
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
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
                    LoggingManager.debug(logger, "core") { "Rollback failed: ${rollbackException.message}" }
                }
                LoggingManager.debug(logger, "core") { "Transaction failed: ${e.message}" }
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
     * Called during world cleanup or plugin shutdown.
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                connection?.let { conn ->
                    synchronized(conn) {
                        conn.close()
                    }
                }
                LoggingManager.debug(logger, "core") { "Database connection closed for world $worldId" }
            } catch (e: Exception) {
                LoggingManager.debug(logger, "core") { "Error closing database connection: ${e.message}" }
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
            throw IllegalStateException("PersistenceService is closed")
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

/**
 * Extension function to safely use ResultSet with automatic resource cleanup.
 */
inline fun <T> ResultSet.useRows(block: (ResultSet) -> T): T {
    return use { rs ->
        block(rs)
    }
}

/**
 * Extension function to safely use Statement with automatic resource cleanup.
 */
inline fun <T> Statement.useStatement(block: (Statement) -> T): T {
    return use { stmt ->
        block(stmt)
    }
}
