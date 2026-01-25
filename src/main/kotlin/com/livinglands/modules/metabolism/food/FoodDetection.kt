package com.livinglands.modules.metabolism.food

/**
 * Result of food effect detection.
 * 
 * @property effectId The detected effect identifier
 * @property foodType The classified type of food
 * @property tier The tier of the food (1-3, or 2 for medium/default)
 */
data class FoodDetection(
    val effectId: String,
    val foodType: FoodType,
    val tier: Int
)

/**
 * Utility functions for food detection and classification.
 */
object FoodDetectionUtils {
    
    /**
     * Effect ID prefixes that indicate food consumption.
     */
    val FOOD_EFFECT_PREFIXES = setOf(
        "Food_Instant_Heal",     // T1/T2/T3/Bread - 100ms duration (CRITICAL: need fast detection)
        "Food_Health_Restore",   // Water/drinks
        "Food_Stamina_Restore",  // Stamina drinks
        "Food_Health_Boost",     // Max health buff - 150s duration
        "Food_Stamina_Boost",    // Max stamina buff - 150s duration
        "Food_Health_Regen",     // Health regen over time
        "Food_Stamina_Regen",    // Stamina regen over time
        "Meat_Buff",             // Meat buffs T1/T2/T3 - 150s duration
        "FruitVeggie_Buff",      // Fruit/veggie buffs T1/T2/T3
        "HealthRegen_Buff",      // Health regen buffs T1/T2/T3
        "Antidote"               // Milk bucket
    )
    
    /**
     * Effect ID prefixes for potions (also consumables).
     */
    val POTION_EFFECT_PREFIXES = setOf(
        "Potion_Health",         // Health potions
        "Potion_Regen_Health",   // Health regen potions
        "Potion_Stamina",        // Stamina potions
        "Potion_Regen_Stamina",  // Stamina regen potions
        "Potion_Signature",      // Mana potions
        "Potion_Mana",           // Mana potions (alternative)
        "Potion_Morph"           // Morph potions
    )
    
    /**
     * All consumable effect prefixes (food + potions).
     */
    val ALL_CONSUMABLE_PREFIXES = FOOD_EFFECT_PREFIXES + POTION_EFFECT_PREFIXES
    
    /**
     * Check if an effect ID represents a food or consumable effect.
     * 
     * @param effectId The effect identifier to check
     * @return True if the effect is a food/consumable effect
     */
    fun isConsumableEffect(effectId: String): Boolean {
        return ALL_CONSUMABLE_PREFIXES.any { effectId.startsWith(it) }
    }
    
    /**
     * Determine the tier of food from the effect ID.
     * 
     * Tier affects restoration amounts:
     * - Tier 1 (Small/Lesser): Low restoration
     * - Tier 2 (Medium/Default): Moderate restoration
     * - Tier 3 (Large/Greater): High restoration
     * 
     * @param effectId The effect identifier
     * @return Tier level (1-3), defaults to 2
     */
    fun determineTier(effectId: String): Int {
        // Explicit tier markers
        if (effectId.contains("_T1")) return 1
        if (effectId.contains("_T2")) return 2
        if (effectId.contains("_T3")) return 3
        
        // Size descriptors (for potions)
        if (effectId.contains("_Tiny") || effectId.contains("_Small")) return 1
        if (effectId.contains("_Medium")) return 2
        if (effectId.contains("_Large")) return 3
        
        // Quality descriptors
        if (effectId.contains("_Lesser")) return 1
        if (effectId.contains("_Greater")) return 3
        
        // Default to medium tier
        return 2
    }
    
    /**
     * Parse an effect ID into a FoodDetection result.
     * 
     * @param effectId The effect identifier to parse
     * @return FoodDetection with classified type and tier, or null if not a consumable
     */
    fun parseEffect(effectId: String): FoodDetection? {
        if (!isConsumableEffect(effectId)) {
            return null
        }
        
        val foodType = FoodType.fromEffectId(effectId)
        val tier = determineTier(effectId)
        
        return FoodDetection(effectId, foodType, tier)
    }
}
