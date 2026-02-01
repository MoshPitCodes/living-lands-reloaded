package com.livinglands.modules.metabolism.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.livinglands.modules.metabolism.food.modded.ItemTierDetector.detectTier

/**
 * Configuration for modded consumables support.
 * 
 * Allows server admins to configure custom food/drink/potion items
 * from other mods with tier-based restoration values.
 * 
 * **Example Config:**
 * ```yaml
 * moddedConsumables:
 *   enabled: true
 *   warnIfMissing: true
 *   mods:
 *     HiddenHarvest:
 *       displayName: "Hidden's Harvest Delights"
 *       enabled: true
 *       consumables:
 *         - effectId: "Food_Instant_Heal_T7"
 *           category: "MEAT"
 *           tier: 7  # auto-detected from _T7
 *     NoCubeBakehouse:
 *       displayName: "NoCube's Bakehouse"
 *       enabled: true
 *       consumables:
 *         - effectId: "Food_Health_Restore"
 *           category: "WATER"
 *           tier: 1  # auto-detected (default)
 * ```
 */
data class ModdedConsumablesConfig(
    /**
     * Whether modded consumables support is enabled.
     * If false, only vanilla Hytale items will be processed.
     */
    val enabled: Boolean = false,
    
    /**
     * Log warnings when configured items are not found in the game registry.
     * Useful for detecting typos or missing mods.
     */
    val warnIfMissing: Boolean = true,
    
    /**
     * Modded consumables organized by mod.
     * Key = mod identifier (e.g., "HiddenHarvest", "NoCubeBakehouse")
     * Value = Mod configuration with consumables
     */
    val mods: Map<String, ModConfig> = emptyMap()
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(enabled = false)
    
    /**
     * Get all configured entries across all mods.
     * Only includes entries from enabled mods.
     * Excluded from JSON serialization.
     */
    @JsonIgnore
    fun getAllEntries(): List<ModdedConsumableEntry> {
        return mods.values
            .filter { it.enabled }
            .flatMap { it.consumables }
    }
    
    /**
     * Get the total count of configured entries from enabled mods.
     * Excluded from JSON serialization.
     */
    @JsonIgnore
    fun getEntryCount(): Int {
        return mods.values
            .filter { it.enabled }
            .sumOf { it.consumables.size }
    }
    
    companion object {
        /**
         * Example configuration with popular Hytale mods.
         * Server admins can enable/disable individual mods.
         * 
         * **Included Mods:**
         * - Hidden's Harvest Delights (44 items)
         * - NoCube's Bakehouse + Tavern + Orchard (48 items)
         * 
         * **Total:** 92 consumable items from 2 major food mods
         */
        fun getDefaultExampleConfig(): ModdedConsumablesConfig {
            return ModdedConsumablesConfig(
                enabled = true,  // Enabled by default with pre-configured popular mods
                warnIfMissing = true,
                
                mods = mapOf(
                    "HiddenHarvest" to ModConfig(
                        displayName = "Hidden's Harvest Delights",
                        enabled = true,
                        consumables = listOf(
                            // High-tier gourmet meals (T4-T7) - tiers auto-detected
                            // Note: itemId is optional reference - system detects by effectId
                            ModdedConsumableEntry("Food_Instant_Heal_T7", "MEAT", detectTier("Food_Instant_Heal_T7"), "HiddenHarvest:Steak_Dinner"),
                            ModdedConsumableEntry("Food_Instant_Heal_T6", "MEAT", detectTier("Food_Instant_Heal_T6"), "HiddenHarvest:Chicken_Buttered"),
                            ModdedConsumableEntry("Food_Instant_Heal_T5", "MEAT", detectTier("Food_Instant_Heal_T5"), "HiddenHarvest:Beef_Well_Done"),
                            ModdedConsumableEntry("Food_Instant_Heal_T5", "GENERIC", detectTier("Food_Instant_Heal_T5"), "HiddenHarvest:Fajita_Skillet"),
                            ModdedConsumableEntry("Food_Instant_Heal_T4", "MEAT", detectTier("Food_Instant_Heal_T4"), "HiddenHarvest:Fish_Tacos"),
                            ModdedConsumableEntry("Food_Instant_Heal_T4", "GENERIC", detectTier("Food_Instant_Heal_T4"), "HiddenHarvest:Birthday_Cake"),
                            ModdedConsumableEntry("Food_Instant_Heal_T4", "BREAD", detectTier("Food_Instant_Heal_T4"), "HiddenHarvest:Pizza_Flatbread"),
                            ModdedConsumableEntry("Food_Instant_Heal_T3", "MEAT", detectTier("Food_Instant_Heal_T3"), "HiddenHarvest:Kebab_Beef"),
                            ModdedConsumableEntry("Food_Instant_Heal_T3", "GENERIC", detectTier("Food_Instant_Heal_T3"), "HiddenHarvest:Hot_Dogs"),
                            ModdedConsumableEntry("Food_Instant_Heal_T2", "MEAT", detectTier("Food_Instant_Heal_T2"), "HiddenHarvest:Berry_Glazed_Cod"),
                        )
                    ),
                    
                    "NoCubeBakehouse" to ModConfig(
                        displayName = "NoCube's Bakehouse + Tavern + Orchard",
                        enabled = true,
                        consumables = listOf(
                            // Breads (auto-detect tier, default T1)
                            ModdedConsumableEntry("NoCube_Peasant_Bread_Buff", "BREAD", detectTier("NoCube_Peasant_Bread_Buff"), "NoCubeBakehouse:Peasant_Bread"),
                            ModdedConsumableEntry("NoCube_Baguette_Buff", "BREAD", detectTier("NoCube_Baguette_Buff"), "NoCubeBakehouse:Baguette"),
                            ModdedConsumableEntry("NoCube_Brioche_Buff", "BREAD", detectTier("NoCube_Brioche_Buff"), "NoCubeBakehouse:Brioche"),
                            ModdedConsumableEntry("NoCube_Bun_Buff", "BREAD", detectTier("NoCube_Bun_Buff"), "NoCubeBakehouse:Bun"),
                            ModdedConsumableEntry("NoCube_Carrot_Bread_Buff", "BREAD", detectTier("NoCube_Carrot_Bread_Buff"), "NoCubeBakehouse:Carrot_Bread"),
                            ModdedConsumableEntry("NoCube_Coconut_Bread_Buff", "BREAD", detectTier("NoCube_Coconut_Bread_Buff"), "NoCubeBakehouse:Coconut_Bread"),
                            ModdedConsumableEntry("NoCube_Corn_Bread_Buff", "BREAD", detectTier("NoCube_Corn_Bread_Buff"), "NoCubeBakehouse:Corn_Bread"),
                            ModdedConsumableEntry("NoCube_Damper_Buff", "BREAD", detectTier("NoCube_Damper_Buff"), "NoCubeBakehouse:Damper"),
                            ModdedConsumableEntry("NoCube_Fruitloaf_Buff", "BREAD", detectTier("NoCube_Fruitloaf_Buff"), "NoCubeBakehouse:Fruitloaf"),
                            ModdedConsumableEntry("NoCube_Glazed_Rum_Scone_Buff", "BREAD", detectTier("NoCube_Glazed_Rum_Scone_Buff"), "NoCubeBakehouse:Glazed_Rum_Scone"),
                            ModdedConsumableEntry("NoCube_Lavash_Buff", "BREAD", detectTier("NoCube_Lavash_Buff"), "NoCubeBakehouse:Lavash"),
                            ModdedConsumableEntry("NoCube_Malt_Loaf_Buff", "BREAD", detectTier("NoCube_Malt_Loaf_Buff"), "NoCubeBakehouse:Malt_Loaf"),
                            ModdedConsumableEntry("NoCube_Pumpernickel_Buff", "BREAD", detectTier("NoCube_Pumpernickel_Buff"), "NoCubeBakehouse:Pumpernickel"),
                            ModdedConsumableEntry("NoCube_Rye_Bread_Buff", "BREAD", detectTier("NoCube_Rye_Bread_Buff"), "NoCubeBakehouse:Rye_Bread"),
                            ModdedConsumableEntry("NoCube_Seedy_Loaf_Buff", "BREAD", detectTier("NoCube_Seedy_Loaf_Buff"), "NoCubeBakehouse:Seedy_Loaf"),
                            ModdedConsumableEntry("NoCube_Sourdough_Loaf_Buff", "BREAD", detectTier("NoCube_Sourdough_Loaf_Buff"), "NoCubeBakehouse:Sourdough_Loaf"),
                            ModdedConsumableEntry("NoCube_Spelt_Bread_Buff", "BREAD", detectTier("NoCube_Spelt_Bread_Buff"), "NoCubeBakehouse:Spelt_Bread"),
                            ModdedConsumableEntry("NoCube_Sweet_Corn_Buff", "BREAD", detectTier("NoCube_Sweet_Corn_Buff"), "NoCubeBakehouse:Sweet_Corn"),
                            ModdedConsumableEntry("NoCube_Sweet_Potato_Bread_Buff", "BREAD", detectTier("NoCube_Sweet_Potato_Bread_Buff"), "NoCubeBakehouse:Sweet_Potato_Bread"),
                            ModdedConsumableEntry("NoCube_Tabatiere_Buff", "BREAD", detectTier("NoCube_Tabatiere_Buff"), "NoCubeBakehouse:Tabatiere"),
                            ModdedConsumableEntry("NoCube_Wheat_Bread_Buff", "BREAD", detectTier("NoCube_Wheat_Bread_Buff"), "NoCubeBakehouse:Wheat_Bread"),
                            ModdedConsumableEntry("NoCube_White_Bread_Buff", "BREAD", detectTier("NoCube_White_Bread_Buff"), "NoCubeBakehouse:White_Bread"),
                            ModdedConsumableEntry("NoCube_Whole_Wheat_Bread_Buff", "BREAD", detectTier("NoCube_Whole_Wheat_Bread_Buff"), "NoCubeBakehouse:Whole_Wheat_Bread"),
                            ModdedConsumableEntry("NoCube_Zucchini_Bread_Buff", "BREAD", detectTier("NoCube_Zucchini_Bread_Buff"), "NoCubeBakehouse:Zucchini_Bread"),
                            
                            // Tavern drinks
                            ModdedConsumableEntry("NoCube_Absinth_Buff", "WATER", detectTier("NoCube_Absinth_Buff"), "NoCubeTavern:Absinth"),
                            ModdedConsumableEntry("NoCube_Ale_Buff", "WATER", detectTier("NoCube_Ale_Buff"), "NoCubeTavern:Ale"),
                            ModdedConsumableEntry("NoCube_Beer_Buff", "WATER", detectTier("NoCube_Beer_Buff"), "NoCubeTavern:Beer"),
                            ModdedConsumableEntry("NoCube_Brandy_Buff", "WATER", detectTier("NoCube_Brandy_Buff"), "NoCubeTavern:Brandy"),
                            ModdedConsumableEntry("NoCube_Cider_Buff", "WATER", detectTier("NoCube_Cider_Buff"), "NoCubeTavern:Cider"),
                            ModdedConsumableEntry("NoCube_Mead_Buff", "WATER", detectTier("NoCube_Mead_Buff"), "NoCubeTavern:Mead"),
                            ModdedConsumableEntry("NoCube_Red_Wine_Buff", "WATER", detectTier("NoCube_Red_Wine_Buff"), "NoCubeTavern:Red_Wine"),
                            ModdedConsumableEntry("NoCube_Rum_Buff", "WATER", detectTier("NoCube_Rum_Buff"), "NoCubeTavern:Rum"),
                            ModdedConsumableEntry("NoCube_Vodka_Buff", "WATER", detectTier("NoCube_Vodka_Buff"), "NoCubeTavern:Vodka"),
                            ModdedConsumableEntry("NoCube_Whiskey_Buff", "WATER", detectTier("NoCube_Whiskey_Buff"), "NoCubeTavern:Whiskey"),
                            ModdedConsumableEntry("NoCube_White_Wine_Buff", "WATER", detectTier("NoCube_White_Wine_Buff"), "NoCubeTavern:White_Wine"),
                            ModdedConsumableEntry("NoCube_Wine_Buff", "WATER", detectTier("NoCube_Wine_Buff"), "NoCubeTavern:Wine"),
                            
                            // Orchard fruits (auto-detect from buff names)
                            ModdedConsumableEntry("NoCube_Apple_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Apple_Buff"), "NoCubeOrchard:Apple"),
                            ModdedConsumableEntry("NoCube_Banana_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Banana_Buff"), "NoCubeOrchard:Banana"),
                            ModdedConsumableEntry("NoCube_Blackberry_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Blackberry_Buff"), "NoCubeOrchard:Blackberry"),
                            ModdedConsumableEntry("NoCube_Blueberry_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Blueberry_Buff"), "NoCubeOrchard:Blueberry"),
                            ModdedConsumableEntry("NoCube_Cherry_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Cherry_Buff"), "NoCubeOrchard:Cherry"),
                            ModdedConsumableEntry("NoCube_Coconut_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Coconut_Buff"), "NoCubeOrchard:Coconut"),
                            ModdedConsumableEntry("NoCube_Cranberry_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Cranberry_Buff"), "NoCubeOrchard:Cranberry"),
                            ModdedConsumableEntry("NoCube_Grape_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Grape_Buff"), "NoCubeOrchard:Grape"),
                            ModdedConsumableEntry("NoCube_Orange_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Orange_Buff"), "NoCubeOrchard:Orange"),
                            ModdedConsumableEntry("NoCube_Peach_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Peach_Buff"), "NoCubeOrchard:Peach"),
                            ModdedConsumableEntry("NoCube_Pear_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Pear_Buff"), "NoCubeOrchard:Pear"),
                            ModdedConsumableEntry("NoCube_Plum_Buff", "FRUIT_VEGGIE", detectTier("NoCube_Plum_Buff"), "NoCubeOrchard:Plum"),
                        )
                    )
                )
            )
        }
    }
}

