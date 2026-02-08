package com.livinglands.modules.claims.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.livinglands.api.safeService
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.commands.AsyncModuleCommand
import com.livinglands.core.persistence.GlobalPlayerDataRepository
import com.livinglands.modules.claims.ChunkPosition
import com.livinglands.modules.claims.ClaimsService
import com.livinglands.modules.claims.TrustResult
import java.awt.Color
import java.util.UUID

/**
 * Command to trust a player in the current claim.
 *
 * Usage: /ll trust <player>
 *
 * Allows the specified player to build in your claim at the current chunk location.
 */
class TrustCommand : AsyncModuleCommand(
    name = "trust",
    description = "Allow a player to build in your claim",
    moduleId = "claims",
    operatorOnly = false
) {

    private val green = Color(85, 255, 85)
    private val yellow = Color(255, 255, 85)

    override fun validateInputs(ctx: CommandContext): ValidationResult {
        if (!ctx.isPlayer) {
            return ValidationResult.error("This command can only be used by players")
        }

        val args = ctx.inputString.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (args.isEmpty()) {
            return ValidationResult.error("Usage: /ll trust <player>")
        }

        return ValidationResult.success(args[0])
    }

    override suspend fun executeAsyncIfModuleEnabled(ctx: CommandContext, validatedData: Any?) {
        val playerRef = ctx.senderAsPlayerRef() ?: return
        val session = CoreModule.players.getAllSessions().find { it.entityRef == playerRef } ?: return
        val playerId = session.playerId
        val targetPlayerName = validatedData as String

        // Get service
        val service = safeService<ClaimsService>("claims")
        if (service == null) {
            MessageFormatter.commandError(ctx, "Claims service not available")
            return
        }

        // Get player position to find the claim
        val transform = session.store.getComponent(playerRef, TransformComponent.getComponentType())
        if (transform == null) {
            MessageFormatter.commandError(ctx, "Unable to determine your position")
            return
        }

        // Calculate chunk position
        val pos = transform.getPosition()
        val chunkX = pos.x.toInt() shr 4
        val chunkZ = pos.z.toInt() shr 4
        val worldId = session.worldId
        val chunkPosition = ChunkPosition(worldId, chunkX, chunkZ)

        // Find the claim (plot) containing this chunk
        val claims = service.getClaimsByOwner(playerId)
        val claim = claims.find { it.containsChunk(chunkPosition) }

        if (claim == null) {
            MessageFormatter.commandError(ctx, "You don't have a claim at this location")
            MessageFormatter.commandRaw(ctx, "Stand in your claim and try again", yellow)
            return
        }

        // Look up target player by name using global player database
        val playerRepo = CoreModule.services.get<GlobalPlayerDataRepository>()
        val targetPlayerData = playerRepo?.findByName(targetPlayerName)

        val targetPlayerId: UUID = if (targetPlayerData != null) {
            UUID.fromString(targetPlayerData.playerId)
        } else {
            MessageFormatter.commandError(ctx, "Player '$targetPlayerName' not found")
            return
        }

        // Trust the player
        val result = service.trustPlayer(playerId, claim.id, targetPlayerId)

        when (result) {
            is TrustResult.Success -> {
                MessageFormatter.commandRaw(ctx, "✓ Trusted $targetPlayerName in this claim", green)
            }
            is TrustResult.ClaimNotFound -> {
                MessageFormatter.commandError(ctx, "Claim not found")
            }
            is TrustResult.NotOwner -> {
                MessageFormatter.commandError(ctx, "You don't own this claim")
            }
            is TrustResult.AlreadyTrusted -> {
                MessageFormatter.commandError(ctx, "$targetPlayerName is already trusted in this claim")
            }
            is TrustResult.LimitReached -> {
                MessageFormatter.commandError(ctx, "This claim has reached the maximum number of trusted players (${result.max})")
            }
            is TrustResult.DatabaseError -> {
                MessageFormatter.commandError(ctx, "Failed to save trust - contact server admin")
            }
        }
    }

    override fun onCommandAcknowledged(ctx: CommandContext) {
        // No acknowledgment message (instant feedback)
    }
}
