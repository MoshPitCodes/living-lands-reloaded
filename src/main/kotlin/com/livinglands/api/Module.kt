package com.livinglands.api

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
    ERROR
}
