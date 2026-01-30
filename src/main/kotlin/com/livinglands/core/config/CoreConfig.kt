package com.livinglands.core.config

/**
 * Core module configuration.
 * Controls logging, debug mode, and which modules are enabled.
 * 
 * Stored in: LivingLandsReloaded/config/core.yml
 * 
 * Version History:
 * - v1: Initial version with debug and enabledModules
 * - v2: Added logging configuration with log levels
 */
data class CoreConfig(
    /**
     * Configuration version for migration support.
     * Increment when making breaking changes to the config structure.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /**
     * Enable debug logging (DEPRECATED - use logging.globalLevel instead).
     * When true, sets global log level to DEBUG.
     * Kept for backward compatibility.
     */
    @Deprecated("Use logging.globalLevel = \"DEBUG\" instead")
    val debug: Boolean = false,
    
    /**
     * Logging configuration.
     */
    val logging: LoggingConfig = LoggingConfig(),
    
    /**
     * List of module IDs to enable.
     * Modules not in this list will not be loaded.
     * 
     * Available modules:
     * - metabolism: Hunger, thirst, and energy systems
     * - professions: XP and profession progression
     * - claims: Land protection system (future)
     */
    val enabledModules: List<String> = listOf("metabolism", "professions")
) : VersionedConfig {
    
    /**
     * No-arg constructor for YAML deserialization.
     * Required by Jackson for data class instantiation.
     */
    constructor() : this(
        configVersion = CURRENT_VERSION,
        debug = false,
        logging = LoggingConfig(),
        enabledModules = listOf("metabolism", "professions")
    )
    
    /**
     * Check if a module is enabled.
     */
    fun isModuleEnabled(moduleId: String): Boolean {
        return enabledModules.contains(moduleId)
    }
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 2
        
        /** Config module ID for migration registry */
        const val MODULE_ID = "core"
    }
}

/**
 * Logging configuration for Living Lands.
 * 
 * **Log Levels (from most to least verbose):**
 * - TRACE: Extremely detailed (every tick, every calculation)
 * - DEBUG: Detailed diagnostic info (state changes, important calculations)
 * - INFO: General informational messages (default)
 * - WARN: Warning messages (degraded functionality, recoverable errors)
 * - ERROR: Error messages (unrecoverable errors, exceptions)
 * - OFF: No logging (not recommended)
 * 
 * **Examples:**
 * ```yaml
 * # Global DEBUG level for all modules
 * logging:
 *   globalLevel: DEBUG
 * 
 * # INFO globally, but DEBUG for specific modules
 * logging:
 *   globalLevel: INFO
 *   moduleOverrides:
 *     metabolism: DEBUG
 *     professions: TRACE
 * 
 * # Production configuration (minimal logging)
 * logging:
 *   globalLevel: WARN
 * ```
 */
data class LoggingConfig(
    /**
     * Global log level applied to all modules (unless overridden).
     * Valid values: TRACE, DEBUG, INFO, WARN, ERROR, OFF
     * Default: INFO
     */
    val globalLevel: String = "INFO",
    
    /**
     * Per-module log level overrides.
     * Map of module ID to log level string.
     * 
     * Available module IDs:
     * - core: Core system logs
     * - metabolism: Metabolism system logs
     * - professions: Professions system logs
     * - claims: Claims system logs (future)
     * 
     * Example:
     * ```yaml
     * moduleOverrides:
     *   metabolism: DEBUG
     *   professions: TRACE
     * ```
     */
    val moduleOverrides: Map<String, String> = emptyMap()
) {
    /**
     * No-arg constructor for YAML deserialization.
     */
    constructor() : this(
        globalLevel = "INFO",
        moduleOverrides = emptyMap()
    )
}
