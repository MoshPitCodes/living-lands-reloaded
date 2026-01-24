# Phase 0 & 1 Audit Report

**Date:** 2026-01-24  
**Audited:** Phase 0 (Project Setup) and Phase 1 (Core Infrastructure)  
**Status:** Issues found requiring immediate attention

---

## Critical Issues (Must Fix)

### C1: plugin.json version not substituted
**File:** `src/main/resources/plugin.json`  
**Line:** 4  
**Severity:** CRITICAL

The JAR contains `"version": "${version}"` instead of the actual version number. Gradle is not configured to expand properties in resource files.

**Expected:**
```json
{
  "version": "1.0.0-beta"
}
```

**Actual:**
```json
{
  "version": "${version}"
}
```

**Fix Required:** Configure `processResources` in `build.gradle.kts`:
```kotlin
tasks.processResources {
    filesMatching("plugin.json") {
        expand(mapOf("version" to version))
    }
}
```

---

### C2: No error handling in event handlers
**File:** `src/main/kotlin/com/livinglands/LivingLandsPlugin.kt`  
**Lines:** 61-76  
**Severity:** CRITICAL

All event listeners wrap code without try/catch blocks. Any exception thrown during event handling will crash the plugin.

**Problematic Code:**
```kotlin
events.register(PlayerReadyEvent::class.java) { event ->
    onPlayerReady(event)  // No try/catch - will crash on error
}
```

**Fix Required:** Wrap all event handlers with error handling:
```kotlin
events.register(PlayerReadyEvent::class.java) { event ->
    try {
        onPlayerReady(event)
    } catch (e: Exception) {
        logger.atError().log("Error in PlayerReadyEvent: ${e.message}", e)
    }
}
```

---

### C3: Inconsistent UUID access - uses deprecated API
**File:** `src/main/kotlin/com/livinglands/LivingLandsPlugin.kt`  
**Line:** 91  
**Severity:** CRITICAL

`PlayerReadyEvent` handler uses deprecated `player.getUuid()` with suppression. `PlayerDisconnectEvent` correctly uses `playerRef.uuid`.

**Inconsistent:**
```kotlin
// Line 91 - deprecated
@Suppress("DEPRECATION")
val playerId = player.getUuid() ?: return

// Line 113 - correct
val playerId = playerRef.uuid
```

**Fix Required:** Use consistent API from event object:
```kotlin
// Should use event.playerRef.uuid or event.playerRef.getUuid()
val playerId = event.playerRef.uuid
```

---

### C4: Missing null validation in event handlers
**File:** `src/main/kotlin/com/livinglands/LivingLandsPlugin.kt`  
**Lines:** 62, 66  
**Severity:** CRITICAL

Event properties accessed without null checks before calling registry methods.

**Problematic:**
```kotlin
events.register(AddWorldEvent::class.java) { event ->
    CoreModule.worlds.onWorldAdded(event)  // Assumes event != null
}
```

**Risk:** Hytale API may pass null events in edge cases.

**Fix Required:** Add null checks:
```kotlin
events.register(AddWorldEvent::class.java) { event ->
    if (event != null && event.world != null) {
        CoreModule.worlds.onWorldAdded(event)
    }
}
```

---

### C5: World name collision in registry
**File:** `src/main/kotlin/com/livinglands/core/WorldRegistry.kt`  
**Line:** 32  
**Severity:** CRITICAL

Worlds are indexed by name, which is not unique. Duplicate world names will overwrite contexts.

**Problematic:**
```kotlin
worldsByName[world.name] = worldId  // Name collisions possible
```

**Scenario:** Server has two test worlds or worlds renamed will conflict.

**Fix Required:** Remove `worldsByName` map or document uniqueness requirement:
```kotlin
// Remove unneeded map - UUID lookup is sufficient
// Or document: "World names must be unique within server"
```

---

### C6: Missing debug command from Phase 1 deliverable
**File:** N/A (not implemented)  
**Severity:** CRITICAL

Phase 1.92 in IMPLEMENTATION_PLAN.md states: "Core infrastructure that tracks players and worlds. Verify with `/ll debug` command showing active players/worlds."

No command system or debug command exists.

**Fix Required:** Implement command system as part of Phase 1 or update deliverable.

---

## Medium Priority Issues

### M1: No duplicate session validation
**File:** `src/main/kotlin/com/livinglands/core/PlayerRegistry.kt`  
**Line:** 17  
**Severity:** MEDIUM

