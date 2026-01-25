# Living Lands - HUD System Analysis & Standardization

**Date:** 2026-01-25  
**Purpose:** Analyze current HUD usage, external mod compatibility, and propose standardization improvements

---

## Current HUD System Overview

### Architecture

We've implemented a **MultiHUD system** based on Buuz135's MHUD pattern to solve Hytale's single-HUD limitation:

```
Hytale Limitation: player.hudManager.setCustomHud() only supports ONE CustomUIHud per player

Our Solution:
┌─────────────────────────────────────────┐
│  player.hudManager.customHud            │
│  ↓                                      │
│  CompositeHud (wrapper)                 │
│  ├─ "livinglands:metabolism" → MetabolismHudElement
│  ├─ "livinglands:leveling" → LevelingHudElement (planned)
│  ├─ "_external" → OtherModHud (preserved)
│  └─ ...                                 │
└─────────────────────────────────────────┘
```

### Components

| Component | Purpose | Lines of Code |
|-----------|---------|---------------|
| **MultiHudManager** | Manages composite HUD pattern | 284 LOC |
| **CompositeHud** | Delegates build() to child HUDs | 106 LOC |
| **MetabolismHudElement** | Metabolism stats display | 141 LOC |

---

## Current Module HUD Usage

### 1. Metabolism Module ✅ (Fully Implemented)

**HUD Element:** `MetabolismHudElement`
- **Namespace:** `"livinglands:metabolism"`
- **UI File:** `src/main/resources/Hud/MetabolismHud.ui`
- **Display:** Three text-based progress bars for hunger, thirst, energy
- **Update Pattern:** Service holds HUD references, updates on stat changes

**Registration Pattern:**
```kotlin
// In MetabolismModule.onPlayerJoin()
private fun registerHudForPlayer(player: Player, playerRef: PlayerRef, playerId: UUID) {
    world.execute {
        val hudElement = MetabolismHudElement(playerRef)
        
        // Register with service for updates
        metabolismService.registerHudElement(playerId.toCachedString(), hudElement)
        
        // ISSUE: Sets HUD directly on player.hudManager instead of using MultiHudManager
        player.hudManager.setCustomHud(playerRef, hudElement)  // ❌ WRONG
        hudElement.show()
    }
}
```

**Cleanup Pattern:**
```kotlin
// In MetabolismModule.onPlayerDisconnect()
private fun cleanupHudForPlayer(playerId: UUID, playerRef: PlayerRef) {
    metabolismService.unregisterHudElement(playerId.toCachedString())
    
    session.world.execute {
        val player = store.getComponent(entityRef, Player.getComponentType())
        if (player != null) {
            // Uses MultiHudManager for cleanup (correct)
            CoreModule.hudManager.removeHud(player, playerRef, MetabolismHudElement.NAMESPACE)
        }
    }
    
    CoreModule.hudManager.onPlayerDisconnect(playerId)
}
```

**⚠️ ISSUE IDENTIFIED:** Metabolism is setting HUD directly instead of using MultiHudManager!

### 2. Mock Modules 🚧 (Not Yet Implemented)

| Module | Planned HUD | Display Content | Priority |
|--------|-------------|-----------------|----------|
| **Leveling** | `LevelingHudElement` | Current profession levels, XP progress | High |
| **Economy** | `BalanceHudElement` | Player's current balance | Medium |
| **Claims** | None (visual boundaries only) | Particle effects at claim edges | Low |
| **Groups** | `GroupHudElement` | Group name, online members | Low |

---

## External Mod Compatibility

### How We Handle External HUDs

**Scenario 1: Our plugin loads BEFORE another mod sets a HUD**

```kotlin
// In MultiHudManager.setHud()
val existingHud = hudManager.customHud  // Another mod's HUD

if (existingHud != null && existingHud !is CompositeHud) {
    // Preserve external HUD under "_external" namespace
    huds["_external"] = existingHud
}

// Create composite with both our HUD and external HUD
val composite = CompositeHud(playerRef, huds, buildMethod, logger)
hudManager.setCustomHud(playerRef, composite)
```

