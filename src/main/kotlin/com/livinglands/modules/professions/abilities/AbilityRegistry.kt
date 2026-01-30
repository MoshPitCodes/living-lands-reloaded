package com.livinglands.modules.professions.abilities

import com.livinglands.modules.professions.data.Profession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of all profession abilities.
 * 
 * Provides fast lookups for:
 * - Abilities by profession
 * - Abilities by tier
 * - Unlocked abilities for a player (based on level)
 * 
 * Thread-safe for concurrent access.
 */
class AbilityRegistry {
    
    /**
     * All 15 abilities indexed by profession and tier.
     * 
     * Structure: Profession -> Tier (1-3) -> Ability
     */
    private val abilitiesByProfessionAndTier: Map<Profession, Map<Int, Ability>>
    
    /**
     * All abilities indexed by ID for fast lookup.
     */
    private val abilitiesById: Map<String, Ability>
    
    /**
     * Cache of unlocked abilities per player.
     * 
     * Key: Player UUID as string
     * Value: Set of unlocked ability IDs
     * 
     * Updated when player levels up.
     */
    private val playerUnlockedAbilities = ConcurrentHashMap<String, MutableSet<String>>()
    
    init {
        // Initialize all 15 abilities
        val allAbilities = listOf(
            // Combat
            WarriorAbility,
            IronStomachAbility,
            AdrenalineRushAbility,
            
            // Mining
            ProspectorAbility,
            DesertNomadAbility,
            OreSenseAbility,
            
            // Logging
            LumberjackAbility,
            TirelessWoodsmanAbility,
            TimberAbility,
            
            // Building
            ArchitectAbility,
            EnduringBuilderAbility,
            EfficientArchitectAbility,
            
            // Gathering
            ForagerAbility,
            HeartyGathererAbility,
            SurvivalistAbility
        )
        
        // Build profession -> tier -> ability map
        val byProfTier = mutableMapOf<Profession, MutableMap<Int, Ability>>()
        allAbilities.forEach { ability ->
            val profMap = byProfTier.getOrPut(ability.profession) { mutableMapOf() }
            profMap[ability.tier] = ability
        }
        abilitiesByProfessionAndTier = byProfTier
        
        // Build ID -> ability map
        abilitiesById = allAbilities.associateBy { it.id }
    }
    
    // ============ Ability Lookup ============
    
    /**
     * Get all abilities for a profession.
     * 
     * @param profession The profession
     * @return List of 3 abilities (tiers 1-3)
     */
    fun getAbilitiesForProfession(profession: Profession): List<Ability> {
        val tierMap = abilitiesByProfessionAndTier[profession] ?: return emptyList()
        return tierMap.values.toList().sortedBy { it.tier }
    }
    
    /**
     * Get a specific ability by ID.
     * 
     * @param abilityId The ability ID (e.g., "combat_warrior")
     * @return The ability or null if not found
     */
    fun getAbilityById(abilityId: String): Ability? {
        return abilitiesById[abilityId]
    }
    
    /**
     * Get an ability for a specific profession and tier.
     * 
     * @param profession The profession
     * @param tier The tier (1, 2, or 3)
     * @return The ability or null if not found
     */
    fun getAbility(profession: Profession, tier: Int): Ability? {
        return abilitiesByProfessionAndTier[profession]?.get(tier)
    }
    
    /**
     * Get all abilities of a specific tier.
     * 
     * @param tier The tier (1, 2, or 3)
     * @return List of 5 abilities (one per profession)
     */
    fun getAbilitiesByTier(tier: Int): List<Ability> {
        return abilitiesByProfessionAndTier.values
            .mapNotNull { it[tier] }
    }
    
    /**
     * Get all abilities (all 15).
     * 
     * @return List of all abilities
     */
    fun getAllAbilities(): List<Ability> {
        return abilitiesById.values.toList()
    }
    
    // ============ Player Unlock Tracking ============
    
    /**
     * Check if a player has unlocked a specific ability.
     * 
     * @param playerId Player's UUID as string
     * @param abilityId The ability ID
     * @return true if unlocked, false otherwise
     */
    fun isAbilityUnlocked(playerId: String, abilityId: String): Boolean {
        val unlockedSet = playerUnlockedAbilities[playerId] ?: return false
        return unlockedSet.contains(abilityId)
    }
    
