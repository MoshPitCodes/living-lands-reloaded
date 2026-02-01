package com.livinglands.modules.metabolism.food

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismService
import com.livinglands.modules.metabolism.config.FoodConsumptionConfig
import com.livinglands.modules.metabolism.food.modded.ModdedConsumablesRegistry
import com.livinglands.core.MessageFormatter
import com.livinglands.core.toCachedString
import java.util.UUID

/**
 * Processes food consumption and restores metabolism stats.
 * 
 * This processor applies stat restoration based on:
 * - Food type (meat, fruit, bread, potions, etc.)
 * - Tier level (1-7, affects base restoration amount)
 * - Type-specific multipliers (meat = high hunger, water = high thirst, etc.)
 * - Custom multipliers from modded consumables config (if configured)
 * 
 * **Restoration Formula:**
 * ```
 * restoration = baseTierValue * foodTypeMultiplier
 * // OR with custom multipliers:
 * restoration = baseTierValue * customMultiplier
 * ```
 * 
 * **Example:**
 * - Cooked Meat T3: base=25.0 * meat_hunger_mult=1.3 = 32.5 hunger
 * - Apple T2: base=15.0 * fruit_hunger_mult=0.9 = 13.5 hunger, base=6.0 * fruit_thirst_mult=1.5 = 9.0 thirst
 * - Water T2: base=0.0 * water_hunger_mult=0.0 = 0 hunger, base=6.0 * water_thirst_mult=3.0 = 18.0 thirst
 * - Custom T2 (hunger=1.5): base=15.0 * 1.5 = 22.5 hunger
 * 
 * @property metabolismService Service for updating player stats
 * @property config Food consumption configuration
 * @property logger Logger for debugging
 * @property moddedRegistry Optional registry for modded consumables (null = vanilla only)
 */