**Scenario 2: Our plugin loads AFTER another mod already set a HUD**

```kotlin
// When we call hudManager.setCustomHud(playerRef, composite)
// Hytale REPLACES the other mod's HUD with our composite

// PROBLEM: Other mod's HUD is preserved in "_external" but:
// 1. Other mod doesn't know its HUD was replaced
// 2. Other mod's HUD updates won't work (they update the wrong reference)
// 3. If other mod removes its HUD later, it won't find it
```

**Scenario 3: Another mod sets a HUD AFTER we've set up composite**

```kotlin
// Other mod calls: player.hudManager.setCustomHud(playerRef, theirHud)

// PROBLEM: This OVERWRITES our entire CompositeHud!
// All our HUD elements are lost until player rejoins
```

---

## Current Issues & Inconsistencies

### Issue 1: Metabolism Bypasses MultiHudManager ⚠️

**Problem:**
```kotlin
// MetabolismModule.registerHudForPlayer() - Line 473
player.hudManager.setCustomHud(playerRef, hudElement)  // ❌ BYPASSES MultiHudManager
```

**Should be:**
```kotlin
CoreModule.hudManager.setHud(player, playerRef, MetabolismHudElement.NAMESPACE, hudElement)  // ✅ CORRECT
```

**Impact:**
- If another module tries to register a HUD, it will detect Metabolism's HUD as "_external"
- MultiHudManager will create a composite unnecessarily
- Cleanup uses MultiHudManager but registration doesn't (inconsistent)

### Issue 2: No Protection Against External Overwrites

**Problem:** Nothing prevents other mods from calling `player.hudManager.setCustomHud()` and destroying our CompositeHud.

**Potential Solutions:**
1. **Hook HudManager** (reflection) to intercept setCustomHud() calls
2. **Periodic check** in tick system to detect if our composite was replaced
3. **Document limitation** and hope other mods cooperate

### Issue 3: Module-Service-HUD Reference Pattern is Redundant

**Current Pattern (Metabolism):**
```kotlin
// Module holds player references
private val playerRefs = ConcurrentHashMap<UUID, Pair<Player, PlayerRef>>()

// Service holds HUD references
metabolismService.registerHudElement(playerId, hudElement)  // In PlayerMetabolismState

// MultiHudManager ALSO holds HUD references
CoreModule.hudManager.hudElements[playerId][namespace] = hud
```

**Problem:** Same HUD element stored in 3 places:
1. `MetabolismModule.playerRefs` → stores Player/PlayerRef (needed for cleanup)
2. `MetabolismService.playerStates[playerId].hudElement` → stores HUD (for stat updates)
3. `CoreModule.hudManager.hudElements[playerId][namespace]` → stores HUD (for composite management)

**Why This Redundancy Exists:**
- Module needs Player/PlayerRef for world.execute() context
- Service needs HUD reference for updateStats() calls
- MultiHudManager needs HUD reference for composite building

### Issue 4: No Standard HUD Lifecycle Pattern

Each module implements HUD registration/cleanup differently:

**Metabolism Pattern:**
```kotlin
// Registration (bypasses MultiHudManager)
val hudElement = MetabolismHudElement(playerRef)
metabolismService.registerHudElement(playerId, hudElement)
player.hudManager.setCustomHud(playerRef, hudElement)  // ❌

// Cleanup (uses MultiHudManager)
metabolismService.unregisterHudElement(playerId)
CoreModule.hudManager.removeHud(player, playerRef, namespace)
CoreModule.hudManager.onPlayerDisconnect(playerId)
```

**Mock Module Template:**
```kotlin
// Shows old pattern (from v2.6.0 reference)
val hudElement = ExampleHudElement(playerRef)
CoreModule.hud.setHud(playerRef, "namespace", hudElement)
```

