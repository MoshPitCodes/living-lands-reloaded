package com.livinglands.modules.professions.ui

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.livinglands.api.safeService
import com.livinglands.core.ui.ModuleTab
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.data.Profession
import java.util.UUID

/**
 * Professions tab for the central modal panel.
 *
 * Displays profession levels, XP progress, and unlocked abilities.
 */
class ProfessionsTab(
    private val playerId: UUID
) : ModuleTab {

    override val tabId: String = "professions"
    override val tabLabel: String = "Professions"

    private var service: ProfessionsService? = null

    override fun onActivate() {
        // Fetch fresh service reference when tab activates
        service = safeService("professions")
    }

    override fun onDeactivate() {
        // Clean up service reference
        service = null
    }

    override fun render(builder: UICommandBuilder) {
        val professionsService = service

        if (professionsService == null) {
            // Module not available
            builder.set("#ContentTitle.Visible", true)
            builder.set("#ContentTitle.Text", "Professions")
            builder.set("#ContentText.Visible", true)
            builder.set("#ContentText.Text", "Professions module is not enabled or not available.")
            return
        }

        // Display title
        builder.set("#ContentTitle.Visible", true)
        builder.set("#ContentTitle.Text", "Professions Overview")

        // Get all profession stats
        val allStats = professionsService.getAllStats(playerId)

        if (allStats.isEmpty()) {
            // No data for player
            builder.set("#ContentText.Visible", true)
            builder.set("#ContentText.Text", "No profession data available.")
            return
        }

        // Get tier indicator
        fun getTierIndicator(level: Int): String {
            return when {
                level >= 100 -> "*** MASTER"
                level >= 45 -> "** JOURNEYMAN"
                level >= 15 -> "* NOVICE"
                else -> ""
            }
        }

        // Display profession summary
        val professionsText = buildString {
            appendLine("PROFESSIONS STATUS")
            appendLine("---------------------------------------")
            appendLine()

            // Sort professions by predefined order
            val professionOrder = listOf(
                Profession.COMBAT,
                Profession.MINING,
                Profession.LOGGING,
                Profession.BUILDING,
                Profession.GATHERING
            )

            for (profession in professionOrder) {
                val stats = allStats[profession] ?: continue

                appendLine("${profession.name.uppercase()}")
                appendLine("  Level ${stats.level}  ${getTierIndicator(stats.level)}")

                if (stats.level < 100) {
                    appendLine("  Total XP: ${stats.xp}")
                    appendLine("  Working towards Level ${stats.level + 1}")
                } else {
                    appendLine("  MAX LEVEL REACHED")
                    appendLine("  Total XP: ${stats.xp}")
                }
                appendLine()
            }

            appendLine("---------------------------------------")
            appendLine("ABILITY TIERS")
            appendLine("---------------------------------------")
            appendLine("  * Tier 1 (Level 15):  +15% XP Boost")
            appendLine("  ** Tier 2 (Level 45):  Max Stat Bonus")
            appendLine("  *** Tier 3 (Level 100): Master Ability")
            appendLine()
            appendLine("Use /ll professions for detailed abilities")
        }

        builder.set("#ContentText.Visible", true)
        builder.set("#ContentText.Text", professionsText)
    }
}
