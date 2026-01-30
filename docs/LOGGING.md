# Living Lands - Logging System

## Overview

Living Lands uses a configurable log level system that allows fine-grained control over what gets logged. You can set a global log level and override it per-module for debugging specific components.

## Log Levels

From most to least verbose:

| Level | Usage | When to Use |
|-------|-------|-------------|
| **TRACE** | Extremely detailed logging | Hot path details, every tick, every calculation. **Very verbose**, use sparingly. |
| **DEBUG** | Detailed diagnostic information | State changes, important calculations, method entries/exits. For development debugging. |
| **INFO** | General informational messages | Module lifecycle, player events, configuration changes. **Default level**. |
| **WARN** | Warning messages | Degraded functionality, deprecated API usage, recoverable errors. |
| **ERROR** | Error messages | Unrecoverable errors, exceptions, data corruption. |
| **OFF** | No logging | Disables all logging. Not recommended except for performance testing. |

**Hierarchy:** When you set a log level, all messages at that level **and above** are shown.

**Example:** Setting level to `INFO` shows `INFO`, `WARN`, and `ERROR` messages, but hides `TRACE` and `DEBUG`.

## Configuration

### File Location

`LivingLandsReloaded/config/core.yml`

### Basic Configuration

```yaml
configVersion: 2

# Legacy debug flag (deprecated - use logging.globalLevel instead)
debug: false

# Logging configuration
logging:
  globalLevel: INFO  # Valid values: TRACE, DEBUG, INFO, WARN, ERROR, OFF
  moduleOverrides: {}

# Enabled modules
enabledModules:
  - metabolism
  - professions
```

### Global Log Level

Set the same log level for all modules:

```yaml
logging:
  globalLevel: DEBUG  # All modules will log at DEBUG level
```

### Per-Module Log Levels

Override the global level for specific modules:

```yaml
logging:
  globalLevel: INFO  # Most modules use INFO
  moduleOverrides:
    metabolism: DEBUG    # Metabolism module uses DEBUG
    professions: TRACE   # Professions module uses TRACE (very verbose!)
    core: WARN           # Core module only shows warnings and errors
```

**Available Module IDs:**
- `core` - Core system logs (initialization, config, module lifecycle)
- `metabolism` - Metabolism system (hunger, thirst, energy, buffs, debuffs)
- `professions` - Professions system (XP, leveling, abilities)
- `claims` - Land claims system (future)

## Common Scenarios

### Development / Debugging

```yaml
logging:
  globalLevel: DEBUG
  moduleOverrides: {}
```

This enables detailed logging across all modules for debugging issues.

### Debugging Specific Module

```yaml
logging:
  globalLevel: INFO
  moduleOverrides:
    metabolism: TRACE  # Super detailed logs for metabolism only
```

Use this when investigating issues in a specific module without flooding logs with other modules.

### Production / Performance

```yaml
logging:
  globalLevel: WARN
  moduleOverrides: {}
```

Minimal logging for production servers - only warnings and errors.

### Quiet Production

```yaml
logging:
  globalLevel: ERROR
  moduleOverrides: {}
```

Only show critical errors. Use for high-performance servers where log I/O is a bottleneck.

## Applying Changes

### Hot-Reload (No Restart Required)

1. Edit `LivingLandsReloaded/config/core.yml`
2. Run command: `/ll reload core`
3. New log level takes effect immediately

### Full Restart

1. Edit `LivingLandsReloaded/config/core.yml`
2. Restart Hytale server
3. New configuration loaded on startup

## Migration from v1 Config

If you have an old `core.yml` with just `debug: true/false`:

**Old (v1):**
```yaml
configVersion: 1
debug: true
enabledModules:
  - metabolism
```

**New (v2) - Auto-migrated:**
```yaml
configVersion: 2
debug: true  # Kept for backward compatibility
logging:
  globalLevel: DEBUG  # Automatically set based on debug flag
  moduleOverrides: {}
enabledModules:
  - metabolism
  - professions
```

The migration happens automatically on first load. The old `debug` field is deprecated but still works (sets global level to DEBUG if true).

## Log Output Examples

### INFO Level (Default)

```
[INFO][core] CoreModule initialized (debug=false)
[INFO][metabolism] MetabolismModule loaded
[INFO][professions] ProfessionsModule loaded
```

### DEBUG Level

