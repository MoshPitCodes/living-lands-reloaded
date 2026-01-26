package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession

/**
 * ECS system for Logging XP awards.
 * 
 * Awards XP when a player breaks log blocks (wood).
 * 
 * Event: BreakBlockEvent (ECS event)
 * - Triggered when a player breaks a block
 * - Provides BlockType and target block position
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * 
 * XP Calculation:
 * - Base XP: config.xpRewards.logging.baseXp (default: 3)
 * - Log multipliers: config.xpRewards.logging.logMultipliers (default 1x for all)
 * - Tier 1 bonus: +15% if Lumberjack ability unlocked
 * 
 * Block Detection:
 * - Check blockType.identifier for "log", "wood"
 * - Use contains() matching (e.g., "hytale:oak_log" contains "log")
 */
class LoggingXpSystem(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
    
    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()
    
    /**
     * Query to match entities - Logging XP only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }
    
    /**
     * Handle a block break event and award Logging XP if applicable.
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
        
        // Get block identifier (e.g., "hytale:oak_log")
        val blockId = blockType.id.lowercase()
        
        // Check if this is a log block
        if (!isLogBlock(blockId)) {
            return
        }
        
        // Get player UUID
        val playerUuid = playerRef.uuid
        
        // Get current Logging level for ability check
        val currentLevel = professionsService.getLevel(playerUuid, Profession.LOGGING)
        
        // Calculate base XP from config
        val baseXp = config.xpRewards.logging.baseXp
        
        // Get log multiplier (default to 1.0 if not found)
        val logMultiplier = getLogMultiplier(blockId)
        
        // Calculate final XP amount
        val xpAmount = (baseXp * logMultiplier).toLong().coerceAtLeast(1)
        
        // Check if player has Lumberjack ability (Tier 1 +15% XP boost)
        val xpMultiplier = abilityRegistry.getXpMultiplier(playerUuid.toString(), Profession.LOGGING, currentLevel)
        
        // Award XP (with multiplier if ability unlocked)
        val result = professionsService.awardXpWithMultiplier(
            playerId = playerUuid,
            profession = Profession.LOGGING,
            baseAmount = xpAmount,
            multiplier = xpMultiplier
        )
        
        // Notify HUD elements (panel + notification)
        com.livinglands.core.CoreModule.getModule<com.livinglands.modules.professions.ProfessionsModule>("professions")?.notifyXpGain(
            playerUuid,
            Profession.LOGGING,
            xpAmount,
            result.didLevelUp
        )
        
        // Log level-ups
        if (result.didLevelUp) {
            logger.atInfo().log("Player ${playerUuid} leveled up Logging: ${result.oldLevel} → ${result.newLevel}")
        }
        
        // Debug logging
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            logger.atFine().log("Awarded $xpAmount Logging XP to player ${playerUuid} ($blockId)")
        }
    }
    
    /**
     * Check if a block is a log block.
     * 
     * @param blockId The block identifier (lowercase)
     * @return True if this is a log block
     */
    private fun isLogBlock(blockId: String): Boolean {
        return blockId.contains("log") || blockId.contains("wood")
    }
    
    /**
     * Get the XP multiplier for a log type.
     * 
     * @param blockId The block identifier (lowercase)
     * @return Multiplier (default 1.0 for all log types)
     */
    private fun getLogMultiplier(blockId: String): Double {
        // Check configured log multipliers
        for ((logName, multiplier) in config.xpRewards.logging.logMultipliers) {
            if (blockId.contains(logName.lowercase())) {
                return multiplier
            }
        }
        
        // Default multiplier for any log
        return config.xpRewards.logging.logMultipliers["default"] ?: 1.0
    }
}