**No consistent pattern for:**
- Where to store HUD references (module, service, or rely on MultiHudManager?)
- How to update HUD state (service method, direct HUD call, or refresh pattern?)
- Thread safety (which operations need world.execute()?)

---

## Proposed Standardization

### Goal: Reduce redundancy, improve consistency, protect against external mods

### Pattern 1: Centralized HUD Management (Recommended)

**Principle:** MultiHudManager is the ONLY source of truth for HUD references.

**Module Implementation:**
```kotlin
class ExampleModule : AbstractModule(...) {
    
    // ✅ NO player refs map needed
    // ✅ NO HUD references in service
    // ✅ Everything through MultiHudManager
    
    override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
        val world = session.world
        world.execute {
            val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return@execute
            @Suppress("DEPRECATION")
            val playerRef = player.playerRef ?: return@execute
            
            // Create and register HUD in one step
            val hudElement = ExampleHudElement(playerRef)
            CoreModule.hudManager.setHud(player, playerRef, NAMESPACE, hudElement)
        }
    }
    
    override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        // Get HUD from manager for cleanup
        val hudElement = CoreModule.hudManager.getHud<ExampleHudElement>(playerId, NAMESPACE)
        
        // Clean up (if needed)
        val world = session.world
        world.execute {
            val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return@execute
            @Suppress("DEPRECATION")
            val playerRef = player.playerRef ?: return@execute
            
            CoreModule.hudManager.removeHud(player, playerRef, NAMESPACE)
        }
        
        CoreModule.hudManager.onPlayerDisconnect(playerId)
    }
    
    companion object {
        const val NAMESPACE = "livinglands:example"
    }
}
```

**Service Implementation:**
```kotlin
class ExampleService {
    
    // ✅ NO HUD references stored
    // Get HUD from MultiHudManager when needed
    
    fun updateSomething(playerId: UUID, value: Int) {
        val hudElement = CoreModule.hudManager.getHud<ExampleHudElement>(playerId, NAMESPACE)
        hudElement?.updateValue(value)
    }
}
```

**Benefits:**
- ✅ Single source of truth for HUD references
- ✅ No redundant storage
- ✅ Consistent access pattern across modules
- ✅ MultiHudManager handles all composite logic

**Drawbacks:**
- ⚠️ Requires type casting with `getHud<T>()`
- ⚠️ Service couples to MultiHudManager (but already couples to config, so acceptable)

### Pattern 2: Enhanced MultiHudManager with Update Helper

**Add convenience method to MultiHudManager:**

```kotlin
class MultiHudManager {
    
    /**
     * Update a HUD element's state and refresh display.
     * Convenience method for common update pattern.
     * 
     * @param playerId Player's UUID
     * @param namespace HUD namespace
     * @param update Lambda to update the HUD state
     */
    fun <T : CustomUIHud> updateHud(playerId: UUID, namespace: String, update: (T) -> Unit) {
        val hudElement = getHud<T>(playerId, namespace) ?: return
        update(hudElement)
        
        // Optionally trigger refresh (if needed by implementation)
        // hudElement.show() or composite.show()
    }
    
    /**
     * Batch update multiple HUD elements for a player.
     */
    fun updateAllHuds(playerId: UUID, updates: Map<String, (CustomUIHud) -> Unit>) {
        updates.forEach { (namespace, update) ->
            val hudElement = getHud<CustomUIHud>(playerId, namespace)
            if (hudElement != null) {
                update(hudElement)
            }
        }
        
        // Refresh composite once after all updates
        val composite = playerHuds[playerId]
        composite?.show()
    }
}
```

**Usage:**
```kotlin
// Simple update
CoreModule.hudManager.updateHud<MetabolismHudElement>(playerId, "livinglands:metabolism") { hud ->
    hud.updateStats(hunger, thirst, energy)
}

// Batch update
CoreModule.hudManager.updateAllHuds(playerId, mapOf(
    "livinglands:metabolism" to { (it as MetabolismHudElement).updateStats(h, t, e) },
    "livinglands:leveling" to { (it as LevelingHudElement).updateLevel(level) }
))
```

