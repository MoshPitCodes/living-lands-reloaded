package com.livinglands.modules.professions.config

import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig

/**
 * Configuration for the Professions module.
 * 
 * Controls XP amounts, level curve, death penalty, and ability toggles.
 */
data class ProfessionsConfig(
    /** Config version for migration tracking */
    override val configVersion: Int = CURRENT_VERSION,
    
    /** Enable/disable entire module */
    val enabled: Boolean = true,
    
    /** XP curve configuration */
    val xpCurve: XpCurveConfig = XpCurveConfig(),
    
    /** XP amounts per activity */
    val xpRewards: XpRewardsConfig = XpRewardsConfig(),
    
    /** Death penalty configuration */
    val deathPenalty: DeathPenaltyConfig = DeathPenaltyConfig(),
    
    /** Ability enable/disable flags */
    val abilities: AbilityConfig = AbilityConfig(),
    
    /** UI/notification settings */
    val ui: UiConfig = UiConfig()
) : VersionedConfig {
    
    companion object {
        const val MODULE_ID = "professions"
        const val CURRENT_VERSION = 1
        
        /**
         * Get migrations for config versioning.
         * Currently no migrations (version 1 is first version).
         */
        fun getMigrations(): List<ConfigMigration> {
            return emptyList()
        }
    }
}

/**
 * XP curve parameters for level calculations.
 */
data class XpCurveConfig(
    /** Base XP required for level 2 (default: 100) */
    val baseXp: Double = 100.0,
    
    /** Exponential multiplier (>1.0 for increasing difficulty, default: 1.15) */
    val multiplier: Double = 1.15,
    
    /** Maximum achievable level (default: 100) */
    val maxLevel: Int = 100
)

/**
 * XP rewards for various activities.
 */
data class XpRewardsConfig(
    /** Combat XP per mob kill */
    val combat: CombatXpConfig = CombatXpConfig(),
    
    /** Mining XP per ore broken */
    val mining: MiningXpConfig = MiningXpConfig(),
    
    /** Logging XP per log broken */
    val logging: LoggingXpConfig = LoggingXpConfig(),
    
    /** Building XP per block placed */
    val building: BuildingXpConfig = BuildingXpConfig(),
    
    /** Gathering XP per item picked up */
    val gathering: GatheringXpConfig = GatheringXpConfig()
)

/**
 * Combat XP configuration.
 */
data class CombatXpConfig(
    /** Base XP per mob kill (modified by mob type) */
    val baseXp: Int = 10,
    
    /** XP multipliers by mob type (future: map mob IDs to multipliers) */
    val mobMultipliers: Map<String, Double> = mapOf(
        "default" to 1.0,
        "boss" to 5.0
    )
)

/**
 * Mining XP configuration.
 */
data class MiningXpConfig(
    /** Base XP per ore broken */
    val baseXp: Int = 5,
    
    /** XP multipliers by ore tier */
    val oreMultipliers: Map<String, Double> = mapOf(
        // Ores (full XP)
        "coal" to 1.0,
        "iron" to 1.5,
        "gold" to 2.0,
        "diamond" to 3.0,
        "emerald" to 4.0,
        
        // Common blocks (reduced XP to prevent spam, rewards all mining activity)
        "stone" to 0.1,        // 0.5 XP per block
        "cobblestone" to 0.1,  // 0.5 XP per block
        "dirt" to 0.05,        // 0.25 XP per block
        "gravel" to 0.08,      // 0.4 XP per block
        "sand" to 0.05         // 0.25 XP per block
    )
)

/**
 * Logging XP configuration.
 */
data class LoggingXpConfig(
    /** Base XP per log broken */
    val baseXp: Int = 3,
    
    /** XP multipliers by log type (future: different tree types) */
    val logMultipliers: Map<String, Double> = mapOf(
        "default" to 1.0
    )
)

/**
 * Building XP configuration.
 * 
 * No anti-exploit systems - relies on block value multipliers only.
 */
data class BuildingXpConfig(
    /** Base XP multiplier (applied to all blocks) */
    val baseXp: Double = 0.1,
    
    /** XP multipliers by block value tier */
    val blockValueMultipliers: Map<String, Double> = mapOf(
        "common" to 0.1,      // Dirt, cobblestone, sand (0.01 XP per block)
        "processed" to 1.0,   // Planks, bricks, smooth stone (0.1 XP per block)
        "crafted" to 3.0      // Doors, stairs, complex blocks (0.3 XP per block)
    )
)

/**
 * Gathering XP configuration.
 */
data class GatheringXpConfig(
    /** Base XP per item picked up */
    val baseXp: Int = 1,
    
    /** Maximum XP per tick (prevents lag from mass pickups) */
    val maxXpPerTick: Int = 100
)

