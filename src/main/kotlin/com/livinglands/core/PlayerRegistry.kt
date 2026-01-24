package com.livinglands.core

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active player sessions across all worlds.
 * Thread-safe via ConcurrentHashMap.
 */
class PlayerRegistry {
    
    private val sessions = ConcurrentHashMap<UUID, PlayerSession>()
    
    /**
     * Register a player session when they become ready.
     */
    fun register(session: PlayerSession) {
        val existing = sessions.putIfAbsent(session.playerId, session)
        if (existing != null) {
            throw IllegalStateException("Session already exists for ${session.playerId}")
        }
    }
    
    /**
     * Unregister a player session when they disconnect.
     * Returns the removed session, or null if not found.
     */
    fun unregister(playerId: UUID): PlayerSession? {
        return sessions.remove(playerId)
    }
    
    /**
     * Get a player's session by their UUID.
     */
    fun getSession(playerId: UUID): PlayerSession? {
        return sessions[playerId]
    }
    
    /**
     * Check if a player has an active session.
     */
    fun hasSession(playerId: UUID): Boolean {
        return sessions.containsKey(playerId)
    }
    
    /**
     * Get all active sessions.
     */
    fun getAllSessions(): Collection<PlayerSession> {
        return sessions.values
    }
    
    /**
     * Get all sessions for a specific world.
     */
    fun getSessionsInWorld(worldId: UUID): List<PlayerSession> {
        return sessions.values.filter { it.worldId == worldId }
    }
    
    /**
     * Get the count of active sessions.
     */
    fun getSessionCount(): Int {
        return sessions.size
    }
    
    /**
     * Clear all sessions (used during shutdown).
     */
    fun clear() {
        sessions.clear()
    }
}
