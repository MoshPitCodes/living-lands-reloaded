package com.livinglands.api

import com.hypixel.hytale.component.system.ISystem
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks resources (listeners, systems, commands) registered by a module.
 * 
 * Since Hytale's EventRegistry and EntityStoreRegistry do not provide unregister/unsubscribe APIs,
 * resources registered by modules cannot be automatically cleaned up. This tracker documents
 * what was registered so we can:
 * 
 * 1. Generate cleanup reports on module shutdown
 * 2. Warn about potential memory leaks from unreleased listeners
 * 3. Identify duplicate registrations
 * 4. Provide diagnostic information for troubleshooting
 * 
 * The cleanup report is logged at WARNING level to ensure visibility,
 * as it indicates resource leaks that the framework cannot prevent.
 * 
 * Example cleanup report:
 * ```
 * [WARN] Module 'metabolism' has 3 unreleasable resources:
 *   - 5 event listeners registered (cannot be unregistered - potential memory leak on reload)
 *   - 3 ECS systems registered (cannot be unregistered - system will continue running)
 *   - 2 commands registered: metabolismstats, metabolismrefresh
 * ```
 */
class ModuleResourceTracker(private val moduleId: String) {
    
    /** Event listeners tracked by type */
    private val eventListeners = ConcurrentHashMap<String, Int>()
    
    /** ECS systems tracked by name */
    private val systems = mutableListOf<String>()
    
    /** Commands tracked by name */
    private val commands = mutableListOf<String>()
    
    /** Config reload callbacks tracked by key */
    private val configCallbacks = mutableSetOf<String>()
    
    /**
     * Track that an event listener was registered.
     * Increments the count for this event type.
     */
    fun trackEventListener(eventClassName: String) {
        eventListeners.compute(eventClassName) { _, count -> (count ?: 0) + 1 }
    }
    
    /**
     * Track that an ECS system was registered.
     */
    fun trackSystem(systemName: String) {
        systems.add(systemName)
    }
    
    /**
     * Track that a command was registered.
     */
    fun trackCommand(commandName: String) {
        commands.add(commandName)
    }
    
    /**
     * Track that a config reload callback was registered.
     */
    fun trackConfigCallback(callbackKey: String) {
        configCallbacks.add(callbackKey)
    }
    
    /**
     * Get a comprehensive cleanup report for this module.
     * 
     * Returns a report including:
     * - Count of each resource type registered
     * - List of warnings about resources that cannot be automatically cleaned
     * - Summary of what to expect on module shutdown
     */
    fun getCleanupReport(): ModuleCleanupReport {
        return ModuleCleanupReport(
            moduleId = moduleId,
            eventListenerCount = eventListeners.values.sum(),
            eventListenersByType = eventListeners.toMap(),
            systemCount = systems.size,
            systemNames = systems.toList(),
            commandCount = commands.size,
            commandNames = commands.toList(),
            configCallbackCount = configCallbacks.size,
            warnings = buildCleanupWarnings()
        )
    }
    
    /**
     * Build a list of warnings about resources that cannot be cleaned up.
     */
    private fun buildCleanupWarnings(): List<String> {
        val warnings = mutableListOf<String>()
        
        val listenerCount = eventListeners.values.sum()
        if (listenerCount > 0) {
            warnings.add(
                "$listenerCount event listener${if (listenerCount != 1) "s" else ""} registered " +
                "(cannot be unregistered - potential memory leak on reload)"
            )
        }
        
        if (systems.isNotEmpty()) {
            warnings.add(
                "${systems.size} ECS system${if (systems.size != 1) "s" else ""} registered " +
                "(cannot be unregistered - system will continue running)"
            )
        }
        
        if (commands.isNotEmpty()) {
            warnings.add(
                "${commands.size} command${if (commands.size != 1) "s" else ""} registered: ${commands.joinToString(", ")}"
            )
        }
        
        if (configCallbacks.isNotEmpty()) {
            warnings.add(
                "${configCallbacks.size} config callback${if (configCallbacks.size != 1) "s" else ""} registered " +
                "(will be unregistered on shutdown)"
            )
        }
        
        return warnings
    }
    
    /**
     * Clear all tracked resources.
     * Called at the end of shutdown to reset tracking state.
     */
    fun clear() {
        eventListeners.clear()
        systems.clear()
        commands.clear()
        configCallbacks.clear()
    }
}

/**
 * Report of resources registered by a module and cleanup information.
 * 
 * This data class documents what resources a module registered during its lifetime.
 * It is used to generate meaningful shutdown messages that help developers understand
 * what resources might be leaking (event listeners, ECS systems) and what will be
 * properly cleaned up (config callbacks).
 */
data class ModuleCleanupReport(
    /** The module ID this report is for */
    val moduleId: String,
    
    /** Total count of event listeners registered (by all types) */
    val eventListenerCount: Int,
    
    /** Event listeners counted by type name */
    val eventListenersByType: Map<String, Int>,
    
    /** Total count of ECS systems registered */
    val systemCount: Int,
    
    /** List of system names/types registered */
    val systemNames: List<String>,
    
    /** Total count of commands registered */
    val commandCount: Int,
    
    /** List of command names registered */
    val commandNames: List<String>,
    
    /** Total count of config callbacks registered */
    val configCallbackCount: Int,
    
    /** List of warnings about resources that cannot be cleaned up */
    val warnings: List<String>
) {
    /**
     * Format this report as a human-readable summary.
     * 
     * Example output:
     * ```
     * Module 'metabolism' registered 13 resources:
     *   - 5 event listeners (cannot be unregistered)
     *   - 3 ECS systems: MetabolismTickSystem, FoodConsumptionSystem, RespawnResetSystem (cannot be unregistered)
     *   - 2 commands: metabolismstats, metabolismrefresh
     *   - 3 config callbacks (will be unregistered)
     * ```
     */
    fun formatSummary(): String {
        val totalResources = eventListenerCount + systemCount + commandCount + configCallbackCount
        
        return buildString {
            appendLine("Module '$moduleId' registered $totalResources resources:")
            
            if (eventListenerCount > 0) {
                appendLine("  - $eventListenerCount event listener${if (eventListenerCount != 1) "s" else ""} " +
                    "(cannot be unregistered)")
                eventListenersByType.forEach { (type, count) ->
                    appendLine("    • $type: $count")
                }
            }
            
            if (systemCount > 0) {
                appendLine("  - $systemCount ECS system${if (systemCount != 1) "s" else ""}: " +
                    "${systemNames.joinToString(", ")} (cannot be unregistered)")
            }
            
            if (commandCount > 0) {
                appendLine("  - $commandCount command${if (commandCount != 1) "s" else ""}: " +
                    "${commandNames.joinToString(", ")}")
            }
            
            if (configCallbackCount > 0) {
                appendLine("  - $configCallbackCount config callback${if (configCallbackCount != 1) "s" else ""} " +
                    "(will be unregistered)")
            }
        }
    }
}
