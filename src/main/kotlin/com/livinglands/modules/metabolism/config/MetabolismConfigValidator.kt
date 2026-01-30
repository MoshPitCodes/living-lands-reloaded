package com.livinglands.modules.metabolism.config

import com.hypixel.hytale.logger.HytaleLogger
import java.util.UUID

/**
 * Validates MetabolismConfig for correctness and logs warnings for issues.
 * Uses permissive validation - warns but doesn't fail on errors.
 */
object MetabolismConfigValidator {
    
    /**
     * Validate world overrides and log warnings for potential issues.
     * 
     * @param config The metabolism config to validate
     * @param knownWorldNames Set of currently registered world names (case-insensitive)
     * @param logger Logger for warnings
     */
    fun validateOverrides(
        config: MetabolismConfig,
        knownWorldNames: Set<String>,
        logger: HytaleLogger
    ) {
        if (config.worldOverrides.isEmpty()) {
            return  // Nothing to validate
        }
        
        logger.atFine().log("Validating ${config.worldOverrides.size} world override(s)...")
        
        // Check for conflicting overrides (same world identified multiple ways)
        validateNoConflictingOverrides(config, logger)
        
        config.worldOverrides.forEach { (key, override) ->
            validateOverrideKey(key, knownWorldNames, logger)
            validateOverrideValues(key, override, logger)
        }
    }
    
    /**
     * Check for duplicate world identifiers (same world referenced by name AND UUID).
     */
    private fun validateNoConflictingOverrides(
        config: MetabolismConfig,
        logger: HytaleLogger
    ) {
        val uuidKeys = mutableSetOf<UUID>()
        val nameKeys = mutableMapOf<String, String>() // lowercase name -> original key
        
        config.worldOverrides.keys.forEach { key ->
            // Check if it's a UUID
            val uuid = try {
                UUID.fromString(key)
            } catch (e: IllegalArgumentException) {
                null
            }
            
            if (uuid != null) {
                if (!uuidKeys.add(uuid)) {
                    logger.atWarning().log(
                        "worldOverrides: Duplicate UUID override detected: '$key'. " +
                        "Only the last override will be used."
                    )
                }
            } else {
                // It's a name
                val lowerKey = key.lowercase()
                val existing = nameKeys[lowerKey]
                if (existing != null && existing != key) {
                    logger.atWarning().log(
                        "worldOverrides: Duplicate world name detected (case-insensitive): '$existing' and '$key'. " +
                        "These will be treated as the same world. Only the last override will be used."
                    )
                }
                nameKeys[lowerKey] = key
            }
        }
    }
    
