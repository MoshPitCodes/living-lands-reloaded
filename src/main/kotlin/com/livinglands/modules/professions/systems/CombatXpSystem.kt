package com.livinglands.modules.professions.systems

import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession

/**
 * ECS system for Combat XP awards.
 * 
 * Awards XP when a player kills an entity.
 * 
 * Event: KillFeedEvent$KillerMessage (ECS event)
 * - Triggered when a player kills another entity (mob, player, etc.)
 * - Provides damage info and target EntityRef
 * - System gets PlayerRef from ECS context (ArchetypeChunk)
 * 
 * XP Calculation:
 * - Base XP: config.xpRewards.combat.baseXp (default: 10)
 * - Mob multipliers: config.xpRewards.combat.mobMultipliers (boss 5x, default 1x)
 * - Tier 1 bonus: +15% if Warrior ability unlocked
 */
class CombatXpSystem(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry,
    private val abilityEffectService: AbilityEffectService,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) : EntityEventSystem<EntityStore, KillFeedEvent.KillerMessage>(KillFeedEvent.KillerMessage::class.java) {
    
    /**
     * Component type for PlayerRef to extract from ECS.
     */
    private val playerRefType = PlayerRef.getComponentType()
    
    /**
     * Query to match entities - Combat XP only applies to players.
     */
    override fun getQuery(): Query<EntityStore> {
        return Query.any()
    }
    
    /**
     * Handle a kill event and award Combat XP.
     * 
     * Called by ECS when KillFeedEvent.KillerMessage is triggered on an entity.
     * 
     * @param index Entity index in the archetype chunk
     * @param chunk Archetype chunk containing entity data
     * @param store Entity component store
     * @param buffer Command buffer for ECS modifications
     * @param event The KillFeedEvent.KillerMessage
     */
    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: KillFeedEvent.KillerMessage
    ) {
        // Get PlayerRef component from the entity that triggered this event
        val playerRef = chunk.getComponent(index, playerRefType) ?: return
        
        // Get player UUID
        val playerUuid = playerRef.uuid
        
        // Get current Combat level for ability check
        val currentLevel = professionsService.getLevel(playerUuid, Profession.COMBAT)
        
        // Calculate base XP from config
        val baseXp = config.xpRewards.combat.baseXp
        
        // Get mob multiplier (default to 1.0 if not found)
        // TODO: Detect mob type from target EntityRef
        // For now, use "default" multiplier
        val mobMultiplier = config.xpRewards.combat.mobMultipliers["default"] ?: 1.0
        
        // Calculate final XP amount
        val xpAmount = (baseXp * mobMultiplier).toLong()
        
        // Check if player has Warrior ability (Tier 1 +15% XP boost)
        val xpMultiplier = abilityRegistry.getXpMultiplier(
            playerUuid.toString(),
            Profession.COMBAT,
            currentLevel,
            config.abilities.tier1XpBoost
        )
        
        // Award XP (with multiplier if ability unlocked)
        val result = professionsService.awardXpWithMultiplier(
            playerId = playerUuid,
            profession = Profession.COMBAT,
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
            Profession.COMBAT,
            xpAmount,
            result.didLevelUp
        )
        
        // Log level-ups
        if (result.didLevelUp) {
            logger.atInfo().log("Player ${playerUuid} leveled up Combat: ${result.oldLevel} → ${result.newLevel}")
        }
        
        // Debug logging
        if (config.ui.showXpGainMessages && xpAmount >= config.ui.minXpToShow) {
            logger.atFine().log("Awarded $xpAmount Combat XP to player ${playerUuid} (kill)")
        }
    }
}
