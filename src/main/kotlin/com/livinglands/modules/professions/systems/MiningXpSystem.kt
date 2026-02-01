package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.abilities.OreSenseAbility
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession
import kotlin.random.Random

/**
 * ECS system for Mining XP awards.
 * 
 * Awards XP when a player breaks ore blocks or common blocks.
 * 
 * Event: BreakBlockEvent (ECS event)
 * - Triggered when a player breaks a block
 * - Provides BlockType and target block position
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * 
 * XP Calculation:
 * - Base XP: config.xpRewards.mining.baseXp (default: 5)
 * - Ore multipliers: config.xpRewards.mining.oreMultipliers (coal 1x → emerald 4x)
 * - Common blocks: stone 0.1x, dirt 0.05x, etc.
 * - Tier 1 bonus: +15% if Prospector ability unlocked
 * 
 * Block Detection:
 * - Check blockType.identifier for "ore", "stone", "cobblestone", etc.
 * - Use contains() matching (e.g., "hytale:iron_ore" contains "ore")
 */
class MiningXpSystem(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry,
    private val abilityEffectService: AbilityEffectService,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
    
    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()
    
    /**
     * Query to match entities - Mining XP only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }
    
    /**
     * Handle a block break event and award Mining XP if applicable.
     * 
     * Called by ECS when BreakBlockEvent is triggered on an entity.
     * 
     * @param index Entity index in the archetype chunk
     * @param chunk Archetype chunk containing entity data
     * @param store Entity component store
     * @param buffer Command buffer for ECS modifications
     * @param event The BreakBlockEvent
     */
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: BreakBlockEvent
    ) {
        // Get PlayerRef component from the entity that triggered this event
        val playerRef = chunk.getComponent(index, playerRefType) ?: return
        
        // Get block type from event
        val blockType = event.blockType
        
        // Get block identifier (e.g., "hytale:iron_ore")
        val blockId = blockType.id.lowercase()
        
        // Determine if this is a mining-related block and get multiplier
        val multiplier = getMiningMultiplier(blockId)
        
        if (multiplier == null) {
            // Not a mining-related block
            return
        }
        
        // Get player UUID
        val playerUuid = playerRef.uuid
        
        // Get current Mining level for ability check
        val currentLevel = professionsService.getLevel(playerUuid, Profession.MINING)
        
        // Calculate base XP from config
        val baseXp = config.xpRewards.mining.baseXp
        
        // Calculate final XP amount
        val xpAmount = (baseXp * multiplier).toLong().coerceAtLeast(1)
        
        // Check if player has Prospector ability (Tier 1 +15% XP boost)
        val xpMultiplier = abilityRegistry.getXpMultiplier(
            playerUuid.toString(),
            Profession.MINING,
            currentLevel,
            config.abilities.tier1XpBoost
        )
        
        // Award XP (with multiplier if ability unlocked)
        val result = professionsService.awardXpWithMultiplier(
            playerId = playerUuid,
            profession = Profession.MINING,
            baseAmount = xpAmount,
            multiplier = xpMultiplier
        )

        // Log multiplier application (INFO level for visibility)
        if (xpMultiplier > 1.0) {
            logger.atFine().log("Applied Tier 1 XP boost for player ${playerUuid}: ${xpMultiplier}x multiplier (base: $xpAmount, final: ${(xpAmount * xpMultiplier).toLong()})")
        }
        
        // Notify HUD elements (panel + notification)
        com.livinglands.core.CoreModule.getModule<com.livinglands.modules.professions.ProfessionsModule>("professions")?.notifyXpGain(
            playerUuid,
            Profession.MINING,
            xpAmount,
            result.didLevelUp
        )
        
        // Log level-ups
        if (result.didLevelUp) {
            logger.atFine().log("Player ${playerUuid} leveled up Mining: ${result.oldLevel} → ${result.newLevel}")
        }
        
        // Debug logging
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            logger.atFine().log("Awarded $xpAmount Mining XP to player ${playerUuid} ($blockId)")
        }
        
        // ========== Tier 3 Ability: Ore Sense ==========
        // Check if player has Ore Sense ability and block is an ore
        try {
            if (isOreBlock(blockId) && abilityEffectService.hasOreSense(playerUuid)) {
                applyOreSense(playerRef, playerUuid, blockId, store)
            }
        } catch (e: Exception) {
            logger.atWarning().log("Error applying Ore Sense for player $playerUuid: ${e.message}")
        }
    }
    
    /**
     * Get the XP multiplier for a block type.
     * 
     * @param blockId The block identifier (lowercase)
     * @return Multiplier (e.g., 1.0 for coal, 4.0 for emerald, 0.1 for stone) or null if not mining-related
     */
    private fun getMiningMultiplier(blockId: String): Double? {
        // Check ores first (highest priority)
        for ((oreName, multiplier) in config.xpRewards.mining.oreMultipliers) {
            if (blockId.contains(oreName.lowercase())) {
                return multiplier
            }
        }
        
        // Not a mining-related block
        return null
    }
    
    /**
     * Check if a block is an ore block.
     * 
     * @param blockId The block identifier (lowercase)
     * @return true if this is an ore block
     */
    private fun isOreBlock(blockId: String): Boolean {
        return blockId.contains("ore")
    }
    
    /**
     * Apply Ore Sense ability - chance to add bonus ore to player inventory.
     * 
     * Effect: 10% chance to double ore drops (adds extra ore to inventory).
     * 
     * **Algorithm:**
     * 1. Check random chance (10% by default from OreSenseAbility.dropChanceBonus)
     * 2. If triggered, create an ItemStack of 1 ore
     * 3. Add to player's inventory (combined storage)
     * 
     * **Thread Safety:**
     * - Inventory access must happen on world thread (via world.execute)
     * - Random is thread-safe for individual calls
     * 
     * @param playerRef Player's PlayerRef component
     * @param playerId Player's UUID
     * @param blockId The ore block identifier (e.g., "hytale:iron_ore")
     * @param store Entity store for ECS access
     */
    private fun applyOreSense(
        playerRef: PlayerRef,
        playerId: java.util.UUID,
        blockId: String,
        store: Store<EntityStore>
    ) {
        // Random chance check (10% = 0.10)
        if (Random.nextDouble() >= OreSenseAbility.dropChanceBonus) {
            return // Ability did not trigger
        }
        
        // Get world for thread-safe inventory access
        val worldUuid = playerRef.worldUuid
        if (worldUuid == null) {
            logger.atFine().log("Ore Sense: World UUID not available for player $playerId")
            return
        }
        val world = Universe.get().getWorld(worldUuid)
        if (world == null) {
            logger.atFine().log("Ore Sense: World not found for player $playerId")
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
                    logger.atFine().log("Ore Sense: Player component not found for $playerId")
                    return@execute
                }
                
                val inventory = player.inventory
                if (inventory == null) {
                    logger.atFine().log("Ore Sense: Inventory not found for player $playerId")
                    return@execute
                }
                
                // Get the item ID from the block ID
                // Ores typically drop as items with the same ID (e.g., "hytale:iron_ore" -> "hytale:iron_ore")
                // Some ores might drop raw materials instead (e.g., "hytale:diamond_ore" -> "hytale:diamond")
                // For now, we'll use the block ID as the item ID - this may need adjustment based on game mechanics
                val itemId = getItemIdFromOre(blockId)
                
                // Create ItemStack with 1 extra ore
                val bonusItem = ItemStack(itemId, 1)
                
                // Add to combined storage (hotbar + storage)
                val container = inventory.combinedHotbarFirst
                val transaction = container.addItemStack(bonusItem)
                
                if (transaction.succeeded()) {
                    logger.atFine().log("Ore Sense triggered! Added bonus $itemId to player $playerId")
                    
                    // Send inventory update to client
                    player.sendInventory()
                } else {
                    logger.atFine().log("Ore Sense: Could not add bonus item to inventory (full?) for player $playerId")
                }
            } catch (e: Exception) {
                logger.atWarning().log("Error in Ore Sense for player $playerId: ${e.message}")
            }
        }
    }
    
    /**
     * Get the item ID that should drop from an ore block.
     * 
     * Some ores drop raw materials instead of the ore block itself.
     * This mapping can be expanded based on game mechanics.
     * 
     * @param blockId The ore block identifier
     * @return The item identifier that should be added to inventory
     */
    private fun getItemIdFromOre(blockId: String): String {
        // By default, use the block ID as the item ID
        // This can be customized for specific ores that drop different items
        // Example mappings (uncomment and adjust as needed):
        // if (blockId.contains("diamond_ore")) return "hytale:diamond"
        // if (blockId.contains("coal_ore")) return "hytale:coal"
        
        return blockId
    }
}
