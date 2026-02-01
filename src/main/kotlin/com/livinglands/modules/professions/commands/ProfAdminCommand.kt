package com.livinglands.modules.professions.commands

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.commands.AsyncCommandBase
import com.livinglands.core.persistence.GlobalPlayerDataRepository
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.data.Profession
import java.util.UUID

/**
 * Admin command for managing player professions.
 *
 * Usage:
 *   /ll prof set <player> <profession> <level>  - Set profession level
 *   /ll prof add <player> <profession> <xp>     - Add XP to profession
 *   /ll prof reset <player> [profession]        - Reset profession(s)
 *   /ll prof show <player>                      - Show player stats
 * 
 * **PERFORMANCE:** Uses async pattern to avoid blocking the command thread
 * during database lookups and operations.
 */
class ProfAdminCommand(
    private val professionsService: ProfessionsService,
    private val abilityRegistry: AbilityRegistry
) : AsyncCommandBase(
    "prof",
    "Admin commands for profession management",
    true  // Operator only
) {

    override fun validateInputs(ctx: CommandContext): ValidationResult {
        val inputString = ctx.inputString.orEmpty()
        val allArgs = inputString.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        // inputString contains the full command path: "ll prof show player --confirm"
        // We need to skip the command prefixes ("ll", "prof") to get actual arguments
        val args = skipCommandPrefixes(allArgs)

        if (args.isEmpty()) {
            return ValidationResult.error("Usage: /ll prof <set|add|reset|show>")
        }

        val subcommand = args[0].lowercase()
        val confirmed = args.contains("--confirm")
        val filteredArgs = args.filter { it != "--confirm" }

        return when (subcommand) {
            "set" -> validateSet(filteredArgs, confirmed)
            "add" -> validateAdd(filteredArgs)
            "reset" -> validateReset(filteredArgs, confirmed)
            "show" -> validateShow(filteredArgs)
            else -> ValidationResult.error("Unknown subcommand: $subcommand. Use: set, add, reset, show")
        }
    }
    
    /**
     * Skip command prefixes from the argument list.
     * inputString may contain: "ll prof show player" or just "show player"
     * depending on how Hytale passes the input to subcommands.
     */
    private fun skipCommandPrefixes(args: List<String>): List<String> {
        var startIndex = 0
        
        // Skip "ll" if present
        if (args.isNotEmpty() && args[0].lowercase() == "ll") {
            startIndex++
        }
        
        // Skip "prof" if present
        if (args.size > startIndex && args[startIndex].lowercase() == "prof") {
            startIndex++
        }
        
        return args.drop(startIndex)
    }

    private fun validateSet(args: List<String>, confirmed: Boolean): ValidationResult {
        if (args.size < 4) {
            return ValidationResult.error("Usage: /ll prof set <player> <profession> <level> [--confirm]")
        }

        val playerName = args[1]
        val professionName = args[2]
        val level = args[3].toIntOrNull()

        if (level == null || level < 1 || level > 100) {
            return ValidationResult.error("Level must be between 1 and 100")
        }

        val profession = parseProfession(professionName)
            ?: return ValidationResult.error("Unknown profession: $professionName. Valid: combat, mining, logging, building, gathering")

        return ValidationResult.success(
            SetCommandData(playerName, profession, level, confirmed)
        )
    }

    private fun validateAdd(args: List<String>): ValidationResult {
        if (args.size < 4) {
            return ValidationResult.error("Usage: /ll prof add <player> <profession> <xp>")
        }

        val playerName = args[1]
        val professionName = args[2]
        val xp = args[3].toLongOrNull()

        if (xp == null || xp < 1) {
            return ValidationResult.error("XP must be a positive number")
        }

        val profession = parseProfession(professionName)
            ?: return ValidationResult.error("Unknown profession: $professionName")

        return ValidationResult.success(
            AddCommandData(playerName, profession, xp)
        )
    }

    private fun validateReset(args: List<String>, confirmed: Boolean): ValidationResult {
        if (args.size < 2) {
            return ValidationResult.error("Usage: /ll prof reset <player> [profession] [--confirm]")
        }

        val playerName = args[1]
        val professionName = if (args.size >= 3) args[2] else null
        val profession = professionName?.let { parseProfession(it) }

        if (professionName != null && profession == null) {
            return ValidationResult.error("Unknown profession: $professionName")
        }

        return ValidationResult.success(
            ResetCommandData(playerName, profession, confirmed)
        )
    }

    private fun validateShow(args: List<String>): ValidationResult {
        if (args.size < 2) {
            return ValidationResult.error("Usage: /ll prof show <player>")
        }

        return ValidationResult.success(
            ShowCommandData(args[1])
        )
    }

    override suspend fun executeAsync(ctx: CommandContext, validatedData: Any?) {
        when (validatedData) {
            is SetCommandData -> executeSetAsync(ctx, validatedData)
            is AddCommandData -> executeAddAsync(ctx, validatedData)
            is ResetCommandData -> executeResetAsync(ctx, validatedData)
            is ShowCommandData -> executeShowAsync(ctx, validatedData)
        }
    }

    private suspend fun executeSetAsync(ctx: CommandContext, data: SetCommandData) {
        val playerId = resolvePlayerId(data.playerName)
        
        if (playerId == null) {
            logger.atWarning().log("Player not found: ${data.playerName}")
            return
        }

        // Require confirmation for destructive changes
        if (!data.confirmed) {
            // Send confirmation request to command sender
            ctx.sendMessage(
                Message.raw("WARNING: This will set ${data.profession.displayName} to level ${data.level} for ${data.playerName}")
                    .color(java.awt.Color(255, 170, 0))
            )
            ctx.sendMessage(
                Message.raw("This action cannot be undone!")
                    .color(java.awt.Color(255, 85, 85))
            )
            ctx.sendMessage(
                Message.raw("To confirm, run: /ll prof set ${data.playerName} ${data.profession.name.lowercase()} ${data.level} --confirm")
                    .color(java.awt.Color(85, 255, 255))
            )
            return
        }

        professionsService.setLevel(playerId, data.profession, data.level)
        logger.atFine().log("Set ${data.profession.displayName} to level ${data.level} for ${data.playerName}")
        
        // Refresh HUD if player is online
        refreshPlayerHud(playerId)
        
        // Notify player if online
        notifyPlayer(playerId, "[Admin] Your ${data.profession.displayName} was set to level ${data.level}")
    }

    private suspend fun executeAddAsync(ctx: CommandContext, data: AddCommandData) {
        val playerId = resolvePlayerId(data.playerName)
        
        if (playerId == null) {
            logger.atWarning().log("Player not found: ${data.playerName}")
            return
        }

        val result = professionsService.addXp(playerId, data.profession, data.xp)
        
        // Refresh HUD if player is online
        refreshPlayerHud(playerId)

        if (result.didLevelUp) {
            logger.atFine().log("Added ${data.xp} XP to ${data.profession.displayName} for ${data.playerName} - Level up! ${result.oldLevel} → ${result.newLevel}")
            notifyPlayer(playerId, "[Admin] You gained ${data.xp} XP in ${data.profession.displayName}! Level up: ${result.oldLevel} → ${result.newLevel}")
        } else {
            logger.atFine().log("Added ${data.xp} XP to ${data.profession.displayName} for ${data.playerName}")
        }
    }

    private suspend fun executeResetAsync(ctx: CommandContext, data: ResetCommandData) {
        val playerId = resolvePlayerId(data.playerName)
        
        if (playerId == null) {
            logger.atWarning().log("Player not found: ${data.playerName}")
            return
        }

        // Require confirmation for destructive changes
        if (!data.confirmed) {
            val target = data.profession?.displayName ?: "ALL professions"
            ctx.sendMessage(
                Message.raw("WARNING: This will reset $target to level 1 for ${data.playerName}")
                    .color(java.awt.Color(255, 170, 0))
            )
            ctx.sendMessage(
                Message.raw("All XP and progress will be lost!")
                    .color(java.awt.Color(255, 85, 85))
            )
            
            val confirmCmd = if (data.profession != null) {
                "/ll prof reset ${data.playerName} ${data.profession.name.lowercase()} --confirm"
            } else {
                "/ll prof reset ${data.playerName} --confirm"
            }
            ctx.sendMessage(
                Message.raw("To confirm, run: $confirmCmd")
                    .color(java.awt.Color(85, 255, 255))
            )
            return
        }

        if (data.profession != null) {
            professionsService.resetProfession(playerId, data.profession)
            logger.atFine().log("Reset ${data.profession.displayName} for ${data.playerName}")
            
            // Refresh HUD if player is online
            refreshPlayerHud(playerId)
            
            notifyPlayer(playerId, "[Admin] Your ${data.profession.displayName} has been reset to level 1")
        } else {
            Profession.values().forEach { profession ->
                professionsService.resetProfession(playerId, profession)
            }
            logger.atFine().log("Reset all professions for ${data.playerName}")
            
            // Refresh HUD if player is online
            refreshPlayerHud(playerId)
            
            notifyPlayer(playerId, "[Admin] All your professions have been reset to level 1")
        }
    }

    private suspend fun executeShowAsync(ctx: CommandContext, data: ShowCommandData) {
        val playerId = resolvePlayerId(data.playerName)
        
        if (playerId == null) {
            logger.atWarning().log("Player not found: ${data.playerName}")
            return
        }

        val allStats = professionsService.getAllStats(playerId)

        // Send stats to command sender
        ctx.sendMessage(
            Message.raw("=== Profession Stats for ${data.playerName} ===")
                .color(java.awt.Color(255, 170, 0))
        )

        allStats.forEach { (profession, stats) ->
            val progress = professionsService.getProgressToNextLevel(playerId, profession)
            val progressPercent = (progress * 100).toInt()

            ctx.sendMessage(
                Message.raw("${profession.displayName}: Level ${stats.level} ($progressPercent% to next)")
                    .color(java.awt.Color(85, 255, 255))
            )

            // Show unlocked abilities
            val abilities = abilityRegistry.getUnlockedAbilities(
                playerId.toString(),
                profession,
                stats.level
            )
            abilities.forEach { ability ->
                ctx.sendMessage(
                    Message.raw("  ✓ ${ability.name}")
                        .color(java.awt.Color(85, 255, 85))
                )
            }
        }
    }

    private suspend fun resolvePlayerId(name: String): UUID? {
        // Look up player in global database by name
        val repository = CoreModule.services.get<GlobalPlayerDataRepository>()
            ?: throw IllegalStateException("GlobalPlayerDataRepository not registered")
        val playerData = repository.findByName(name)
        return playerData?.playerId?.let { UUID.fromString(it) }
    }

    private fun parseProfession(name: String): Profession? {
        return try {
            Profession.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Refresh the professions panel HUD for a player if they're online.
     * Uses updateProfessionsPanel() to avoid refreshing the entire HUD.
     */
    private fun refreshPlayerHud(playerId: UUID) {
        CoreModule.players.getSession(playerId)?.let { session ->
            session.world.execute {
                try {
                    val player = session.store.getComponent(
                        session.entityRef,
                        com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()
                    )
                    @Suppress("DEPRECATION")
                    val playerRef = player?.playerRef ?: return@execute
                    
                    // Get the HUD and update ONLY professions panel (not entire HUD)
                    val hud = CoreModule.hudManager.getHud(playerId)
                    hud?.refreshProfessionsPanel()  // Set refresh flag
                    hud?.updateProfessionsPanel()   // Update only professions section
                    
                    logger.atFine().log("Refreshed professions panel for player $playerId")
                } catch (e: Exception) {
                    logger.atFine().log("Could not refresh professions panel for player $playerId: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Send a message to a player if they're online.
     */
    private fun notifyPlayer(playerId: UUID, message: String) {
        CoreModule.players.getSession(playerId)?.let { session ->
            session.world.execute {
                try {
                    val player = session.store.getComponent(
                        session.entityRef,
                        com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()
                    )
                    @Suppress("DEPRECATION")
                    player?.playerRef?.sendMessage(
                        Message.raw(message).color(java.awt.Color(255, 170, 0))
                    )
                } catch (e: Exception) {
                    logger.atFine().log("Could not notify player $playerId: ${e.message}")
                }
            }
        }
    }

    // Data classes for command validation
    private data class SetCommandData(
        val playerName: String,
        val profession: Profession,
        val level: Int,
        val confirmed: Boolean
    )

    private data class AddCommandData(
        val playerName: String,
        val profession: Profession,
        val xp: Long
    )

    private data class ResetCommandData(
        val playerName: String,
        val profession: Profession?,
        val confirmed: Boolean
    )

    private data class ShowCommandData(
        val playerName: String
    )
}
