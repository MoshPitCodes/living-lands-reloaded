package com.livinglands.modules.professions.data

/**
 * Enumeration of all professions in Living Lands.
 * 
 * Each profession has its own leveling track and passive abilities.
 * Players gain XP in professions by performing related activities.
 */
enum class Profession(
    /** Display name shown to players */
    val displayName: String,
    
    /** Short description of the profession */
    val description: String,
    
    /** Database identifier (lowercase, stable) */
    val dbId: String
) {
    /**
     * Combat profession - gained from killing mobs.
     * 
     * Abilities:
     * - Tier 1 (Level 3): Warrior - +15% Combat XP
     * - Tier 2 (Level 7): Regeneration - Restore 25% health on kill
     * - Tier 3 (Level 10): Adrenaline Rush - +10% speed for 5s on kill
     */
    COMBAT(
        displayName = "Combat",
        description = "Fight mobs and gain combat prowess",
        dbId = "combat"
    ),
    
    /**
     * Mining profession - gained from breaking ore blocks.
     * 
     * Abilities:
     * - Tier 1 (Level 3): Prospector - +15% Mining XP
     * - Tier 2 (Level 7): Efficient Miner - Restore 20% energy on ore break
     * - Tier 3 (Level 10): Ore Sense - +10% ore drop chance
     */
    MINING(
        displayName = "Mining",
        description = "Mine ores and discover valuable resources",
        dbId = "mining"
    ),
    
    /**
     * Logging profession - gained from breaking log blocks.
     * 
     * Abilities:
     * - Tier 1 (Level 3): Lumberjack - +15% Logging XP
     * - Tier 2 (Level 7): Swift Chopper - Restore 20% energy on log break
     * - Tier 3 (Level 10): Timber! - Tree felling bonus (extra logs)
     */
    LOGGING(
        displayName = "Logging",
        description = "Chop trees and master woodcraft",
        dbId = "logging"
    ),
    
    /**
     * Building profession - gained from placing blocks.
     * 
     * Abilities:
     * - Tier 1 (Level 3): Architect - +15% Building XP
     * - Tier 2 (Level 7): Efficient Builder - Restore 20% stamina on block place
     * - Tier 3 (Level 10): Master Builder - Faster block placement
     */
    BUILDING(
        displayName = "Building",
        description = "Construct structures and become a master builder",
        dbId = "building"
    ),
    
    /**
     * Gathering profession - gained from picking up items.
     * 
     * Abilities:
     * - Tier 1 (Level 3): Forager - +15% Gathering XP
     * - Tier 2 (Level 7): Hearty Gatherer - +5 hunger per pickup
     * - Tier 3 (Level 10): Survivalist - -15% metabolism depletion
     */
    GATHERING(
        displayName = "Gathering",
        description = "Collect resources and survive in the wild",
        dbId = "gathering"
    );
    
    companion object {
        /**
         * Get a Profession by its database ID.
         * 
         * @param dbId The database identifier (lowercase)
         * @return The matching Profession, or null if not found
         */
        fun fromDbId(dbId: String): Profession? {
            return values().find { it.dbId == dbId }
        }
        
        /**
         * Get all professions as a list.
         */
        fun all(): List<Profession> = values().toList()
    }
}
