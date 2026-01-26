package com.livinglands.core.hud

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.UUID

/**
 * Combined HUD for Living Lands that contains all HUD elements.
 * This is the single CustomUIHud registered with the player's HudManager.
 * 
 * Uses the v2.6.0 approach: ONE UI file with all elements, individual modules
 * only update values with builder.set(), never call builder.append().
 * 
 * UI file: Common/UI/Custom/Hud/LivingLandsHud.ui
 */
class LivingLandsHud(
    playerRef: PlayerRef,
    private val playerId: UUID,
    private val logger: HytaleLogger
) : CustomUIHud(playerRef) {

    companion object {
        private const val UI_FILE = "Hud/LivingLandsHud.ui"
    }

    /** Track if this is the first build (need to append UI file) */
    private var firstBuild = true

    override fun build(builder: UICommandBuilder) {
        // Only append UI file on first build
        if (firstBuild) {
            logger.atInfo().log("LivingLandsHud: First build, appending UI file: $UI_FILE")
            builder.append(UI_FILE)
            firstBuild = false
        }
        
        // Individual modules will call updateElements() to set values
    }

    /**
     * Update HUD elements with new values.
     * Call this from modules when values change.
     * 
     * @param builder UICommandBuilder with set() calls for updated values
     */
    fun updateElements(builder: UICommandBuilder) {
        // Trigger an update with the provided commands
        update(false, builder)
    }
}
