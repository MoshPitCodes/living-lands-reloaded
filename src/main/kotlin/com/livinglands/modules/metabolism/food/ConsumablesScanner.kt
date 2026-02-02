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
                val effectId = extractEffectId(item, logger)
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
                val namespace = extractNamespaceFromAsset(item, itemId, logger)
                
                discovered.add(DiscoveredConsumable(
                    itemId = itemId,
                    effectId = effectId,
                    category = category,
                    tier = tier,
                    namespace = namespace
                ))
                
                logger.atFine().log("Discovered: $namespace:$itemId -> $effectId (T$tier, $category)")
            }
            
            logger.atInfo().log(
                "Scan complete: $consumableCount consumables total, " +
                "$skippedCount already configured, " +
                "$noEffectCount without effects, " +
                "${discovered.size} new"
            )
            
            // Log namespace breakdown
            val byNamespace = discovered.groupBy { it.namespace }
            byNamespace.forEach { (namespace, items) ->
                logger.atInfo().log("  Namespace '$namespace': ${items.size} items")
            }
            
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to scan Item registry")
        }
        
        return discovered
    }
    
    /**
     * Extract effect ID from item's interactions.
     * 
     * Consumables use the "Secondary" interaction type (right-click consumption).
     * Examples: "Root_Secondary_Consume_Food_T3", "Root_Secondary_Consume_Potion"
     */
    private fun extractEffectId(item: Item, logger: HytaleLogger): String? {
        val interactions = item.interactions ?: return null
        
        // Consumable items use the "Secondary" interaction type (right-click to consume)
        val effectId = interactions[InteractionType.Secondary]
        
        // Skip placeholder/template values
        if (effectId != null && (effectId.startsWith("*") || effectId.isBlank())) {
            logger.atFine().log("Item ${item.id} has placeholder/template secondary interaction: $effectId")
            return null  // Treat as no real effect
        }
        
        return effectId
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
     * Infer tier level from effect ID pattern.
     * 
     * Uses ItemTierDetector to extract tier from patterns like:
     * - "Food_Instant_Heal_T4" -> Tier 4
     * - "Food_Regen_T7" -> Tier 7
     * - "Food_Small_Heal" -> Tier 1 (default)
     * 
     * @param effectId The effect ID to analyze
     * @return Tier level (1-7)
     */
    private fun inferTier(effectId: String): Int {
        return ItemTierDetector.detectTier(effectId)
    }
    
    /**
     * Extract namespace/mod identifier from item's asset pack.
     * 
     * Uses AssetMap.getAssetPack() to get the pack name, then extracts the mod group
     * from the PluginManager's manifest registry.
     * 
     * **Examples:**
     * - Asset from "NoCube:[NoCube's] Bakehouse" -> "NoCube"
     * - Asset from "ChampionsVandal:More Potions" -> "ChampionsVandal"
     * - Asset from "Hytale:Hytale" -> "Hytale"
     * 
     * @param item The item asset
     * @param itemId The item ID (used to query asset pack)
     * @param logger Logger for debug output
     * @return Namespace/mod identifier
     */
    private fun extractNamespaceFromAsset(item: Item, itemId: String, logger: HytaleLogger): String {
        try {
            // Get the asset pack name from AssetMap
            val itemStore = Item.getAssetStore()
            val packName = itemStore.assetMap.getAssetPack(itemId)
            
            if (packName != null) {
                // Pack name format is typically "ModGroup:PackName"
                // Extract just the mod group (first part before colon)
                val modGroup = if (packName.contains(":")) {
                    packName.substringBefore(":")
                } else {
                    packName
                }
                
                logger.atFine().log("Extracted namespace from asset pack: $itemId -> $modGroup (pack: $packName)")
                return modGroup
            }
        } catch (e: Exception) {
            logger.atFine().log("Failed to extract namespace from asset pack for $itemId: ${e.message}")
        }
        
        // Fallback: Parse item ID for namespace hints
        return extractNamespaceFromItemId(itemId)
    }
    
    /**
     * Fallback: Extract namespace from item ID patterns.
     * Used when asset data is unavailable.
     */
    private fun extractNamespaceFromItemId(itemId: String): String {
        // Remove state prefix if present
        val cleanId = if (itemId.startsWith("*")) itemId.substring(1) else itemId
        
        // Check for colon separator (e.g., "ModName:ItemId")
        if (cleanId.contains(":")) {
            return cleanId.substringBefore(":")
        }
        
        // Check for underscore prefix patterns (e.g., "NoCube_Food_Brioche")
        val parts = cleanId.split("_")
        if (parts.size >= 2) {
            val potentialModPrefix = parts[0]
            
            // Known vanilla categories
            val vanillaCategories = setOf(
                "Food", "Potion", "Plant", "Recipe", "Bandage", "Antidote",
                "Container", "Deco", "Template", "Halloween"
            )
            
            if (potentialModPrefix !in vanillaCategories) {
                return potentialModPrefix
            }
        }
        
        // No mod prefix detected = vanilla
        return "Hytale"
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
     * Namespace/mod identifier (e.g., "Hytale", "CoolMod").
     */
    val namespace: String,
    
    /**
     * Inferred food category (MEAT, WATER, BREAD, etc.).
     */
    val category: String,
    
    /**
     * Inferred tier (1-7).
     */
    val tier: Int
)
