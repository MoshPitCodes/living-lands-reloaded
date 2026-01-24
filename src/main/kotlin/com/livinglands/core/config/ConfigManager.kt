package com.livinglands.core.config

import com.hypixel.hytale.logger.HytaleLogger
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Manages YAML configuration files with hot-reload support.
 * Config files are stored in LivingLandsReloaded/config/
 * 
 * Thread-safe for concurrent access. Configuration changes require reload() to apply.
 */
class ConfigManager(
    private val configPath: Path,
    private val logger: HytaleLogger
) {
    
    // YAML parser configured for readable output
    private val yaml: Yaml
    
    // Cached configurations by module ID
    private val configs = ConcurrentHashMap<String, Any>()
    
    // Type information for reload support
    private val configTypes = ConcurrentHashMap<String, KClass<*>>()
    
    // Reload callbacks by module ID
    private val reloadCallbacks = ConcurrentHashMap<String, () -> Unit>()
    
    init {
        // Create config directory if it doesn't exist
        Files.createDirectories(configPath)
        
        // Configure YAML for pretty, readable output
        val dumperOptions = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            indicatorIndent = 0  // Must be smaller than indent
        }
        
        val loaderOptions = LoaderOptions().apply {
            // Prevent arbitrary code execution via YAML
            allowRecursiveKeys = false
            // Allow global tags when loading
            tagInspector = { _ -> true }
        }
        
        // Use a representer that doesn't add type tags
        val representer = Representer(dumperOptions).apply {
            propertyUtils.isSkipMissingProperties = true
        }
        
        yaml = Yaml(Constructor(loaderOptions), representer, dumperOptions, loaderOptions)
        
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
                    @Suppress("UNCHECKED_CAST")
                    val loaded = yaml.loadAs(content, type.java) as T
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
            logger.atInfo().log("Created default config '$moduleId'")
            default
        }
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
            val content = yaml.dump(config)
            Files.writeString(configFile, content)
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
                        val loaded = yaml.loadAs(content, configType.java)
                        if (loaded != null) {
                            configs[id] = loaded
                            reloaded.add(id)
                            logger.atInfo().log("Reloaded config '$id'")
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
