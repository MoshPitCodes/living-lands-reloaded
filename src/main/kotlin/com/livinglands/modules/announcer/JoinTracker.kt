package com.livinglands.modules.announcer

import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks player joins in-memory for welcome message differentiation.
 * Does NOT persist to database - resets on server restart.
 */
class JoinTracker {
    private val joinData = ConcurrentHashMap<UUID, PlayerJoinData>()
    
    /**
     * Internal data structure for tracking join history.
     */
    private data class PlayerJoinData(
        val firstJoined: Instant,
        var lastSeen: Instant,
        val joinCount: AtomicInteger
    )
    
    /**
     * Records a player join and returns information about the join.
     * 
     * @param playerId The player's UUID
     * @return Join information (first join, count, time since last seen)
     */
    fun recordJoin(playerId: UUID): JoinInfo {
        val now = Instant.now()
        val data = joinData.computeIfAbsent(playerId) {
            PlayerJoinData(now, now, AtomicInteger(0))
        }
        
        val count = data.joinCount.incrementAndGet()
        val isFirstJoin = count == 1
        val timeSinceLastSeen = Duration.between(data.lastSeen, now)
        data.lastSeen = now
        
        return JoinInfo(
            isFirstJoin = isFirstJoin,
            joinCount = count,
            firstJoined = data.firstJoined,
            timeSinceLastSeen = timeSinceLastSeen
        )
    }
    
    /**
     * Information about a player's join.
     */
    data class JoinInfo(
        val isFirstJoin: Boolean,
        val joinCount: Int,
        val firstJoined: Instant,
        val timeSinceLastSeen: Duration
    )
}
