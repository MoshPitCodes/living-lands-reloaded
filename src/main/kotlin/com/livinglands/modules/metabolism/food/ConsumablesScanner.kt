package com.livinglands.modules.metabolism.food

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.asset.type.item.config.Item
import com.livinglands.modules.metabolism.food.modded.ItemTierDetector

/**
 * Scans the Item registry for consumable items.
 * 
 * Shared logic used by both:
 * - Auto-scan on first startup
 * - Manual `/ll scan consumables` command
 * 
 * **Detection Strategy:**
 * 1. Scan all items in Item AssetStore
 * 2. Filter for consumable items (`item.isConsumable`)
 * 3. Extract effect ID from CONSUME interaction
 * 4. Skip items already in config (via excludeEffects set)
 * 5. Infer category and tier from item properties
 * 
 * **Performance:**
 * - O(n) where n = total items in registry (typically 1000-3000)
 * - Most items filtered out early (isConsumable check)
 * - Actual consumables usually 20-100 items
 */
object ConsumablesScanner {
    
    /**
     * Scan Item registry for consumables.
     * 
     * @param excludeEffects Effect IDs to skip (already configured)
     * @param logger Logger for debug output
     * @return List of discovered consumables
     */
    fun scanItemRegistry(
        excludeEffects: Set<String>,
        logger: HytaleLogger
    ): List<DiscoveredConsumable> {
        val discovered = mutableListOf<DiscoveredConsumable>()
        
        try {
            // Get Item AssetStore
            val itemStore = Item.getAssetStore()
            val allItems = itemStore.assetMap.assetMap
            
            logger.atFine().log("Scanning ${allItems.size} items for consumables...")
            
            var consumableCount = 0
            var skippedCount = 0
            var noEffectCount = 0
            
            allItems.forEach { (itemId, item) ->
                // Only check consumable items
                if (!item.isConsumable) return@forEach
                consumableCount++
                
                // Get consumption effect ID
                val effectId = extractEffectId(item)
                if (effectId == null) {
                    logger.atFine().log("Consumable $itemId has no effect")
                    noEffectCount++
                    return@forEach
                }
                
                // Skip if already configured
                if (effectId in excludeEffects) {
                    logger.atFine().log("Skipping already configured: $itemId -> $effectId")
                    skippedCount++
                    return@forEach
                }
                
                // Discover!
                val tier = inferTier(effectId)
                val category = inferCategory(item, effectId)
                
                discovered.add(DiscoveredConsumable(
                    itemId = itemId,
                    effectId = effectId,
                    category = category,
                    tier = tier
                ))
                
                logger.atFine().log("Discovered: $itemId -> $effectId (T$tier, $category)")
            }
            
            logger.atFine().log(
                "Scan complete: $consumableCount consumables total, " +
                "$skippedCount already configured, " +
                "$noEffectCount without effects, " +
                "${discovered.size} new"
            )
            
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to scan Item registry")
        }
        
        return discovered
    }
    
    /**
     * Extract effect ID from item's interactions.
     * 
     * Consumables typically use the "Use" interaction type.
     */
    private fun extractEffectId(item: Item): String? {
        val interactions = item.interactions ?: return null
        
        // Consumable items use the "Use" interaction type
        return interactions[InteractionType.Use]
    }
    
    /**
     * Infer food category from item name, categories, and effect ID.
     * 
     * Maps to existing FoodConsumptionConfig categories:
     * - MEAT
     * - FRUIT_VEGGIE
     * - BREAD
     * - WATER
     * - MILK
     * - HEALTH_POTION
     * - MANA_POTION
     * - STAMINA_POTION
     * - GENERIC (fallback)
     * 
     * **Detection Strategy:**
     * - Combine item ID, categories array, and effect ID into searchable string
     * - Use regex patterns to match common food keywords
     * - Return most specific match, fallback to GENERIC
     */
    private fun inferCategory(item: Item, effectId: String): String {
        // Combine all available text for pattern matching
        val itemIdLower = item.id.lowercase()
        val categoriesLower = item.categories?.joinToString(" ") { it.lowercase() } ?: ""
        val effectIdLower = effectId.lowercase()
        val combined = "$itemIdLower $categoriesLower $effectIdLower"
        
        return when {
            // MEAT - Animal products, cooked meats
            combined.contains(Regex("meat|burger|steak|chicken|beef|pork|bacon|fish|sausage|ham")) -> "MEAT"
            
            // WATER - Drinks, beverages, alcoholic
            combined.contains(Regex("water|juice|tea|coffee|drink|beverage|ale|beer|wine|mead|liquor|rum|vodka|whiskey|brandy|cider")) -> "WATER"
            
            // MILK - Dairy products
            combined.contains(Regex("milk|cream|dairy|cheese|yogurt")) -> "MILK"
            
            // FRUIT_VEGGIE - Fruits and vegetables
            combined.contains(Regex("apple|berry|fruit|banana|orange|grape|pear|peach|plum|cherry|veggie|vegetable|carrot|potato|tomato")) -> "FRUIT_VEGGIE"
            
            // BREAD - Baked goods
            combined.contains(Regex("bread|bun|roll|loaf|baguette|pastry|bake|cake|cookie|pie|scone|brioche|damper")) -> "BREAD"
            
            // HEALTH_POTION - Healing items
            combined.contains(Regex("health|heal|restoration|cure|remedy")) -> "HEALTH_POTION"
            
            // MANA_POTION - Magic restoration
            combined.contains(Regex("mana|magic|arcane|mystical")) -> "MANA_POTION"
            
            // STAMINA_POTION - Energy/stamina restoration
            combined.contains(Regex("stamina|energy|vigor|vitality|endurance")) -> "STAMINA_POTION"
            
            // GENERIC - Fallback for unrecognized items
            else -> "GENERIC"
        }
    }
    
    /**
     * Infer tier from effect ID using existing ItemTierDetector.
     * 
     * Detects patterns like:
     * - "Food_Instant_Heal_T4" -> Tier 4
     * - "Food_Regen_T7" -> Tier 7
     * - "Food_Small_Heal" -> Tier 1 (default)
     * 
     * @return Tier between 1-7 (inclusive)
     */
    private fun inferTier(effectId: String): Int {
        return ItemTierDetector.detectTier(effectId)
    }
}

/**
 * Temporary data class for discovered consumables.
 * Used to transport scan results before converting to ModdedConsumableEntry.
 */
data class DiscoveredConsumable(
    /**
     * Item ID from the registry (e.g., "Hytale:Cooked_Meat", "CoolMod:Burger_Deluxe").
     */
    val itemId: String,
    
    /**
     * Effect ID applied when consuming (e.g., "Food_Instant_Heal_T4").
     */
    val effectId: String,
    
    /**
     * Inferred food category (MEAT, WATER, BREAD, etc.).
     */
    val category: String,
    
    /**
     * Inferred tier (1-7).
     */
    val tier: Int
)