/**
 * Configuration for a single mod's consumables.
 */
data class ModConfig(
    /**
     * Human-readable mod name for display.
     */
    val displayName: String = "",
    
    /**
     * Whether this mod's consumables are enabled.
     * Allows disabling a mod without removing config.
     */
    val enabled: Boolean = true,
    
    /**
     * List of consumable items from this mod.
     */
    val consumables: List<ModdedConsumableEntry> = emptyList()
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(displayName = "")
}

/**
 * Individual modded consumable entry.
 * 
 * Note: Null fields (like customMultipliers) are automatically excluded from
 * serialization by ConfigManager's global NON_NULL setting.
 */
data class ModdedConsumableEntry(
    /**
     * Effect ID from the mod (e.g., "FarmingMod:CookedChicken_Buff").
     * Must match the exact effect ID in the Hytale registry.
     */
    val effectId: String,
    
    /**
     * Food category for restoration multipliers.
     * Options: MEAT, FRUIT_VEGGIE, BREAD, WATER, MILK, HEALTH_POTION, MANA_POTION, STAMINA_POTION, GENERIC
     */
    val category: String,
    
    /**
     * Tier level (1-7) for base restoration values.
     * Auto-detected from effect ID if not specified.
     * Higher tiers restore more stats.
     */
    val tier: Int = 1,
    
    /**
     * Optional item ID that triggers this effect (e.g., "HiddenHarvest:Steak_Dinner").
     * Useful for reference when editing config.
     * If null, only effectId is used for detection.
     * Excluded from serialization when null.
     */
    val itemId: String? = null,
    
    /**
     * Optional custom multipliers to override category defaults.
     * If null, uses category-based defaults.
     * Excluded from serialization when null.
     */
    val customMultipliers: CustomMultipliers? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(effectId = "", category = "GENERIC", tier = 1)
    
    /**
     * Get the effective tier (manual override or auto-detected).
     * Ensures tier is always within valid range (1-7).
     * Excluded from JSON serialization.
     */
    @JsonIgnore
    fun getEffectiveTier(): Int {
        return tier.coerceIn(1, 7)
    }
    
    /**
     * Validate that this entry has required fields.
     * Excluded from JSON serialization.
     */
    @JsonIgnore
    fun isValid(): Boolean {
        return effectId.isNotBlank() && category.isNotBlank()
    }
}

/**
 * Custom multipliers for hunger/thirst/energy restoration.
 * If a multiplier is null, the category default is used.
 * 
 * Note: Null fields are automatically excluded from serialization by
 * ConfigManager's global NON_NULL setting.
 */
data class CustomMultipliers(
    /**
     * Hunger restoration multiplier (e.g., 1.5 = 50% more hunger).
     * If null, uses category default (e.g., MEAT = 1.3).
     */
    val hunger: Double? = null,
    
    /**
     * Thirst restoration multiplier (e.g., 2.0 = double thirst).
     * If null, uses category default (e.g., WATER = 3.0).
     */
    val thirst: Double? = null,
    
    /**
     * Energy restoration multiplier (e.g., 1.2 = 20% more energy).
     * If null, uses category default.
     */
    val energy: Double? = null
) {
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(hunger = null)
}
