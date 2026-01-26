package com.livinglands.modules.metabolism.pages

import com.livinglands.core.ui.BasicCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import javax.annotation.Nonnull

/**
 * Full-screen page displaying detailed metabolism statistics.
 * 
 * This is different from the persistent HUD overlay - this is a modal page
 * that shows detailed stats when the player wants to review their status.
 * 
 * Opens with `/metabolism stats` command.
 * 
 * @param playerRef The player viewing the stats
 * @param hunger Current hunger value (0-100)
 * @param thirst Current thirst value (0-100)
 * @param energy Current energy value (0-100)
 * @param activeBuffs List of active buff names
 * @param activeDebuffs List of active debuff names
 */
class MetabolismStatsPage(
    @Nonnull playerRef: PlayerRef,
    private val hunger: Float,
    private val thirst: Float,
    private val energy: Float,
    private val activeBuffs: List<String> = emptyList(),
    private val activeDebuffs: List<String> = emptyList()
) : BasicCustomUIPage(playerRef) {
    
    @Nonnull
    override fun buildPage(@Nonnull cmd: UICommandBuilder) {
        // Load the UI template
        cmd.append("Pages/MetabolismStatsPage.ui")
        
        // Set stat values
        cmd.set("#HungerValue.Text", "${hunger.toInt()} / 100")
        cmd.set("#ThirstValue.Text", "${thirst.toInt()} / 100")
        cmd.set("#EnergyValue.Text", "${energy.toInt()} / 100")
        
        // Note: Progress bars can't be dynamically sized via cmd.set() 
        // They would need to be pre-rendered with different widths or use a different UI approach
        
        // Set buffs display
        val buffsText = if (activeBuffs.isEmpty()) {
            "None"
        } else {
            activeBuffs.joinToString("\n")
        }
        cmd.set("#BuffsList.Text", buffsText)
        
        // Set debuffs display
        val debuffsText = if (activeDebuffs.isEmpty()) {
            "None"
        } else {
            activeDebuffs.joinToString("\n")
        }
        cmd.set("#DebuffsList.Text", debuffsText)
    }
}
