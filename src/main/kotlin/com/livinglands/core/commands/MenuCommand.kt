package com.livinglands.core.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.ui.CentralModalPanel
import com.livinglands.modules.metabolism.ui.MetabolismTab
import com.livinglands.modules.professions.ui.ProfessionsTab
import com.livinglands.modules.claims.ui.ClaimsTab

/**
 * Command to open the central modal panel with module tabs.
 *
 * Usage: /ll menu
 *
 * Opens a tabbed interface showing all enabled modules:
 * - Metabolism tab (hunger, thirst, energy stats)
 * - Professions tab (profession levels and XP)
 * - Claims tab (owned claims and trust management)
 *
 * Tabs are only shown for modules that are currently enabled.
 */
class MenuCommand : CommandBase("menu", "Open the Living Lands central menu") {

    override fun executeSync(ctx: CommandContext) {
        // Ensure sender is a player
        if (!ctx.isPlayer) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }

        val entityRef = ctx.senderAsPlayerRef()
        if (entityRef == null) {
            MessageFormatter.commandError(ctx, "Could not get player reference")
            return
        }

        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }

        if (session == null) {
            MessageFormatter.commandError(ctx, "Could not find player session")
            return
        }

        // Get the world to execute on WorldThread
        val world = com.hypixel.hytale.server.core.universe.Universe.get().getWorld(session.worldId)
        if (world == null) {
            MessageFormatter.commandError(ctx, "Could not find world")
            return
        }

        // All ECS operations must be on WorldThread
        world.execute {
            // Get Player component to access the PlayerRef
            val player = session.store.getComponent(entityRef, Player.getComponentType())
            if (player == null) {
                MessageFormatter.commandError(ctx, "Could not access player component")
                return@execute
            }

            @Suppress("DEPRECATION")
            val playerRef = player.getPlayerRef()
            if (playerRef == null) {
                MessageFormatter.commandError(ctx, "Could not get PlayerRef")
                return@execute
            }

            // Create central modal panel
            val panel = CentralModalPanel(playerRef)

            // Register tabs for enabled modules
            val modules = CoreModule.getAllModules()

            // DEBUG: Log module states
            com.livinglands.core.logging.LoggingManager.info(com.livinglands.core.CoreModule.logger, "core") {
                "Modules found: ${modules.size}"
            }
            modules.forEach { module ->
                com.livinglands.core.logging.LoggingManager.info(com.livinglands.core.CoreModule.logger, "core") {
                    "Module '${module.id}': state=${module.state}, operational=${module.state.isOperational()}"
                }
            }

            // Check if Metabolism module is enabled and register tab
            val metabolismModule = modules.find { it.id == "metabolism" }
            if (metabolismModule?.state?.isOperational() == true) {
                panel.registerTab(MetabolismTab(session.playerId))
            }

            // Check if Professions module is enabled and register tab
            val professionsModule = modules.find { it.id == "professions" }
            if (professionsModule?.state?.isOperational() == true) {
                panel.registerTab(ProfessionsTab(session.playerId))
            }

            // Check if Claims module is enabled and register tab
            val claimsModule = modules.find { it.id == "claims" }
            if (claimsModule?.state?.isOperational() == true) {
                // Get player's chunk position from TransformComponent for the claims grid
                val transform = session.store.getComponent(entityRef, TransformComponent.getComponentType())
                val chunkX = if (transform != null) transform.getPosition().x.toInt() shr 4 else 0
                val chunkZ = if (transform != null) transform.getPosition().z.toInt() shr 4 else 0

                panel.registerTab(ClaimsTab(session.playerId, session.worldId, chunkX, chunkZ))
            }

            // Check if any tabs were registered
            // Note: Don't check getActiveTabId() here because it's only set after open() is called
            val registeredTabs = listOfNotNull(
                if (metabolismModule?.state?.isOperational() == true) "metabolism" else null,
                if (professionsModule?.state?.isOperational() == true) "professions" else null,
                if (claimsModule?.state?.isOperational() == true) "claims" else null
            )

            if (registeredTabs.isEmpty()) {
                MessageFormatter.commandError(ctx, "No modules available - all modules are disabled")
                return@execute
            }

            com.livinglands.core.logging.LoggingManager.info(com.livinglands.core.CoreModule.logger, "core") {
                "Registered tabs: ${registeredTabs.joinToString(", ")}"
            }

            // Open the modal panel
            try {
                player.pageManager.openCustomPage(entityRef, session.store, panel)
                MessageFormatter.commandSuccess(ctx, "Opening menu...")
            } catch (e: Exception) {
                MessageFormatter.commandError(ctx, "Failed to open menu: ${e.message}")
            }
        }
    }
}
