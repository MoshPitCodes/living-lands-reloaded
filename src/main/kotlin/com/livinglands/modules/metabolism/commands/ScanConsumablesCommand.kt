package com.livinglands.modules.metabolism.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.commands.ModuleCommand
import com.livinglands.modules.metabolism.MetabolismModule
import com.livinglands.modules.metabolism.config.MetabolismConsumablesConfig
import com.livinglands.modules.metabolism.config.ModdedConsumableEntry
import com.livinglands.modules.metabolism.food.ConsumablesScanner
import com.livinglands.modules.metabolism.food.DiscoveredConsumable
import java.awt.Color
import java.time.LocalDate

/**
 * Command to scan the Item registry for unrecognized consumable items.
 * 
 * Usage:
 *   /ll scan consumables              - Preview discovered items
 *   /ll scan consumables --save       - Save to config
 *   /ll scan consumables --save --section MyMod  - Save with custom section name
 * 
 * **Auto-Scan vs Manual Scan:**
 * - Auto-scan runs once on first server start (if config is empty)
 * - Manual scan allows admins to discover new items after installing mods
 * 
 * **Performance:**
 * - Scans ~1000-3000 items in registry
 * - Most filtered out early (isConsumable check)
 * - Actual consumables typically 20-100 items
 * - Fast enough to run during gameplay
 */