/**
 * Death penalty configuration.
 * 
 * Progressive system with decay-based forgiveness:
 * - Base penalty: 10% of current level progress
 * - Increases by 3% per death in session
 * - Capped at 35% max loss
 * - Adaptive mercy: After 5 deaths, penalty reduced by 50%
 * - Decay system: 0.25 weight lost per hour alive
 */
data class DeathPenaltyConfig(
    /** Enable death penalty */
    val enabled: Boolean = true,
    
    /** Base XP loss percentage of current level progress (0.10 = 10%) */
    val baseXpLossPercent: Double = 0.10,
    
    /** Progressive increase per death (0.03 = +3% per death) */
    val progressiveIncreasePercent: Double = 0.03,
    
    /** Maximum XP loss cap (0.35 = 35% max) */
    val maxXpLossPercent: Double = 0.35,
    
    /** Number of highest professions affected (not random) */
    val affectedProfessions: Int = 2,
    
    /** Cannot drop below current level */
    val canDropLevels: Boolean = false,
    
    /** Decay system configuration */
    val decay: DeathPenaltyDecayConfig = DeathPenaltyDecayConfig(),
    
    /** Adaptive mercy system configuration */
    val adaptiveMercy: AdaptiveMercyConfig = AdaptiveMercyConfig(),
    
    /** Warning system configuration */
    val warnings: DeathWarningConfig = DeathWarningConfig()
)

/**
 * Death penalty decay configuration.
 * 
 * Each death has a "weight" that decays over time:
 * - Death 1 = 1.0 weight → decays by 0.25 per hour
 * - After 4 hours alive, death fully forgiven
 * - Decay pauses on logout, resumes on login
 */
data class DeathPenaltyDecayConfig(
    /** Enable decay system */
    val enabled: Boolean = true,
    
    /** Initial weight per death */
    val deathWeight: Double = 1.0,
    
    /** Weight lost per hour alive (0.25 = 4 hours to fully forgive) */
    val decayRatePerHour: Double = 0.25,
    
    /** Pause decay when player is offline */
    val pauseOnLogout: Boolean = true,
    
    /** Decay check interval in seconds */
    val checkIntervalSeconds: Int = 300  // Check every 5 minutes
)

/**
 * Adaptive mercy system configuration.
 * 
 * If player dies repeatedly, system reduces further penalties:
 * - After 5 deaths, recognize player is struggling
 * - Reduce all subsequent death penalties by 50%
 */
data class AdaptiveMercyConfig(
    /** Enable adaptive mercy */
    val enabled: Boolean = true,
    
    /** Death threshold to trigger mercy (5 deaths = mercy activates) */
    val deathThreshold: Int = 5,
    
    /** Penalty reduction multiplier (0.5 = reduce by 50%) */
    val penaltyReduction: Double = 0.50
)

/**
 * Death warning system configuration.
 * 
 * Warn players as penalty increases:
 * - Death 2: Soft warning (informational)
 * - Death 5: Hard warning (actionable advice)
 * - Death 8: Critical warning (mercy system preview)
 */
data class DeathWarningConfig(
    /** Enable warning messages */
    val enabled: Boolean = true,
    
    /** Soft warning threshold (Death 2) */
    val softWarningDeaths: Int = 2,
    
    /** Hard warning threshold (Death 5) */
    val hardWarningDeaths: Int = 5,
    
    /** Critical warning threshold (Death 8) */
    val criticalWarningDeaths: Int = 8
)

/**
 * Ability enable/disable flags.
 * Useful for testing or disabling specific abilities.
 */
data class AbilityConfig(
    /** Tier 1 XP boost abilities (Level 3) */
    val tier1XpBoost: Boolean = true,
    
    /** Tier 2 resource restore abilities (Level 7) */
    val tier2ResourceRestore: Boolean = true,
    
    /** Tier 3 permanent passive abilities (Level 10) */
    val tier3Passives: Boolean = true,
    
    /** Per-ability overrides (ability ID -> enabled) */
    val overrides: Map<String, Boolean> = emptyMap()
)

/**
 * UI and notification settings.
 */
data class UiConfig(
    /** Show level-up title on screen */
    val showLevelUpTitle: Boolean = true,
    
    /** Show ability unlock notifications */
    val showAbilityUnlocks: Boolean = true,
    
    /** Show XP gain action bar messages */
    val showXpGainMessages: Boolean = true,
    
    /** Minimum XP to show in action bar (prevents spam) */
    val minXpToShow: Int = 5,
    
    /** Enable skills panel HUD */
    val skillsPanelEnabled: Boolean = true
)
