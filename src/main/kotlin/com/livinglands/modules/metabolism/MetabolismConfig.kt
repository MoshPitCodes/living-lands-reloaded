package com.livinglands.modules.metabolism

/**
 * Configuration for the metabolism system.
 * Loaded from metabolism.yml
 * 
 * Defines depletion rates for hunger, thirst, and energy,
 * along with activity multipliers that affect how fast stats deplete.
 */
data class MetabolismConfig(
    /** Master enable/disable for the entire metabolism system */
    val enabled: Boolean = true,
    
    /** Hunger stat configuration */
    val hunger: StatConfig = StatConfig(
        enabled = true,
        baseDepletionRateSeconds = 1440.0, // 24 minutes from 100 to 0 at idle
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
        baseDepletionRateSeconds = 1080.0, // 18 minutes from 100 to 0 at idle
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
    
    /** Save interval in seconds (how often to persist stats to database) */
    val saveIntervalSeconds: Int = 60
) {
    /** No-arg constructor for SnakeYAML deserialization */
    constructor() : this(enabled = true)
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
    val baseDepletionRateSeconds: Double = 480.0,
    
    /**
     * Activity multipliers that affect depletion rate.
     * Keys should be lowercase activity names: idle, walking, sprinting, swimming, combat
     * Values are multipliers - 2.0 means stat depletes twice as fast.
     */
    val activityMultipliers: Map<String, Double> = emptyMap()
) {
    /** No-arg constructor for SnakeYAML deserialization */
    constructor() : this(enabled = true)
    
    /**
     * Get the multiplier for a given activity state.
     * Returns 1.0 if the activity is not configured.
     */
    fun getMultiplier(activityName: String): Double {
        return activityMultipliers[activityName.lowercase()] ?: 1.0
    }
}
