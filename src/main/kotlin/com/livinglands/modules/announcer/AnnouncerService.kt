package com.livinglands.modules.announcer

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.announcer.config.AnnouncerConfig
import com.livinglands.modules.announcer.config.PlaceholderResolver
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for sending announcements to players.
 * Handles MOTD, welcome messages, and message cycling.
 */
class AnnouncerService(
    private val config: AnnouncerConfig,
    private val joinTracker: JoinTracker,
    private val placeholderResolver: PlaceholderResolver,
    private val logger: HytaleLogger
) {
    // Track message cycle indices for rotating through message lists
    private val cycleIndices = ConcurrentHashMap<String, Int>()
    
    /**
     * Sends the Message of the Day to a player.
     * Applies per-world overrides if configured.
     * 
     * @param playerRef Player to send MOTD to
     */
    fun sendMotd(playerRef: PlayerRef) {
        if (!config.motd.enabled) return
        
        // Validate session before sending
        val session = CoreModule.players.getSession(playerRef.uuid) ?: run {
            LoggingManager.warn(logger, "announcer") { 
                "Cannot send MOTD: player ${playerRef.uuid} has no active session" 
            }
            return
        }
        
        // Apply per-world override if exists
        // TODO: Get world name from session for per-world overrides
        val motdMessage = config.motd.message
        
        val resolved = placeholderResolver.resolve(motdMessage, playerRef)
        MessageFormatter.announcement(playerRef, resolved)
        
        LoggingManager.debug(logger, "announcer") { "Sent MOTD to ${playerRef.username}" }
    }
    
    /**
     * Sends a welcome message to a player.
     * Differentiates between first-time joins and returning players.
     * 
     * @param playerRef Player to send welcome message to
     * @param joinInfo Join information from JoinTracker
     */
    fun sendWelcomeMessage(playerRef: PlayerRef, joinInfo: JoinTracker.JoinInfo) {
        if (!config.welcome.enabled) return
        
        // Validate session
        val session = CoreModule.players.getSession(playerRef.uuid) ?: run {
            LoggingManager.warn(logger, "announcer") { 
                "Cannot send welcome: player ${playerRef.uuid} has no active session"
            }
            return
        }
        
        // Determine message type
        val message = when {
            joinInfo.isFirstJoin -> config.welcome.firstJoin
            joinInfo.timeSinceLastSeen > parseDuration(config.welcome.minAbsenceDuration) -> config.welcome.returning
            else -> {
                LoggingManager.debug(logger, "announcer") { 
                    "Skipping welcome message for ${playerRef.username} (recently online)"
                }
                return  // Don't spam if recently online
            }
        }
        
        val resolved = placeholderResolver.resolve(message, playerRef, joinInfo)
        MessageFormatter.announcement(playerRef, resolved)
        
        LoggingManager.debug(logger, "announcer") { 
            "Sent welcome message to ${playerRef.username} (firstJoin=${joinInfo.isFirstJoin})"
        }
    }
    
    /**
     * Gets the next message in a rotating message list.
     * Messages cycle sequentially (1, 2, 3, 1, 2, 3, ...).
     * 
     * @param announcementId Unique identifier for the announcement
     * @param messages List of messages to cycle through
     * @return Next message in the sequence
     */
    fun getNextMessage(announcementId: String, messages: List<String>): String {
        if (messages.isEmpty()) return ""
        if (messages.size == 1) return messages[0]
        
        // Sequential rotation
        val currentIndex = cycleIndices.compute(announcementId) { _, oldIndex ->
            val next = (oldIndex ?: 0) + 1
            next % messages.size
        } ?: 0
        
        return messages[currentIndex]
    }
    
    /**
     * Parses a duration string like "5m", "1h", "30s" into a Duration.
     * 
     * @param duration Duration string (e.g., "5m", "1h", "30s")
     * @return Parsed Duration
     */
    private fun parseDuration(duration: String): Duration {
        if (duration.isEmpty()) return Duration.ZERO
        
        val value = duration.dropLast(1).toLongOrNull() ?: run {
            LoggingManager.warn(logger, "announcer") { "Invalid duration format: $duration" }
            return Duration.ZERO
        }
        
        return when (duration.last()) {
            's' -> Duration.ofSeconds(value)
            'm' -> Duration.ofMinutes(value)
            'h' -> Duration.ofHours(value)
            'd' -> Duration.ofDays(value)
            else -> {
                LoggingManager.warn(logger, "announcer") { "Unknown duration unit: ${duration.last()}" }
                Duration.ZERO
            }
        }
    }
}
