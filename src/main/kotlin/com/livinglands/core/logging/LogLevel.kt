package com.livinglands.core.logging

/**
 * Standard log levels for Living Lands.
 * 
 * **Hierarchy:** TRACE < DEBUG < CONFIG < INFO < WARN < ERROR < OFF
 * 
 * When a log level is set, all messages at that level and above are shown.
 * For example, setting level to INFO shows INFO, WARN, and ERROR messages,
 * but hides TRACE, DEBUG, and CONFIG.
 * 
 * **Usage Guidelines:**
 * - **TRACE:** Very detailed logging for development (hot path details, every tick)
 * - **DEBUG:** Detailed diagnostic information (state changes, important calculations)
 * - **CONFIG:** Configuration-related messages (config loading, validation, migration)
 * - **INFO:** General informational messages (module loaded, player joined)
 * - **WARN:** Warning messages (degraded functionality, recoverable errors)
 * - **ERROR:** Error messages (unrecoverable errors, exceptions)
 * - **OFF:** No logging (not recommended for production)
 * 
 * **Note:** Numeric values align with Java's logging levels (higher = more severe).
 * This matches java.util.logging.Level semantics: FINEST(300) < FINER(400) < FINE(500) 
 * < CONFIG(700) < INFO(800) < WARNING(900) < SEVERE(1000)
 * 
 * @property level Numeric level for comparison (higher = more severe)
 */
enum class LogLevel(val level: Int) {
    /**
     * Extremely detailed logging (e.g., every tick, every calculation).
     * Use sparingly as this generates massive log files.
     * Maps to java.util.logging.Level.FINEST (300)
     */
    TRACE(300),
    
    /**
     * Detailed diagnostic information useful for debugging.
     * Shows state changes, important calculations, method entries/exits.
     * Maps to java.util.logging.Level.FINE (500)
     */
    DEBUG(500),
    
    /**
     * Configuration-related messages.
     * Config loading, validation, migration, and hot-reload events.
     * Maps to java.util.logging.Level.CONFIG (700)
     */
    CONFIG(700),
    
    /**
     * General informational messages about application flow.
     * Module lifecycle, player events, configuration changes.
     * Maps to java.util.logging.Level.INFO (800)
     */
    INFO(800),
    
    /**
     * Warning messages for potentially harmful situations.
     * Degraded functionality, deprecated API usage, recoverable errors.
     * Maps to java.util.logging.Level.WARNING (900)
     */
    WARN(900),
    
    /**
     * Error messages for serious problems.
     * Unrecoverable errors, exceptions, data corruption.
     * Maps to java.util.logging.Level.SEVERE (1000)
     */
    ERROR(1000),
    
    /**
     * No logging. Not recommended for production.
     * Use only when debugging performance issues.
     */
    OFF(9999);
    
    /**
     * Check if this level should be logged given a configured minimum level.
     * 
     * @param configuredLevel The minimum level to log
     * @return True if this message should be logged
     */
    fun shouldLog(configuredLevel: LogLevel): Boolean {
        return this.level >= configuredLevel.level
    }
    
    companion object {
        /**
         * Parse a log level from a string (case-insensitive).
         * 
         * @param value String representation (e.g., "debug", "INFO", "Warn")
         * @return Parsed LogLevel, or null if invalid
         */
        fun fromString(value: String): LogLevel? {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
        
        /**
         * Parse a log level from a string with a fallback default.
         * 
         * @param value String representation
         * @param default Default level if parsing fails
         * @return Parsed LogLevel or default
         */
        fun fromStringOrDefault(value: String, default: LogLevel = INFO): LogLevel {
            return fromString(value) ?: default
        }
    }
}
