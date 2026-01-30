package com.livinglands.modules.professions.abilities

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.MetabolismService
import com.livinglands.modules.professions.data.Profession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for applying and managing ability effects.
 * 
 * Handles the actual application of ability bonuses to players,
 * including Tier 2 max stat increases and Tier 3 passive effects.
 * 
 * This service is separate from AbilityRegistry to maintain clean
 * separation between ability definitions and their effects.
 * 
 * Thread-safe for concurrent access.
 */
class AbilityEffectService(
    private val logger: HytaleLogger
) {
    
    /**
     * Track which Tier 2 abilities have been applied per player.
     * 
     * Key: Player UUID as string
     * Value: Set of applied Tier 2 ability IDs
     * 
     * Used to prevent duplicate applications and to know which
     * abilities to re-apply on world switch.
     */
    private val appliedTier2Abilities = ConcurrentHashMap<String, MutableSet<String>>()
    
    /**
     * Track which Tier 3 abilities have been applied per player.
     * 
     * Key: Player UUID as string
     * Value: Set of applied Tier 3 ability IDs
     */
    private val appliedTier3Abilities = ConcurrentHashMap<String, MutableSet<String>>()
    
    // ============ Tier 2 Ability Application ============
    
    /**
     * Apply Tier 2 ability effect for a profession.
     * Called when player reaches level 45 in a profession.
     * 
     * @param playerId Player's UUID
     * @param profession The profession that unlocked Tier 2
     */
    fun applyTier2Ability(playerId: UUID, profession: Profession) {
        val playerIdStr = playerId.toString()
        val metabolismService = CoreModule.services.get<MetabolismService>()
        
        if (metabolismService == null) {
            logger.atWarning().log("Cannot apply Tier 2 ability - MetabolismService not available")
            return
        }
        
        when (profession) {
            Profession.COMBAT -> applyIronStomach(playerId, playerIdStr, metabolismService)
            Profession.MINING -> applyDesertNomad(playerId, playerIdStr, metabolismService)
            Profession.LOGGING -> applyTirelessWoodsman(playerId, playerIdStr, metabolismService)
            Profession.BUILDING -> applyEnduringBuilder(playerId, playerIdStr)
            Profession.GATHERING -> applyHeartyGatherer(playerId, playerIdStr)
        }
    }
    
    /**
     * Iron Stomach (Combat Tier 2) - +15 max hunger.
     */
    private fun applyIronStomach(playerId: UUID, playerIdStr: String, metabolismService: MetabolismService?) {
        if (metabolismService == null) {
            logger.atWarning().log("Cannot apply Iron Stomach - MetabolismService not available")
            return
        }
        val abilityId = IronStomachAbility.id
        
        // Check if already applied
        if (isAbilityApplied(playerIdStr, abilityId, 2)) {
            logger.atFine().log("Iron Stomach already applied for $playerId")
            return
        }
        
        // Apply +15 max hunger (100 -> 115)
        val newMaxHunger = 100.0 + IronStomachAbility.maxHungerBonus
        metabolismService.setMaxHunger(playerId, newMaxHunger)
        
        // Track that this ability was applied
        markAbilityApplied(playerIdStr, abilityId, 2)
        
        logger.atFine().log("Applied Iron Stomach (+${IronStomachAbility.maxHungerBonus.toInt()} max hunger) to player $playerId")
    }
    
    /**
     * Desert Nomad (Mining Tier 2) - +10 max thirst.
     */
    private fun applyDesertNomad(playerId: UUID, playerIdStr: String, metabolismService: MetabolismService?) {
        if (metabolismService == null) {
            logger.atWarning().log("Cannot apply Desert Nomad - MetabolismService not available")
            return
        }
        val abilityId = DesertNomadAbility.id
        
        // Check if already applied
        if (isAbilityApplied(playerIdStr, abilityId, 2)) {
            logger.atFine().log("Desert Nomad already applied for $playerId")
            return
        }
        
        // Apply +10 max thirst (100 -> 110)
        val newMaxThirst = 100.0 + DesertNomadAbility.maxThirstBonus
        metabolismService.setMaxThirst(playerId, newMaxThirst)
        
        // Track that this ability was applied
        markAbilityApplied(playerIdStr, abilityId, 2)
        
        logger.atFine().log("Applied Desert Nomad (+${DesertNomadAbility.maxThirstBonus.toInt()} max thirst) to player $playerId")
    }
    
    /**
     * Tireless Woodsman (Logging Tier 2) - +10 max energy.
     */
    private fun applyTirelessWoodsman(playerId: UUID, playerIdStr: String, metabolismService: MetabolismService?) {
        if (metabolismService == null) {
            logger.atWarning().log("Cannot apply Tireless Woodsman - MetabolismService not available")
            return
        }
        val abilityId = TirelessWoodsmanAbility.id
        
        // Check if already applied
        if (isAbilityApplied(playerIdStr, abilityId, 2)) {
            logger.atFine().log("Tireless Woodsman already applied for $playerId")
            return
        }
        
        // Apply +10 max energy (100 -> 110)
        val newMaxEnergy = 100.0 + TirelessWoodsmanAbility.maxEnergyBonus
        metabolismService.setMaxEnergy(playerId, newMaxEnergy)
        
        // Track that this ability was applied
        markAbilityApplied(playerIdStr, abilityId, 2)
        
        logger.atFine().log("Applied Tireless Woodsman (+${TirelessWoodsmanAbility.maxEnergyBonus.toInt()} max energy) to player $playerId")
    }
    
    /**
     * Enduring Builder (Building Tier 2) - +15 max stamina.
     * 
     * NOTE: Stamina is a Hytale built-in stat, NOT part of our metabolism system.
     * This ability requires Hytale API research to implement properly.
     */
    private fun applyEnduringBuilder(playerId: UUID, playerIdStr: String) {
        val abilityId = EnduringBuilderAbility.id
        
        // Check if already applied
        if (isAbilityApplied(playerIdStr, abilityId, 2)) {
            logger.atFine().log("Enduring Builder already applied for $playerId")
            return
        }
        
        // TODO: Implement when Hytale stamina API is researched
        // For now, just log and track the ability
        markAbilityApplied(playerIdStr, abilityId, 2)
        
        logger.atFine().log("Enduring Builder unlocked for player $playerId (stamina API pending)")
    }
    
    /**
     * Hearty Gatherer (Gathering Tier 2) - +4 hunger/thirst on food pickup.
     * 
     * This ability is TRIGGER-BASED, not a permanent stat change.
     * The actual trigger is in GatheringXpSystem.
     * 
     * We just track that it's unlocked here.
     */
    private fun applyHeartyGatherer(playerId: UUID, playerIdStr: String) {
        val abilityId = HeartyGathererAbility.id
        
        // Check if already applied
        if (isAbilityApplied(playerIdStr, abilityId, 2)) {
            logger.atFine().log("Hearty Gatherer already applied for $playerId")
            return
        }
        
        // Track that this ability was unlocked
        markAbilityApplied(playerIdStr, abilityId, 2)
        
        logger.atFine().log("Hearty Gatherer unlocked for player $playerId (trigger-based, +4 hunger/thirst on food pickup)")
    }
    
    // ============ Tier 3 Ability Application ============
    
    /**
     * Apply Tier 3 ability effect for a profession.
     * Called when player reaches level 100 in a profession.
     * 
     * Most Tier 3 abilities are trigger-based, except Survivalist
     * which is a permanent depletion modifier.
     * 
     * @param playerId Player's UUID
     * @param profession The profession that unlocked Tier 3
     */
    fun applyTier3Ability(playerId: UUID, profession: Profession) {
        val playerIdStr = playerId.toString()
        
        when (profession) {
            Profession.COMBAT -> applyAdrenalineRush(playerId, playerIdStr)
            Profession.MINING -> applyOreSense(playerId, playerIdStr)
            Profession.LOGGING -> applyTimber(playerId, playerIdStr)
            Profession.BUILDING -> applyEfficientArchitect(playerId, playerIdStr)
            Profession.GATHERING -> applySurvivalist(playerId, playerIdStr)
        }
    }
    
    /**
     * Adrenaline Rush (Combat Tier 3) - +10% speed for 5s on kill.
     * This is a TRIGGER-BASED ability, handled in CombatXpSystem.
     */
    private fun applyAdrenalineRush(playerId: UUID, playerIdStr: String) {
        val abilityId = AdrenalineRushAbility.id
        
        if (isAbilityApplied(playerIdStr, abilityId, 3)) {
            logger.atFine().log("Adrenaline Rush already applied for $playerId")
            return
        }
        
        markAbilityApplied(playerIdStr, abilityId, 3)
        logger.atFine().log("Adrenaline Rush unlocked for player $playerId (trigger-based, +10% speed on kill)")
    }
    
    /**
     * Ore Sense (Mining Tier 3) - +10% ore drop chance.
     * This is a TRIGGER-BASED ability, handled in MiningXpSystem.
     */
    private fun applyOreSense(playerId: UUID, playerIdStr: String) {
        val abilityId = OreSenseAbility.id
        
        if (isAbilityApplied(playerIdStr, abilityId, 3)) {
            logger.atFine().log("Ore Sense already applied for $playerId")
            return
        }
        
        markAbilityApplied(playerIdStr, abilityId, 3)
        logger.atFine().log("Ore Sense unlocked for player $playerId (trigger-based, +10% ore drop)")
    }
    
    /**
     * Timber! (Logging Tier 3) - +25% extra log drops.
     * This is a TRIGGER-BASED ability, handled in LoggingXpSystem.
     */
    private fun applyTimber(playerId: UUID, playerIdStr: String) {
        val abilityId = TimberAbility.id
        
        if (isAbilityApplied(playerIdStr, abilityId, 3)) {
            logger.atFine().log("Timber! already applied for $playerId")
            return
        }
        
        markAbilityApplied(playerIdStr, abilityId, 3)
        logger.atFine().log("Timber! unlocked for player $playerId (trigger-based, +25% extra logs)")
    }
    
    /**
     * Efficient Architect (Building Tier 3) - 12% chance to not consume blocks.
     * This is a TRIGGER-BASED ability, handled in BuildingXpSystem.
     */
    private fun applyEfficientArchitect(playerId: UUID, playerIdStr: String) {
        val abilityId = EfficientArchitectAbility.id
        
        if (isAbilityApplied(playerIdStr, abilityId, 3)) {
            logger.atFine().log("Efficient Architect already applied for $playerId")
            return
        }
        
        markAbilityApplied(playerIdStr, abilityId, 3)
        logger.atFine().log("Efficient Architect unlocked for player $playerId (trigger-based, 12% block save)")
    }
    
    /**
     * Survivalist (Gathering Tier 3) - -15% metabolism depletion rate.
     * This is a PERMANENT PASSIVE effect, applied via MetabolismService.
     */
    private fun applySurvivalist(playerId: UUID, playerIdStr: String) {
        val abilityId = SurvivalistAbility.id
        
        if (isAbilityApplied(playerIdStr, abilityId, 3)) {
            logger.atFine().log("Survivalist already applied for $playerId")
            return
        }
        
        val metabolismService = CoreModule.services.get<MetabolismService>()
        if (metabolismService == null) {
            logger.atWarning().log("Cannot apply Survivalist - MetabolismService not available")
            return
        }
        
        // Apply -15% depletion modifier
        metabolismService.applyDepletionModifier(
            playerId,
            "professions:survivalist",
            SurvivalistAbility.depletionMultiplier
        )
        
        markAbilityApplied(playerIdStr, abilityId, 3)
        logger.atFine().log("Applied Survivalist (-15% depletion) to player $playerId")
    }
    
    // ============ Re-application on World Switch ============
    
    /**
     * Re-apply all unlocked abilities for a player.
     * Called on world switch to ensure bonuses persist.
     * 
     * @param playerId Player's UUID
     * @param professionLevels Map of profession to current level
     */
    fun reapplyAllAbilities(playerId: UUID, professionLevels: Map<Profession, Int>) {
        val playerIdStr = playerId.toString()
        
        // Clear tracking (will re-populate as we apply)
        appliedTier2Abilities.remove(playerIdStr)
        appliedTier3Abilities.remove(playerIdStr)
        
        professionLevels.forEach { (profession, level) ->
            // Re-apply Tier 2 if level >= 45
            if (level >= 45) {
                applyTier2Ability(playerId, profession)
            }
            
            // Re-apply Tier 3 if level >= 100
            if (level >= 100) {
                applyTier3Ability(playerId, profession)
            }
        }
        
        logger.atFine().log("Re-applied all abilities for player $playerId after world switch")
    }
    
    // ============ Ability Removal (config reload) ============
    
    /**
     * Remove all ability effects from a player.
     * Called when abilities are disabled via config reload.
     * 
     * @param playerId Player's UUID
     */
    fun removeAllAbilities(playerId: UUID) {
        val playerIdStr = playerId.toString()
        val metabolismService = CoreModule.services.get<MetabolismService>()
        
        if (metabolismService != null) {
            // Reset max stats to defaults
            metabolismService.setMaxHunger(playerId, 100.0)
            metabolismService.setMaxThirst(playerId, 100.0)
            metabolismService.setMaxEnergy(playerId, 100.0)
            
            // Remove Survivalist depletion modifier
            metabolismService.removeDepletionModifier(playerId, "professions:survivalist")
        }
        
        // Clear tracking
        appliedTier2Abilities.remove(playerIdStr)
        appliedTier3Abilities.remove(playerIdStr)
        
        logger.atFine().log("Removed all ability effects for player $playerId (config reload)")
    }
    
    // ============ Check Methods (for XP systems) ============
    
    /**
     * Check if Hearty Gatherer is unlocked for a player.
     * Used by GatheringXpSystem to trigger the effect.
     * 
     * @param playerId Player's UUID
     * @return true if Hearty Gatherer is unlocked
     */
    fun hasHeartyGatherer(playerId: UUID): Boolean {
        return isAbilityApplied(playerId.toString(), HeartyGathererAbility.id, 2)
    }
    
    /**
     * Check if Adrenaline Rush is unlocked for a player.
     * Used by CombatXpSystem to trigger the effect.
     * 
     * @param playerId Player's UUID
     * @return true if Adrenaline Rush is unlocked
     */
    fun hasAdrenalineRush(playerId: UUID): Boolean {
        return isAbilityApplied(playerId.toString(), AdrenalineRushAbility.id, 3)
    }
    
    /**
     * Check if Ore Sense is unlocked for a player.
     * Used by MiningXpSystem to trigger the effect.
     * 
     * @param playerId Player's UUID
     * @return true if Ore Sense is unlocked
     */
    fun hasOreSense(playerId: UUID): Boolean {
        return isAbilityApplied(playerId.toString(), OreSenseAbility.id, 3)
    }
    
    /**
     * Check if Timber! is unlocked for a player.
     * Used by LoggingXpSystem to trigger the effect.
     * 
     * @param playerId Player's UUID
     * @return true if Timber! is unlocked
     */
    fun hasTimber(playerId: UUID): Boolean {
        return isAbilityApplied(playerId.toString(), TimberAbility.id, 3)
    }
    
    /**
     * Check if Efficient Architect is unlocked for a player.
     * Used by BuildingXpSystem to trigger the effect.
     * 
     * @param playerId Player's UUID
     * @return true if Efficient Architect is unlocked
     */
    fun hasEfficientArchitect(playerId: UUID): Boolean {
        return isAbilityApplied(playerId.toString(), EfficientArchitectAbility.id, 3)
    }
    
    // ============ Tracking Helpers ============
    
    /**
     * Check if an ability has been applied to a player.
     */
    private fun isAbilityApplied(playerIdStr: String, abilityId: String, tier: Int): Boolean {
        val appliedSet = when (tier) {
            2 -> appliedTier2Abilities[playerIdStr]
            3 -> appliedTier3Abilities[playerIdStr]
            else -> null
        }
        return appliedSet?.contains(abilityId) == true
    }
    
    /**
     * Mark an ability as applied for a player.
     */
    private fun markAbilityApplied(playerIdStr: String, abilityId: String, tier: Int) {
        when (tier) {
            2 -> appliedTier2Abilities.getOrPut(playerIdStr) { mutableSetOf() }.add(abilityId)
            3 -> appliedTier3Abilities.getOrPut(playerIdStr) { mutableSetOf() }.add(abilityId)
        }
    }
    
    // ============ Cache Management ============
    
    /**
     * Remove player from tracking caches.
     * Called on disconnect.
     */
    fun removePlayer(playerId: UUID) {
        val playerIdStr = playerId.toString()
        appliedTier2Abilities.remove(playerIdStr)
        appliedTier3Abilities.remove(playerIdStr)
    }
    
    /**
     * Clear all caches.
     * Called on shutdown.
     */
    fun clearCache() {
        appliedTier2Abilities.clear()
        appliedTier3Abilities.clear()
    }
}
