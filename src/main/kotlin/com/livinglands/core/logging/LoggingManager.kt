package com.livinglands.core.logging

import com.hypixel.hytale.logger.HytaleLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized logging manager with configurable log levels.
 * 
 * **Features:**
 * - Global log level configuration
 * - Per-module log level overrides
 * - Thread-safe configuration updates
 * - Zero-overhead when logging is disabled
 * 
 * **Usage:**
 * ```kotlin
 * class MyModule {
 *     private val logger = CoreModule.logger
 *     
 *     fun doWork() {
 *         LoggingManager.trace(logger, "metabolism") { "Detailed trace message" }
 *         LoggingManager.debug(logger, "metabolism") { "Debug info: $value" }
 *         LoggingManager.info(logger, "metabolism") { "Important event occurred" }
 *         LoggingManager.warn(logger, "metabolism") { "Potential issue detected" }
 *         LoggingManager.error(logger, "metabolism") { "Critical error!" }
 *     }
 * }
 * ```
 * 
 * **Why lambdas?**
 * Using lambda parameters `() -> String` ensures expensive string operations
 * (like `"Value: $x"`) are only executed if the log level is enabled.
 * This provides zero-cost logging when disabled.
 */
object LoggingManager {
    
    /**
     * Global minimum log level.
     * Messages below this level are filtered out (unless overridden per-module).
     */
    @Volatile
    private var globalLevel: LogLevel = LogLevel.INFO
    
    /**
     * Per-module log level overrides.
     * If a module has an override, it takes precedence over the global level.
     * 
     * Thread-safe for concurrent reads/writes during config reload.
     */
    private val moduleOverrides: ConcurrentHashMap<String, LogLevel> = ConcurrentHashMap()
    
    // ========================================
    // Configuration
    // ========================================
    
    /**
     * Set the global log level.
     * Affects all modules unless they have a specific override.
     * 
     * @param level New global log level
     */
    fun setGlobalLevel(level: LogLevel) {
        globalLevel = level
    }
    
    /**
     * Get the current global log level.
     */
    fun getGlobalLevel(): LogLevel = globalLevel
    
    /**
     * Set a per-module log level override.
     * 
     * @param moduleId Module identifier (e.g., "metabolism", "professions")
     * @param level Log level for this module
     */
    fun setModuleLevel(moduleId: String, level: LogLevel) {
        moduleOverrides[moduleId] = level
    }
    
    /**
     * Remove a per-module log level override.
     * Module will revert to using the global log level.
     * 
     * @param moduleId Module identifier
     */
    fun removeModuleLevel(moduleId: String) {
        moduleOverrides.remove(moduleId)
    }
    
    /**
     * Clear all per-module overrides.
     */
    fun clearModuleOverrides() {
        moduleOverrides.clear()
    }
    
    /**
     * Get the effective log level for a module.
     * Returns the module override if present, otherwise the global level.
     * 
     * @param moduleId Module identifier
     * @return Effective log level
     */
    fun getEffectiveLevel(moduleId: String): LogLevel {
        return moduleOverrides[moduleId] ?: globalLevel
    }
    
    /**
     * Check if a message should be logged for a given module and level.
     * 
     * @param moduleId Module identifier
     * @param level Log level to check
     * @return True if this level should be logged
     */
    fun shouldLog(moduleId: String, level: LogLevel): Boolean {
        val effectiveLevel = getEffectiveLevel(moduleId)
        return level.shouldLog(effectiveLevel)
    }
    
    // ========================================
    // Logging Methods (with lambda for lazy evaluation)
    // ========================================
    
    /**
     * Log a TRACE message (most verbose).
     * Only logged if the module's effective level is TRACE.
     * Maps to java.util.logging.Level.FINEST
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier (e.g., "metabolism")
     * @param message Lazy message supplier (only called if level is enabled)
     */
    inline fun trace(logger: HytaleLogger, moduleId: String, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.TRACE)) {
            logger.atFinest().log("[TRACE][$moduleId] ${message()}")
        }
    }
    
    /**
     * Log a DEBUG message.
     * Only logged if the module's effective level is DEBUG or lower.
     * Maps to java.util.logging.Level.FINE
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier
     * @param message Lazy message supplier
     */
    inline fun debug(logger: HytaleLogger, moduleId: String, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.DEBUG)) {
            logger.atFine().log("[DEBUG][$moduleId] ${message()}")
        }
    }
    
    /**
     * Log a CONFIG message.
     * Only logged if the module's effective level is CONFIG or lower.
     * Maps to java.util.logging.Level.CONFIG
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier
     * @param message Lazy message supplier
     */
    inline fun config(logger: HytaleLogger, moduleId: String, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.CONFIG)) {
            logger.atConfig().log("[CONFIG][$moduleId] ${message()}")
        }
    }
    
    /**
     * Log an INFO message.
     * Only logged if the module's effective level is INFO or lower.
     * Maps to java.util.logging.Level.INFO
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier
     * @param message Lazy message supplier
     */
    inline fun info(logger: HytaleLogger, moduleId: String, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.INFO)) {
            logger.atFine().log("[INFO][$moduleId] ${message()}")
        }
    }
    
    /**
     * Log a WARN message.
     * Only logged if the module's effective level is WARN or lower.
     * Maps to java.util.logging.Level.WARNING
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier
     * @param message Lazy message supplier
     */
    inline fun warn(logger: HytaleLogger, moduleId: String, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.WARN)) {
            logger.atWarning().log("[WARN][$moduleId] ${message()}")
        }
    }
    
    /**
     * Log an ERROR message.
     * Only logged if the module's effective level is ERROR or lower.
     * Maps to java.util.logging.Level.SEVERE
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier
     * @param message Lazy message supplier
     */
    inline fun error(logger: HytaleLogger, moduleId: String, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.ERROR)) {
            logger.atSevere().log("[ERROR][$moduleId] ${message()}")
        }
    }
    
    /**
     * Log an ERROR message with exception.
     * Always includes the exception stack trace.
     * Maps to java.util.logging.Level.SEVERE
     * 
     * @param logger Hytale logger instance
     * @param moduleId Module identifier
     * @param exception Exception to log
     * @param message Lazy message supplier
     */
    inline fun error(logger: HytaleLogger, moduleId: String, exception: Throwable, message: () -> String) {
        if (shouldLog(moduleId, LogLevel.ERROR)) {
            logger.atSevere().withCause(exception).log("[ERROR][$moduleId] ${message()}")
        }
    }
    
    // ========================================
    // Diagnostics
    // ========================================
    
    /**
     * Get a summary of current logging configuration.
     * Useful for debugging log configuration issues.
     * 
     * @return Human-readable configuration summary
     */
    fun getConfigurationSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("Logging Configuration:")
        sb.appendLine("  Global Level: $globalLevel")
        
        if (moduleOverrides.isNotEmpty()) {
            sb.appendLine("  Module Overrides:")
            moduleOverrides.entries.sortedBy { it.key }.forEach { (module, level) ->
                sb.appendLine("    $module: $level")
            }
        } else {
            sb.appendLine("  Module Overrides: (none)")
        }
        
        return sb.toString()
    }
}
