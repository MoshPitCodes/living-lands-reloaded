package com.livinglands.core.config

/**
 * Core module configuration.
 * Controls debug mode and which modules are enabled.
 * 
 * Stored in: LivingLandsReloaded/config/core.yml
 * 
 * Version History:
 * - v1: Initial version with debug and enabledModules
 */
data class CoreConfig(
    /**
     * Configuration version for migration support.
     * Increment when making breaking changes to the config structure.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /**
     * Enable debug logging.
     * When true, additional diagnostic messages are logged.
     */
    val debug: Boolean = false,
    
    /**
     * List of module IDs to enable.
     * Modules not in this list will not be loaded.
     * 
     * Available modules:
     * - metabolism: Hunger, thirst, and energy systems
     * - leveling: XP and profession progression (future)
     * - claims: Land protection system (future)
     * - hud: Player HUD display (future)
     */
    val enabledModules: List<String> = listOf("metabolism")
) : VersionedConfig {
    
    /**
     * No-arg constructor for YAML deserialization.
     * Required by SnakeYAML for data class instantiation.
     */
    constructor() : this(
        configVersion = CURRENT_VERSION,
        debug = false,
        enabledModules = listOf("metabolism")
    )
    
    /**
     * Check if a module is enabled.
     */
    fun isModuleEnabled(moduleId: String): Boolean {
        return enabledModules.contains(moduleId)
    }
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 1
        
        /** Config module ID for migration registry */
        const val MODULE_ID = "core"
    }
}
