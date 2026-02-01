package com.livinglands.modules.metabolism.config

import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig
import java.util.UUID

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
 * - v3: Restructured debuffs to 3-stage system
 * - v4: Added per-world configuration overrides
 * - v5: Added modded consumables support
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
    val saveIntervalSeconds: Int = 60,
    
    /**
     * Per-world configuration overrides.
     * Key can be world NAME (preferred) or world UUID string.
     * Values are partial configs that override global defaults.
     * 
     * Example:
     * ```yaml
     * worldOverrides:
     *   hardcore:  # World name
     *     hunger:
     *       baseDepletionRateSeconds: 960.0  # 2x faster
     *   creative:
     *     hunger:
     *       enabled: false  # Disable hunger completely
     * ```
     */
    val worldOverrides: Map<String, MetabolismWorldOverride> = emptyMap(),
    
    /**
     * Modded consumables support.
     * Configure custom food/drink/potion items from other mods with
     * tier-based restoration values.
     * 
     * **Pre-configured Examples Available:**
     * Run `/ll metabolism genconfig` to generate a metabolism.yml with
     * 92 pre-configured items from popular mods:
     * - Hidden's Harvest Delights (44 items)
     * - NoCube's Bakehouse (48 items)
     * 
     * Or manually configure:
     * ```yaml
     * moddedConsumables:
     *   enabled: true
     *   warnIfMissing: true
     *   foods:
     *     - effectId: "FarmingMod:CookedChicken"
     *       category: "MEAT"
     *       tier: null  # auto-detect
     * ```
     * 
     * See ModdedConsumablesConfig.getDefaultExampleConfig() for full list.
     */
    val moddedConsumables: ModdedConsumablesConfig = ModdedConsumablesConfig.getDefaultExampleConfig()
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION, enabled = true)
    
    /**
     * Find the override for a specific world.
     * Checks world name first (case-insensitive), then UUID string.
     */
    fun findOverride(worldName: String, worldUuid: UUID): MetabolismWorldOverride? {
        // 1. Try exact world name match (case-insensitive)
        val byName = worldOverrides.entries.firstOrNull { 
            it.key.equals(worldName, ignoreCase = true) 
        }?.value
        
        if (byName != null) return byName
        
        // 2. Try UUID string match
        val uuidStr = worldUuid.toString()
        return worldOverrides[uuidStr]
    }
    
    /**
     * Merge world override into this config, returning a new merged instance.
     */
    fun mergeOverride(override: MetabolismWorldOverride): MetabolismConfig {
        return this.copy(
            hunger = hunger.mergeWith(override.hunger),
            thirst = thirst.mergeWith(override.thirst),
            energy = energy.mergeWith(override.energy),
            buffs = buffs.mergeWith(override.buffs),
            debuffs = debuffs.mergeWith(override.debuffs)
        )
    }
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 5
        
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
            ),
            
            // v2 -> v3: Restructure debuffs to 3-stage system
            ConfigMigration(
                fromVersion = 2,
                toVersion = 3,
                description = "Restructure debuffs config to 3-stage system (hunger/thirst/energy with uniform thresholds)",
                migrate = { old ->
                    old.toMutableMap().apply {
                        @Suppress("UNCHECKED_CAST")
                        val oldDebuffs = this["debuffs"] as? MutableMap<String, Any>
                        
                        if (oldDebuffs != null) {
                            // Create new 3-stage structure
                            val newDebuffs = mutableMapOf<String, Any>(
                                "enabled" to (oldDebuffs["enabled"] ?: true),
                                
                                // Hunger debuffs (old: starvation only)
                                "hunger" to mapOf(
                                    "enabled" to true,
                                    "peckishDamage" to 0.5,
                                    "hungryDamage" to 1.5,
                                    "starvingDamage" to 3.0,
                                    "damageIntervalMs" to 3000
                                ),
                                
                                // Thirst debuffs (old: dehydration + parched)
                                "thirst" to mapOf(
                                    "enabled" to true,
                                    "thirstyDrain" to 2.0,
                                    "parchedDrain" to 4.0,
                                    "dehydratedDrain" to 6.0,
                                    "drainIntervalMs" to 2000
                                ),
                                
                                // Energy debuffs (old: exhaustion + tired)
                                "energy" to mapOf(
                                    "enabled" to true,
                                    "drowsySpeed" to 0.90,
                                    "tiredSpeed" to 0.75,
                                    "exhaustedSpeed" to 0.55
                                )
                            )
                            
                            this["debuffs"] = newDebuffs
                        }
                        
                        // Update config version
                        this["configVersion"] = 3
                    }
                }
            ),
            
            // v3 -> v4: Add per-world configuration overrides support
            ConfigMigration(
                fromVersion = 3,
                toVersion = 4,
                description = "Add per-world configuration overrides support (worldOverrides field)",
                migrate = { old ->
                    old.toMutableMap().apply {
                        // Add empty worldOverrides if not present
                        if (!containsKey("worldOverrides")) {
                            this["worldOverrides"] = emptyMap<String, Any>()
                        }
                        this["configVersion"] = 4
                    }
                }
            ),
            
            // v4 -> v5: Add modded consumables support
            ConfigMigration(
                fromVersion = 4,
                toVersion = 5,
                description = "Add modded consumables support with grouped mod structure",
                migrate = { old ->
                    old.toMutableMap().apply {
                        // Add moddedConsumables section if not present
                        if (!containsKey("moddedConsumables")) {
                            // For existing configs (migrations), add empty structure with disabled
                            this["moddedConsumables"] = mapOf(
                                "enabled" to false,  // Conservative: disabled by default for migrations
                                "warnIfMissing" to true,
                                "mods" to emptyMap<String, Any>()  // Empty mods map
                            )
                        } else {
                            // If moddedConsumables exists but has old structure (foods/drinks/potions),
                            // convert to new grouped structure
                            @Suppress("UNCHECKED_CAST")
                            val modded = this["moddedConsumables"] as? Map<String, Any>
                            if (modded != null && (modded.containsKey("foods") || modded.containsKey("drinks") || modded.containsKey("potions"))) {
                                // Old structure detected, convert to new grouped structure
                                val enabled = modded["enabled"] as? Boolean ?: false
                                val warnIfMissing = modded["warnIfMissing"] as? Boolean ?: true
                                
                                this["moddedConsumables"] = mapOf(
                                    "enabled" to enabled,
                                    "warnIfMissing" to warnIfMissing,
                                    "mods" to emptyMap<String, Any>()  // Convert old entries to empty (too complex to auto-migrate)
                                )
                            }
                        }
                        this["configVersion"] = 5
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
 * 
 * New 3-stage system:
 * - Stage 1: ≤ 75% (mild effects)
 * - Stage 2: ≤ 50% (moderate effects)
 * - Stage 3: ≤ 25% (severe effects)
 */
data class DebuffsConfig(
    /** Whether debuffs are enabled */
    val enabled: Boolean = true,
    
    /** Hunger debuffs configuration (health drain) */
    val hunger: HungerDebuffsConfig = HungerDebuffsConfig(),
    
    /** Thirst debuffs configuration (stamina drain) */
    val thirst: ThirstDebuffsConfig = ThirstDebuffsConfig(),
    
    /** Energy debuffs configuration (speed reduction) */
    val energy: EnergyDebuffsConfig = EnergyDebuffsConfig()
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for hunger debuffs (health drain).
 * 3-stage system: Peckish (75%) → Hungry (50%) → Starving (25%)
 */
data class HungerDebuffsConfig(
    /** Whether hunger debuffs are enabled */
    val enabled: Boolean = true,
    
    /** Stage 1: Peckish (≤ 75%) - health drain */
    val peckishDamage: Double = 0.5,  // HP per tick
    
    /** Stage 2: Hungry (≤ 50%) - health drain */
    val hungryDamage: Double = 1.5,  // HP per tick
    
    /** Stage 3: Starving (≤ 25%) - health drain */
    val starvingDamage: Double = 3.0,  // HP per tick
    
    /** Interval between damage ticks (milliseconds) */
    val damageIntervalMs: Long = 3000 // 3 seconds
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for thirst debuffs (max stamina reduction).
 * 3-stage system: Thirsty (75%) → Parched (50%) → Dehydrated (25%)
 * 
 * Uses multiplicative modifiers to reduce max stamina capacity.
 * Lower max stamina means regen fills the bar slower (visual effect of reduced regen).
 */
data class ThirstDebuffsConfig(
    /** Whether thirst debuffs are enabled */
    val enabled: Boolean = true,
    
    /** Stage 1: Thirsty (≤ 75%) - max stamina multiplier */
    val thirstyMaxStamina: Double = 0.85,  // 85% of max (like -15%)
    
    /** Stage 2: Parched (≤ 50%) - max stamina multiplier */
    val parchedMaxStamina: Double = 0.65,  // 65% of max (like -35%)
    
    /** Stage 3: Dehydrated (≤ 25%) - max stamina multiplier */
    val dehydratedMaxStamina: Double = 0.40  // 40% of max (like -60%)
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

/**
 * Configuration for energy debuffs (speed reduction).
 * 3-stage system: Drowsy (75%) → Tired (50%) → Exhausted (25%)
 */
data class EnergyDebuffsConfig(
    /** Whether energy debuffs are enabled */
    val enabled: Boolean = true,
    
    /** Stage 1: Drowsy (≤ 75%) - speed multiplier */
    val drowsySpeed: Double = 0.90,  // 90% speed (-10%)
    
    /** Stage 2: Tired (≤ 50%) - speed multiplier */
    val tiredSpeed: Double = 0.75,  // 75% speed (-25%)
    
    /** Stage 3: Exhausted (≤ 25%) - speed multiplier */
    val exhaustedSpeed: Double = 0.55  // 55% speed (-45%)
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = true)
}

// ============================================================================
// Per-World Override Data Classes
// ============================================================================

/**
 * Per-world metabolism configuration override.
 * Only fields that are non-null will override the global defaults.
 * 
 * Example:
 * ```yaml
 * worldOverrides:
 *   hardcore:
 *     hunger:
 *       baseDepletionRateSeconds: 960.0  # Only override this field
 *       # Other fields inherit from global
 * ```
 */
data class MetabolismWorldOverride(
    /** Override hunger settings (null = inherit from global) */
    val hunger: StatConfigOverride? = null,
    
    /** Override thirst settings (null = inherit from global) */
    val thirst: StatConfigOverride? = null,
    
    /** Override energy settings (null = inherit from global) */
    val energy: StatConfigOverride? = null,
    
    /** Override buffs settings (null = inherit from global) */
    val buffs: BuffsConfigOverride? = null,
    
    /** Override debuffs settings (null = inherit from global) */
    val debuffs: DebuffsConfigOverride? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null, null, null)
}

/**
 * Partial stat config for world overrides.
 * Null fields inherit from global.
 */
data class StatConfigOverride(
    val enabled: Boolean? = null,
    val baseDepletionRateSeconds: Double? = null,
    val activityMultipliers: Map<String, Double>? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null)
}

/**
 * Partial buffs config for world overrides.
 * Null fields inherit from global.
 */
data class BuffsConfigOverride(
    val enabled: Boolean? = null,
    val speedBuff: BuffConfigOverride? = null,
    val defenseBuff: BuffConfigOverride? = null,
    val staminaBuff: BuffConfigOverride? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null, null)
}

/**
 * Partial buff config for world overrides.
 * Null fields inherit from global.
 */
data class BuffConfigOverride(
    val enabled: Boolean? = null,
    val multiplier: Double? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null)
}

/**
 * Partial debuffs config for world overrides.
 * Null fields inherit from global.
 */
data class DebuffsConfigOverride(
    val enabled: Boolean? = null,
    val hunger: HungerDebuffsConfigOverride? = null,
    val thirst: ThirstDebuffsConfigOverride? = null,
    val energy: EnergyDebuffsConfigOverride? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null, null)
}

