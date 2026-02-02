package com.livinglands.modules.metabolism.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.livinglands.core.config.VersionedConfig

/**
 * Separate configuration for modded consumables support.
 * 
 * This config is stored in `metabolism_consumables.yml` and automatically
 * populated by scanning the Item registry on first startup.
 * 
 * **Auto-Scan Behavior:**
 * - On first startup (when config is empty), LLR scans the Item registry
 * - All consumable items are discovered and categorized automatically
 * - Config is populated with discovered items under "AutoDiscovered_YYYY-MM-DD" section
 * - Subsequent startups skip auto-scan (config already populated)
 * 
 * **Manual Scan:**
 * - Admins can run `/ll scan consumables --save` to discover new items after installing mods
 * - New items are added to a separate section (e.g., "ManualScan_YYYY-MM-DD")
 * 
 * **Example Config:**
 * ```yaml
 * configVersion: 1
 * enabled: true
 * warnIfMissing: true
 * 
 * consumables:
 *   AutoDiscovered_2026-02-02:
 *     - effectId: "Food_Instant_Heal_T1"
 *       category: "MEAT"
 *       tier: 1
 *       itemId: "Hytale:Cooked_Meat"
 *     - effectId: "Food_Restore_T1"
 *       category: "FRUIT_VEGGIE"
 *       tier: 1
 *       itemId: "Hytale:Apple"
 * 
 *   ManualScan_2026-02-10:
 *     - effectId: "CoolMod:Burger_Effect"
 *       category: "MEAT"
 *       tier: 4
 *       itemId: "CoolMod:Burger_Deluxe"
 * ```
 */
data class MetabolismConsumablesConfig(
    /**
     * Config version for migration tracking.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /**
     * Whether modded consumables support is enabled.
     * Auto-enabled on first scan.
     */
    val enabled: Boolean = false,
    
    /**
     * Log warnings when configured items are not found in the game registry.
     * Useful for detecting typos or missing mods.
     */
    val warnIfMissing: Boolean = true,
    
    /**
     * Discovered consumables organized by scan date or custom section name.
     * 
     * Key = section name (e.g., "AutoDiscovered_2026-02-02", "ManualScan_2026-02-10", "MyCustomMod")
     * Value = List of consumable entries
     * 
     * Sections are created by:
     * - Auto-scan on startup: "AutoDiscovered_YYYY-MM-DD"
     * - Manual scan: "ManualScan_YYYY-MM-DD"
     * - Custom section: `/ll scan consumables --save --section MyCustomMod`
     */
    val consumables: Map<String, List<ModdedConsumableEntry>> = emptyMap()
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION)
    
    companion object {
        const val MODULE_ID = "metabolism"
        const val CONFIG_NAME = "metabolism_consumables"
        const val CURRENT_VERSION = 1
    }
    
    /**
     * Get all configured entries across all sections (flat list).
     * Only used for lookup, not serialization.
     */
    @JsonIgnore
    fun getAllEntries(): List<ModdedConsumableEntry> {
        return consumables.values.flatten()
    }
    
    /**
     * Get the total count of configured entries.
     */
    @JsonIgnore
    fun getEntryCount(): Int {
        return consumables.values.sumOf { it.size }
    }
    
    /**
     * Check if this config is empty (no consumables configured).
     * Used to determine if auto-scan should run.
     */
    @JsonIgnore
    fun isEmpty(): Boolean {
        return consumables.isEmpty() || getEntryCount() == 0
    }
}

/**
 * Individual modded consumable entry.
 * 
 * Simplified structure (no nested ModConfig) for easier management.
 * 
 * Note: Null fields (like customMultipliers) are automatically excluded from
 * serialization by ConfigManager's global NON_NULL setting.
 */
data class ModdedConsumableEntry(
    /**
     * Effect ID from the item (e.g., "Food_Instant_Heal_T4", "CoolMod:Burger_Effect").
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
     * Optional item ID that triggers this effect (e.g., "Hytale:Cooked_Meat", "CoolMod:Burger_Deluxe").
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
