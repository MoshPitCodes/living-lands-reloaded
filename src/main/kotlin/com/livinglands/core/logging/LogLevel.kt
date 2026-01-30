package com.livinglands.core.logging

/**
 * Standard log levels for Living Lands.
 * 
 * **Hierarchy:** TRACE < DEBUG < INFO < WARN < ERROR < OFF
 * 
 * When a log level is set, all messages at that level and above are shown.
 * For example, setting level to INFO shows INFO, WARN, and ERROR messages,
 * but hides TRACE and DEBUG.
 * 
 * **Usage Guidelines:**
 * - **TRACE:** Very detailed logging for development (hot path details, every tick)
 * - **DEBUG:** Detailed diagnostic information (state changes, important calculations)
 * - **INFO:** General informational messages (module loaded, player joined)
 * - **WARN:** Warning messages (degraded functionality, recoverable errors)
 * - **ERROR:** Error messages (unrecoverable errors, exceptions)
 * - **OFF:** No logging (not recommended for production)
 * 
 * @property level Numeric level for comparison (lower = more verbose)
 */
enum class LogLevel(val level: Int) {
    /**
     * Extremely detailed logging (e.g., every tick, every calculation).
     * Use sparingly as this generates massive log files.
     */
    TRACE(0),
    
    /**
     * Detailed diagnostic information useful for debugging.
     * Shows state changes, important calculations, method entries/exits.
     */
    DEBUG(1),
    
    /**
     * General informational messages about application flow.
     * Module lifecycle, player events, configuration changes.
     */
    INFO(2),
    
    /**
     * Warning messages for potentially harmful situations.
     * Degraded functionality, deprecated API usage, recoverable errors.
     */
    WARN(3),
    
    /**
     * Error messages for serious problems.
     * Unrecoverable errors, exceptions, data corruption.
     */
    ERROR(4),
    
    /**
     * No logging. Not recommended for production.
     * Use only when debugging performance issues.
     */
    OFF(999);
    
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