/**
 * Partial hunger debuffs config for world overrides.
 * Null fields inherit from global.
 */
data class HungerDebuffsConfigOverride(
    val enabled: Boolean? = null,
    val peckishDamage: Double? = null,
    val hungryDamage: Double? = null,
    val starvingDamage: Double? = null,
    val damageIntervalMs: Long? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null, null, null)
}

/**
 * Partial thirst debuffs config for world overrides.
 * Null fields inherit from global.
 */
data class ThirstDebuffsConfigOverride(
    val enabled: Boolean? = null,
    val thirstyMaxStamina: Double? = null,
    val parchedMaxStamina: Double? = null,
    val dehydratedMaxStamina: Double? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null, null)
}

/**
 * Partial energy debuffs config for world overrides.
 * Null fields inherit from global.
 */
data class EnergyDebuffsConfigOverride(
    val enabled: Boolean? = null,
    val drowsySpeed: Double? = null,
    val tiredSpeed: Double? = null,
    val exhaustedSpeed: Double? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(null, null, null, null)
}

// ============================================================================
// Config Merging Extension Functions
// ============================================================================

/**
 * Merge StatConfig with partial override.
 * Non-null override fields take precedence.
 */
fun StatConfig.mergeWith(override: StatConfigOverride?): StatConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        baseDepletionRateSeconds = override.baseDepletionRateSeconds ?: this.baseDepletionRateSeconds,
        activityMultipliers = if (override.activityMultipliers != null) {
            // Deep merge activity multipliers
            this.activityMultipliers.toMutableMap().apply {
                putAll(override.activityMultipliers)
            }
        } else {
            this.activityMultipliers
        }
    )
}