    /**
     * Validate that the override key is either a known world name or valid UUID.
     */
    private fun validateOverrideKey(
        key: String,
        knownWorldNames: Set<String>,
        logger: HytaleLogger
    ) {
        // Check if key is a known world name (case-insensitive)
        val isKnownWorld = knownWorldNames.any { it.equals(key, ignoreCase = true) }
        
        // Check if key is a valid UUID string
        val isValidUuid = try {
            UUID.fromString(key)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
        
        if (!isKnownWorld && !isValidUuid) {
            logger.atWarning().log(
                "worldOverrides: Unknown world '$key'. This override will only apply if a world with this name is created later. " +
                "Known worlds: ${knownWorldNames.joinToString(", ") { "'$it'" }}"
            )
        }
    }
    
    /**
     * Validate override values are within acceptable ranges.
     */
    private fun validateOverrideValues(
        worldKey: String,
        override: MetabolismWorldOverride,
        logger: HytaleLogger
    ) {
        // Check for enabled=false with other settings (likely config error)
        validateDisabledSettings(worldKey, override, logger)
        
        // Validate hunger stat
        override.hunger?.let { hunger ->
            validateStatConfig(worldKey, "hunger", hunger, logger)
        }
        
        // Validate thirst stat
        override.thirst?.let { thirst ->
            validateStatConfig(worldKey, "thirst", thirst, logger)
        }
        
        // Validate energy stat
        override.energy?.let { energy ->
            validateStatConfig(worldKey, "energy", energy, logger)
        }
        
        // Validate buffs
        override.buffs?.let { buffs ->
            validateBuffsConfig(worldKey, buffs, logger)
        }
        
        // Validate debuffs
        override.debuffs?.let { debuffs ->
            validateDebuffsConfig(worldKey, debuffs, logger)
        }
    }
    
    /**
     * Validate stat configuration override.
     */
    private fun validateStatConfig(
        worldKey: String,
        statName: String,
        stat: StatConfigOverride,
        logger: HytaleLogger
    ) {
        // Validate depletion rate
        stat.baseDepletionRateSeconds?.let { rate ->
            if (rate <= 0) {
                logger.atWarning().log(
                    "worldOverrides.$worldKey.$statName.baseDepletionRateSeconds: " +
                    "Invalid value $rate (must be > 0). Will clamp to minimum 1.0 second."
                )
            }
            if (rate > 1_000_000) {
                logger.atWarning().log(
                    "worldOverrides.$worldKey.$statName.baseDepletionRateSeconds: " +
                    "Very large value $rate (> 1 million seconds = 11.5 days). Is this intentional?"
                )
            }
        }
        
        // Validate activity multipliers
        stat.activityMultipliers?.forEach { (activity, multiplier) ->
            if (multiplier < 0) {
                logger.atWarning().log(
                    "worldOverrides.$worldKey.$statName.activityMultipliers.$activity: " +
                    "Negative multiplier $multiplier. Will clamp to 0.0."
                )
            }
            if (multiplier > 100) {
                logger.atWarning().log(
                    "worldOverrides.$worldKey.$statName.activityMultipliers.$activity: " +
                    "Very large multiplier $multiplier (> 100x). Is this intentional?"
                )
            }
        }
    }
    
    /**
     * Validate buffs configuration override.
     */
    private fun validateBuffsConfig(
        worldKey: String,
        buffs: BuffsConfigOverride,
        logger: HytaleLogger
    ) {
        buffs.speedBuff?.let { buff ->
            validateBuffMultiplier(worldKey, "buffs.speedBuff", buff.multiplier, logger)
        }
        buffs.defenseBuff?.let { buff ->
            validateBuffMultiplier(worldKey, "buffs.defenseBuff", buff.multiplier, logger)
        }
        buffs.staminaBuff?.let { buff ->
            validateBuffMultiplier(worldKey, "buffs.staminaBuff", buff.multiplier, logger)
        }
    }
    
    /**
     * Validate buff multiplier (should be >= 1.0 for buffs).
     */
    private fun validateBuffMultiplier(
        worldKey: String,
        path: String,
        multiplier: Double?,
        logger: HytaleLogger
    ) {
        multiplier?.let { mult ->
            if (mult < 1.0) {
                logger.atWarning().log(
                    "worldOverrides.$worldKey.$path.multiplier: " +
                    "Value $mult is less than 1.0. Buffs typically use multipliers >= 1.0 (e.g., 1.132 = +13.2%). " +
                    "Did you mean to use a debuff instead?"
                )
            }
            if (mult > 10.0) {
                logger.atWarning().log(
                    "worldOverrides.$worldKey.$path.multiplier: " +
                    "Very large multiplier $mult (> 10x). Is this intentional?"
                )
            }
        }
    }
    
    /**
     * Validate debuffs configuration override.
     */
    private fun validateDebuffsConfig(
        worldKey: String,
        debuffs: DebuffsConfigOverride,
        logger: HytaleLogger
    ) {
        // Validate hunger debuffs
        debuffs.hunger?.let { hunger ->
            hunger.peckishDamage?.let { damage ->
                validateDamageValue(worldKey, "debuffs.hunger.peckishDamage", damage, logger)
            }
            hunger.hungryDamage?.let { damage ->
                validateDamageValue(worldKey, "debuffs.hunger.hungryDamage", damage, logger)
            }
            hunger.starvingDamage?.let { damage ->
                validateDamageValue(worldKey, "debuffs.hunger.starvingDamage", damage, logger)
            }
            hunger.damageIntervalMs?.let { interval ->
                validateIntervalMs(worldKey, "debuffs.hunger.damageIntervalMs", interval, logger)
            }
        }
        
        // Validate thirst debuffs (stamina multipliers)
        debuffs.thirst?.let { thirst ->
            thirst.thirstyMaxStamina?.let { mult ->
                validateDebuffMultiplier(worldKey, "debuffs.thirst.thirstyMaxStamina", mult, logger)
            }
            thirst.parchedMaxStamina?.let { mult ->
                validateDebuffMultiplier(worldKey, "debuffs.thirst.parchedMaxStamina", mult, logger)
            }
            thirst.dehydratedMaxStamina?.let { mult ->
                validateDebuffMultiplier(worldKey, "debuffs.thirst.dehydratedMaxStamina", mult, logger)
            }
        }
        
        // Validate energy debuffs (speed multipliers)
        debuffs.energy?.let { energy ->
            energy.drowsySpeed?.let { mult ->
                validateDebuffMultiplier(worldKey, "debuffs.energy.drowsySpeed", mult, logger)
            }
            energy.tiredSpeed?.let { mult ->
                validateDebuffMultiplier(worldKey, "debuffs.energy.tiredSpeed", mult, logger)
            }
            energy.exhaustedSpeed?.let { mult ->
                validateDebuffMultiplier(worldKey, "debuffs.energy.exhaustedSpeed", mult, logger)
            }
        }
    }
    
    /**
     * Validate damage value (should be non-negative).
     */
    private fun validateDamageValue(
        worldKey: String,
        path: String,
        damage: Double,
        logger: HytaleLogger
    ) {
        if (damage < 0) {
            logger.atWarning().log(
                "worldOverrides.$worldKey.$path: " +
                "Negative damage value $damage. Will clamp to 0.0."
            )
        }
        if (damage > 100) {
            logger.atWarning().log(
                "worldOverrides.$worldKey.$path: " +
                "Very high damage value $damage (can kill in one tick). Is this intentional?"
            )
        }
    }
    
    /**
     * Validate that disabled stats don't have conflicting settings.
     * Warns if a stat is disabled but has other config values set.
     */
    private fun validateDisabledSettings(
        worldKey: String,
        override: MetabolismWorldOverride,
        logger: HytaleLogger
    ) {
        // Check hunger
        override.hunger?.let { hunger ->
            if (hunger.enabled == false) {
                if (hunger.baseDepletionRateSeconds != null) {
                    logger.atWarning().log(
                        "worldOverrides.$worldKey.hunger: " +
                        "Stat is disabled but baseDepletionRateSeconds is set. " +
                        "This setting will be ignored."
                    )
                }
                if (hunger.activityMultipliers?.isNotEmpty() == true) {
                    logger.atWarning().log(
                        "worldOverrides.$worldKey.hunger: " +
                        "Stat is disabled but activityMultipliers are set. " +
                        "These settings will be ignored."
                    )
                }
            }
        }
        
        // Check thirst
        override.thirst?.let { thirst ->
            if (thirst.enabled == false) {
                if (thirst.baseDepletionRateSeconds != null || thirst.activityMultipliers?.isNotEmpty() == true) {
                    logger.atWarning().log(
                        "worldOverrides.$worldKey.thirst: " +
                        "Stat is disabled but has config values. These will be ignored."
                    )
                }
            }
        }
        
        // Check energy
        override.energy?.let { energy ->
            if (energy.enabled == false) {
                if (energy.baseDepletionRateSeconds != null || energy.activityMultipliers?.isNotEmpty() == true) {
                    logger.atWarning().log(
                        "worldOverrides.$worldKey.energy: " +
                        "Stat is disabled but has config values. These will be ignored."
                    )
                }
            }
        }
    }
    
    /**
     * Validate interval in milliseconds.
     */
    private fun validateIntervalMs(
        worldKey: String,
        path: String,
        interval: Long,
        logger: HytaleLogger
    ) {
        if (interval <= 0) {
            logger.atWarning().log(
                "worldOverrides.$worldKey.$path: " +
                "Invalid interval $interval ms (must be > 0). Will clamp to minimum 100ms."
            )
        }
        if (interval < 100) {
            logger.atWarning().log(
                "worldOverrides.$worldKey.$path: " +
                "Very short interval $interval ms (< 100ms). This may cause performance issues."
            )
        }
    }
    
    /**
     * Validate debuff multiplier (should be between 0.0 and 1.0 typically).
     */
    private fun validateDebuffMultiplier(
        worldKey: String,
        path: String,
        multiplier: Double,
        logger: HytaleLogger
    ) {
        if (multiplier < 0) {
            logger.atWarning().log(
                "worldOverrides.$worldKey.$path: " +
                "Negative multiplier $multiplier. Will clamp to 0.0."
            )
        }
        if (multiplier > 1.0) {
            logger.atWarning().log(
                "worldOverrides.$worldKey.$path: " +
                "Multiplier $multiplier is greater than 1.0. Debuffs typically use multipliers < 1.0 (e.g., 0.65 = 65% of base). " +
                "Did you mean to use a buff instead?"
            )
        }
    }
}
