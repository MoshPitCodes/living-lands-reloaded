package com.livinglands.modules.metabolism.food.modded

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.metabolism.config.ModdedConsumableEntry
import com.livinglands.modules.metabolism.config.ModdedConsumablesConfig
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
    private val config: ModdedConsumablesConfig,
    private val logger: HytaleLogger
) {
    /**
     * Registry of modded consumable entries.
     * Key: effectId (lowercase for case-insensitive matching)
     * Value: The configured entry with tier and category information
     */
    private val registry = ConcurrentHashMap<String, ModdedConsumableEntry>()
    
    /**
     * Statistics about loaded entries per mod
     */
    private val loadedPerMod = ConcurrentHashMap<String, Int>()
    
    init {
        if (config.enabled) {
            loadEntries()
        }
    }
    
    /**
     * Load all entries from config into the registry.
     */
    private fun loadEntries() {
        loadedPerMod.clear()
        
        // Load entries from each mod
        config.mods.forEach { (modId, modConfig) ->
            if (modConfig.enabled) {
                var modLoadedCount = 0
                
                modConfig.consumables.forEach { entry ->
                    if (entry.isValid()) {
                        registry[entry.effectId.lowercase()] = entry
                        modLoadedCount++
                    }
                }
                
                if (modLoadedCount > 0) {
                    loadedPerMod[modId] = modLoadedCount
                }
            }
        }
        
        val total = loadedPerMod.values.sum()
        if (total > 0) {
            val modsSummary = loadedPerMod.entries
                .joinToString(", ") { (modId, count) -> "$modId: $count" }
            
            LoggingManager.config(logger, "metabolism") {
                "Loaded $total modded consumables from ${loadedPerMod.size} mods ($modsSummary)"
            }
        }
    }
    
    /**
     * Find a modded consumable entry by its effect ID.
     * 
     * The search is case-insensitive. Returns null if:
     * - Modded consumables are disabled
     * - The effect ID is not configured
     * 
     * @param effectId The effect ID to look up (e.g., "FarmingMod:CookedChicken")
     * @return The configured entry, or null if not found
     */
    fun findByEffectId(effectId: String): ModdedConsumableEntry? {
        if (!config.enabled || effectId.isBlank()) {
            return null
        }
        
        return registry[effectId.lowercase()]
    }
    
    /**
     * Check if modded consumables support is enabled.
     * 
     * @return True if enabled in config
     */
    fun isEnabled(): Boolean = config.enabled
    
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
     * Get the number of loaded entries for a specific mod.
     * 
     * @param modId The mod identifier
     * @return Number of entries loaded from that mod
     */
    fun getModEntryCount(modId: String): Int = loadedPerMod[modId] ?: 0
    
    /**
     * Get all loaded mod IDs.
     * 
     * @return Set of mod IDs that have at least one loaded entry
     */
    fun getLoadedModIds(): Set<String> = loadedPerMod.keys.toSet()
    
    /**
     * Clear the registry.
     * Called during config reload.
     */
    fun clear() {
        registry.clear()
        loadedPerMod.clear()
    }
    
    /**
     * Reload the registry with new config.
     * 
     * @param newConfig The new configuration to load
     */
    fun reload(newConfig: ModdedConsumablesConfig) {
        clear()
        
        if (newConfig.enabled) {
            // Load entries from each mod
            newConfig.mods.forEach { (modId, modConfig) ->
                if (modConfig.enabled) {
                    var modLoadedCount = 0
                    
                    modConfig.consumables.forEach { entry ->
                        if (entry.isValid()) {
                            registry[entry.effectId.lowercase()] = entry
                            modLoadedCount++
                        }
                    }
                    
                    if (modLoadedCount > 0) {
                        loadedPerMod[modId] = modLoadedCount
                    }
                }
            }
            
            val total = loadedPerMod.values.sum()
            if (total > 0) {
                val modsSummary = loadedPerMod.entries
                    .joinToString(", ") { (modId, count) -> "$modId: $count" }
                
                LoggingManager.config(logger, "metabolism") {
                    "Reloaded $total modded consumables from ${loadedPerMod.size} mods ($modsSummary)"
                }
            } else {
                LoggingManager.config(logger, "metabolism") {
                    "Modded consumables enabled but no entries loaded"
                }
            }
        } else {
            LoggingManager.config(logger, "metabolism") {
                "Modded consumables support is disabled"
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
