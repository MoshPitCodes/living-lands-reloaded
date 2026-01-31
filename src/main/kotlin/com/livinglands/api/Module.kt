package com.livinglands.api

import com.livinglands.core.PlayerSession
import java.util.UUID

/**
 * Base interface for all Living Lands modules.
 * 
 * Modules are self-contained features that register with CoreModule.
 * They follow a lifecycle: DISABLED -> SETUP -> STARTED -> STOPPED/ERROR
 * 
 * Use AbstractModule for the base implementation with common functionality.
 */
sealed interface Module {
    /** Unique module identifier (e.g., "metabolism") */
    val id: String
    
    /** Human-readable name */
    val name: String
    
    /** Module version */
    val version: String
    
    /** Dependencies (other module IDs that must be loaded first) */
    val dependencies: Set<String>
    
    /** Current module state */
    val state: ModuleState
    
    /**
     * Setup phase - initialize resources, register listeners/commands.
     * Called after dependencies are set up, in dependency order.
     */
    suspend fun setup(context: ModuleContext)
    
    /**
     * Start phase - begin operations.
     * Called after all modules are set up, in dependency order.
     */
    suspend fun start()
    
    /**
     * Shutdown phase - cleanup resources, save data.
     * Called in reverse dependency order.
     */
    suspend fun shutdown()
    
    /**
     * Called when configuration is reloaded.
     * Modules should refresh their config instances.
     */
    fun onConfigReload() {}
    
    // ============ Player Lifecycle Hooks ============
    
    /**
     * Called when a player joins the server.
     * The session is already registered in CoreModule.players.
     * 
     * @param playerId The player's UUID
     * @param session The registered player session with entityRef and worldId
     */
    suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {}
    
    /**
     * Called when a player disconnects from the server.
     * Save this player's data here. The session will be unregistered AFTER all modules return.
     * 
     * @param playerId The player's UUID
     * @param session The player session (still valid during this call)
     */
    suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {}
}

/**
 * Represents the current state of a module in its lifecycle.
 */
enum class ModuleState {
    /** Module is registered but not yet set up */
    DISABLED,
    
    /** Module has completed setup phase */
    SETUP,
    
    /** Module is running (started) */
    STARTED,
    
    /** Module has been stopped/shutdown */
    STOPPED,
    
    /** Module encountered an error during lifecycle */
    ERROR,
    
    /** Module is disabled by configuration (setup complete, but not started) */
    DISABLED_BY_CONFIG;
    
    /**
     * Check if the module is in a state where it should process commands and player events.
     * Returns true for STARTED, false for all other states including DISABLED_BY_CONFIG.
     */
    fun isOperational(): Boolean = this == STARTED
    
    /**
     * Check if the module has been initialized (setup or beyond).
     * Returns true for SETUP, STARTED, STOPPED, or DISABLED_BY_CONFIG.
     */
    fun isInitialized(): Boolean = this in listOf(SETUP, STARTED, STOPPED, DISABLED_BY_CONFIG)
}