/**
 * Merge BuffsConfig with partial override.
 */
fun BuffsConfig.mergeWith(override: BuffsConfigOverride?): BuffsConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        speedBuff = this.speedBuff.mergeWith(override.speedBuff),
        defenseBuff = this.defenseBuff.mergeWith(override.defenseBuff),
        staminaBuff = this.staminaBuff.mergeWith(override.staminaBuff)
    )
}

/**
 * Merge BuffConfig with partial override.
 */
fun BuffConfig.mergeWith(override: BuffConfigOverride?): BuffConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        multiplier = override.multiplier ?: this.multiplier
    )
}

/**
 * Merge DebuffsConfig with partial override.
 */
fun DebuffsConfig.mergeWith(override: DebuffsConfigOverride?): DebuffsConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        hunger = this.hunger.mergeWith(override.hunger),
        thirst = this.thirst.mergeWith(override.thirst),
        energy = this.energy.mergeWith(override.energy)
    )
}

/**
 * Merge HungerDebuffsConfig with partial override.
 */
fun HungerDebuffsConfig.mergeWith(override: HungerDebuffsConfigOverride?): HungerDebuffsConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        peckishDamage = override.peckishDamage ?: this.peckishDamage,
        hungryDamage = override.hungryDamage ?: this.hungryDamage,
        starvingDamage = override.starvingDamage ?: this.starvingDamage,
        damageIntervalMs = override.damageIntervalMs ?: this.damageIntervalMs
    )
}

/**
 * Merge ThirstDebuffsConfig with partial override.
 */
fun ThirstDebuffsConfig.mergeWith(override: ThirstDebuffsConfigOverride?): ThirstDebuffsConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        thirstyMaxStamina = override.thirstyMaxStamina ?: this.thirstyMaxStamina,
        parchedMaxStamina = override.parchedMaxStamina ?: this.parchedMaxStamina,
        dehydratedMaxStamina = override.dehydratedMaxStamina ?: this.dehydratedMaxStamina
    )
}

/**
 * Merge EnergyDebuffsConfig with partial override.
 */
fun EnergyDebuffsConfig.mergeWith(override: EnergyDebuffsConfigOverride?): EnergyDebuffsConfig {
    if (override == null) return this
    
    return this.copy(
        enabled = override.enabled ?: this.enabled,
        drowsySpeed = override.drowsySpeed ?: this.drowsySpeed,
        tiredSpeed = override.tiredSpeed ?: this.tiredSpeed,
        exhaustedSpeed = override.exhaustedSpeed ?: this.exhaustedSpeed
    )
}
