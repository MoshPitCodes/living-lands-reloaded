package com.livinglands.modules.professions.migration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

/**
 * Legacy v2.6.0 player leveling data structure.
 * 
 * Used for migration from JSON files to new SQLite database.
 * Structure matches the v2.6.0 PlayerLevelingData class.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LegacyPlayerData(
    val playerId: UUID?,
    val professions: Map<String, LegacyProfessionData>? = null,
    val hudEnabled: Boolean = true,
    val lastSaveTime: Long = 0L,
    val totalXpEarned: Long = 0L
)

/**
 * Legacy v2.6.0 profession data structure.
 * 
 * Structure matches the v2.6.0 ProfessionData class.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LegacyProfessionData(
    val level: Int = 1,
    val currentXp: Long = 0L,
    val xpToNextLevel: Long = 0L
)
