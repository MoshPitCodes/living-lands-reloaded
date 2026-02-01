package com.livinglands.modules.metabolism.food.modded

/**
 * Detects the tier of a modded consumable item from its effect ID.
 * 
 * This utility class parses effect IDs for tier indicators to automatically
 * classify modded items into tiers 1-7 based on naming patterns.
 * 
 * **Supported Patterns:**
 * - Explicit tier markers: `_T1` through `_T7`, `_Tier1` through `_Tier7`
 * - Size descriptors: `_Tiny`, `_Small` (T1), `_Medium` (T2), `_Large` (T3)
 * - Quality descriptors: `_Lesser` (T1), `_Greater` (T3)
 * 
 * **Performance:**
 * - Pattern matching is O(n) where n = number of patterns
 * - Results should be cached at the registry level
 * - Target: < 0.5ms per detection
 */
object ItemTierDetector {
    
    /**
     * Tier detection patterns ordered by priority.
     * Earlier patterns take precedence if multiple match.
     */
    private val tierPatterns = listOf(
        // Explicit tier markers (highest priority) - T1 through T7
        Regex(".*[_-]?T1[_-]?.*", RegexOption.IGNORE_CASE) to 1,
        Regex(".*[_-]?T2[_-]?.*", RegexOption.IGNORE_CASE) to 2,
        Regex(".*[_-]?T3[_-]?.*", RegexOption.IGNORE_CASE) to 3,
        Regex(".*[_-]?T4[_-]?.*", RegexOption.IGNORE_CASE) to 4,
        Regex(".*[_-]?T5[_-]?.*", RegexOption.IGNORE_CASE) to 5,
        Regex(".*[_-]?T6[_-]?.*", RegexOption.IGNORE_CASE) to 6,
        Regex(".*[_-]?T7[_-]?.*", RegexOption.IGNORE_CASE) to 7,
        Regex(".*[_-]?Tier1[_-]?.*", RegexOption.IGNORE_CASE) to 1,
        Regex(".*[_-]?Tier2[_-]?.*", RegexOption.IGNORE_CASE) to 2,
        Regex(".*[_-]?Tier3[_-]?.*", RegexOption.IGNORE_CASE) to 3,
        Regex(".*[_-]?Tier4[_-]?.*", RegexOption.IGNORE_CASE) to 4,
        Regex(".*[_-]?Tier5[_-]?.*", RegexOption.IGNORE_CASE) to 5,
        Regex(".*[_-]?Tier6[_-]?.*", RegexOption.IGNORE_CASE) to 6,
        Regex(".*[_-]?Tier7[_-]?.*", RegexOption.IGNORE_CASE) to 7,
        
        // Size descriptors (medium priority)
        Regex(".*[_-]?Tiny[_-]?.*", RegexOption.IGNORE_CASE) to 1,
        Regex(".*[_-]?Small[_-]?.*", RegexOption.IGNORE_CASE) to 1,
        Regex(".*[_-]?Medium[_-]?.*", RegexOption.IGNORE_CASE) to 2,
        Regex(".*[_-]?Large[_-]?.*", RegexOption.IGNORE_CASE) to 3,
        
        // Quality descriptors (lower priority)
        Regex(".*[_-]?Lesser[_-]?.*", RegexOption.IGNORE_CASE) to 1,
        Regex(".*[_-]?Greater[_-]?.*", RegexOption.IGNORE_CASE) to 3,
        Regex(".*[_-]?Minor[_-]?.*", RegexOption.IGNORE_CASE) to 1,
        Regex(".*[_-]?Major[_-]?.*", RegexOption.IGNORE_CASE) to 3
    )
    
    /**
     * Default tier when no pattern matches.
     */
    const val DEFAULT_TIER = 1
    
    /**
     * Detect the tier of a consumable item from its effect ID.
     * 
     * Searches for tier indicators in the effect ID string.
     * If no tier pattern is found, returns DEFAULT_TIER (1).
     * 
     * @param effectId The effect ID to analyze (e.g., "FarmingMod:CookedChicken_T2")
     * @return Detected tier (1-7)
     */
    fun detectTier(effectId: String): Int {
        if (effectId.isBlank()) return DEFAULT_TIER
        
        // Check each pattern in priority order
        for ((pattern, tier) in tierPatterns) {
            if (pattern.matches(effectId)) {
                return tier
            }
        }
        
        // No pattern matched, return default
        return DEFAULT_TIER
    }
    
    /**
     * Validate that a tier value is within the valid range.
     * 
     * @param tier The tier to validate
     * @return The tier clamped to the valid range (1-7)
     */
    fun validateTier(tier: Int): Int {
        return tier.coerceIn(1, 7)
    }
    
    /**
     * Check if the effect ID contains any tier indicator.
     * 
     * @param effectId The effect ID to check
     * @return True if a tier indicator was found
     */
    fun hasTierIndicator(effectId: String): Boolean {
        if (effectId.isBlank()) return false
        
        return tierPatterns.any { (pattern, _) -> pattern.matches(effectId) }
    }
}
