package com.livinglands.modules.professions.abilities

import com.livinglands.modules.professions.data.Profession

/**
 * Sealed interface for profession abilities.
 * 
 * All abilities are passive (always active once unlocked).
 * Abilities unlock at specific levels:
 * - Tier 1: Level 15 (+15% XP boost)
 * - Tier 2: Level 45 (Resource restoration)
 * - Tier 3: Level 100 (Permanent passives)
 */
sealed interface Ability {
    /** Unique identifier for this ability */
    val id: String
    
    /** Display name shown to players */
    val name: String
    
    /** Description of what the ability does */
    val description: String
    
    /** The profession this ability belongs to */
    val profession: Profession
    
    /** Tier of the ability (1, 2, or 3) */
    val tier: Int
    
    /** Level required to unlock this ability */
    val requiredLevel: Int
}

/**
 * Tier 1 abilities - XP boost (+15%).
 * Unlocked at level 15.
 */
sealed class Tier1Ability : Ability {
    override val tier: Int = 1
    override val requiredLevel: Int = 15
    
    /** XP multiplier (1.15 = +15% XP) */
    abstract val xpMultiplier: Double
}

/**
 * Tier 2 abilities - Permanent max stat increases.
 * Unlocked at level 45.
 * 
 * These abilities PERMANENTLY increase the maximum capacity of metabolism stats,
 * allowing players to survive longer between food/water consumption.
 */
sealed class Tier2Ability : Ability {
    override val tier: Int = 2
    override val requiredLevel: Int = 45
}

/**
 * Tier 3 abilities - Permanent passive bonuses.
 * Unlocked at level 100.
 */
sealed class Tier3Ability : Ability {
    override val tier: Int = 3
    override val requiredLevel: Int = 100
}

// ============ Combat Abilities ============

/**
 * Warrior (Combat Tier 1) - +15% Combat XP gain.
 */
data object WarriorAbility : Tier1Ability() {
    override val id = "combat_warrior"
    override val name = "Warrior"
    override val description = "+15% Combat XP gain"
    override val profession = Profession.COMBAT
    override val xpMultiplier = 1.15
}

/**
 * Iron Stomach (Combat Tier 2) - Permanently increase max hunger.
 */
data object IronStomachAbility : Tier2Ability() {
    override val id = "combat_iron_stomach"
    override val name = "Iron Stomach"
    override val description = "Permanently +15 max hunger capacity"
    override val profession = Profession.COMBAT
    
    /** Max hunger bonus */
    val maxHungerBonus = 15.0
}

/**
 * Adrenaline Rush (Combat Tier 3) - +10% speed for 5s on kill.
 */
data object AdrenalineRushAbility : Tier3Ability() {
    override val id = "combat_adrenaline"
    override val name = "Adrenaline Rush"
    override val description = "+10% speed for 5 seconds on kill"
    override val profession = Profession.COMBAT
    
    /** Speed multiplier */
    val speedMultiplier = 1.10
    
    /** Duration in milliseconds */
    val durationMs = 5000L
}

// ============ Mining Abilities ============

/**
 * Prospector (Mining Tier 1) - +15% Mining XP gain.
 */
data object ProspectorAbility : Tier1Ability() {
    override val id = "mining_prospector"
    override val name = "Prospector"
    override val description = "+15% Mining XP gain"
    override val profession = Profession.MINING
    override val xpMultiplier = 1.15
}

/**
 * Desert Nomad (Mining Tier 2) - Permanently increase max thirst.
 */
data object DesertNomadAbility : Tier2Ability() {
    override val id = "mining_desert_nomad"
    override val name = "Desert Nomad"
    override val description = "Permanently +10 max thirst capacity"
    override val profession = Profession.MINING
    
    /** Max thirst bonus */
    val maxThirstBonus = 10.0
}

/**
 * Ore Sense (Mining Tier 3) - +10% ore drop chance.
 */
data object OreSenseAbility : Tier3Ability() {
    override val id = "mining_ore_sense"
    override val name = "Ore Sense"
    override val description = "+10% ore drop chance"
    override val profession = Profession.MINING
    
