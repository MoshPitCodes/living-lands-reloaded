package com.livinglands.modules.metabolism.config

import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig

/**
 * Configuration for the metabolism system.
 * Loaded from metabolism.yml
 * 
 * Defines depletion rates for hunger, thirst, and energy,
 * along with activity multipliers that affect how fast stats deplete.
 * 
 * Version History:
 * - v1: Initial version with aggressive depletion rates (480s/360s/600s)
 * - v2: Balanced depletion rates for better gameplay (1440s/1080s/2400s)
 */
data class MetabolismConfig(
    /**
     * Configuration version for migration support.
     * Increment when making breaking changes to the config structure.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /** Master enable/disable for the entire metabolism system */
    val enabled: Boolean = true,
    
    /** Hunger stat configuration */
    val hunger: StatConfig = StatConfig(
        enabled = true,
        baseDepletionRateSeconds = 2880.0, // 48 minutes from 100 to 0 at idle
        activityMultipliers = mapOf(
            "idle" to 1.0,
            "walking" to 1.3,
            "sprinting" to 2.0,
            "swimming" to 1.8,
            "combat" to 2.5
        )
    ),
    
    /** Thirst stat configuration */
    val thirst: StatConfig = StatConfig(
        enabled = true,
        baseDepletionRateSeconds = 2160.0, // 36 minutes from 100 to 0 at idle
        activityMultipliers = mapOf(
            "idle" to 1.0,
            "walking" to 1.2,
            "sprinting" to 1.8,
            "swimming" to 1.3, // Swimming has water nearby
            "combat" to 2.2
        )
    ),
    
    /** Energy stat configuration */
    val energy: StatConfig = StatConfig(
        enabled = true,
        baseDepletionRateSeconds = 2400.0, // 40 minutes from 100 to 0 at base rate
        activityMultipliers = mapOf(
            "idle" to 0.3, // Resting regenerates slowly (133 min to empty)
            "walking" to 1.0,
            "sprinting" to 2.0,
            "swimming" to 1.6,
            "combat" to 2.2
        )
    ),
    
    /** Food consumption configuration */
    val foodConsumption: FoodConsumptionConfig = FoodConsumptionConfig(),
    
    /** Buffs configuration (bonuses for high stats) */
    val buffs: BuffsConfig = BuffsConfig(),
    
    /** Debuffs configuration (penalties for low stats) */
    val debuffs: DebuffsConfig = DebuffsConfig(),
    
    /** Save interval in seconds (how often to persist stats to database) */
    val saveIntervalSeconds: Int = 60
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION, enabled = true)
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 2
        
        /** Config module ID for migration registry */
        const val MODULE_ID = "metabolism"
        
        /**
         * Get all migrations for MetabolismConfig.
         * Register these with ConfigMigrationRegistry on startup.
         */
        fun getMigrations(): List<ConfigMigration> = listOf(
            // v1 -> v2: Update depletion rates from aggressive to balanced
            ConfigMigration(
                fromVersion = 1,
                toVersion = 2,
                description = "Update depletion rates to balanced values (hunger: 480s→1440s, thirst: 360s→1080s, energy: 600s→2400s)",
                migrate = { old ->
                    old.toMutableMap().apply {
                        // Update hunger depletion rate: 480s → 1440s (3x slower)
                        updateStatDepletionRate(this, "hunger", 1440.0)
                        
                        // Update thirst: 360s → 1080s (3x slower)
                        updateStatDepletionRate(this, "thirst", 1080.0)
                        
                        // Update energy: 600s → 2400s (4x slower)
                        updateStatDepletionRate(this, "energy", 2400.0)
                        
                        // Update config version
                        this["configVersion"] = 2
                    }
                }
            )
        )
        
        /**
         * Helper to update baseDepletionRateSeconds in a stat config.
         * Only updates if the current value matches the old v1 default.
         */
        @Suppress("UNCHECKED_CAST")
        private fun updateStatDepletionRate(
            config: MutableMap<String, Any>,
            statName: String,
            newRate: Double
        ) {
            val stat = config[statName] as? MutableMap<String, Any>
            if (stat != null) {
                val currentRate = (stat["baseDepletionRateSeconds"] as? Number)?.toDouble()
                // Only update if using old aggressive defaults (within 10% tolerance)
                val oldDefaults = mapOf("hunger" to 480.0, "thirst" to 360.0, "energy" to 600.0)
                val oldDefault = oldDefaults[statName] ?: return
                
                if (currentRate == null || isNearDefault(currentRate, oldDefault)) {
                    stat["baseDepletionRateSeconds"] = newRate
                }
                // If user customized the value, preserve it
            }
        }
        
        /**
         * Check if a value is near the old default (within 10%).
         * This preserves user customizations while updating defaults.
         */
        private fun isNearDefault(value: Double, default: Double): Boolean {
            val tolerance = default * 0.1
            return kotlin.math.abs(value - default) <= tolerance
        }
    }
}

/**
 * Configuration for a single metabolism stat (hunger, thirst, or energy).
 */
