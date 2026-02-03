package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.abilities.EfficientArchitectAbility
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession
import kotlin.random.Random

/**
 * ECS system for Building XP awards.
 * 
 * Awards XP when a player places blocks.
 * 
 * Event: PlaceBlockEvent (ECS event)
 * - Triggered when a player places a block
 * - Provides item in hand and target block position
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * 
 * XP Calculation:
 * - Base XP: config.xpRewards.building.baseXp (default: 0.1)
 * - Block value multipliers:
 *   - Common (dirt, cobblestone): 0.1x = 0.01 XP per block
 *   - Processed (planks, bricks): 1.0x = 0.1 XP per block
 *   - Crafted (doors, stairs): 3.0x = 0.3 XP per block
 * - Tier 1 bonus: +15% if Architect ability unlocked
 * 
 * Block Detection:
 * - Check item in hand identifier for material category
 * - Use simple heuristics (common blocks, processed blocks, crafted blocks)
 */
class BuildingXpSystem(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry,
    private val abilityEffectService: AbilityEffectService,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
    
    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()
    
    /**
     * Query to match entities - Building XP only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }
    
    /**
     * Handle a block place event and award Building XP.
     * 
     * Called by ECS when PlaceBlockEvent is triggered on an entity.
     * 
     * @param index Entity index in the archetype chunk
     * @param chunk Archetype chunk containing entity data
     * @param store Entity component store
     * @param buffer Command buffer for ECS modifications
     * @param event The PlaceBlockEvent
     */
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: PlaceBlockEvent
    ) {
        // Get PlayerRef component from the entity that triggered this event
        val playerRef = chunk.getComponent(index, playerRefType) ?: return
        
        // Get item in hand from event (this is what's being placed)
        val itemInHand = event.itemInHand ?: return
        
        // Get block identifier from item (e.g., "hytale:oak_planks")
        val blockId = itemInHand.itemId.lowercase()
        
        // Get player UUID
        val playerUuid = playerRef.uuid
        
        // Get current Building level for ability check
        val currentLevel = professionsService.getLevel(playerUuid, Profession.BUILDING)
        
        // Calculate base XP from config
        val baseXp = config.xpRewards.building.baseXp
        
        // Determine block value category and get multiplier
        val blockValueMultiplier = getBlockValueMultiplier(blockId)
        
        // Calculate final XP amount
        val xpAmount = (baseXp * blockValueMultiplier).toLong().coerceAtLeast(1)
        
        // Check if player has Architect ability (Tier 1 +15% XP boost)
        val xpMultiplier = abilityRegistry.getXpMultiplier(
            playerUuid.toString(),
            Profession.BUILDING,
            currentLevel,
            config.abilities.tier1XpBoost
        )
        
        // Award XP (with multiplier if ability unlocked)
        val result = professionsService.awardXpWithMultiplier(
            playerId = playerUuid,
            profession = Profession.BUILDING,
            baseAmount = xpAmount,
            multiplier = xpMultiplier
        )

        // Log multiplier application (INFO level for visibility)
        if (xpMultiplier > 1.0) {
            LoggingManager.debug(logger, "professions") { "Applied Tier 1 XP boost for player ${playerUuid}: ${xpMultiplier}x multiplier (base: $xpAmount, final: ${(xpAmount * xpMultiplier).toLong()})" }
        }
        
        // Notify HUD elements (panel + notification)
        com.livinglands.core.CoreModule.getModule<com.livinglands.modules.professions.ProfessionsModule>("professions")?.notifyXpGain(
            playerUuid,
            Profession.BUILDING,
            xpAmount,
            result.didLevelUp
        )
        
        // Log level-ups
        if (result.didLevelUp) {
            LoggingManager.debug(logger, "professions") { "Player ${playerUuid} leveled up Building: ${result.oldLevel} → ${result.newLevel}" }
        }
        
        // Debug logging (only if XP is significant enough)
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            LoggingManager.debug(logger, "professions") { "Awarded $xpAmount Building XP to player ${playerUuid} ($blockId)" }
        }
        
        // ========== Tier 3 Ability: Efficient Architect ==========
        // Check if player has Efficient Architect ability
        try {
            if (abilityEffectService.hasEfficientArchitect(playerUuid)) {
                applyEfficientArchitect(playerRef, playerUuid, blockId, store)
            }
        } catch (e: Exception) {
            LoggingManager.warn(logger, "professions") { "Error applying Efficient Architect for player $playerUuid: ${e.message}" }
        }
    }
    
    /**
     * Get the block value multiplier based on block type.
     * 
     * Categories:
     * - Common: Raw materials (dirt, cobblestone, sand, gravel, stone)
     * - Processed: Refined materials (planks, bricks, smooth stone, glass)
     * - Crafted: Complex blocks (doors, stairs, slabs, fences, crafting tables)
     * 
     * @param blockId The block identifier (lowercase)
     * @return Multiplier from config.xpRewards.building.blockValueMultipliers
     */
    private fun getBlockValueMultiplier(blockId: String): Double {
        val multipliers = config.xpRewards.building.blockValueMultipliers
        
        // Check for crafted blocks (highest value)
        if (isCraftedBlock(blockId)) {
            return multipliers["crafted"] ?: 3.0
        }
        
        // Check for processed blocks (medium value)
        if (isProcessedBlock(blockId)) {
            return multipliers["processed"] ?: 1.0
        }
        
        // Default to common blocks (lowest value)
        return multipliers["common"] ?: 0.1
    }
    
    /**
     * Check if a block is a crafted block (complex, requires crafting).
     * 
     * @param blockId The block identifier (lowercase)
     * @return True if crafted block
     */
    private fun isCraftedBlock(blockId: String): Boolean {
        return blockId.contains("door") ||
               blockId.contains("stairs") ||
               blockId.contains("slab") ||
               blockId.contains("fence") ||
               blockId.contains("gate") ||
               blockId.contains("crafting") ||
               blockId.contains("furnace") ||
               blockId.contains("chest") ||
               blockId.contains("barrel") ||
               blockId.contains("anvil") ||
               blockId.contains("enchant") ||
               blockId.contains("table")
    }
    
    /**
     * Check if a block is a processed block (refined materials).
     * 
     * @param blockId The block identifier (lowercase)
     * @return True if processed block
     */
    private fun isProcessedBlock(blockId: String): Boolean {
        return blockId.contains("plank") ||
               blockId.contains("brick") ||
               blockId.contains("smooth") ||
               blockId.contains("polish") ||
               blockId.contains("glass") ||
               blockId.contains("tile") ||
               blockId.contains("cut")
    }
    
    /**
     * Check if a block is valid for the Efficient Architect refund.
     * 
     * Excludes blocks that could cause exploits or don't make sense to refund:
     * - Air and void blocks
     * - Liquids (water, lava)
     * - Non-placeable blocks
     * - Creative-only blocks
     * 
     * @param blockId The block identifier (lowercase)
     * @return true if the block can be refunded
     */
    private fun isValidRefundBlock(blockId: String): Boolean {
        // Exclude air, void, and technical blocks
        if (blockId.contains("air") || blockId.contains("void")) {
            return false
        }
        
        // Exclude liquids
        if (blockId.contains("water") || blockId.contains("lava") || blockId.contains("liquid")) {
            return false
        }
        
        // Exclude bedrock and barrier blocks
        if (blockId.contains("bedrock") || blockId.contains("barrier")) {
            return false
        }
        
        // Exclude command blocks and structure blocks (creative-only)
        if (blockId.contains("command") || blockId.contains("structure")) {
            return false
        }
        
        return true
    }
    
    /**
     * Apply Efficient Architect ability - chance to refund a placed block.
     * 
     * Effect: 12% chance to not consume the block on placement (adds it back to inventory).
     * 
     * **Algorithm:**
     * 1. Validate the block is refund-eligible (not air, liquids, etc.)
     * 2. Check random chance (12% by default from EfficientArchitectAbility.noConsumeChance)
     * 3. If triggered, create an ItemStack of 1 block
     * 4. Add to player's inventory (combined storage)
     * 
     * **Thread Safety:**
     * - Inventory access must happen on world thread (via world.execute)
     * - Random is thread-safe for individual calls
     * 
     * **Anti-Exploit Measures:**
     * - Excludes air, liquids, bedrock, command blocks, structure blocks
     * - Cannot be triggered for invalid blocks
     * - Refund happens AFTER the block is placed (not preventing placement)
     * 
     * @param playerRef Player's PlayerRef component
     * @param playerId Player's UUID
     * @param blockId The block identifier (e.g., "hytale:oak_planks")
     * @param store Entity store for ECS access
     */
    private fun applyEfficientArchitect(
        playerRef: PlayerRef,
        playerId: java.util.UUID,
        blockId: String,
        store: Store<EntityStore>
    ) {
        // Anti-exploit: Check if block is valid for refund
        if (!isValidRefundBlock(blockId)) {
            return
        }
        
        // Random chance check (12% = 0.12)
        if (Random.nextDouble() >= EfficientArchitectAbility.noConsumeChance) {
            return // Ability did not trigger
        }
        
        // Get world for thread-safe inventory access
        val worldUuid = playerRef.worldUuid
        if (worldUuid == null) {
            LoggingManager.debug(logger, "professions") { "Efficient Architect: World UUID not available for player $playerId" }
            return
        }
        val world = Universe.get().getWorld(worldUuid)
        if (world == null) {
            LoggingManager.debug(logger, "professions") { "Efficient Architect: World not found for player $playerId" }
            return
        }
        
        // Execute on world thread for ECS safety
        world.execute {
            try {
                val entityRef = playerRef.reference
                if (entityRef == null || !entityRef.isValid) {
                    return@execute
                }
                
                // Get Player component to access inventory
                val player = store.getComponent(entityRef, Player.getComponentType())
                if (player == null) {
                    LoggingManager.debug(logger, "professions") { "Efficient Architect: Player component not found for $playerId" }
                    return@execute
                }
                
                val inventory = player.inventory
                if (inventory == null) {
                    LoggingManager.debug(logger, "professions") { "Efficient Architect: Inventory not found for player $playerId" }
                    return@execute
                }
                
                // Create ItemStack with 1 refunded block
                val refundItem = ItemStack(blockId, 1)
                
                // Add to combined storage (hotbar + storage)
                val container = inventory.combinedHotbarFirst
                val transaction = container.addItemStack(refundItem)
                
                if (transaction.succeeded()) {
                    LoggingManager.debug(logger, "professions") { "Efficient Architect triggered! Refunded $blockId to player $playerId" }
                    
                    // Send inventory update to client
                    player.sendInventory()
                } else {
                    LoggingManager.debug(logger, "professions") { "Efficient Architect: Could not refund item to inventory (full?) for player $playerId" }
                }
            } catch (e: Exception) {
                LoggingManager.warn(logger, "professions") { "Error in Efficient Architect for player $playerId: ${e.message}" }
            }
        }
    }
}
