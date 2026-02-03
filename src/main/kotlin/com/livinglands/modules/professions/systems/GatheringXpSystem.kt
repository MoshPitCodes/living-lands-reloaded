package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.api.safeService
import com.livinglands.core.CoreModule
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.metabolism.MetabolismService
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.abilities.HeartyGathererAbility
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession

/**
 * ECS system for Gathering XP awards.
 * 
 * Awards XP when a player picks up items.
 * 
 * Event: InteractivelyPickupItemEvent (ECS event)
 * - Triggered when a player picks up an item from the ground
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * 
 * XP Calculation:
 * - Base XP: config.xpRewards.gathering.baseXp (default: 1)
 * - Max XP per tick: config.xpRewards.gathering.maxXpPerTick (default: 100)
 * - Tier 1 bonus: +15% if Forager ability unlocked
 * - Tier 2 bonus (Hearty Gatherer): +4 hunger & +4 thirst per FOOD item only
 * 
 * Rate Limiting:
 * - Tracks XP awarded this tick to prevent lag from mass pickups
 * - Caps at maxXpPerTick (default 100 XP)
 */
class GatheringXpSystem(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry,
    private val abilityEffectService: AbilityEffectService,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, InteractivelyPickupItemEvent>(InteractivelyPickupItemEvent::class.java) {
    
    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()
    
    /**
     * XP awarded this tick (for rate limiting).
     * Resets every tick.
     */
    private var xpThisTick = 0
    private var lastTickReset = System.currentTimeMillis()
    
    /**
     * Query to match entities - Gathering XP only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }
    
    /**
     * Handle an item pickup event and award Gathering XP.
     * 
     * Called by ECS when InteractivelyPickupItemEvent is triggered on an entity.
     * 
     * @param index Entity index in the archetype chunk
     * @param chunk Archetype chunk containing entity data
     * @param store Entity component store
     * @param buffer Command buffer for ECS modifications
     * @param event The InteractivelyPickupItemEvent
     */
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: InteractivelyPickupItemEvent
    ) {
        // Get PlayerRef component from the entity that triggered this event
        val playerRef = chunk.getComponent(index, playerRefType) ?: return
        
        // Reset tick counter if we're in a new tick (>50ms since last reset)
        val now = System.currentTimeMillis()
        if (now - lastTickReset > 50) {
            xpThisTick = 0
            lastTickReset = now
        }
        
        // Check if we've hit the max XP per tick limit
        if (xpThisTick >= config.xpRewards.gathering.maxXpPerTick) {
            return
        }
        
        // Get player UUID
        val playerUuid = playerRef.uuid
        
        // Get current Gathering level for ability check
        val currentLevel = professionsService.getLevel(playerUuid, Profession.GATHERING)
        
        // Calculate base XP from config
        val baseXp = config.xpRewards.gathering.baseXp
        
        // Calculate final XP amount (capped by remaining XP this tick)
        val remainingXp = config.xpRewards.gathering.maxXpPerTick - xpThisTick
        val xpAmount = baseXp.toLong().coerceAtMost(remainingXp.toLong())
        
        // Check if player has Forager ability (Tier 1 +15% XP boost)
        val xpMultiplier = abilityRegistry.getXpMultiplier(
            playerUuid.toString(),
            Profession.GATHERING,
            currentLevel,
            config.abilities.tier1XpBoost
        )
        
        // Award XP (with multiplier if ability unlocked)
        val result = professionsService.awardXpWithMultiplier(
            playerId = playerUuid,
            profession = Profession.GATHERING,
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
            Profession.GATHERING,
            xpAmount,
            result.didLevelUp
        )
        
        // Update tick counter
        xpThisTick += xpAmount.toInt()
        
        // Log level-ups
        if (result.didLevelUp) {
            logger.atFine().log("Player ${playerUuid} leveled up Gathering: ${result.oldLevel} → ${result.newLevel}")
        }
        
        // Debug logging
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            logger.atFine().log("Awarded $xpAmount Gathering XP to player ${playerUuid} (item pickup)")
        }
        
        // Hearty Gatherer (Tier 2) - +4 hunger/thirst on FOOD item pickup
        if (currentLevel >= 45 && abilityEffectService.hasHeartyGatherer(playerUuid)) {
            // Check if the picked up item is food
            if (isFoodItem(event)) {
                val metabolismService = safeService<MetabolismService>("metabolism")
                if (metabolismService != null) {
                    metabolismService.restoreStats(
                        playerUuid, 
                        hunger = HeartyGathererAbility.hungerRestore, 
                        thirst = HeartyGathererAbility.thirstRestore
                    )
                    
                    LoggingManager.debug(logger, "professions") { "Hearty Gatherer restored +${HeartyGathererAbility.hungerRestore.toInt()} hunger/thirst for player $playerUuid" }
                }
            }
        }
    }
    
    /**
     * Check if the picked up item is a food item.
     * 
     * Uses item type identifier patterns to detect food items.
     * This is a heuristic-based approach since we can't access the full item registry.
     * 
     * @param event The item pickup event
     * @return true if the item appears to be food
     */
    private fun isFoodItem(event: InteractivelyPickupItemEvent): Boolean {
        try {
            // Get the item stack from the event
            val itemStack = event.itemStack
            val itemId = itemStack.itemId.lowercase()
            
            // Check against common food patterns
            return foodPatterns.any { pattern -> itemId.contains(pattern) }
        } catch (e: Exception) {
            logger.atFine().log("Could not determine if item is food: ${e.message}")
            return false
        }
    }
    
    companion object {
        /**
         * Patterns to identify food items by their identifier.
         * 
         * These patterns match common food item naming conventions:
         * - Generic: "food", "edible", "consumable"
         * - Produce: "apple", "berry", "fruit", "vegetable"
         * - Protein: "meat", "fish", "egg", "steak"
         * - Grains: "bread", "grain", "wheat"
         * - Prepared: "cooked", "roasted", "baked"
         */
        private val foodPatterns = setOf(
            // Generic food indicators
            "food", "edible", "consumable",
            // Fruits and berries
            "apple", "berry", "fruit", "melon", "grape", "cherry", "pear", "orange",
            // Vegetables
            "vegetable", "carrot", "potato", "tomato", "corn", "beet",
            // Meat
            "meat", "steak", "pork", "beef", "chicken", "mutton", "venison", "bacon",
            // Fish
            "fish", "salmon", "cod", "trout",
            // Other protein
            "egg",
            // Grains and bread
            "bread", "grain", "wheat", "loaf",
            // Prepared foods
            "cooked", "roasted", "baked", "stew", "soup", "pie",
            // Mushrooms (edible)
            "mushroom",
            // Honey and sweets
            "honey", "sugar", "cake", "cookie"
        )
    }
}
