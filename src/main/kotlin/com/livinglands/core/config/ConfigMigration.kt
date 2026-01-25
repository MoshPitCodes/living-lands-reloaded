package com.livinglands.core.config

/**
 * Defines a migration from one config version to another.
 * 
 * Migrations operate on raw YAML maps to transform the structure
 * before deserialization to the target config class.
 * 
 * Example usage:
 * ```kotlin
 * val migration = ConfigMigration(
 *     fromVersion = 1,
 *     toVersion = 2,
 *     description = "Update hunger rate to balanced value",
 *     migrate = { old ->
 *         old.toMutableMap().apply {
 *             (this["hunger"] as? MutableMap<String, Any>)?.let {
 *                 it["baseDepletionRateSeconds"] = 1440.0
 *             }
 *             this["configVersion"] = 2
 *         }
 *     }
 * )
 * ```
 * 
 * @property fromVersion The version this migration applies to
 * @property toVersion The version this migration produces
 * @property description Human-readable description of what this migration does
 * @property migrate Function that transforms the config map from old to new version
 */
data class ConfigMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val description: String = "Migration from v$fromVersion to v$toVersion",
    val migrate: (Map<String, Any>) -> Map<String, Any>
) {
    init {
        require(toVersion > fromVersion) {
            "toVersion ($toVersion) must be greater than fromVersion ($fromVersion)"
        }
        require(toVersion == fromVersion + 1) {
            "Migrations must be sequential: fromVersion=$fromVersion, toVersion=$toVersion (expected ${fromVersion + 1})"
        }
    }
}

/**
 * Registry for config migrations.
 * 
 * Stores migrations indexed by config module ID and source version.
 * Provides methods to apply sequential migrations.
 * 
 * Thread-safe for concurrent access.
 */
class ConfigMigrationRegistry {
    
    // Map of moduleId -> (fromVersion -> migration)
    private val migrations = java.util.concurrent.ConcurrentHashMap<String, MutableMap<Int, ConfigMigration>>()
    
    /**
     * Register a migration for a config module.
     * 
     * @param moduleId The config module ID (e.g., "metabolism", "core")
     * @param migration The migration to register
     * @throws IllegalStateException if a migration already exists for this version
     */
    fun register(moduleId: String, migration: ConfigMigration) {
        val moduleMigrations = migrations.getOrPut(moduleId) { mutableMapOf() }
        
        if (moduleMigrations.containsKey(migration.fromVersion)) {
            throw IllegalStateException(
                "Migration already registered for '$moduleId' from version ${migration.fromVersion}"
            )
        }
        
        moduleMigrations[migration.fromVersion] = migration
    }
    
    /**
     * Register multiple migrations for a config module.
     * 
     * @param moduleId The config module ID
     * @param migrations List of migrations to register
     */
    fun registerAll(moduleId: String, migrations: List<ConfigMigration>) {
        migrations.forEach { register(moduleId, it) }
    }
    
    /**
     * Get a migration for a specific version.
     * 
     * @param moduleId The config module ID
     * @param fromVersion The version to migrate from
     * @return The migration, or null if none exists
     */
    fun getMigration(moduleId: String, fromVersion: Int): ConfigMigration? {
        return migrations[moduleId]?.get(fromVersion)
    }
    
    /**
     * Get all migrations needed to upgrade from one version to another.
     * 
     * @param moduleId The config module ID
     * @param fromVersion Current config version
     * @param toVersion Target config version
     * @return List of migrations in order, or empty if no path exists
     */
    fun getMigrationPath(moduleId: String, fromVersion: Int, toVersion: Int): List<ConfigMigration> {
        if (fromVersion >= toVersion) return emptyList()
        
        val moduleMigrations = migrations[moduleId] ?: return emptyList()
        val path = mutableListOf<ConfigMigration>()
        
        var currentVersion = fromVersion
        while (currentVersion < toVersion) {
            val migration = moduleMigrations[currentVersion]
                ?: return emptyList() // Gap in migration path
            path.add(migration)
            currentVersion = migration.toVersion
        }
        
        return path
    }
    
    /**
     * Apply all migrations needed to upgrade config data.
     * 
     * @param moduleId The config module ID
     * @param data Raw config data as a map
     * @param fromVersion Current version
     * @param toVersion Target version
     * @return Migrated data, or null if migration path is incomplete
     */
    fun applyMigrations(
        moduleId: String,
        data: Map<String, Any>,
        fromVersion: Int,
        toVersion: Int
    ): Map<String, Any>? {
        val path = getMigrationPath(moduleId, fromVersion, toVersion)
        if (path.isEmpty() && fromVersion < toVersion) {
            return null // No migration path available
        }
        
        var current = data
        for (migration in path) {
            current = migration.migrate(current)
        }
        
        return current
    }
    
    /**
     * Check if a complete migration path exists.
     * 
     * @param moduleId The config module ID
     * @param fromVersion Current version
     * @param toVersion Target version
     * @return true if all migrations exist for the path
     */
    fun hasMigrationPath(moduleId: String, fromVersion: Int, toVersion: Int): Boolean {
        if (fromVersion >= toVersion) return true
        return getMigrationPath(moduleId, fromVersion, toVersion).size == (toVersion - fromVersion)
    }
    
    /**
     * Get the highest target version available for a module.
     * 
     * @param moduleId The config module ID
     * @return The highest toVersion, or 0 if no migrations exist
     */
    fun getHighestVersion(moduleId: String): Int {
        return migrations[moduleId]?.values?.maxOfOrNull { it.toVersion } ?: 0
    }
    
    /**
     * Clear all registered migrations.
     * Primarily for testing.
     */
    fun clear() {
        migrations.clear()
    }
    
    /**
     * Check if any migrations are registered for a module.
     * 
     * @param moduleId The config module ID
     * @return true if at least one migration exists
     */
    fun hasMigrations(moduleId: String): Boolean {
        return migrations[moduleId]?.isNotEmpty() == true
    }
}
