package com.livinglands.modules.metabolism.food

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismService
import com.livinglands.modules.metabolism.config.FoodConsumptionConfig
import com.livinglands.core.MessageFormatter
import java.util.UUID

/**
 * Processes food consumption and restores metabolism stats.
 * 
 * This processor applies stat restoration based on:
 * - Food type (meat, fruit, bread, potions, etc.)
 * - Tier level (1-3, affects base restoration amount)
 * - Type-specific multipliers (meat = high hunger, water = high thirst, etc.)
 * 
 * **Restoration Formula:**
 * ```
 * restoration = baseTierValue * foodTypeMultiplier
 * ```
 * 
 * **Example:**
 * - Cooked Meat T3: base=25.0 * meat_hunger_mult=1.3 = 32.5 hunger
 * - Apple T2: base=15.0 * fruit_hunger_mult=0.9 = 13.5 hunger, base=6.0 * fruit_thirst_mult=1.5 = 9.0 thirst
 * - Water T2: base=0.0 * water_hunger_mult=0.0 = 0 hunger, base=6.0 * water_thirst_mult=3.0 = 18.0 thirst
 * 
 * @property metabolismService Service for updating player stats
 * @property config Food consumption configuration
 * @property logger Logger for debugging
 */
class FoodConsumptionProcessor(
    private val metabolismService: MetabolismService,
    private val config: FoodConsumptionConfig,
    private val logger: HytaleLogger
) {
    
    /**
     * Process a detected food consumption and restore appropriate stats.
     * 
     * @param playerId The player's UUID
     * @param detection The detected food consumption (type, tier, effectId)
     */
    fun processConsumption(playerId: UUID, detection: FoodDetection) {
        // Calculate restoration amounts based on tier and food type
        val hungerRestore = calculateHungerRestoration(detection.foodType, detection.tier)
        val thirstRestore = calculateThirstRestoration(detection.foodType, detection.tier)
        val energyRestore = calculateEnergyRestoration(detection.foodType, detection.tier)
        
        // Apply restoration to metabolism stats
        metabolismService.restoreStats(
            playerId = playerId,
            hunger = hungerRestore,
            thirst = thirstRestore,
            energy = energyRestore
        )
        
        // Log food consumption at INFO level (useful for server monitoring)
        logger.atInfo().log(
            "Food consumed: ${detection.effectId} " +
            "(type=${detection.foodType}, tier=${detection.tier}) -> " +
            "H+%.2f, T+%.2f, E+%.2f".format(hungerRestore, thirstRestore, energyRestore)
        )
        
        // Only send chat message for ACTUAL food consumption, not for buff effects
        // Buff effects (Meat_Buff, HealthRegen_Buff, etc.) are applied BY the food
        // but shouldn't trigger their own consumption messages - they're for Phase 7 buff system
        if (config.showChatMessages && isPrimaryConsumable(detection.effectId)) {
            val foodName = formatFoodName(detection.effectId)
            sendFoodConsumptionMessage(playerId, foodName, detection.tier, hungerRestore, thirstRestore, energyRestore)
        }
    }
    
    /**
     * Check if this is a primary consumable effect (not a secondary buff).
     * 
     * Primary consumables are the actual "eating" action:
     * - Food_Instant_Heal (the actual food item consumption)
     * - Food_Health_Restore (water/drinks)
     * - Potion_* (potion consumption)
     * 
     * Secondary buffs are applied BY the food but aren't consumption events:
     * - Meat_Buff (applied by cooked meat)
     * - FruitVeggie_Buff (applied by fruits/veggies)
     * - HealthRegen_Buff (applied by certain foods)
     * These will be used in Phase 7 buff system.
     * 
     * @param effectId The effect ID to check
     * @return True if this is a primary consumable, false if it's a secondary buff
     */
    private fun isPrimaryConsumable(effectId: String): Boolean {
        return when {
            // Primary consumable effects (actual eating/drinking)
            effectId.startsWith("Food_Instant_Heal") -> true
            effectId.startsWith("Food_Health_Restore") -> true
            effectId.startsWith("Food_Stamina_Restore") -> true
            effectId.startsWith("Food_Health_Boost") -> true
            effectId.startsWith("Food_Stamina_Boost") -> true
            effectId.startsWith("Food_Health_Regen") -> true
            effectId.startsWith("Food_Stamina_Regen") -> true
            effectId.startsWith("Potion_") -> true
            effectId.startsWith("Antidote") -> true
            
            // Secondary buff effects (applied BY food, not consumption events)
            effectId.startsWith("Meat_Buff") -> false
            effectId.startsWith("FruitVeggie_Buff") -> false
            effectId.startsWith("HealthRegen_Buff") -> false
            
            else -> true // Default to showing message for unknown effects
        }
    }
    
    /**
     * Send a chat message to the player about the food they consumed.
     * 
     * @param playerId The player's UUID
     * @param foodName Formatted food name
     * @param tier Food tier
     * @param hungerRestore Hunger points restored
     * @param thirstRestore Thirst points restored
     * @param energyRestore Energy points restored
     */
    private fun sendFoodConsumptionMessage(
        playerId: UUID,
        foodName: String,
        tier: Int,
        hungerRestore: Double,
        thirstRestore: Double,
        energyRestore: Double
    ) {
        // Get player session to access world and entity
        val session = CoreModule.players.getSession(playerId) ?: return
        
        // Execute on world thread to access ECS
        session.world.execute {
            try {
                // Get Player component from entity ref
                val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return@execute
                
                // Get PlayerRef from Player component
                @Suppress("DEPRECATION")
                val playerRef = player.getPlayerRef() ?: return@execute
                
                // Send formatted food consumption message
                MessageFormatter.foodConsumption(
                    playerRef,
                    foodName,
                    tier,
                    hungerRestore,
                    thirstRestore,
                    energyRestore
                )
            } catch (e: Exception) {
                logger.atFine().log("Failed to send food consumption message to player $playerId: ${e.message}")
            }
        }
    }
    
    /**
     * Format the effect ID into a readable food name.
     * 
     * Examples:
     * - "Meat_Buff_T3" -> "Meat"
     * - "Food_Instant_Heal_T2" -> "Food"
     * - "Potion_Health" -> "Health Potion"
     * - "FruitVeggie_Buff_T1" -> "Fruit/Veggie"
     * 
     * @param effectId The raw effect ID
     * @return Formatted food name
     */
    private fun formatFoodName(effectId: String): String {
        return when {
            effectId.startsWith("Meat_Buff") -> "Meat"
            effectId.startsWith("FruitVeggie_Buff") -> "Fruit/Veggie"
            effectId.startsWith("HealthRegen_Buff") -> "Health Regen Food"
            effectId.startsWith("Food_Instant_Heal") -> "Food"
            effectId.startsWith("Food_Health_Restore") -> "Water"
            effectId.startsWith("Food_Stamina_Restore") -> "Stamina Drink"
            effectId.startsWith("Food_Health_Boost") -> "Health Boost Food"
            effectId.startsWith("Food_Stamina_Boost") -> "Stamina Boost Food"
            effectId.startsWith("Food_Health_Regen") -> "Health Regen Food"
            effectId.startsWith("Food_Stamina_Regen") -> "Stamina Regen Food"
            effectId.startsWith("Potion_Health") -> "Health Potion"
            effectId.startsWith("Potion_Regen_Health") -> "Health Regen Potion"
            effectId.startsWith("Potion_Stamina") -> "Stamina Potion"
            effectId.startsWith("Potion_Regen_Stamina") -> "Stamina Regen Potion"
            effectId.startsWith("Potion_Mana") || effectId.startsWith("Potion_Signature") -> "Mana Potion"
            effectId.startsWith("Potion_Morph") -> "Morph Potion"
            effectId.startsWith("Antidote") -> "Milk"
            else -> "Consumable"
        }
    }
    
    // ========================================
    // Hunger Restoration
    // ========================================
    
    /**
     * Calculate hunger restoration amount.
     * 
     * @param foodType The type of food consumed
     * @param tier The food tier (1-3)
     * @return Hunger points to restore
     */
    private fun calculateHungerRestoration(foodType: FoodType, tier: Int): Double {
        val base = HUNGER_BY_TIER[tier]
        val multiplier = HUNGER_MULTIPLIERS[foodType] ?: 1.0
        return base * multiplier
    }
    
    /**
     * Base hunger restoration by tier.
     * Index 0 unused, indices 1-3 represent tiers 1-3.
     */
    private val HUNGER_BY_TIER = doubleArrayOf(
        0.0,   // Unused
        8.0,   // Tier 1 (small/lesser)
        15.0,  // Tier 2 (medium/default)
        25.0   // Tier 3 (large/greater)
    )
    
    /**
     * Hunger restoration multipliers by food type.
     */
    private val HUNGER_MULTIPLIERS = mapOf(
        FoodType.MEAT to 1.3,          // Very filling
        FoodType.BREAD to 1.2,         // Filling
        FoodType.FRUIT_VEGGIE to 0.9,  // Lighter
        FoodType.INSTANT_HEAL to 0.5,  // Some hunger
        FoodType.HEALTH_REGEN to 0.5,
        FoodType.HEALTH_BOOST to 0.8,
        FoodType.STAMINA_BOOST to 0.8,
        FoodType.HEALTH_POTION to 0.3, // Minimal hunger
        FoodType.MANA_POTION to 0.0,   // No hunger
        FoodType.STAMINA_POTION to 0.2,
        FoodType.WATER to 0.0,         // No hunger
        FoodType.MILK to 0.3,          // Slight hunger
        FoodType.GENERIC to 0.6        // Moderate default
    )
    
    // ========================================
    // Thirst Restoration
    // ========================================
    
    /**
     * Calculate thirst restoration amount.
     * 
     * @param foodType The type of food consumed
     * @param tier The food tier (1-3)
     * @return Thirst points to restore
     */
    private fun calculateThirstRestoration(foodType: FoodType, tier: Int): Double {
        val base = THIRST_BY_TIER[tier]
        val multiplier = THIRST_MULTIPLIERS[foodType] ?: 1.0
        return base * multiplier
    }
    
    /**
     * Base thirst restoration by tier.
     * Index 0 unused, indices 1-3 represent tiers 1-3.
     */
    private val THIRST_BY_TIER = doubleArrayOf(
        0.0,   // Unused
        3.0,   // Tier 1 (small/lesser)
        6.0,   // Tier 2 (medium/default)
        10.0   // Tier 3 (large/greater)
    )
    
    /**
     * Thirst restoration multipliers by food type.
     */
    private val THIRST_MULTIPLIERS = mapOf(
        FoodType.FRUIT_VEGGIE to 1.5,  // Good moisture content
        FoodType.MEAT to 0.5,          // Dry
        FoodType.BREAD to 0.3,         // Very dry
        FoodType.INSTANT_HEAL to 0.8,
        FoodType.HEALTH_REGEN to 0.8,
        FoodType.HEALTH_BOOST to 0.5,
        FoodType.STAMINA_BOOST to 0.5,
        FoodType.HEALTH_POTION to 2.0, // Liquid
        FoodType.MANA_POTION to 2.0,   // Liquid
        FoodType.STAMINA_POTION to 2.0, // Liquid
        FoodType.WATER to 3.0,         // Pure hydration
        FoodType.MILK to 2.5,          // Good hydration
        FoodType.GENERIC to 0.8        // Moderate default
    )
    
    // ========================================
    // Energy Restoration
    // ========================================
    
    /**
     * Calculate energy restoration amount.
     * 
     * @param foodType The type of food consumed
     * @param tier The food tier (1-3)
     * @return Energy points to restore
     */
    private fun calculateEnergyRestoration(foodType: FoodType, tier: Int): Double {
        val base = ENERGY_BY_TIER[tier]
        val multiplier = ENERGY_MULTIPLIERS[foodType] ?: 1.0
        return base * multiplier
    }
    
    /**
     * Base energy restoration by tier.
     * Index 0 unused, indices 1-3 represent tiers 1-3.
     */
    private val ENERGY_BY_TIER = doubleArrayOf(
        0.0,   // Unused
        5.0,   // Tier 1 (small/lesser)
        10.0,  // Tier 2 (medium/default)
        15.0   // Tier 3 (large/greater)
    )
    
    /**
     * Energy restoration multipliers by food type.
     */
    private val ENERGY_MULTIPLIERS = mapOf(
        FoodType.MEAT to 1.2,          // Protein energy
        FoodType.FRUIT_VEGGIE to 1.1,  // Natural energy
        FoodType.BREAD to 1.0,
        FoodType.INSTANT_HEAL to 0.5,
        FoodType.HEALTH_REGEN to 0.5,
        FoodType.HEALTH_BOOST to 0.8,
        FoodType.STAMINA_BOOST to 1.5, // Stamina food
        FoodType.HEALTH_POTION to 0.0, // No energy
        FoodType.MANA_POTION to 0.3,   // Slight energy
        FoodType.STAMINA_POTION to 0.3, // Slight energy
        FoodType.WATER to 0.5,         // Slight energy
        FoodType.MILK to 0.8,          // Moderate energy
        FoodType.GENERIC to 0.7        // Moderate default
    )
}
