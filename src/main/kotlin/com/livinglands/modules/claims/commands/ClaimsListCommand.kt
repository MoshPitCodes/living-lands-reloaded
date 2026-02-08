package com.livinglands.modules.claims.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.livinglands.api.safeService
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.commands.AsyncModuleCommand
import com.livinglands.modules.claims.ClaimsService
import java.awt.Color

/**
 * Command to list all plots (multi-chunk claims) owned by the player.
 *
 * Usage: /ll claims
 *
 * Shows a formatted list of all plots with their chunk counts and names.
 */
class ClaimsListCommand : AsyncModuleCommand(
    name = "claims",
    description = "List all your claim plots",
    moduleId = "claims",
    operatorOnly = false
) {

    private val gold = Color(255, 170, 0)
    private val aqua = Color(85, 255, 255)
    private val gray = Color(170, 170, 170)
    private val yellow = Color(255, 255, 85)

    override fun validateInputs(ctx: CommandContext): ValidationResult {
        if (!ctx.isPlayer) {
            return ValidationResult.error("This command can only be used by players")
        }
        return ValidationResult.success()
    }

    override suspend fun executeAsyncIfModuleEnabled(ctx: CommandContext, validatedData: Any?) {
        val playerRef = ctx.senderAsPlayerRef() ?: return
        val session = CoreModule.players.getAllSessions().find { it.entityRef == playerRef } ?: return
        val playerId = session.playerId

        // Get service
        val service = safeService<ClaimsService>("claims")
        if (service == null) {
            MessageFormatter.commandError(ctx, "Claims service not available")
            return
        }

        // Get all player's claims (plots)
        val claims = service.getClaimsByOwner(playerId)

        if (claims.isEmpty()) {
            MessageFormatter.commandRaw(ctx, "You don't have any claim plots yet", gray)
            MessageFormatter.commandRaw(ctx, "Use /ll menu to open the claims panel and select chunks to claim", yellow)
            return
        }

        // Calculate totals
        val totalChunks = claims.sumOf { it.chunks.size }

        // Display header
        MessageFormatter.commandRaw(ctx, "=== Your Plots (${claims.size}) - $totalChunks chunks total ===", gold)
        MessageFormatter.commandRaw(ctx, "", gray)

        // List each plot
        for ((index, claim) in claims.withIndex()) {
            val plotName = claim.name ?: "Plot ${index + 1}"
            val chunkCount = claim.chunks.size
            val trustedInfo = if (claim.trustedPlayers.isNotEmpty()) {
                " | ${claim.trustedPlayers.size} trusted"
            } else ""

            MessageFormatter.commandRaw(ctx, "  ${index + 1}. $plotName ($chunkCount chunks$trustedInfo)", aqua)

            // Show chunk coordinates (up to 4)
            val chunkCoords = claim.chunks.take(4).joinToString(", ") { "(${it.chunkX}, ${it.chunkZ})" }
            MessageFormatter.commandRaw(ctx, "     Chunks: $chunkCoords", gray)
            if (claim.chunks.size > 4) {
                MessageFormatter.commandRaw(ctx, "     ... and ${claim.chunks.size - 4} more", gray)
            }
        }

        MessageFormatter.commandRaw(ctx, "", gray)

        // Show usage hints
        MessageFormatter.commandRaw(ctx, "Commands:", gold)
        MessageFormatter.commandRaw(ctx, "/ll trust <player> - Allow player to build in your plot", yellow)
        MessageFormatter.commandRaw(ctx, "/ll untrust <player> - Revoke build permission", yellow)
        MessageFormatter.commandRaw(ctx, "/ll menu - Open claims management panel", yellow)
    }

    override fun onCommandAcknowledged(ctx: CommandContext) {
        // No acknowledgment message (instant feedback)
    }
}