### Pattern 3: External Mod Detection & Recovery

**Add to MultiHudManager:**

```kotlin
class MultiHudManager {
    
    /**
     * Verify our composite is still set on the player.
     * If another mod overwrote it, restore our composite.
     * 
     * Call this periodically (e.g., every 5 seconds) or on HUD update.
     */
    fun verifyComposite(player: Player, playerRef: PlayerRef): Boolean {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        
        val ourComposite = playerHuds[playerId] ?: return true  // No composite = nothing to verify
        val currentHud = player.hudManager.customHud
        
        if (currentHud !== ourComposite) {
            // Our composite was replaced by another mod!
            logger.atWarning().log("HUD for player $playerId was overwritten by external mod, restoring...")
            
            // Check if external HUD is new
            if (currentHud != null && currentHud !is CompositeHud) {
                // Preserve new external HUD
                ourComposite.addHud("_external", currentHud)
            }
            
            // Restore our composite
            try {
                player.hudManager.setCustomHud(playerRef, ourComposite)
                ourComposite.show()
                return false  // Was overwritten, now restored
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to restore composite HUD for $playerId")
                return false
            }
        }
        
        return true  // Still ours
    }
    
    /**
     * Periodic check for all players (call from tick system).
     */
    fun verifyAllComposites() {
        playerHuds.forEach { (playerId, composite) ->
            // Need Player and PlayerRef - require session
            val session = CoreModule.players.getSession(playerId) ?: return@forEach
            session.world.execute {
                val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return@execute
                @Suppress("DEPRECATION")
                val playerRef = player.playerRef ?: return@execute
                
                verifyComposite(player, playerRef)
            }
        }
    }
}
```

**Usage:**
```kotlin
// In CoreModule or a dedicated tick system
class HudVerificationTickSystem : TickingSystem<EntityStore>() {
    private var tickCounter = 0
    
    override fun tick(deltaTime: Float, ...) {
        tickCounter++
        
        // Check every 5 seconds (100 ticks at 20 TPS)
        if (tickCounter >= 100) {
            tickCounter = 0
            CoreModule.hudManager.verifyAllComposites()
        }
    }
}
```

---

## Recommended Changes

### Phase 1: Fix Metabolism Module (Immediate)

1. **Change MetabolismModule.registerHudForPlayer():**
   ```kotlin
   - player.hudManager.setCustomHud(playerRef, hudElement)
   + CoreModule.hudManager.setHud(player, playerRef, MetabolismHudElement.NAMESPACE, hudElement)
   ```

2. **Remove redundant HUD storage from MetabolismService:**
   ```kotlin
   - data class PlayerMetabolismState(
   -     val hudElement: MetabolismHudElement? = null
   - )
   - 
   - fun registerHudElement(playerId: String, hudElement: MetabolismHudElement) { ... }
   - fun unregisterHudElement(playerId: String) { ... }
   ```

3. **Update stat update pattern:**
   ```kotlin
   fun updateStats(playerId: UUID, hunger: Float, thirst: Float, energy: Float) {
       // Get HUD from MultiHudManager instead of storing it
       val hudElement = CoreModule.hudManager.getHud<MetabolismHudElement>(
           playerId, 
           MetabolismHudElement.NAMESPACE
       )
       hudElement?.updateStats(hunger, thirst, energy)
       hudElement?.updateHud()
   }
   ```

### Phase 2: Enhance MultiHudManager (Short-term)

1. **Add updateHud() helper method** (Pattern 2)
2. **Add verifyComposite() protection** (Pattern 3)
3. **Add periodic verification tick system**

### Phase 3: Update Module Template (Documentation)

1. Update `docs/MODULE_ANALYSIS.md` with standardized HUD pattern
2. Add HUD section to module checklist
3. Document external mod compatibility

### Phase 4: Implement Future Module HUDs

