package com.livinglands.core.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hypixel.hytale.logger.HytaleLogger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Manages YAML configuration files with hot-reload and migration support.
 * Config files are stored in LivingLandsReloaded/config/
 * 
 * Features:
 * - Type-safe loading with reified types
 * - Hot-reload via reload() method
 * - Automatic migration for versioned configs
 * - Backup before migration
 * - Clean YAML serialization with Jackson
 * 
 * Thread-safe for concurrent access. Configuration changes require reload() to apply.
 */
class ConfigManager(
    private val configPath: Path,
    private val logger: HytaleLogger
) {
    
    // Jackson ObjectMapper configured for clean YAML output
    private val mapper: ObjectMapper
    
    // Raw mapper for loading maps without type conversion (for migrations)
    private val rawMapper: ObjectMapper
    
    // Cached configurations by module ID
    private val configs = ConcurrentHashMap<String, Any>()
    
    // Type information for reload support
    private val configTypes = ConcurrentHashMap<String, KClass<*>>()
    
    // Reload callbacks by module ID
    private val reloadCallbacks = ConcurrentHashMap<String, () -> Unit>()
    
    // Migration registry for versioned configs
    val migrations = ConfigMigrationRegistry()
    
    // Target versions for versioned configs
    private val targetVersions = ConcurrentHashMap<String, Int>()
    
    init {
        // Create config directory if it doesn't exist
        Files.createDirectories(configPath)
        
        // Configure YAML factory for clean, readable output
        val yamlFactory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)  // No "---" at start
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)           // Minimize quote usage
            .build()
        
        // Create mapper with Kotlin support
        mapper = ObjectMapper(yamlFactory).apply {
            registerKotlinModule()
            
            // Pretty printing with indentation
            enable(SerializationFeature.INDENT_OUTPUT)
            
            // Don't write null values (both map values and object fields)
            disable(SerializationFeature.WRITE_NULL_MAP_VALUES)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            
            // Don't write dates as timestamps
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        
        // Raw mapper for loading as generic maps (for migration)
        rawMapper = ObjectMapper(yamlFactory).apply {
            registerKotlinModule()
        }
        
        logger.atFine().log("ConfigManager initialized at: $configPath")
    }
    
    /**
     * Load a configuration file. Creates default if not exists.
     * 
     * @param moduleId Unique identifier for the config (used as filename without .yml)
     * @param default Default configuration to use if file doesn't exist or fails to parse
     * @return Loaded or default configuration
     */
    inline fun <reified T : Any> load(moduleId: String, default: T): T {
        return load(moduleId, default, T::class)
    }
    
    /**
     * Internal load implementation with explicit class parameter.
     */
    @PublishedApi
    internal fun <T : Any> load(moduleId: String, default: T, type: KClass<T>): T {
        configTypes[moduleId] = type
        
        val configFile = configPath.resolve("$moduleId.yml")
        
        return if (Files.exists(configFile)) {
            try {
                val content = Files.readString(configFile)
                if (content.isBlank()) {
                    // Empty file, use and save default
                    save(moduleId, default)
                    configs[moduleId] = default
                    logger.atFine().log("Config '$moduleId' was empty, created default")
                    default
                } else {
                    val loaded = mapper.readValue(content, type.java)
                    configs[moduleId] = loaded
                    logger.atFine().log("Loaded config '$moduleId'")
                    loaded
                }
            } catch (e: Exception) {
                // Failed to load, use default and save it
                logger.atWarning().log("Failed to parse config '$moduleId': ${e.message}. Using defaults.")
                save(moduleId, default)
                configs[moduleId] = default
                default
            }
        } else {
            // Create default config
            save(moduleId, default)
            configs[moduleId] = default
            logger.atFine().log("Created default config '$moduleId'")
            default
        }
    }
    
    /**
     * Load a versioned configuration file with automatic migration support.
     * 
     * If the config file exists and has an older version, migrations will be applied
     * sequentially to bring it up to the current version. A backup is created before
     * any migration is performed.
     * 
     * @param moduleId Unique identifier for the config (used as filename without .yml)
     * @param default Default configuration to use if file doesn't exist or migration fails
     * @param targetVersion The target version to migrate to (should match default.configVersion)
     * @return Loaded, migrated, or default configuration
     */
    inline fun <reified T> loadWithMigration(
        moduleId: String,
        default: T,
        targetVersion: Int
    ): T where T : Any, T : VersionedConfig {
        return loadWithMigration(moduleId, default, targetVersion, T::class)
    }
    
    /**
     * Internal implementation of loadWithMigration with explicit class parameter.
     */
    @PublishedApi
    internal fun <T> loadWithMigration(
        moduleId: String,
        default: T,
        targetVersion: Int,
        type: KClass<T>
    ): T where T : Any, T : VersionedConfig {
        configTypes[moduleId] = type
        targetVersions[moduleId] = targetVersion
        
        val configFile = configPath.resolve("$moduleId.yml")
        
        // If file doesn't exist, create with default
        if (!Files.exists(configFile)) {
            save(moduleId, default)
            configs[moduleId] = default
            logger.atFine().log("Created default config '$moduleId' (v$targetVersion)")
            return default
        }
        
        // Read file content
        val content = try {
            Files.readString(configFile)
        } catch (e: Exception) {
            logger.atWarning().log("Failed to read config '$moduleId': ${e.message}. Using defaults.")
            save(moduleId, default)
            configs[moduleId] = default
            return default
        }
        
        if (content.isBlank()) {
            save(moduleId, default)
            configs[moduleId] = default
            logger.atFine().log("Config '$moduleId' was empty, created default (v$targetVersion)")
            return default
        }
        
        // Load as raw map to check version
        @Suppress("UNCHECKED_CAST")
        val rawData = try {
            rawMapper.readValue(content, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            logger.atWarning().log("Failed to parse config '$moduleId' as map: ${e.message}. Using defaults.")
            createBackup(moduleId, "parse-error")
            save(moduleId, default)
            configs[moduleId] = default
            return default
        }
        
        // Get current version from file (default to 1 if not present - legacy configs)
        val currentVersion = (rawData["configVersion"] as? Number)?.toInt() ?: 1
        
        // If already at target version, load normally
        if (currentVersion >= targetVersion) {
            return try {
                val loaded = mapper.readValue(content, type.java)
                configs[moduleId] = loaded
                logger.atFine().log("Loaded config '$moduleId' (v$currentVersion)")
                loaded
            } catch (e: Exception) {
                logger.atWarning().log("Failed to deserialize config '$moduleId': ${e.message}. Using defaults.")
                createBackup(moduleId, "deserialize-error")
                save(moduleId, default)
                configs[moduleId] = default
                default
            }
        }
        
        // Migration needed
        logger.atFine().log("Config '$moduleId' needs migration: v$currentVersion -> v$targetVersion")
        
        // Check if migration path exists
        if (!migrations.hasMigrationPath(moduleId, currentVersion, targetVersion)) {
            logger.atWarning().log(
                "No migration path for '$moduleId' from v$currentVersion to v$targetVersion. " +
                "Using defaults. Old config backed up."
            )
            createBackup(moduleId, "no-migration-path")
            save(moduleId, default)
            configs[moduleId] = default
            return default
        }
        
        // Create backup before migration
        createBackup(moduleId, "pre-migration-v$currentVersion")
        
        // Apply migrations
        val migratedData = try {
            migrations.applyMigrations(moduleId, rawData, currentVersion, targetVersion)
        } catch (e: Exception) {
            logger.atSevere().log("Migration failed for '$moduleId': ${e.message}. Using defaults.")
            save(moduleId, default)
            configs[moduleId] = default
            return default
        }
        
        if (migratedData == null) {
            logger.atSevere().log("Migration returned null for '$moduleId'. Using defaults.")
            save(moduleId, default)
            configs[moduleId] = default
            return default
        }
        
        // Convert migrated map to typed object
        val migrated = try {
            mapper.convertValue(migratedData, type.java)
        } catch (e: Exception) {
            logger.atSevere().log("Failed to deserialize migrated config '$moduleId': ${e.message}. Using defaults.")
            save(moduleId, default)
            configs[moduleId] = default
            return default
        }
        
        // Validate migrated config version
        if (migrated.configVersion != targetVersion) {
            logger.atWarning().log(
                "Migrated config '$moduleId' has wrong version: ${migrated.configVersion} (expected $targetVersion). " +
                "Saving to update version field."
            )
        }
        
        // Save migrated config
        save(moduleId, migrated)
        configs[moduleId] = migrated
        
        // Log migration details
        val migrationPath = migrations.getMigrationPath(moduleId, currentVersion, targetVersion)
        migrationPath.forEach { migration ->
            logger.atFine().log("  Applied: ${migration.description}")
        }
        logger.atFine().log("Config '$moduleId' migrated successfully: v$currentVersion -> v$targetVersion")
        
        return migrated
    }
    
    /**
     * Create a timestamped backup of a config file.
     * 
     * @param moduleId The config module ID
     * @param reason Reason for backup (e.g., "pre-migration-v1", "parse-error")
     * @return Path to backup file, or null if backup failed
     */
    fun createBackup(moduleId: String, reason: String): Path? {
        val configFile = configPath.resolve("$moduleId.yml")
        if (!Files.exists(configFile)) {
            return null
        }
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val backupName = "$moduleId.$reason.$timestamp.yml.backup"
        val backupFile = configPath.resolve(backupName)
        
        return try {
            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
            logger.atFine().log("Created backup: $backupName")
            backupFile
        } catch (e: Exception) {
            logger.atWarning().log("Failed to create backup for '$moduleId': ${e.message}")
            null
        }
    }
    
    /**
     * Register migrations for a module.
     * Convenience method that delegates to the migration registry.
     * 
     * @param moduleId The config module ID
     * @param moduleMigrations List of migrations to register
     */
    fun registerMigrations(moduleId: String, moduleMigrations: List<ConfigMigration>) {
        migrations.registerAll(moduleId, moduleMigrations)
        logger.atFine().log("Registered ${moduleMigrations.size} migrations for '$moduleId'")
    }
    
    /**
     * Save configuration to file.
     * 
     * @param moduleId Unique identifier for the config
     * @param config Configuration object to save
     */
    fun save(moduleId: String, config: Any) {
        val configFile = configPath.resolve("$moduleId.yml")
        try {
            mapper.writeValue(configFile.toFile(), config)
            configs[moduleId] = config
            logger.atFine().log("Saved config '$moduleId'")
        } catch (e: Exception) {
            logger.atSevere().log("Failed to save config '$moduleId': ${e.message}")
        }
    }
    
    /**
     * Get cached configuration.
     * 
     * @param moduleId Unique identifier for the config
     * @return Cached configuration or null if not loaded
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(moduleId: String): T? {
        return configs[moduleId] as? T
    }
    
    /**
     * Register a callback to be invoked when config is reloaded.
     * Typically used by modules to refresh their cached config values.
     * 
     * @param moduleId Module ID to listen for
     * @param callback Function to call after reload
     */
    fun onReload(moduleId: String, callback: () -> Unit) {
        reloadCallbacks[moduleId] = callback
    }
    
    /**
     * Unregister a reload callback.
     * 
     * @param moduleId Module ID to unregister
     */
    fun removeReloadCallback(moduleId: String) {
        reloadCallbacks.remove(moduleId)
    }
    
    /**
     * Reload configuration from disk.
     * Called by /ll reload [module] command.
     * 
     * @param moduleId Specific module to reload, or null for all
     * @return List of reloaded module IDs
     */
    fun reload(moduleId: String? = null): List<String> {
        val reloaded = mutableListOf<String>()
        
        val toReload = if (moduleId != null) {
            if (configTypes.containsKey(moduleId)) {
                listOf(moduleId)
            } else {
                logger.atWarning().log("Cannot reload config '$moduleId': not registered")
                return emptyList()
            }
        } else {
            // Use configTypes.keys instead of configs.keys to get all registered configs
            configTypes.keys.toList()
        }
        
        toReload.forEach { id ->
            val configType = configTypes[id] ?: return@forEach
            val configFile = configPath.resolve("$id.yml")
            
            if (Files.exists(configFile)) {
                try {
                    val content = Files.readString(configFile)
                    if (content.isNotBlank()) {
                        val loaded = mapper.readValue(content, configType.java)
                        if (loaded != null) {
                            configs[id] = loaded
                            reloaded.add(id)
                            logger.atFine().log("Reloaded config '$id'")
                        }
                    }
                } catch (e: Exception) {
                    logger.atWarning().log("Failed to reload config '$id': ${e.message}")
                }
            } else {
                logger.atWarning().log("Config file not found for '$id': $configFile")
            }
        }
        
        // Notify callbacks for reloaded configs
        reloaded.forEach { id ->
            reloadCallbacks[id]?.let { callback ->
                try {
                    callback()
                } catch (e: Exception) {
                    logger.atWarning().log("Error in reload callback for '$id': ${e.message}")
                }
            }
        }
        
        return reloaded
    }
    
    /**
     * Check if a config is loaded.
     * 
     * @param moduleId Module ID to check
     * @return true if config is loaded
     */
    fun isLoaded(moduleId: String): Boolean {
        return configs.containsKey(moduleId)
    }
    
    /**
     * Get list of all loaded config IDs.
     */
    fun getLoadedConfigs(): Set<String> {
        return configs.keys.toSet()
    }
    
    /**
     * Clear all cached configs.
     * Called during shutdown.
     */
    fun clear() {
        configs.clear()
        configTypes.clear()
        reloadCallbacks.clear()
        logger.atFine().log("ConfigManager cleared")
    }
}
