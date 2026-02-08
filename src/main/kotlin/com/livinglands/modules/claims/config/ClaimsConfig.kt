package com.livinglands.modules.claims.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig

/**
 * Configuration for the claims system.
 * Loaded from claims.yml
 * 
 * Defines chunk claiming limits, protection rules, and visualization settings.
 */
data class ClaimsConfig(
    /**
     * Configuration version for migration support.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /** Master enable/disable for the entire claims system */
    val enabled: Boolean = true,
    
    /** Claim limit settings */
    val limits: LimitsConfig = LimitsConfig(),
    
    /** Protection settings */
    val protection: ProtectionConfig = ProtectionConfig(),
    
    /** Visualization settings */
    val visualization: VisualizationConfig = VisualizationConfig()
    
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION, enabled = true)
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 1

        /** Config module ID for migration registry */
        const val MODULE_ID = "claims"

        /**
         * Get default configuration.
         */
        fun default(): ClaimsConfig = ClaimsConfig()

        /**
         * Get all migrations for ClaimsConfig.
         */
        fun getMigrations(): List<ConfigMigration> = emptyList()
    }
}

/**
 * Claim limit configuration.
 * JsonIgnoreProperties allows old config files with removed fields (e.g., maxClaimsPerPlayer)
 * to deserialize without errors.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LimitsConfig(
    /** Maximum plots (multi-chunk claims) per player */
    val maxPlotsPerPlayer: Int = 10,

    /** Maximum chunks per single plot */
    val maxChunksPerPlot: Int = 25,

    /** Maximum total chunks a player can claim across all plots */
    val maxTotalChunks: Int = 100,

    /** Starting claims for new players */
    val startingClaims: Int = 2,

    /** Additional claims granted per hour played */
    val claimsPerHourPlayed: Double = 0.1,

    /** Maximum trusted players per claim */
    val maxTrustedPerClaim: Int = 20,

    /** Maximum groups per player */
    val maxGroupsPerPlayer: Int = 5,

    /** Maximum members per group */
    val maxMembersPerGroup: Int = 50
)

/**
 * Protection settings.
 */
data class ProtectionConfig(
    /** Protect against block breaking */
    val blockBreak: Boolean = true,
    
    /** Protect against block placing */
    val blockPlace: Boolean = true,
    
    /** Protect against explosions (creepers, TNT) */
    val explosions: Boolean = true,
    
    /** Protect against fire spread */
    val fireSpread: Boolean = true,
    
    /** Protect against mob spawning */
    val mobSpawning: Boolean = false,
    
    /** Protect against entity damage (animals, mobs) */
    val entityDamage: Boolean = true,
    
    /** Protect against PvP damage */
    val pvp: Boolean = true
)

/**
 * Visualization settings.
 */
data class VisualizationConfig(
    /** Show claim boundaries when entering */
    val showBoundaries: Boolean = true,
    
    /** Boundary display duration in seconds */
    val boundaryDurationSeconds: Int = 5,
    
    /** Show claim owner name in boundary display */
    val showOwnerName: Boolean = true,
    
    /** Particle effect for boundaries */
    val boundaryParticles: Boolean = true
)
