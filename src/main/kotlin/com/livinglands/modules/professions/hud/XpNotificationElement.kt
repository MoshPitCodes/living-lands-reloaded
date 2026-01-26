package com.livinglands.modules.professions.hud

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.modules.professions.data.Profession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * HUD element for displaying temporary XP gain notifications with rate limiting.
 * 
 * Shows a popup notification (top left, next to metabolism bars) when XP is gained.
 * To prevent spam, XP gains are accumulated over a 15-second window and displayed
 * as a single notification.
 * 
 * Position: Top-left corner, to the right of metabolism bars
 * Duration: 3 seconds (display), 15 seconds (accumulation window)
 * 
 * Example: If player mines 10 stone blocks in 15 seconds:
 * - Shows: "MINING +50 XP" (accumulated total)
 * - Instead of 10 separate "+5 XP" notifications
 * 
 * @param playerRef The player this notification is for
 * @param playerId Player's UUID
 */
class XpNotificationElement(
    playerRef: PlayerRef,
    private val playerId: UUID
) : CustomUIHud(playerRef) {
    
    /** Current notification visibility */
    private val visible = AtomicBoolean(false)
    
    /** Current profession being displayed */
    private val currentProfession = AtomicReference<Profession?>(null)
    
    /** Current XP amount being displayed */
    private val currentXpAmount = AtomicLong(0)
    
    /** Timestamp when notification was shown */
    private val showTimestamp = AtomicLong(0)
    
    /** Duration to show notification after accumulation stops (milliseconds) */
    private val displayDuration = 3000L
    
    /** Accumulation window - collect XP gains for this long before showing (milliseconds) */
    private val accumulationWindow = 15000L
    
    /** Accumulated XP per profession (within current accumulation window) */
    private val accumulatedXp = ConcurrentHashMap<Profession, AccumulationState>()
    
    /**
     * State for tracking accumulated XP for a profession.
     */
    private data class AccumulationState(
        /** Total XP accumulated in this window */
        var totalXp: Long = 0L,
        /** Timestamp when first XP was gained in this window */
        var windowStart: Long = 0L,
        /** Timestamp of last XP gain */
        var lastGain: Long = 0L
    )
    
    companion object {
        /** HUD namespace for XP notifications */
        const val NAMESPACE = "livinglands:xpnotification"
        
        /** Profession colors (matching panel) */
        private val PROFESSION_COLORS = mapOf(
            Profession.COMBAT to "#e74c3c",
            Profession.MINING to "#3498db",
            Profession.LOGGING to "#27ae60",
            Profession.BUILDING to "#f39c12",
            Profession.GATHERING to "#9b59b6"
        )
    }
    
    /**
     * Build the XP notification UI.
     * 
     * Checks for accumulated XP ready to display and manages notification lifecycle.
     * Auto-hides after display duration expires.
     */
    override fun build(builder: UICommandBuilder) {
        // Always append the UI file (uses same file as professions panel)
        // Path is relative to Common/UI/Custom/ - Hytale auto-prepends the base path
        builder.append("Hud/ProfessionsPanel.ui")
        
        val now = System.currentTimeMillis()
        
        // Check for accumulated XP ready to be shown
        if (!visible.get()) {
            // Find profession with accumulated XP that hasn't gained more in 15 seconds
            for ((profession, state) in accumulatedXp) {
                val timeSinceLastGain = now - state.lastGain
                
                // If it's been 15 seconds since last gain, show the notification
                if (timeSinceLastGain >= accumulationWindow && state.totalXp > 0) {
                    currentProfession.set(profession)
                    currentXpAmount.set(state.totalXp)
                    showTimestamp.set(now)
                    visible.set(true)
                    
                    // Clear this accumulation
                    state.totalXp = 0
                    break // Only show one notification at a time
                }
            }
        }
        
        // Check if notification should auto-hide
        if (visible.get()) {
            val elapsedTime = now - showTimestamp.get()
            if (elapsedTime >= displayDuration) {
                hide()
            }
        }
        
        // Set notification visibility
        builder.set("#XpNotification.Visible", visible.get())
        
        // If visible, set the content
        if (visible.get()) {
            val profession = currentProfession.get()
            val xpAmount = currentXpAmount.get()
            
            if (profession != null) {
                val professionName = profession.name
                val professionColor = PROFESSION_COLORS[profession] ?: "#95a5a6"
                
                builder.set("#XpProfessionName.Text", professionName)
                builder.set("#XpGainText.Text", "+$xpAmount XP")
                
                // Apply profession color to XP text
                builder.set("#XpGainText.Style.TextColor", professionColor)
            }
        }
    }
    
    /**
     * Accumulate XP gain for a profession.
     * 
     * XP gains are accumulated over a 15-second window to prevent notification spam.
     * After 15 seconds of no gains, the accumulated total is displayed.
     * 
     * @param profession The profession that gained XP
     * @param xpAmount The amount of XP gained
     */
    fun showXpGain(profession: Profession, xpAmount: Long) {
        val now = System.currentTimeMillis()
        
        // Get or create accumulation state for this profession
        val state = accumulatedXp.computeIfAbsent(profession) {
            AccumulationState(windowStart = now, lastGain = now)
        }
        
        // Check if we need to start a new accumulation window
        val timeSinceLastGain = now - state.lastGain
        if (timeSinceLastGain > accumulationWindow) {
            // Reset window if it's been too long since last gain
            state.totalXp = xpAmount
            state.windowStart = now
            state.lastGain = now
        } else {
            // Accumulate XP within current window
            state.totalXp += xpAmount
            state.lastGain = now
        }
    }
    
    /**
     * Hide the notification immediately.
     */
    fun hide() {
        visible.set(false)
        currentProfession.set(null)
        currentXpAmount.set(0)
    }
    
    /**
     * Check if notification is currently visible.
     */
    fun isVisible(): Boolean = visible.get()
}
