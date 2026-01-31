package com.livinglands.modules.announcer

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.announcer.config.AnnouncerConfig
import com.livinglands.modules.announcer.config.PlaceholderResolver
import kotlinx.coroutines.*
import java.time.Duration
import kotlin.coroutines.coroutineContext

/**
 * Coroutine-based scheduler for recurring announcements.
 * Manages multiple concurrent announcement schedules.
 */
class AnnouncerScheduler(
    private val config: AnnouncerConfig,
    private val service: AnnouncerService,
    private val placeholderResolver: PlaceholderResolver,
    private val logger: HytaleLogger
) {
    private var supervisorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Starts all enabled recurring announcements.
     * Each announcement runs in its own coroutine.
     */
    fun start() {
        if (!config.recurring.enabled) {
            LoggingManager.info(logger, "announcer") { "Recurring announcements disabled" }
            return
        }
        
        stop()  // Stop existing scheduler if running
        
        val enabledAnnouncements = config.recurring.announcements.filter { it.enabled }
        
        if (enabledAnnouncements.isEmpty()) {
            LoggingManager.info(logger, "announcer") { "No enabled recurring announcements" }
            return
        }
        
        supervisorJob = scope.launch {
            enabledAnnouncements.forEach { announcement ->
                launch {
                    runAnnouncement(announcement)
                }
            }
        }
        
        LoggingManager.info(logger, "announcer") { 
            "Started ${enabledAnnouncements.size} recurring announcements"
        }
    }
    
    /**
     * Runs a single recurring announcement in a loop.
     * 
     * @param announcement Announcement configuration
     */
    private suspend fun runAnnouncement(announcement: com.livinglands.modules.announcer.config.AnnouncementConfig) {
        val interval = parseDuration(announcement.interval)
        
        if (interval.isZero || interval.isNegative) {
            LoggingManager.warn(logger, "announcer") { 
                "Invalid interval for announcement '${announcement.id}': ${announcement.interval}" 
            }
            return
        }
        
        LoggingManager.info(logger, "announcer") { 
            "Announcement '${announcement.id}' starting (interval: ${interval.toMinutes()}min)"
        }
        
        while (coroutineContext.isActive) {
            delay(interval.toMillis())
            
            try {
                broadcastAnnouncement(announcement)
            } catch (e: Exception) {
                LoggingManager.error(logger, "announcer", e) { 
                    "Error sending announcement '${announcement.id}'"
                }
            }
        }
    }
    
    /**
     * Broadcasts an announcement to all target players.
     * 
     * @param announcement Announcement to broadcast
     */
    private fun broadcastAnnouncement(announcement: com.livinglands.modules.announcer.config.AnnouncementConfig) {
        val message = service.getNextMessage(announcement.id, announcement.messages)
        if (message.isEmpty()) {
            LoggingManager.warn(logger, "announcer") { 
                "No messages configured for announcement '${announcement.id}'" 
            }
            return
        }
        
        // Get target players
        val targetPlayers = when {
            announcement.target == "all" -> CoreModule.players.getAllSessions()
            announcement.target.startsWith("world:") -> {
                val worldName = announcement.target.substringAfter("world:")
                CoreModule.players.getAllSessions().filter { session ->
                    try {
                        session.world.name == worldName
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            else -> {
                LoggingManager.warn(logger, "announcer") { 
                    "Unknown target type for announcement '${announcement.id}': ${announcement.target}"
                }
                emptyList()
            }
        }
        
        if (targetPlayers.isEmpty()) {
            LoggingManager.debug(logger, "announcer") { 
                "No target players for announcement '${announcement.id}'"
            }
            return
        }
        
        // Send to each player
        var successCount = 0
        targetPlayers.forEach { session ->
            try {
                // Get PlayerRef from session
                val playerRef = session.world.entityStore.store.getComponent(
                    session.entityRef,
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType()
                ) ?: return@forEach
                
                val resolved = placeholderResolver.resolve(message, playerRef)
                MessageFormatter.announcement(playerRef, resolved)
                successCount++
            } catch (e: Exception) {
                LoggingManager.warn(logger, "announcer") { 
                    "Failed to send announcement to player ${session.playerId}: ${e.message}"
                }
            }
        }
        
        LoggingManager.debug(logger, "announcer") { 
            "Sent announcement '${announcement.id}' to $successCount/${targetPlayers.size} players"
        }
    }
    
    /**
     * Stops all running announcement coroutines.
     */
    fun stop() {
        supervisorJob?.cancel()
        supervisorJob = null
        LoggingManager.info(logger, "announcer") { "Stopped recurring announcements" }
    }
    
    /**
     * Parses a duration string like "5m", "1h", "30s" into a Duration.
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
                Duration.ofMinutes(5)  // Default to 5 minutes
            }
        }
    }
}
