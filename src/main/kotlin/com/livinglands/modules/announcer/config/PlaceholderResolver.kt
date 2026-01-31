package com.livinglands.modules.announcer.config

import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.core.CoreModule
import com.livinglands.modules.announcer.JoinTracker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Resolves placeholder variables in announcement messages.
 * 
 * Supported placeholders:
 * - {player_name} - Player's display name
 * - {world_name} - Current world name
 * - {online_count} - Total players online
 * - {join_count} - Player's visit count
 * - {first_joined} - Date of first join (formatted)
 * - {server_name} - Server name from config
 */
class PlaceholderResolver(
    private val serverName: String = "Living Lands"
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
    }
    
    /**
     * Resolves all placeholders in a message.
     * 
     * @param message Message with placeholders
     * @param playerRef Player to resolve placeholders for
     * @param joinInfo Optional join information (for {join_count}, {first_joined})
     * @return Message with placeholders replaced
     */
    fun resolve(
        message: String,
        playerRef: PlayerRef,
        joinInfo: JoinTracker.JoinInfo? = null
    ): String {
        var result = message
        
        // Player-specific
        result = result.replace("{player_name}", playerRef.username)
        
        // World-specific (safe navigation - player may not be in world yet)
        val worldName = try {
            // PlayerRef doesn't have direct world access - get from entity store
            "Unknown"  // TODO: Get world name from session if needed
        } catch (e: Exception) {
            "Unknown"
        }
        result = result.replace("{world_name}", worldName)
        
        // Server-wide
        result = result.replace("{server_name}", serverName)
        result = result.replace("{online_count}", CoreModule.players.getAllSessions().size.toString())
        
        // Join tracking (only if joinInfo provided)
        joinInfo?.let {
            result = result.replace("{join_count}", it.joinCount.toString())
            result = result.replace("{first_joined}", formatDate(it.firstJoined))
        }
        
        return result
    }
    
    private fun formatDate(instant: Instant): String {
        return DATE_FORMATTER.format(instant)
    }
}
