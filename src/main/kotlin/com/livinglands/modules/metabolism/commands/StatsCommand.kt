package com.livinglands.modules.metabolism.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.entity.entities.Player
import com.livinglands.modules.metabolism.MetabolismService
import com.livinglands.core.MessageFormatter
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.pages.MetabolismStatsPage

/**
 * Command to display current metabolism stats in a full-screen UI page.
 * 
 * Usage: /ll show
 * 
 * Opens a detailed stats page showing hunger, thirst, energy, buffs, and debuffs.
 * Can only be used by players (not console).
 * 
 * Uses the new CustomUIPage pattern from hytale-basic-uis.
 */
class StatsCommand(
    private val metabolismService: MetabolismService
) : CommandBase(
    "show",                              // Subcommand name (no "ll" prefix)
    "Display your metabolism stats",     // Description
    false                                // Does not require operator
) {
    
    override fun executeSync(ctx: CommandContext) {
        // Check if sender is a player
        if (!ctx.isPlayer) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }
        
        // Get player entity reference
        val entityRef = ctx.senderAsPlayerRef() ?: run {
            MessageFormatter.commandError(ctx, "Unable to get player reference")
            return
        }
        
        // Find player session by entity ref
        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }
        if (session == null) {
            MessageFormatter.commandError(ctx, "Player session not found")
            return
        }
        
        // Get player UUID
        val playerId = session.playerId.toString()
        
        // Get metabolism stats from service
        val stats = metabolismService.getStats(playerId)
        
        if (stats == null) {
            MessageFormatter.commandError(ctx, "Metabolism data not loaded. Please wait a moment and try again.")
            return
        }
        
        // Get buffs/debuffs from systems
        val buffsSystem = metabolismService.getBuffsSystem()
        val debuffsSystem = metabolismService.getDebuffsSystem()
        
        val activeBuffs = buffsSystem?.getActiveBuffNames(session.playerId) ?: emptyList()
        val activeDebuffs = debuffsSystem?.getActiveDebuffNames(session.playerId) ?: emptyList()
        
        // Open the stats page (must run on world thread)
        session.world.execute {
            try {
                val player = session.store.getComponent(session.entityRef, Player.getComponentType())
                if (player != null) {
                    @Suppress("DEPRECATION")
                    val playerRef = player.playerRef
                    if (playerRef != null) {
                        val page = MetabolismStatsPage(
                            playerRef,
                            stats.hunger,
                            stats.thirst,
                            stats.energy,
                            activeBuffs,
                            activeDebuffs
                        )
                        player.pageManager.openCustomPage(session.entityRef, session.store, page)
                    }
                }
            } catch (e: Exception) {
                MessageFormatter.commandError(ctx, "Failed to open stats page: ${e.message}")
            }
        }
    }
}