Use the standardized pattern for Leveling, Economy, Groups HUDs:

```kotlin
// ✅ Standard pattern (no redundant storage)
override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
    session.world.execute {
        val player = session.store.getComponent(session.entityRef, Player.getComponentType()) ?: return@execute
        @Suppress("DEPRECATION")
        val playerRef = player.playerRef ?: return@execute
        
        val hudElement = LevelingHudElement(playerRef)
        CoreModule.hudManager.setHud(player, playerRef, "livinglands:leveling", hudElement)
    }
}

// ✅ Update from service
fun updateLevel(playerId: UUID, profession: Profession, level: Int) {
    CoreModule.hudManager.updateHud<LevelingHudElement>(playerId, "livinglands:leveling") { hud ->
        hud.updateProfession(profession, level)
    }
}
```

---

## External Mod Compatibility Summary

### What We Do Well ✅

1. **Preserve external HUDs:** When we detect another mod's HUD, we save it as "_external"
2. **Composite pattern:** Multiple HUDs can coexist in our composite
3. **Graceful degradation:** If a child HUD fails to build(), others still work

### What We Can't Prevent ⚠️

1. **External mod overwrites:** If another mod calls `setCustomHud()` after us, our composite is lost
2. **External mod compatibility:** Other mods don't know about our composite system

### Recommended Communication

Add to plugin documentation:

```markdown
## For Other Mod Authors

Living Lands uses a **MultiHUD system** to support multiple HUD elements simultaneously.

If you want your mod to be compatible with Living Lands:

### Option 1: Register through our MultiHudManager (Recommended)
```java
// Get Living Lands service
Object hudManager = Hytale.getServer().getPluginManager()
    .getPlugin("LivingLands")
    .getService("MultiHudManager");

// Register your HUD
hudManager.setHud(player, playerRef, "yourmod:yourhud", yourHudElement);
```

### Option 2: Use your own HUD system
If you implement your own composite HUD system, our system will detect and preserve
your HUD under the "_external" namespace. However, this may lead to conflicts.

### What NOT to do
❌ Don't call `player.hudManager.setCustomHud()` directly - this will overwrite
   all other mods' HUDs including Living Lands.
```

---

## Summary

### Current HUD Usage

| Module | HUD | Registration Pattern | Issues |
|--------|-----|---------------------|--------|
| **Metabolism** | MetabolismHudElement | ❌ Bypasses MultiHudManager | Inconsistent, redundant storage |
| **Leveling** | (planned) | Not implemented | - |
| **Economy** | (planned) | Not implemented | - |
| **Groups** | (planned) | Not implemented | - |

### Identified Issues

1. ⚠️ **Inconsistent registration:** Metabolism bypasses MultiHudManager on registration but uses it for cleanup
2. ⚠️ **Redundant storage:** HUD references stored in 3 places (module, service, MultiHudManager)
3. ⚠️ **No protection:** External mods can overwrite our composite without detection
4. ⚠️ **No standard pattern:** Each module may implement HUD lifecycle differently

### Recommended Improvements

1. ✅ **Fix Metabolism:** Use MultiHudManager for registration (Phase 1)
2. ✅ **Centralize storage:** Remove redundant HUD references from modules/services (Phase 1)
3. ✅ **Add helpers:** Implement updateHud() convenience method (Phase 2)
4. ✅ **Add protection:** Implement periodic composite verification (Phase 2)
5. ✅ **Standardize pattern:** Update documentation and templates (Phase 3)
6. ✅ **Document compatibility:** Add external mod integration guide (Phase 3)

### Benefits of Standardization

- 🎯 **Reduced complexity:** Single source of truth for HUD references
- 🎯 **Consistent patterns:** All modules use same registration/update/cleanup flow
- 🎯 **Better compatibility:** Protection against external mod conflicts
- 🎯 **Easier maintenance:** Less redundant code to keep in sync
- 🎯 **Cleaner modules:** Services don't need to store HUD references
