package com.livinglands.modules.leveling.config

import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig

/**
 * Configuration for the leveling system.
 * Loaded from leveling.yml
 * 
 * Defines XP curves, profession settings, and level rewards.
 */
data class LevelingConfig(
    /**
     * Configuration version for migration support.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /** Master enable/disable for the entire leveling system */
    val enabled: Boolean = true,
    
    /** XP curve settings */
    val xp: XPConfig = XPConfig(),
    
    /** Profession-specific settings */
    val professions: ProfessionsConfig = ProfessionsConfig(),
    
    /** Level-up rewards configuration */
    val rewards: RewardsConfig = RewardsConfig()
    
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION, enabled = true)
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 1
        
        /** Config module ID for migration registry */
        const val MODULE_ID = "leveling"
        
        /**
         * Get all migrations for LevelingConfig.
         */
        fun getMigrations(): List<ConfigMigration> = emptyList()
    }
}

/**
 * XP curve configuration.
 */
data class XPConfig(
    /** Base XP required for first level */
    val baseXP: Int = 1000,
    
    /** XP multiplier per level (exponential growth) */
    val multiplier: Double = 1.15,
    
    /** Maximum level cap (0 = unlimited) */
    val maxLevel: Int = 100
)

/**
 * Profession settings.
 */
data class ProfessionsConfig(
    /** Enable mining profession */
    val mining: Boolean = true,
    
    /** Enable logging profession */
    val logging: Boolean = true,
    
    /** Enable combat profession */
    val combat: Boolean = true,
    
    /** Enable farming profession */
    val farming: Boolean = true,
    
    /** Enable fishing profession */
    val fishing: Boolean = true,
    
    /** Enable crafting profession */
    val crafting: Boolean = true,
    
    /** Enable building profession */
    val building: Boolean = true,
    
    /** Enable cooking profession */
    val cooking: Boolean = true
)

/**
 * Level-up rewards configuration.
 */
data class RewardsConfig(
    /** Grant money on level up (requires economy module) */
    val moneyRewards: Boolean = true,
    
    /** Base money reward per level */
    val moneyPerLevel: Int = 100,
    
    /** Unlock abilities at specific levels */
    val abilityUnlocks: Boolean = true
)
