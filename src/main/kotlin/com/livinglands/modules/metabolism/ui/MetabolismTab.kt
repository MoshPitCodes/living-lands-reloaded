package com.livinglands.modules.metabolism.ui

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.livinglands.api.safeService
import com.livinglands.core.ui.ModuleTab
import com.livinglands.modules.metabolism.MetabolismService
import java.util.UUID

/**
 * Metabolism tab for the central modal panel.
 *
 * Displays current metabolism stats (hunger, thirst, energy) and recent consumption history.
 */
class MetabolismTab(
    private val playerId: UUID
) : ModuleTab {

    override val tabId: String = "metabolism"
    override val tabLabel: String = "Metabolism"

    private var service: MetabolismService? = null

    override fun onActivate() {
        // Fetch fresh service reference when tab activates
        service = safeService("metabolism")
    }

    override fun onDeactivate() {
        // Clean up service reference
        service = null
    }

    override fun render(builder: UICommandBuilder) {
        val metabolismService = service

        if (metabolismService == null) {
            // Module not available
            builder.set("#ContentTitle.Visible", true)
            builder.set("#ContentTitle.Text", "Metabolism")
            builder.set("#ContentText.Visible", true)
            builder.set("#ContentText.Text", "Metabolism module is not enabled or not available.")
            return
        }

        // Get current stats
        val stats = metabolismService.getStats(playerId)

        if (stats == null) {
            // No data for player
            builder.set("#ContentTitle.Visible", true)
            builder.set("#ContentTitle.Text", "Metabolism")
            builder.set("#ContentText.Visible", true)
            builder.set("#ContentText.Text", "No metabolism data available.")
            return
        }

        // Display title
        builder.set("#ContentTitle.Visible", true)
        builder.set("#ContentTitle.Text", "Metabolism Stats")

        // Create simple progress bar
        fun createBar(current: Float, max: Float, width: Int = 20): String {
            val filled = ((current / max) * width).toInt().coerceIn(0, width)
            val empty = width - filled
            return "#".repeat(filled) + "-".repeat(empty)
        }

        // Display stats with progress bars
        val statsText = buildString {
            appendLine("METABOLISM STATUS")
            appendLine("---------------------------------------")
            appendLine()

            appendLine("HUNGER: %.1f / 100.0".format(stats.hunger))
            appendLine("  [${createBar(stats.hunger, 100f)}]")
            appendLine()

            appendLine("THIRST: %.1f / 100.0".format(stats.thirst))
            appendLine("  [${createBar(stats.thirst, 100f)}]")
            appendLine()

            appendLine("ENERGY: %.1f / 100.0".format(stats.energy))
            appendLine("  [${createBar(stats.energy, 100f)}]")
            appendLine()

            appendLine("---------------------------------------")
            appendLine("STATUS")
            appendLine("---------------------------------------")

            // Status indicators
            when {
                stats.hunger < 20.0 -> appendLine("! WARNING: Low hunger - eat food soon")
                stats.hunger < 40.0 -> appendLine("* NOTICE: Hunger getting low")
                stats.hunger > 90.0 -> appendLine("+ Well fed!")
            }

            when {
                stats.thirst < 20.0 -> appendLine("! WARNING: Dehydrated - drink water")
                stats.thirst < 40.0 -> appendLine("* NOTICE: Getting thirsty")
                stats.thirst > 90.0 -> appendLine("+ Hydrated!")
            }

            when {
                stats.energy < 20.0 -> appendLine("! WARNING: Exhausted - rest needed")
                stats.energy < 40.0 -> appendLine("* NOTICE: Energy running low")
                stats.energy > 90.0 -> appendLine("+ Well rested!")
            }
        }

        builder.set("#ContentText.Visible", true)
        builder.set("#ContentText.Text", statsText)
    }
}
