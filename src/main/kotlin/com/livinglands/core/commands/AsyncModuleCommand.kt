package com.livinglands.core.commands

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.api.Module
import com.livinglands.api.ModuleState
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Base class for async commands that belong to a specific module.
 * 
 * Combines the functionality of [AsyncCommandBase] with module state checking from [ModuleCommand].
 * Commands will only execute if the associated module is in STARTED state.
 * If the module is disabled or in an error state, a user-friendly message is shown.
 * 
 * ## Usage
 * 
 * ```kotlin
 * class MyAsyncModuleCommand : AsyncModuleCommand(
 *     name = "mycommand",
 *     description = "Does something async",
 *     moduleId = "mymodule",
 *     operatorOnly = true
 * ) {
 *     override fun validateInputs(ctx: CommandContext): ValidationResult {
 *         // Parse and validate arguments
 *         return ValidationResult.success(parsedData)
 *     }
 *     
 *     override suspend fun executeAsyncIfModuleEnabled(ctx: CommandContext, validatedData: Any?) {
 *         // Your async command logic - only called if module is STARTED
 *     }
 * }
 * ```
 * 
 * @param name Command name (e.g., "prof")
 * @param description Command description for help
 * @param moduleId The ID of the module this command belongs to
 * @param operatorOnly Whether command requires operator permissions (default: false)
 */
abstract class AsyncModuleCommand(
    name: String,
    description: String,
    protected val moduleId: String,
    operatorOnly: Boolean = false
) : CommandBase(name, description, operatorOnly) {
    
    init {
        // Allow extra arguments - we parse them manually in validateInputs()
        setAllowsExtraArguments(true)
    }
    
    /**
     * Logger instance for command operations.
     */
    protected val logger: HytaleLogger = CoreModule.logger
    
    /**
     * Coroutine scope for async command execution.
     */
    protected val commandScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Final implementation that checks module state before executing.
     * Subclasses should override [validateInputs] and [executeAsyncIfModuleEnabled] instead.
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
                // Module is operational - proceed with async execution
                executeAsyncPipeline(ctx)
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
     * Execute the async pipeline: validate -> acknowledge -> execute async.
     */
    private fun executeAsyncPipeline(ctx: CommandContext) {
        try {
            // 1. Validate inputs synchronously (fast, immediate error feedback)
            val validationResult = validateInputs(ctx)
            
            if (!validationResult.isValid) {
                validationResult.errorMessage?.let { error ->
                    MessageFormatter.commandError(ctx, error)
                }
                return
            }
            
            // 2. Acknowledge command execution (optional, can be overridden)
            onCommandAcknowledged(ctx)
            
            // 3. Execute async work without blocking
            commandScope.launch {
                try {
                    // Double-check module is still operational before async work
                    if (!isModuleOperational()) {
                        LoggingManager.warn(logger, "core") { "Module '$moduleId' became non-operational during async execution" }
                        return@launch
                    }
                    
                    executeAsyncIfModuleEnabled(ctx, validationResult.validatedData)
                    onCommandSuccess(ctx, validationResult.validatedData)
                } catch (e: Exception) {
                    LoggingManager.error(logger, "core", e) { "Async command '$name' failed" }
                    onCommandFailure(ctx, validationResult.validatedData, e)
                }
            }
            
        } catch (e: Exception) {
            MessageFormatter.commandError(ctx, "Command failed: ${e.message}")
            LoggingManager.error(logger, "core", e) { "Error in command '$name'" }
        }
    }
    
    /**
     * Validate command inputs synchronously.
     * This is called on the command thread and should be fast (no I/O).
     * 
     * @param ctx Command context with input data
     * @return ValidationResult containing either validated data or error message
     */
    abstract fun validateInputs(ctx: CommandContext): ValidationResult
    
    /**
     * Execute the command asynchronously. Only called when the module is in STARTED state.
     * 
     * Subclasses must implement this instead of [executeSync].
     * 
     * **Note:** Do not send messages via ctx here - it may be stale.
     * Use logger for operational feedback, or find the player via CoreModule
     * and send messages through the world thread.
     * 
     * @param ctx Command context (for reference only, don't send messages)
     * @param validatedData Data returned from validateInputs()
     */
    abstract suspend fun executeAsyncIfModuleEnabled(ctx: CommandContext, validatedData: Any?)
    
    /**
     * Called when command is acknowledged (before async execution).
     * Override to provide custom acknowledgment messages.
     * 
     * Default: Sends "Processing command..." message
     */
    protected open fun onCommandAcknowledged(ctx: CommandContext) {
        MessageFormatter.commandInfo(ctx, "Processing command...")
    }
    
    /**
     * Called when async execution succeeds.
     * Default: Logs success, no player message (fire-and-forget pattern)
     */
    protected open fun onCommandSuccess(ctx: CommandContext, validatedData: Any?) {
        LoggingManager.debug(logger, "core") { "Command '$name' completed successfully" }
    }
    
    /**
     * Called when async execution fails.
     * Default: Logs error, no player message
     */
    protected open fun onCommandFailure(ctx: CommandContext, validatedData: Any?, error: Exception) {
        LoggingManager.error(logger, "core", error) { "Command '$name' failed: ${error.message}" }
    }
    
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
     * Result of input validation.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val validatedData: Any? = null,
        val errorMessage: String? = null
    ) {
        companion object {
            /**
             * Create a successful validation result.
             * @param data The validated data to pass to executeAsync()
             */
            fun success(data: Any? = null): ValidationResult {
                return ValidationResult(isValid = true, validatedData = data)
            }
            
            /**
             * Create a failed validation result.
             * @param message Error message to display to the user
             */
            fun error(message: String): ValidationResult {
                return ValidationResult(isValid = false, errorMessage = message)
            }
        }
    }
}
