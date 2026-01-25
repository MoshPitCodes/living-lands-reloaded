package com.livinglands.modules.metabolism.food

/**
 * Classification of food and consumable types based on their effects.
 * Used to determine appropriate stat restoration amounts.
 */
enum class FoodType {
    /** Cooked meat - high hunger restoration */
    MEAT,
    
    /** Fruits and vegetables - moderate hunger, good thirst */
    FRUIT_VEGGIE,
    
    /** Bread and baked goods - good hunger restoration */
    BREAD,
    
    /** Instant health restore effects */
    INSTANT_HEAL,
    
    /** Health regeneration over time */
    HEALTH_REGEN,
    
    /** Maximum health buff */
    HEALTH_BOOST,
    
    /** Maximum stamina buff */
    STAMINA_BOOST,
    
    /** Health potions - primarily thirst */
    HEALTH_POTION,
    
    /** Mana potions - thirst and slight energy */
    MANA_POTION,
    
    /** Stamina potions - thirst and energy */
    STAMINA_POTION,
    
    /** Pure water - maximum thirst restoration */
    WATER,
    
    /** Milk - good thirst, slight hunger */
    MILK,
    
    /** Unknown or unclassified consumable */
    GENERIC;
    
    companion object {
        /**
         * Determine food type from effect ID.
         * 
         * @param effectId The effect identifier (e.g., "Food_Instant_Heal_T2", "Meat_Buff_T3")
         * @return The classified food type
         */
        fun fromEffectId(effectId: String): FoodType {
            val id = effectId.lowercase()
            
            return when {
                // Meat buffs
                id.contains("meat_buff") -> MEAT
                
                // Fruit/veggie buffs
                id.contains("fruitveggie_buff") || id.contains("fruit") || id.contains("veggie") -> FRUIT_VEGGIE
                
                // Bread
                id.contains("bread") -> BREAD
                
                // Instant heal
                id.contains("food_instant_heal") -> INSTANT_HEAL
                
                // Health effects
                id.contains("health_regen") || id.contains("healthregen_buff") -> HEALTH_REGEN
                id.contains("health_boost") || id.contains("food_health_boost") -> HEALTH_BOOST
                
                // Stamina effects
                id.contains("stamina_boost") || id.contains("food_stamina_boost") -> STAMINA_BOOST
                id.contains("food_stamina_restore") || id.contains("stamina_regen") -> STAMINA_POTION
                
                // Potions
                id.contains("potion_health") || id.contains("potion_regen_health") -> HEALTH_POTION
                id.contains("potion_mana") || id.contains("potion_signature") -> MANA_POTION
                id.contains("potion_stamina") || id.contains("potion_regen_stamina") -> STAMINA_POTION
                
                // Water and milk
                id.contains("food_health_restore") && !id.contains("potion") -> WATER
                id.contains("antidote") || id.contains("milk") -> MILK
                
                // Generic food
                id.contains("food_") -> GENERIC
                
                else -> GENERIC
            }
        }
    }
}