    /** Additional drop chance */
    val dropChanceBonus = 0.10
}

// ============ Logging Abilities ============

/**
 * Lumberjack (Logging Tier 1) - +15% Logging XP gain.
 */
data object LumberjackAbility : Tier1Ability() {
    override val id = "logging_lumberjack"
    override val name = "Lumberjack"
    override val description = "+15% Logging XP gain"
    override val profession = Profession.LOGGING
    override val xpMultiplier = 1.15
}

/**
 * Tireless Woodsman (Logging Tier 2) - Permanently increase max energy.
 */
data object TirelessWoodsmanAbility : Tier2Ability() {
    override val id = "logging_tireless"
    override val name = "Tireless Woodsman"
    override val description = "Permanently +10 max energy capacity"
    override val profession = Profession.LOGGING
    
    /** Max energy bonus */
    val maxEnergyBonus = 10.0
}

/**
 * Timber! (Logging Tier 3) - Tree felling bonus (extra logs).
 */
data object TimberAbility : Tier3Ability() {
    override val id = "logging_timber"
    override val name = "Timber!"
    override val description = "Tree felling bonus - extra log drops"
    override val profession = Profession.LOGGING
    
    /** Chance for extra log drop */
    val extraLogChance = 0.25
}

// ============ Building Abilities ============

/**
 * Architect (Building Tier 1) - +15% Building XP gain.
 */
data object ArchitectAbility : Tier1Ability() {
    override val id = "building_architect"
    override val name = "Architect"
    override val description = "+15% Building XP gain"
    override val profession = Profession.BUILDING
    override val xpMultiplier = 1.15
}

/**
 * Enduring Builder (Building Tier 2) - Permanently increase max stamina.
 * 
 * Note: Stamina is NOT part of the metabolism system - it's a Hytale built-in stat.
 * This ability directly modifies the player's max stamina attribute.
 */
data object EnduringBuilderAbility : Tier2Ability() {
    override val id = "building_enduring"
    override val name = "Enduring Builder"
    override val description = "Permanently +15 max stamina capacity"
    override val profession = Profession.BUILDING
    
    /** Max stamina bonus */
    val maxStaminaBonus = 15.0
}

/**
 * Efficient Architect (Building Tier 3) - Chance to not consume blocks.
 */
data object EfficientArchitectAbility : Tier3Ability() {
    override val id = "building_efficient_architect"
    override val name = "Efficient Architect"
    override val description = "12% chance to not consume block on placement"
    override val profession = Profession.BUILDING
    
    /** Chance to not consume block (0.12 = 12%) */
    val noConsumeChance = 0.12
}

// ============ Gathering Abilities ============

/**
 * Forager (Gathering Tier 1) - +15% Gathering XP gain.
 */
data object ForagerAbility : Tier1Ability() {
    override val id = "gathering_forager"
    override val name = "Forager"
    override val description = "+15% Gathering XP gain"
    override val profession = Profession.GATHERING
    override val xpMultiplier = 1.15
}

/**
 * Hearty Gatherer (Gathering Tier 2) - Restore hunger/thirst when picking up FOOD items.
 * 
 * This is the ONLY Tier 2 ability that is NOT a permanent max stat increase.
 * It's a triggered effect that activates on food item pickup.
 */
data object HeartyGathererAbility : Tier2Ability() {
    override val id = "gathering_hearty"
    override val name = "Hearty Gatherer"
    override val description = "+4 hunger and +4 thirst per food item pickup"
    override val profession = Profession.GATHERING
    
    /** Hunger restored per FOOD pickup */
    val hungerRestore = 4.0
    
    /** Thirst restored per FOOD pickup */
    val thirstRestore = 4.0
}

/**
 * Survivalist (Gathering Tier 3) - -15% metabolism depletion rate.
 */
data object SurvivalistAbility : Tier3Ability() {
    override val id = "gathering_survivalist"
    override val name = "Survivalist"
    override val description = "-15% metabolism depletion rate"
    override val profession = Profession.GATHERING
    
    /** Depletion rate multiplier (0.85 = 15% slower) */
    val depletionMultiplier = 0.85
}