```
[DEBUG][metabolism] Player 123e4567-e89b-12d3-a456-426614174000 joined - initializing stats
[DEBUG][metabolism] Loaded metabolism stats: hunger=100.0, thirst=100.0, energy=100.0
[DEBUG][professions] Loaded profession stats for player: 5 professions
```

### TRACE Level (Very Verbose!)

```
[TRACE][metabolism] Tick for player 123e4567-e89b-12d3-a456-426614174000
[TRACE][metabolism] Current stats: hunger=99.8, thirst=99.5, energy=98.2
[TRACE][metabolism] Activity state: IDLE, multiplier=1.0
[TRACE][metabolism] Depletion: hunger=-0.02, thirst=-0.03, energy=-0.01
[TRACE][professions] Checking XP award for block break...
```

## Troubleshooting

### Logs Still Too Verbose

If even `INFO` level logs too much, try `WARN`:

```yaml
logging:
  globalLevel: WARN
```

### Not Seeing Expected Logs

1. Check your log level isn't too restrictive (`ERROR` or `OFF`)
2. Verify the module ID in `moduleOverrides` is correct
3. Run `/ll reload core` to apply changes
4. Check server console output (not just chat)

### Log Configuration Not Working

1. Verify YAML syntax is correct (proper indentation)
2. Check `configVersion: 2` is present
3. Ensure module IDs match exactly (case-sensitive)
4. Look for warnings in server logs about invalid log levels

## Developer Usage

### Logging in Code

When writing new features, use `LoggingManager` instead of direct logger calls:

```kotlin
import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger

class MyFeature {
    private val logger: HytaleLogger = CoreModule.logger
    
    fun doWork() {
        // TRACE - Hot path details
        LoggingManager.trace(logger, "metabolism") {
            "Processing tick for ${playerCount} players"
        }
        
        // DEBUG - Detailed diagnostic info
        LoggingManager.debug(logger, "metabolism") {
            "Player state: hunger=$hunger, thirst=$thirst"
        }
        
        // INFO - General events
        LoggingManager.info(logger, "metabolism") {
            "Player joined, initialized metabolism stats"
        }
        
        // WARN - Potential issues
        LoggingManager.warn(logger, "metabolism") {
            "Player has negative hunger value: $hunger (clamping to 0)"
        }
        
        // ERROR - Critical problems
        LoggingManager.error(logger, "metabolism") {
            "Failed to save metabolism stats for player $playerId"
        }
        
        // ERROR with exception
        try {
            riskyOperation()
        } catch (e: Exception) {
            LoggingManager.error(logger, "metabolism", e) {
                "Operation failed for player $playerId"
            }
        }
    }
}
```

### Why Use Lambdas?

Using lambda parameters `() -> String` ensures expensive string operations are only executed if the log level is enabled:

```kotlin
// ❌ BAD - String interpolation happens even if DEBUG is disabled
logger.atFine().log("[DEBUG][metabolism] Stats: ${expensiveCalculation()}")

// ✅ GOOD - expensiveCalculation() only called if DEBUG is enabled
LoggingManager.debug(logger, "metabolism") {
    "Stats: ${expensiveCalculation()}"
}
```

This provides **zero-cost logging** when disabled - perfect for hot paths!

## Performance Impact

### Log Level Performance

| Level | Performance Impact |
|-------|-------------------|
| **ERROR/WARN** | Negligible (~0.001ms/call) |
| **INFO** | Minimal (~0.01ms/call) |
| **DEBUG** | Low (~0.1ms/call with some string ops) |
| **TRACE** | **High** (can impact TPS if used heavily in hot paths) |

**Recommendation:** Use `INFO` or `WARN` in production. Reserve `DEBUG/TRACE` for development debugging only.

### Hot Path Logging

If you must log in hot paths (e.g., tick systems processing 100+ players):

1. Use `TRACE` level (disabled by default)
2. Use lambdas to avoid string allocation when disabled
3. Keep messages simple - avoid expensive calculations
4. Consider throttling (only log every N ticks)

Example:

```kotlin
// Only log every 100 ticks to avoid spam
if (tickCount % 100 == 0L) {
    LoggingManager.trace(logger, "metabolism") {
        "Processed $playerCount players in ${elapsedMs}ms"
    }
}
```

## See Also

- [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md) - Architecture overview
- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) - Development roadmap
- [Configuration Migration System](TECHNICAL_DESIGN.md#configuration-migration) - How config migrations work
