package com.livinglands.core.config

/**
 * Core module configuration.
 * Controls debug mode and which modules are enabled.
 * 
 * Stored in: LivingLandsReloaded/config/core.yml
 */
data class CoreConfig(
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
) {
    /**
     * No-arg constructor for YAML deserialization.
     * Required by SnakeYAML for data class instantiation.
     */
    constructor() : this(debug = false, enabledModules = listOf("metabolism"))
    
    /**
     * Check if a module is enabled.
     */
    fun isModuleEnabled(moduleId: String): Boolean {
        return enabledModules.contains(moduleId)
    }
}
