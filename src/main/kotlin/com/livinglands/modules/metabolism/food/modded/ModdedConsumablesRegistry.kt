package com.livinglands.modules.metabolism.food.modded

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.metabolism.config.ModdedConsumableEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for modded consumable items.
 * 
 * Provides fast lookup of configured modded items by their effect ID.
 * Used by FoodEffectDetector to identify consumables from other mods.
 * 
 * **Thread Safety:**
 * - Uses ConcurrentHashMap for thread-safe lookups
 * - Registry is populated on construction and config reload
 * 
 * **Performance:**
 * - Lookup: O(1) via ConcurrentHashMap
 * - Target: < 0.1ms per lookup
 */
class ModdedConsumablesRegistry(
    entries: List<ModdedConsumableEntry>,
    private val logger: HytaleLogger
) {
    /**
     * Registry of modded consumable entries.
     * Key: effectId (lowercase for case-insensitive matching)
     * Value: The configured entry with tier and category information
     */
    private val registry = ConcurrentHashMap<String, ModdedConsumableEntry>()
    
    /**
     * Track if registry is enabled (has entries)
     */
    private var enabled: Boolean = false
    
    init {
        loadEntries(entries)
    }
    
    /**
     * Load all entries into the registry.
     */
    private fun loadEntries(entries: List<ModdedConsumableEntry>) {
        registry.clear()
        
        var validCount = 0
        entries.forEach { entry ->
            if (entry.isValid()) {
                registry[entry.effectId.lowercase()] = entry
                validCount++
            }
        }
        
        enabled = validCount > 0
        
        if (validCount > 0) {
            LoggingManager.config(logger, "metabolism") {
                "Loaded $validCount modded consumables into registry"
            }
        }
    }
    
    /**
     * Find a modded consumable entry by its effect ID.
     * 
     * The search is case-insensitive. Returns null if:
     * - Registry is empty/disabled
     * - The effect ID is not configured
     * 
     * @param effectId The effect ID to look up (e.g., "FarmingMod:CookedChicken")
     * @return The configured entry, or null if not found
     */
    fun findByEffectId(effectId: String): ModdedConsumableEntry? {
        if (!enabled || effectId.isBlank()) {
            return null
        }
        
        return registry[effectId.lowercase()]
    }
    
    /**
     * Check if modded consumables support is enabled.
     * 
     * @return True if registry has entries
     */
    fun isEnabled(): Boolean = enabled
    
    /**
     * Check if the registry has any entries.
     * 
     * @return True if at least one entry is registered
     */
    fun hasEntries(): Boolean = registry.isNotEmpty()
    
    /**
     * Get the number of registered entries.
     */
    fun getEntryCount(): Int = registry.size
    
    /**
     * Clear the registry.
     * Called during config reload.
     */
    fun clear() {
        registry.clear()
        enabled = false
    }
    
    /**
     * Reload the registry with new entries.
     * 
     * @param newEntries The new list of entries to load
     */
    fun reload(newEntries: List<ModdedConsumableEntry>) {
        clear()
        loadEntries(newEntries)
        
        if (registry.isNotEmpty()) {
            LoggingManager.config(logger, "metabolism") {
                "Reloaded ${registry.size} modded consumables"
            }
        } else {
            LoggingManager.config(logger, "metabolism") {
                "Modded consumables registry is empty after reload"
            }
        }
    }
    
    /**
     * Get all registered effect IDs.
     * Useful for debugging and diagnostics.
     * 
     * @return Set of registered effect IDs (lowercase)
     */
    fun getAllEffectIds(): Set<String> = registry.keys.toSet()
}