class ScanConsumablesCommand : ModuleCommand(
    name = "consumables",
    description = "Scan Item registry for consumable items",
    moduleId = "metabolism",
    operatorOnly = true
) {
    
    private val logger: HytaleLogger = CoreModule.logger
    
    // Define command arguments
    private val saveFlag = withFlagArg("save", "Save discovered items to config")
    private val sectionArg = withOptionalArg("section", "Custom section name", com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes.STRING)
    
    override fun executeIfModuleEnabled(ctx: CommandContext) {
        // Get argument values
        val shouldSave = ctx.provided(saveFlag)
        val sectionName = if (ctx.provided(sectionArg)) {
            ctx.get(sectionArg)
        } else {
            "ManualScan_${LocalDate.now()}"
        }
        
        // Load current consumables config
        val consumablesConfig = CoreModule.config.load(MetabolismConsumablesConfig.CONFIG_NAME, MetabolismConsumablesConfig())
        
        // Get already configured effect IDs to skip
        val configuredEffects = consumablesConfig.getAllEntries().map { it.effectId }.toSet()
        
        // Perform scan
        ctx.sendMessage(Message.raw("Scanning Item registry for consumables...").color(Color(255, 170, 0)))
        
        val discovered = ConsumablesScanner.scanItemRegistry(configuredEffects, logger)
        
        // Display results
        displayScanResults(ctx, discovered, configuredEffects.size)
        
        if (discovered.isEmpty()) {
            MessageFormatter.commandInfo(ctx, "No new consumables found. All items are already configured.")
            return
        }
        
        if (shouldSave) {
            // Save to config
            saveToConsumablesConfig(ctx, discovered, consumablesConfig, sectionName)
        } else {
            // Show preview
            displayPreview(ctx, discovered)
            MessageFormatter.commandRaw(ctx, "", Color(170, 170, 170))
            MessageFormatter.commandInfo(ctx, "To save these items, run: /ll scan consumables --save")
            MessageFormatter.commandInfo(ctx, "To use a custom section: /ll scan consumables --save --section MyModName")
        }
    }
    
    /**
     * Parse command arguments from input string.
     */
    private fun parseArguments(inputString: String): List<String> {
        return inputString.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    
    /**
     * Extract section name from arguments.
     * Looks for --section followed by a name.
     */
    private fun extractSectionName(args: List<String>): String? {
        val sectionIndex = args.indexOf("--section")
        if (sectionIndex == -1 || sectionIndex >= args.size - 1) {
            return null
        }
        
        val sectionName = args[sectionIndex + 1]
        // Validate section name (alphanumeric, underscores, hyphens)
        if (!sectionName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            return null
        }
        
        return sectionName
    }
    
    /**
     * Display scan results summary.
     */
    private fun displayScanResults(
        ctx: CommandContext,
        discovered: List<DiscoveredConsumable>,
        alreadyConfigured: Int
    ) {
        val gold = Color(255, 170, 0)
        val aqua = Color(85, 255, 255)
        val gray = Color(170, 170, 170)
        val green = Color(85, 255, 85)
        
        MessageFormatter.commandRaw(ctx, "=== Consumables Scan Results ===", gold)
        MessageFormatter.commandRaw(ctx, "", gray)
        
        ctx.sendMessage(
            Message.raw("Already configured: ").color(gray)
                .insert(Message.raw("$alreadyConfigured").color(aqua))
        )
        ctx.sendMessage(
            Message.raw("Newly discovered: ").color(gray)
                .insert(Message.raw("${discovered.size}").color(green))
        )
    }
    
    /**
     * Display preview of discovered items.
     * Shows first 10 items with category and tier info.
     */
    private fun displayPreview(ctx: CommandContext, discovered: List<DiscoveredConsumable>) {
        val aqua = Color(85, 255, 255)
        val gray = Color(170, 170, 170)
        val green = Color(85, 255, 85)
        
        MessageFormatter.commandRaw(ctx, "", gray)
        MessageFormatter.commandRaw(ctx, "Preview (first 10):", Color(255, 170, 0))
        
        discovered.take(10).forEach { item ->
            ctx.sendMessage(
                Message.raw("  • ").color(gray)
                    .insert(Message.raw(item.itemId).color(aqua))
                    .insert(Message.raw(" → ").color(gray))
                    .insert(Message.raw(item.effectId).color(green))
                    .insert(Message.raw(" (${item.category}, T${item.tier})").color(gray))
            )
        }
        
        if (discovered.size > 10) {
            MessageFormatter.commandRaw(ctx, "  ... and ${discovered.size - 10} more", gray)
        }
    }
    
    /**
     * Save discovered consumables to config file.
     * 
     * - Groups by namespace/mod
     * - Merges with existing config
     * - Creates sections per mod (e.g., "Hytale", "CoolMod")
     * - Reloads MetabolismModule registry
     */
    private fun saveToConsumablesConfig(
        ctx: CommandContext,
        discovered: List<DiscoveredConsumable>,
        existingConfig: MetabolismConsumablesConfig,
        sectionName: String
    ) {
        try {
            // Group discovered items by namespace
            val byNamespace = discovered.groupBy { it.namespace }
            
            // Create section name for each namespace
            val newSections = mutableMapOf<String, List<ModdedConsumableEntry>>()
            
            byNamespace.forEach { (namespace, items) ->
                val entries = items.map { item ->
                    ModdedConsumableEntry(
                        effectId = item.effectId,
                        category = item.category,
                        tier = item.tier,
                        itemId = item.itemId
                    )
                }
                
                // Create section name: "SectionName_Namespace" (e.g., "ManualScan_2026-02-02_Hytale")
                val namespaceSectionName = if (byNamespace.size > 1) {
                    "${sectionName}_${namespace}"
                } else {
                    sectionName  // Only one namespace, no need to suffix
                }
                
                newSections[namespaceSectionName] = entries
            }
            
            // Create new config with merged entries
            val updatedConfig = existingConfig.copy(
                enabled = true,  // Auto-enable when items are saved
                consumables = existingConfig.consumables + newSections
            )
            
            // Save to config file
            CoreModule.config.save(MetabolismConsumablesConfig.CONFIG_NAME, updatedConfig)
            
            // Update the MetabolismModule's config reference
            val metabolismModule = CoreModule.getModule<MetabolismModule>("metabolism")
            if (metabolismModule != null) {
                metabolismModule.updateConsumablesConfig(updatedConfig)
                metabolismModule.rebuildConsumablesRegistry()
            }
            
            // Success message
            val totalItems = discovered.size
            val namespaceCount = byNamespace.size
            
            MessageFormatter.commandSuccess(
                ctx, 
                "Saved $totalItems consumables from $namespaceCount mod(s) to '${MetabolismConsumablesConfig.CONFIG_NAME}.yml'"
            )
            
            // Show per-namespace breakdown
            byNamespace.entries
                .sortedByDescending { it.value.size }
                .forEach { (namespace, items) ->
                    val byCategory = items.groupBy { it.category }
                    val categorySummary = byCategory.entries
                        .sortedByDescending { it.value.size }
                        .take(3)
                        .joinToString(", ") { (category, items) -> "$category: ${items.size}" }
                    
                    MessageFormatter.commandInfo(ctx, "$namespace: ${items.size} items ($categorySummary)")
                }
            
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to save consumables config")
            MessageFormatter.commandError(ctx, "Failed to save: ${e.message}")
        }
    }
}
