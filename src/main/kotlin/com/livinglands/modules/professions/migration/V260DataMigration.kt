package com.livinglands.modules.professions.migration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.professions.ProfessionsRepository
import com.livinglands.modules.professions.data.Profession
import com.livinglands.modules.professions.data.ProfessionStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Migrates player profession data from v2.6.0 JSON files to v1.0.0-beta SQLite database.
 * 
 * v2.6.0 Structure:
 * - JSON files stored per player: `LivingLands/leveling/{player-uuid}.json`
 * - Professions: COMBAT, MINING, BUILDING, LOGGING, GATHERING (same as v1.0.0)
 * - Fields: level (Int), currentXp (Long), xpToNextLevel (Long)
 * 
 * Migration Strategy:
 * - Read all JSON files from legacy directory
 * - Parse using Jackson (matches v2.6.0 Gson structure)
 * - Insert into new global professions database
 * - Mark files as migrated (rename to .migrated)
 * - Log migration results
 * 
 * Thread Safety:
 * - Migration runs on Dispatchers.IO
 * - Database operations use existing repository (thread-safe)
 */
class V260DataMigration(
    private val repository: ProfessionsRepository,
    private val xpCalculator: com.livinglands.modules.professions.XpCalculator,
    private val logger: HytaleLogger
) {
    
    private val mapper = jacksonObjectMapper()
    
    /**
     * Check if v2.6.0 data directory exists.
     * 
     * Plugin data dir is:    Saves\new1\mods\MPC_LivingLandsReloaded\data
     * v2.6.0 data is at:     UserData\Mods\LivingLands\leveling\playerdata (GLOBAL)
     * 
     * v2.6.0 stores data in the GLOBAL Mods\ directory, not per-world.
     */
    fun hasLegacyData(pluginDataDir: Path): Boolean {
        val absolutePluginDataDir = pluginDataDir.toAbsolutePath()
        LoggingManager.debug(logger, "professions") { "Absolute plugin data directory: $absolutePluginDataDir" }
        
        // Navigate from Saves\new1\mods\MPC_LivingLandsReloaded\data to UserData\Mods\LivingLands
        // Go up to UserData: data -> MPC_LivingLandsReloaded -> mods -> new1 -> Saves -> UserData
        // Then: UserData\Mods\LivingLands\leveling\playerdata
        val userDataDir = absolutePluginDataDir.parent?.parent?.parent?.parent?.parent
        val legacyDir = userDataDir?.resolve("Mods")?.resolve("LivingLands")?.resolve("leveling")?.resolve("playerdata")
        
        LoggingManager.debug(logger, "professions") { "Looking for v2.6.0 data at: $legacyDir" }
        
        if (legacyDir == null || !legacyDir.exists()) {
            LoggingManager.debug(logger, "professions") { "v2.6.0 legacy directory does not exist" }
            return false
        }
        
        // Check for any .json files
        val hasFiles = Files.list(legacyDir).use { stream ->
            stream.anyMatch { it.toString().endsWith(".json") && it.isRegularFile() }
        }
        
        if (hasFiles) {
            LoggingManager.debug(logger, "professions") { "Found v2.6.0 legacy data at: $legacyDir" }
        } else {
            LoggingManager.debug(logger, "professions") { "v2.6.0 directory exists but contains no .json files" }
        }
        
        return hasFiles
    }
    
    /**
     * Migrate all v2.6.0 JSON files to new database.
     * 
     * @param pluginDataDir Current plugin data directory (Saves\new1\mods\MPC_LivingLandsReloaded\data)
     * @return Migration result with counts
     */
    suspend fun migrate(pluginDataDir: Path): MigrationResult {
        return withContext(Dispatchers.IO) {
            // Navigate to GLOBAL Mods\LivingLands directory
            val absolutePluginDataDir = pluginDataDir.toAbsolutePath()
            val userDataDir = absolutePluginDataDir.parent?.parent?.parent?.parent?.parent
            val legacyDir = userDataDir?.resolve("Mods")?.resolve("LivingLands")?.resolve("leveling")?.resolve("playerdata")
            
            if (legacyDir == null || !legacyDir.exists()) {
                LoggingManager.debug(logger, "professions") { "No v2.6.0 legacy data found at: $legacyDir" }
                return@withContext MigrationResult(0, 0, 0)
            }
            
            LoggingManager.debug(logger, "professions") { "Starting v2.6.0 data migration from: $legacyDir" }
            
            var totalPlayers = 0
            var migratedPlayers = 0
            var failedPlayers = 0
            val migratedPlayerIds = mutableSetOf<UUID>()
            
            // Find all JSON files (convert to list to support suspend functions)
            val jsonFiles = Files.list(legacyDir).use { stream ->
                stream.filter { it.toString().endsWith(".json") && it.isRegularFile() }
                    .toList()
            }
            
            // Process each file with suspend function support
            for (file in jsonFiles) {
                totalPlayers++
                
                try {
                    // Direct suspend call - we're already in IO dispatcher context
                    val playerUuid = migratePlayerFile(file)
                    
                    if (playerUuid != null) {
                        migratedPlayers++
                        migratedPlayerIds.add(playerUuid)
                        
                        // Rename to .migrated to prevent re-migration
                        val migratedPath = file.resolveSibling("${file.fileName}.migrated")
                        Files.move(file, migratedPath)
                    } else {
                        failedPlayers++
                    }
                } catch (e: Exception) {
                    LoggingManager.warn(logger, "professions") { "Failed to migrate player file: ${file.fileName}" }
                    failedPlayers++
                }
            }
            
            LoggingManager.debug(logger, "professions") { "v2.6.0 migration complete: $migratedPlayers/$totalPlayers migrated, $failedPlayers failed" }
            
            MigrationResult(totalPlayers, migratedPlayers, failedPlayers, migratedPlayerIds)
        }
    }
    
    /**
     * Migrate a single player JSON file.
     * 
     * @param file Path to JSON file
     * @return player UUID if migrated successfully, null otherwise
     */
    private suspend fun migratePlayerFile(file: Path): UUID? {
        // Parse JSON
        val json = file.readText()
        val legacyData: LegacyPlayerData = try {
            mapper.readValue(json)
        } catch (e: Exception) {
            LoggingManager.warn(logger, "professions") { "Failed to parse legacy JSON: ${file.fileName}" }
            return null
        }
        
        val playerId = legacyData.playerId ?: run {
            LoggingManager.warn(logger, "professions") { "Legacy data missing playerId: ${file.fileName}" }
            return null
        }
        
        val professions = legacyData.professions ?: run {
            LoggingManager.warn(logger, "professions") { "Legacy data missing professions: ${file.fileName}" }
            return null
        }
        
        val playerIdString = playerId.toString()
        
        // Check if player already has data
        val existing = repository.findAllByPlayer(playerIdString)
        
        // Smart migration logic:
        // - If NO existing data: migrate
        // - If existing data looks like defaults (all level 1, low XP): OVERWRITE with v2.6.0 data
        // - If existing data has real progress (any level > 1): SKIP migration (preserve real progress)
        if (existing.isNotEmpty()) {
            val hasRealProgress = existing.any { it.level > 1 || it.xp > 1000 }
            
            if (hasRealProgress) {
                LoggingManager.debug(logger, "professions") { "Player $playerId already has real progress (levels > 1), skipping migration to preserve data" }
                return null
            } else {
                LoggingManager.debug(logger, "professions") { "Player $playerId has default data (all level 1), overwriting with v2.6.0 data" }
                // Delete existing default data before migration
                repository.deletePlayer(playerIdString)
            }
        }
        
        // Map legacy professions to new format
        val newStats = mutableListOf<ProfessionStats>()
        
        professions.forEach { (professionName, professionData) ->
            val profession = mapLegacyProfession(professionName)
            if (profession != null) {
                // Calculate total cumulative XP from v2.6.0 data
                // v2.6.0 stores: level (int), currentXp (long within that level)
                // v1.0.0 expects: total XP earned across all levels
                val totalXp = xpCalculator.xpForLevel(professionData.level) + professionData.currentXp
                
                newStats.add(ProfessionStats(
                    playerId = playerIdString,
                    profession = profession,
                    xp = totalXp,  // Total cumulative XP, not currentXp
                    level = professionData.level,
                    lastUpdated = System.currentTimeMillis()
                ))
            } else {
                LoggingManager.warn(logger, "professions") { "Unknown legacy profession: $professionName" }
            }
        }
        
        // Save to new database (batch insert)
        if (newStats.isNotEmpty()) {
            repository.saveAll(newStats)
            LoggingManager.debug(logger, "professions") { "Migrated player $playerId: ${newStats.size} professions" }
            return playerId  // Return UUID on success
        }
        
        return null
    }
    
    /**
     * Map legacy v2.6.0 profession names to new enum.
     * 
     * v2.6.0 used enum names: COMBAT, MINING, BUILDING, LOGGING, GATHERING
     * v1.0.0-beta uses same names, so direct mapping works.
     */
    private fun mapLegacyProfession(name: String): Profession? {
        return when (name.uppercase()) {
            "COMBAT" -> Profession.COMBAT
            "MINING" -> Profession.MINING
            "BUILDING" -> Profession.BUILDING
            "LOGGING" -> Profession.LOGGING
            "GATHERING" -> Profession.GATHERING
            else -> null
        }
    }
}

/**
 * Result of v2.6.0 migration operation.
 */
data class MigrationResult(
    val totalPlayers: Int,
    val migratedPlayers: Int,
    val failedPlayers: Int,
    val migratedPlayerIds: Set<UUID> = emptySet()
) {
    val skippedPlayers: Int
        get() = totalPlayers - migratedPlayers - failedPlayers
}