`register()` doesn't check if session already exists. Re-registration overwrites previous session without warning.

**Risk:** Player rejoin during session mismatch could lose tracked state.

**Fix Required:**
```kotlin
fun register(session: PlayerSession) {
    val existing = sessions.putIfAbsent(session.playerId, session)
    if (existing != null) {
        logger.atWarning().log("Session already exists for ${session.playerId}")
    }
}
```

---

### M2: ServiceRegistry single-implementation limitation
**File:** `src/main/kotlin/com/livinglands/core/ServiceRegistry.kt`  
**Line:** 19  
**Severity:** MEDIUM

Services keyed by `T::class` only. Cannot register multiple implementations of same interface.

**Issue:** If multiple repository implementations or service variants needed, registry will reject duplicates.

**Fix Required:** Add optional namespace parameter:
```kotlin
inline fun <reified T : Any> register(service: T, namespace: String = "default") {
    val key = "${T::class.qualifiedName}:$namespace"
    services[key] = service
}
```

---

### M3: WorldContext cleanup incomplete
**File:** `src/main/kotlin/com/livinglands/core/WorldRegistry.kt`  
**Lines:** 44-47  
**Severity:** MEDIUM

`onWorldRemoved()` only removes from maps. `moduleData` in `WorldContext` remains populated.

**Issue:** When Phase 2 persistence added, DB connections won't close properly. Memory leak per module.

**Fix Required:** Add cleanup hook:
```kotlin
fun onWorldRemoved(event: RemoveWorldEvent) {
    val context = worlds.remove(worldId)
    context?.cleanup()  // Add cleanup method to WorldContext
}
```

---

### M4: PlayerSession.world reference unsafe
**File:** `src/main/kotlin/com/livinglands/core/PlayerSession.kt`  
**Line:** 17  
**Severity:** MEDIUM

Stores `World` reference directly. No validation world still exists when used.

**Issue:** If world unloaded and session persists, stale `World` reference may be invalid.

**Fix Required:** Store worldId and fetch current world from WorldRegistry:
```kotlin
data class PlayerSession(
    val playerId: UUID,
    val entityRef: Ref<EntityStore>,
    val store: Store<EntityStore>,
    val worldId: UUID  // Store ID instead of reference
)
```

---

## Low Priority Issues

### L1: CoreModule.shutdown() doesn't call cleanup
**File:** `src/main/kotlin/com/livinglands/core/CoreModule.kt`  
**Lines:** 73-75  
**Severity:** LOW

Only clears data structures. When Phase 2 persistence added, DB connections won't close.

**Impact:** Connection leaks on plugin reload.

**Fix Required:** Call context cleanup before clearing:
```kotlin
fun shutdown() {
    worlds.getAllContexts().forEach { it.cleanup() }
    players.clear()
    worlds.clear()
    services.clear()
}
```

---

### L2: No test evidence for Phase 0.4
**File:** IMPLEMENTATION_PLAN.md  
**Lines:** 47-50  
**Severity:** LOW

Phase 0.4 requires: "Build JAR, Place in server plugins folder, Confirm log messages appear"

No evidence or documentation confirms plugin was tested on real server.

**Action Required:** Add testing documentation or remove unverified task from plan.

---

## Summary Statistics

| Severity | Count | Blocking Phase Progression |
|----------|-------|---------------------------|
| Critical | 6 | YES |
| Medium   | 4 | NO |
| Low      | 2 | NO |
| Total    | 12 | - |

---

## Recommended Action Plan

1. **Immediate (Blockers):** Fix C2, C3, C4 - Add error handling and consistent API usage
2. **Before Phase 2:** Fix C1, C5, C6 - Version substitution, uniqueness, debug command
3. **Refactoring Before Persistence:** Fix M3, M4, L1 - Prepare for DB integration
4. **Improvement (Optional):** Fix M1, M2 - Enhanced validation and service registry

**Estimated Fix Time:** 4-6 hours for critical issues.

---

## Post-Fix Verification Checklist

- [ ] Build JAR and verify `plugin.json` contains version `1.0.0-beta`
- [ ] Build fails test cases with exception in event handler - confirm doesn't crash
- [ ] PlayerReadyEvent uses non-deprecated UUID access
- [ ] All event handlers have null checks
- [ ] `/ll debug` command implemented and shows player/world counts
- [ ] WorldRegistry handles duplicate name scenario
- [ ] Unit tests added for core components
- [ ] Plugin loads, runs, reloads without errors