data class StatConfig(
    /** Whether this stat is enabled */
    val enabled: Boolean = true,
    
    /** 
     * Base time in seconds to deplete from 100 to 0 at idle (multiplier = 1.0).
     * Higher values = slower depletion.
     */
    val baseDepletionRateSeconds: Double = 1440.0,
    
    /**
     * Activity multipliers that affect depletion rate.
     * Keys should be lowercase activity names: idle, walking, sprinting, swimming, combat
     * Values are multipliers - 2.0 means stat depletes twice as fast.
     */
    val activityMultipliers: Map<String, Double> = emptyMap()
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
    
    /**
     * Get the multiplier for a given activity state.
     * Returns 1.0 if the activity is not configured.
     */
    fun getMultiplier(activityName: String): Double {
        return activityMultipliers[activityName.lowercase()] ?: 1.0
    }
}

/**
 * Configuration for food consumption detection and restoration.
 */
data class FoodConsumptionConfig(
    /** Whether food consumption detection is enabled */
    val enabled: Boolean = true,
    
    /**
     * Detection tick interval in game ticks (30 TPS = 33.33ms per tick).
     * 
     * Default: 2 ticks = 66.66ms
     * 
     * IMPORTANT: Instant heal effects last only ~100ms (3 ticks), so detection
     * must run at least every 2 ticks to catch them reliably.
     * 
     * Lower values (1 tick) = more CPU but catches all effects
     * Higher values (3+ ticks) = less CPU but may miss instant effects
     */
    val detectionTickInterval: Int = 2,
    
    /**
     * Number of players to process per detection tick (batch size).
     * 
     * Default: 10 players
     * 
     * With 100 players and batch size of 10:
     * - Full cycle time = 10 batches × 2 ticks = 20 ticks = 666ms
     * - Still fast enough to catch 100ms instant heal effects
     * 
     * Adjust based on player count and server performance.
     */
    val batchSize: Int = 10,
    
    /**
     * Show chat messages when consuming food.
     * 
     * Default: true
     * 
     * When enabled, displays messages like:
     * "You ate Cooked Meat (T3): +32.5 hunger, +5.0 thirst, +18.0 energy"
     */
    val showChatMessages: Boolean = true
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for metabolism buffs (bonuses for high stats).
 */
data class BuffsConfig(
    /** Whether buffs are enabled */
    val enabled: Boolean = true,
    
    /** Speed buff configuration (energy >= 90%) */
    val speedBuff: BuffConfig = BuffConfig(
        enabled = true,
        multiplier = 1.132 // +13.2% movement speed
    ),
    
    /** Defense buff configuration (hunger >= 90%) */
    val defenseBuff: BuffConfig = BuffConfig(
        enabled = true,
        multiplier = 1.132 // +13.2% max health
    ),
    
    /** Stamina buff configuration (thirst >= 90%) */
    val staminaBuff: BuffConfig = BuffConfig(
        enabled = true,
        multiplier = 1.132 // +13.2% max stamina
    )
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for a single buff.
 */
data class BuffConfig(
    /** Whether this buff is enabled */
    val enabled: Boolean = true,
    
    /** Stat multiplier (1.132 = +13.2%) */
    val multiplier: Double = 1.132
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for metabolism debuffs (penalties for low stats).
 */
data class DebuffsConfig(
    /** Whether debuffs are enabled */
    val enabled: Boolean = true,
    
    /** Starvation debuff configuration (hunger = 0) */
    val starvation: StarvationConfig = StarvationConfig(),
    
    /** Dehydration debuff configuration (thirst < 30) */
    val dehydration: DehydrationConfig = DehydrationConfig(),
    
    /** Exhaustion debuff configuration (energy < 30) */
    val exhaustion: ExhaustionConfig = ExhaustionConfig()
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for starvation debuff (hunger = 0).
 */
data class StarvationConfig(
    /** Whether starvation is enabled */
    val enabled: Boolean = true,
    
    /** Initial damage per tick */
    val initialDamage: Double = 1.1,
    
    /** Damage increase per tick */
    val damageIncreasePerTick: Double = 0.55,
    
    /** Maximum damage per tick */
    val maxDamage: Double = 5.5,
    
    /** Interval between damage ticks (milliseconds) */
    val damageIntervalMs: Long = 3000 // 3 seconds
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for dehydration debuff (thirst < 30).
 */
data class DehydrationConfig(
    /** Whether dehydration is enabled */
    val enabled: Boolean = true,
    
    /** Minimum speed multiplier at 0 thirst (0.405 = 40.5% of normal speed) */
    val minSpeed: Double = 0.405,
    
    /** Damage when critically dehydrated (thirst = 0) */
    val damage: Double = 1.65,
    
    /** Interval between damage ticks (milliseconds) */
    val damageIntervalMs: Long = 4000 // 4 seconds
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for exhaustion debuff (energy < 30).
 */
data class ExhaustionConfig(
    /** Whether exhaustion is enabled */
    val enabled: Boolean = true,
    
    /** Minimum speed multiplier at 0 energy (0.54 = 54% of normal speed) */
    val minSpeed: Double = 0.54,
    
    /** Maximum stamina consumption multiplier at 0 energy (1.65 = 65% more consumption) */
    val maxStaminaConsumption: Double = 1.65,
    
    /** Stamina drain when critically exhausted (energy = 0) */
    val staminaDrain: Double = 5.5,
    
    /** Interval between stamina drain ticks (milliseconds) */
    val drainIntervalMs: Long = 1000 // 1 second
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}