class FoodConsumptionProcessor(
    private val metabolismService: MetabolismService,
    private val config: FoodConsumptionConfig,
    private val logger: HytaleLogger,
    private val moddedRegistry: ModdedConsumablesRegistry? = null
) {
    
    /**
     * Process a detected food consumption and restore appropriate stats.
     * 
     * Checks modded registry for custom multipliers first, then falls back
     * to category-based defaults.
     * 
     * IMPORTANT: Must be called from world thread (already wrapped in world.execute in FoodDetectionTickSystem).
     * 
     * @param playerId The player's UUID
     * @param detection The detected food consumption (type, tier, effectId)
     * @param worldId World UUID (for config resolution)
     * @param entityRef Optional player entity reference (for immediate buff/debuff re-evaluation)
     * @param store Optional entity store (for immediate buff/debuff re-evaluation)
     */
    fun processConsumption(
        playerId: UUID, 
        detection: FoodDetection,
        worldId: UUID? = null,
        entityRef: com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>? = null,
        store: com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>? = null
    ) {
        // Check for custom multipliers from modded config
        val customMults = if (moddedRegistry != null && moddedRegistry.isEnabled()) {
            moddedRegistry.findByEffectId(detection.effectId)?.customMultipliers
        } else null
        
        // Get current stats BEFORE restoration (for accurate "actual applied" calculation)
        val statsBefore = metabolismService.getStats(playerId)
        
        // Calculate restoration amounts based on tier and food type (or custom multipliers)
        val hungerRestore = calculateHungerRestoration(detection.foodType, detection.tier, customMults?.hunger)
        val thirstRestore = calculateThirstRestoration(detection.foodType, detection.tier, customMults?.thirst)
        val energyRestore = calculateEnergyRestoration(detection.foodType, detection.tier, customMults?.energy)
        
        // Apply restoration to metabolism stats
        metabolismService.restoreStats(
            playerId = playerId,
            hunger = hungerRestore,
            thirst = thirstRestore,
            energy = energyRestore
        )
        
        // IMMEDIATELY trigger buff/debuff re-evaluation after food consumption
        // This prevents the 2-second delay before debuffs are removed (Fix #8)
        if (entityRef != null && store != null && worldId != null) {
            val worldContext = CoreModule.worlds.getContext(worldId)
            val metabolismConfig = worldContext?.metabolismConfig
            if (metabolismConfig != null) {
                metabolismService.triggerBuffDebuffReevaluation(
                    playerId, entityRef, store, metabolismConfig.buffs, metabolismConfig.debuffs
                )
            }
        }
        
        // IMMEDIATELY force HUD update after food consumption
        // This prevents the 2-second delay before HUD shows updated values (Fix #11)
        metabolismService.forceUpdateHud(playerId.toCachedString(), playerId)
        
        // Get current stats AFTER restoration (to calculate actual amounts applied)
        val statsAfter = metabolismService.getStats(playerId)
        
        // Calculate actual amounts applied (may be less than calculated due to max cap)
        val hungerActual = if (statsBefore != null && statsAfter != null) {
            (statsAfter.hunger - statsBefore.hunger).toDouble()
        } else hungerRestore
        
        val thirstActual = if (statsBefore != null && statsAfter != null) {
            (statsAfter.thirst - statsBefore.thirst).toDouble()
        } else thirstRestore
        
        val energyActual = if (statsBefore != null && statsAfter != null) {
            (statsAfter.energy - statsBefore.energy).toDouble()
        } else energyRestore
        
        // Log food consumption at INFO level (useful for server monitoring)
        val source = if (customMults != null) "custom" else "default"
        logger.atFine().log(
            "Food consumed [$source]: ${detection.effectId} " +
            "(type=${detection.foodType}, tier=${detection.tier}) -> " +
            "Calculated: H+%.2f, T+%.2f, E+%.2f | Actual: H+%.2f, T+%.2f, E+%.2f".format(
                hungerRestore, thirstRestore, energyRestore,
                hungerActual, thirstActual, energyActual
            )
        )
        
        // Only send chat message for ACTUAL food consumption, not for buff effects
        // Buff effects (Meat_Buff, HealthRegen_Buff, etc.) are applied BY the food
        // but shouldn't trigger their own consumption messages - they're for Phase 7 buff system
        if (config.showChatMessages && isPrimaryConsumable(detection.effectId)) {
            val foodName = formatFoodName(detection.effectId)
            // Show ACTUAL amounts applied (not calculated amounts that may exceed max)
            sendFoodConsumptionMessage(playerId, foodName, detection.tier, hungerActual, thirstActual, energyActual)
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
     * @param tier The food tier (1-7)
     * @param customMultiplier Optional custom multiplier from modded config (overrides default)
     * @return Hunger points to restore
     */
    private fun calculateHungerRestoration(foodType: FoodType, tier: Int, customMultiplier: Double? = null): Double {
        val base = HUNGER_BY_TIER.getOrElse(tier.coerceIn(1, 7)) { HUNGER_BY_TIER[3] }
        val multiplier = customMultiplier ?: (HUNGER_MULTIPLIERS[foodType] ?: 1.0)
        return base * multiplier
    }
    
    /**
     * Base hunger restoration by tier.
     * Index 0 unused, indices 1-7 represent tiers 1-7.
     * 
     * Hunger capacity:
     * - Base: 100 (no abilities)
     * - Max: 115 (with Iron Stomach T3 ability: +15)
     * 
     * Scaling designed for balanced gameplay across all progression stages:
     * - T1: 8.0 (8% of base, 7% of max) - Small snacks
     * - T2: 15.0 (15% of base, 13% of max) - Basic meals
     * - T3: 23.0 (23% of base, 20% of max) - Standard meals
     * - T4: 32.0 (32% of base, 28% of max) - Gourmet dishes
     * - T5: 42.0 (42% of base, 37% of max) - Premium cuisine
     * - T6: 53.0 (53% of base, 46% of max) - Exquisite feast
     * - T7: 65.0 (65% of base, 57% of max) - Legendary feast
     * 
     * Example with MEAT multiplier (1.3x):
     * - T7: 65.0 * 1.3 = 84.5 hunger
     *   → 84.5% of base capacity (without abilities)
     *   → 73% of max capacity (with Iron Stomach)
     */
    private val HUNGER_BY_TIER = doubleArrayOf(
        0.0,    // Unused
        8.0,    // Tier 1 - Small snacks
        15.0,   // Tier 2 - Basic meals
        23.0,   // Tier 3 - Standard meals
        32.0,   // Tier 4 - Gourmet dishes (Hidden's Harvest Delights)
        42.0,   // Tier 5 - Premium cuisine
        53.0,   // Tier 6 - Exquisite feast
        65.0    // Tier 7 - Legendary feast
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
     * @param tier The food tier (1-7)
     * @param customMultiplier Optional custom multiplier from modded config (overrides default)
     * @return Thirst points to restore
     */
    private fun calculateThirstRestoration(foodType: FoodType, tier: Int, customMultiplier: Double? = null): Double {
        val base = THIRST_BY_TIER.getOrElse(tier.coerceIn(1, 7)) { THIRST_BY_TIER[3] }
        val multiplier = customMultiplier ?: (THIRST_MULTIPLIERS[foodType] ?: 1.0)
        return base * multiplier
    }
    
    /**
     * Base thirst restoration by tier.
     * Index 0 unused, indices 1-7 represent tiers 1-7.
     * 
     * Thirst capacity:
     * - Base: 100 (no abilities)
     * - Max: 110 (with Desert Nomad T3 ability: +10)
     * 
     * Scaling designed for balanced hydration gameplay:
     * - T1: 3.0 (3% of base/max) - Small sips
     * - T2: 6.0 (6% of base, 5% of max) - Basic drinks
     * - T3: 10.0 (10% of base, 9% of max) - Standard drinks
     * - T4: 15.0 (15% of base, 14% of max) - Quality beverages
     * - T5: 21.0 (21% of base, 19% of max) - Premium drinks
     * - T6: 27.0 (27% of base, 25% of max) - Exquisite elixirs
     * - T7: 34.0 (34% of base, 31% of max) - Legendary nectar
     * 
     * Example with WATER multiplier (3.0x):
     * - T7: 34.0 * 3.0 = 102.0 thirst
     *   → 102% of base capacity (overfills without abilities)
     *   → 93% of max capacity (with Desert Nomad)
     */
    private val THIRST_BY_TIER = doubleArrayOf(
        0.0,    // Unused
        3.0,    // Tier 1 - Small sips
        6.0,    // Tier 2 - Basic drinks
        10.0,   // Tier 3 - Standard drinks
        15.0,   // Tier 4 - Quality beverages
        21.0,   // Tier 5 - Premium drinks
        27.0,   // Tier 6 - Exquisite elixirs
        34.0    // Tier 7 - Legendary nectar
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
     * @param tier The food tier (1-7)
     * @param customMultiplier Optional custom multiplier from modded config (overrides default)
     * @return Energy points to restore
     */
    private fun calculateEnergyRestoration(foodType: FoodType, tier: Int, customMultiplier: Double? = null): Double {
        val base = ENERGY_BY_TIER.getOrElse(tier.coerceIn(1, 7)) { ENERGY_BY_TIER[3] }
        val multiplier = customMultiplier ?: (ENERGY_MULTIPLIERS[foodType] ?: 1.0)
        return base * multiplier
    }
    
    /**
     * Base energy restoration by tier.
     * Index 0 unused, indices 1-7 represent tiers 1-7.
     * 
     * Energy capacity:
     * - Base: 100 (no abilities)
     * - Max: 110 (with Tireless Woodsman T3 ability: +10)
     * 
     * Scaling intentionally lower than hunger/thirst:
     * - T1: 5.0 (5% of base/max) - Light snacks
     * - T2: 10.0 (10% of base, 9% of max) - Basic meals
     * - T3: 16.0 (16% of base, 15% of max) - Standard meals
     * - T4: 23.0 (23% of base, 21% of max) - Hearty dishes
     * - T5: 31.0 (31% of base, 28% of max) - Energizing cuisine
     * - T6: 40.0 (40% of base, 36% of max) - Revitalizing feast
     * - T7: 50.0 (50% of base, 45% of max) - Legendary vitality
     * 
     * Design: Energy is harder to restore via food by design.
     * Primary recovery = idle regeneration (resting).
     * Food provides modest boost, not full restoration.
     */
    private val ENERGY_BY_TIER = doubleArrayOf(
        0.0,    // Unused
        5.0,    // Tier 1 - Light snacks
        10.0,   // Tier 2 - Basic meals
        16.0,   // Tier 3 - Standard meals
        23.0,   // Tier 4 - Hearty dishes
        31.0,   // Tier 5 - Energizing cuisine
        40.0,   // Tier 6 - Revitalizing feast
        50.0    // Tier 7 - Legendary vitality
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
