package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession

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
            logger.atInfo().log("Applied Tier 1 XP boost for player ${playerUuid}: ${xpMultiplier}x multiplier (base: $xpAmount, final: ${(xpAmount * xpMultiplier).toLong()})")
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
            logger.atInfo().log("Player ${playerUuid} leveled up Building: ${result.oldLevel} → ${result.newLevel}")
        }
        
        // Debug logging (only if XP is significant enough)
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            logger.atFine().log("Awarded $xpAmount Building XP to player ${playerUuid} ($blockId)")
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
}
