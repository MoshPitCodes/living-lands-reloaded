package com.livinglands.modules.metabolism.food.modded

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.metabolism.config.ModdedConsumableEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Validates modded consumable entries to ensure they exist in the game.
 * 
 * This validator checks if configured effect IDs are valid and logs warnings
 * for missing items when `warnIfMissing` is enabled. Validation results are
 * cached to prevent repeated lookups.
 * 
 * **Graceful Degradation:**
 * - Missing items don't crash the plugin
 * - Warning is logged only once per effect ID
 * - Invalid entries are skipped during processing
 * 
 * **Performance:**
 * - Results cached in ConcurrentHashMap
 * - Target: < 1ms per item (cached)
 */
class ModdedItemValidator(private val logger: HytaleLogger) {
    
    /**
     * Cache of validation results.
     * Key: effectId (lowercase), Value: validation result
     */
    private val validationCache = ConcurrentHashMap<String, ValidationResult>()
    
    /**
     * Validate a modded consumable entry.
     * 
     * Checks if the entry has a valid configuration and if the effect exists
     * in the game registry. Results are cached for performance.
     * 
     * @param entry The entry to validate
     * @param warnIfMissing Whether to log warnings for missing items
     * @return True if the entry is valid and can be used
     */
    fun validateEntry(entry: ModdedConsumableEntry, warnIfMissing: Boolean): Boolean {
        // Check basic validity first
        if (!entry.isValid()) {
            if (warnIfMissing) {
                LoggingManager.warn(logger, "metabolism") { 
                    "Modded consumable has empty effectId, skipping" 
                }
            }
            return false
        }
        
        val cacheKey = entry.effectId.lowercase()
        
        return validationCache.getOrPut(cacheKey) {
            val result = performValidation(entry)
            
            // Log warning if validation failed and warnings are enabled
            if (!result.isValid && warnIfMissing) {
                LoggingManager.warn(logger, "metabolism") { 
                    "Modded consumable effect not found: ${entry.effectId} (${result.reason})"
                }
            }
            
            result
        }.isValid
    }
    
    /**
     * Perform the actual validation check.
     * 
     * @param entry The entry to validate
     * @return ValidationResult with status and reason
     */
    private fun performValidation(entry: ModdedConsumableEntry): ValidationResult {
        // Validate effectId format
        if (entry.effectId.isBlank()) {
            return ValidationResult(false, "Empty effectId")
        }
        
        // Validate category
        val validCategories = setOf(
            "MEAT", "FRUIT_VEGGIE", "BREAD", "GENERIC",
            "WATER", "MILK",
            "HEALTH_POTION", "MANA_POTION", "STAMINA_POTION",
            "INSTANT_HEAL", "HEALTH_REGEN", "HEALTH_BOOST", "STAMINA_BOOST"
        )
        
        if (entry.category.uppercase() !in validCategories) {
            return ValidationResult(false, "Invalid category: ${entry.category}")
        }
        
        // Validate tier if specified
        // Modded consumables support T1-T7 (extended from vanilla T1-T3)
        if (entry.tier < 1 || entry.tier > 7) {
            return ValidationResult(false, "Invalid tier: ${entry.tier} (must be 1-7)")
        }
        
        // Validate custom multipliers if specified
        entry.customMultipliers?.let { mults ->
            if (mults.hunger != null && mults.hunger < 0) {
                return ValidationResult(false, "Invalid hunger multiplier: ${mults.hunger}")
            }
            if (mults.thirst != null && mults.thirst < 0) {
                return ValidationResult(false, "Invalid thirst multiplier: ${mults.thirst}")
            }
            if (mults.energy != null && mults.energy < 0) {
                return ValidationResult(false, "Invalid energy multiplier: ${mults.energy}")
            }
        }
        
        // TODO: Check if effect exists in Hytale registry
        // Currently we assume all items exist (graceful degradation)
        // When the Hytale API provides effect registry access, we can validate here
        val effectExists = checkEffectExists(entry.effectId)
        
        return if (effectExists) {
            ValidationResult(true, "Valid")
        } else {
            // For now, we still return valid since we can't check the registry
            // This allows configured items to be processed even if we can't verify them
            ValidationResult(true, "Effect registry check not available, assuming valid")
        }
    }
    
    /**
     * Check if an effect exists in the Hytale effect registry.
     * 
     * Note: This is a placeholder that always returns true.
     * When Hytale's API provides access to the effect registry,
     * this method should be updated to perform actual validation.
     * 
     * @param effectId The effect ID to check
     * @return True if the effect exists (or check is unavailable)
     */
    private fun checkEffectExists(effectId: String): Boolean {
        // TODO: Implement actual effect registry check when API is available
        // For now, we assume all configured items exist
        // This provides graceful degradation - items that don't exist
        // simply won't match during food detection
        return true
    }
    
    /**
     * Clear the validation cache.
     * Called during config reload to re-validate all entries.
     */
    fun clearCache() {
        validationCache.clear()
    }
    
    /**
     * Get the number of cached validation results.
     */
    fun getCacheSize(): Int = validationCache.size
    
    /**
     * Validation result container.
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val reason: String
    )
}