    /**
     * Check if a player has unlocked an ability based on their level.
     * 
     * @param profession The profession
     * @param level Current level in the profession
     * @param tier Ability tier (1, 2, or 3)
     * @return true if level is high enough to unlock the ability
     */
    fun canUnlockAbility(profession: Profession, level: Int, tier: Int): Boolean {
        val ability = getAbility(profession, tier) ?: return false
        return level >= ability.requiredLevel
    }
    
    /**
     * Get all unlocked abilities for a player in a specific profession.
     * 
     * @param playerId Player's UUID as string
     * @param profession The profession
     * @param currentLevel Current level in the profession
     * @return List of unlocked abilities for this profession
     */
    fun getUnlockedAbilities(playerId: String, profession: Profession, currentLevel: Int): List<Ability> {
        return getAbilitiesForProfession(profession).filter { ability ->
            currentLevel >= ability.requiredLevel
        }
    }
    
    /**
     * Get all unlocked abilities for a player across all professions.
     * 
     * @param playerId Player's UUID as string
     * @param professionLevels Map of Profession to current level
     * @return List of all unlocked abilities
     */
    fun getAllUnlockedAbilities(playerId: String, professionLevels: Map<Profession, Int>): List<Ability> {
        val unlockedAbilities = mutableListOf<Ability>()
        
        professionLevels.forEach { (profession, level) ->
            unlockedAbilities.addAll(getUnlockedAbilities(playerId, profession, level))
        }
        
        return unlockedAbilities
    }
    
    /**
     * Mark an ability as unlocked for a player.
     * Called when player levels up.
     * 
     * @param playerId Player's UUID as string
     * @param abilityId The ability ID
     */
    fun unlockAbility(playerId: String, abilityId: String) {
        val unlockedSet = playerUnlockedAbilities.getOrPut(playerId) { mutableSetOf() }
        unlockedSet.add(abilityId)
    }
    
    /**
     * Get newly unlocked abilities after a level-up.
     * 
     * @param profession The profession that leveled up
     * @param oldLevel Level before level-up
     * @param newLevel Level after level-up
     * @return List of newly unlocked abilities (if any)
     */
    fun getNewlyUnlockedAbilities(profession: Profession, oldLevel: Int, newLevel: Int): List<Ability> {
        val abilities = getAbilitiesForProfession(profession)
        
        return abilities.filter { ability ->
            // Ability was locked at old level, but unlocked at new level
            oldLevel < ability.requiredLevel && newLevel >= ability.requiredLevel
        }
    }
    
    /**
     * Initialize a player's unlocked abilities based on their current levels.
     * Called when player joins.
     * 
     * @param playerId Player's UUID as string
     * @param professionLevels Map of Profession to current level
     */
    fun initializePlayerAbilities(playerId: String, professionLevels: Map<Profession, Int>) {
        val unlockedSet = mutableSetOf<String>()
        
        professionLevels.forEach { (profession, level) ->
            val unlockedAbilities = getUnlockedAbilities(playerId, profession, level)
            unlockedSet.addAll(unlockedAbilities.map { it.id })
        }
        
        playerUnlockedAbilities[playerId] = unlockedSet
    }
    
    /**
     * Remove a player from the unlock cache.
     * Called on player disconnect.
     * 
     * @param playerId Player's UUID as string
     */
    fun removePlayer(playerId: String) {
        playerUnlockedAbilities.remove(playerId)
    }
    
    /**
     * Clear the entire unlock cache.
     * Called during shutdown.
     */
    fun clearCache() {
        playerUnlockedAbilities.clear()
    }
    
    // ============ Tier 1 XP Boost Helpers ============

    /**
     * Get the XP multiplier for a profession if Tier 1 ability is unlocked.
     *
     * @param playerId Player's UUID as string
     * @param profession The profession
     * @param currentLevel Current level in the profession
     * @param tier1Enabled Whether Tier 1 XP boosts are enabled in config (default: true)
     * @return XP multiplier (1.15 if Tier 1 unlocked and enabled, 1.0 otherwise)
     */
    fun getXpMultiplier(
        playerId: String,
        profession: Profession,
        currentLevel: Int,
        tier1Enabled: Boolean = true
    ): Double {
        // Check if Tier 1 abilities are enabled in config
        if (!tier1Enabled) {
            return 1.0
        }

        val tier1Ability = getAbility(profession, 1) as? Tier1Ability ?: return 1.0

        return if (currentLevel >= tier1Ability.requiredLevel) {
            tier1Ability.xpMultiplier
        } else {
            1.0
        }
    }
}
