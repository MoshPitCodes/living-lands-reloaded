package com.livinglands.core.commands

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Base class for commands that need to perform asynchronous operations.
 * 
 * The Hytale command system uses synchronous `executeSync()` which blocks the command thread.
 * This base class provides an async pattern that:
 * 1. Validates inputs synchronously (fast, immediate feedback on errors)
 * 2. Acknowledges command execution immediately
 * 3. Executes the actual work asynchronously (non-blocking)
 * 4. Handles success/failure appropriately
 * 
 * **Usage Example:**
 * ```kotlin
 * class MyAdminCommand : AsyncCommandBase(
 *     name = "mycommand",
 *     description = "Does something async",
 *     requiresOp = true
 * ) {
 *     override fun validateInputs(ctx: CommandContext): ValidationResult {
 *         val args = ctx.inputString?.split(" ") ?: return ValidationResult.error("No arguments")
 *         if (args.size < 2) return ValidationResult.error("Usage: /ll mycommand <player>")
 *         return ValidationResult.success(args[1])
 *     }
 *     
 *     override suspend fun executeAsync(ctx: CommandContext, validatedData: Any?) {
 *         val playerName = validatedData as String
 *         // Perform async database operations here
 *         val playerData = repository.findByName(playerName)
 *         // Results are logged, not sent to ctx (ctx may be stale in async context)
 *     }
 * }
 * ```
 * 
 * **Important:** Since command execution is async, you cannot send messages back to the player
 * via `ctx.sendMessage()` in `executeAsync()`. Instead:
 * - Use `logger.atFine()` for operational feedback
 * - Use `CoreModule.players.getSession()` to find the player and send messages via the world thread
 * - Or design commands to be "fire and forget" where results are visible through gameplay
 */
abstract class AsyncCommandBase(
    name: String,
    description: String,
    requiresOp: Boolean = false
) : CommandBase(name, description, requiresOp) {
    
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
     * Uses Default dispatcher for computation work.
     * Database operations should use Dispatchers.IO internally.
     */
    protected val commandScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Entry point called by the command system.
     * This method is final - override validateInputs() and executeAsync() instead.
     */
    final override fun executeSync(ctx: CommandContext) {
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
                    executeAsync(ctx, validationResult.validatedData)
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
     * Execute the command asynchronously.
     * This runs in a coroutine and can perform I/O operations.
     * 
     * **Note:** Do not send messages via ctx here - it may be stale.
     * Use logger for operational feedback, or find the player via CoreModule
     * and send messages through the world thread.
     * 
     * @param ctx Command context (for reference only, don't send messages)
     * @param validatedData Data returned from validateInputs()
     */
    abstract suspend fun executeAsync(ctx: CommandContext, validatedData: Any?)
    
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
     * 
     * Override to send confirmation to player if needed:
     * ```kotlin
     * override fun onCommandSuccess(ctx: CommandContext, validatedData: Any?) {
     *     // Find player and send message via world thread
     *     val playerId = validatedData as UUID
     *     CoreModule.players.getSession(playerId)?.let { session ->
     *         session.world.execute {
     *             val player = session.store.getComponent(...)
     *             player?.playerRef?.sendMessage(Message.raw("Success!"))
     *         }
     *     }
     * }
     * ```
     */
    protected open fun onCommandSuccess(ctx: CommandContext, validatedData: Any?) {
        LoggingManager.debug(logger, "core") { "Command '$name' completed successfully" }
    }
    
    /**
     * Called when async execution fails.
     * Default: Logs error, no player message
     * 
     * Override to send error notification to player if needed.
     */
    protected open fun onCommandFailure(ctx: CommandContext, validatedData: Any?, error: Exception) {
        LoggingManager.error(logger, "core", error) { "Command '$name' failed: ${error.message}" }
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
