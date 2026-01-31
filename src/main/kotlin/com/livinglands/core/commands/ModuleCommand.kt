package com.livinglands.core.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.api.Module
import com.livinglands.api.ModuleState
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter

/**
 * Base class for commands that belong to a specific module.
 * 
 * Provides automatic module state checking before command execution.
 * Commands will only execute if the associated module is in STARTED state.
 * If the module is disabled or in an error state, a user-friendly message is shown.
 * 
 * ## Usage
 * 
 * ```kotlin
 * class MyModuleCommand : ModuleCommand(
 *     name = "mycommand",
 *     description = "Does something cool",
 *     moduleId = "mymodule"
 * ) {
 *     override fun executeIfModuleEnabled(ctx: CommandContext) {
 *         // Your command logic here - only called if module is STARTED
 *     }
 * }
 * ```
 * 
 * ## Behavior by Module State
 * 
 * | State | Command Behavior |
 * |-------|-----------------|
 * | STARTED | Executes normally |
 * | DISABLED_BY_CONFIG | Shows "module disabled" message |
 * | SETUP | Shows "module not started" message |
 * | ERROR | Shows "module error" message |
 * | STOPPED | Shows "module stopped" message |
 * | DISABLED | Shows "module not loaded" message |
 * 
 * ## Thread Safety
 * 
 * The module state check is thread-safe as ModuleState is an enum and
 * the modules map in CoreModule uses ConcurrentHashMap.
 * 
 * @param name Command name (e.g., "professions")
 * @param description Command description for help
 * @param moduleId The ID of the module this command belongs to
 * @param operatorOnly Whether command requires operator permissions (default: false)
 */
abstract class ModuleCommand(
    name: String,
    description: String,
    protected val moduleId: String,
    operatorOnly: Boolean = false
) : CommandBase(name, description, operatorOnly) {
    
    /**
     * Final implementation that checks module state before executing.
     * Subclasses should override [executeIfModuleEnabled] instead.
     */
    final override fun executeSync(ctx: CommandContext) {
        val module = CoreModule.getModule<Module>(moduleId)
        
        when {
            module == null -> {
                MessageFormatter.commandError(
                    ctx,
                    "Module '$moduleId' is not loaded. Contact server administrator."
                )
            }
            
            module.state == ModuleState.STARTED -> {
                // Module is operational - execute the command
                executeIfModuleEnabled(ctx)
            }
            
            module.state == ModuleState.DISABLED_BY_CONFIG -> {
                MessageFormatter.commandError(
                    ctx,
                    "${module.name} module is disabled in configuration."
                )
            }
            
            module.state == ModuleState.ERROR -> {
                MessageFormatter.commandError(
                    ctx,
                    "${module.name} module encountered an error. Check server logs."
                )
            }
            
            module.state == ModuleState.SETUP -> {
                MessageFormatter.commandError(
                    ctx,
                    "${module.name} module is initializing. Please wait..."
                )
            }
            
            module.state == ModuleState.STOPPED -> {
                MessageFormatter.commandError(
                    ctx,
                    "${module.name} module has been stopped."
                )
            }
            
            else -> {
                MessageFormatter.commandError(
                    ctx,
                    "${module.name} module is not available (state: ${module.state})."
                )
            }
        }
    }
    
    /**
     * Execute the command logic. Only called when the module is in STARTED state.
     * 
     * Subclasses must implement this instead of [executeSync].
     * 
     * @param ctx The command context with sender information and arguments
     */
    protected abstract fun executeIfModuleEnabled(ctx: CommandContext)
    
    /**
     * Check if the module is currently operational.
     * Can be used in async callbacks to verify module is still running.
     * 
     * @return true if module is in STARTED state
     */
    protected fun isModuleOperational(): Boolean {
        val module = CoreModule.getModule<Module>(moduleId)
        return module?.state?.isOperational() == true
    }
    
    /**
     * Get the module instance if it's operational.
     * Returns null if module is not found or not in STARTED state.
     */
    protected inline fun <reified T : Module> getOperationalModule(): T? {
        val module = CoreModule.getModule<T>(moduleId)
        return if (module?.state?.isOperational() == true) module else null
    }
}
