# Linear Issues for Phase 11: Professions Module

## How to Create These Issues

1. Go to Linear.app → Your team (moshpitcodes)
2. For each issue below, click "New Issue" (N key)
3. Copy the title and description
4. Set priority and estimate
5. Add labels as needed

---

## IMMEDIATE FIXES (Before Phase 11)

### MPC-XXX: Remove debug logging from commands
**Priority:** Low (4)  
**Estimate:** 1 point  
**Labels:** enhancement, cleanup

**Description:**
```
Clean up debug logging added during recent development

**Acceptance Criteria:**
- Remove debug logs from ModuleCommand.kt (lines 66-72)
- Remove debug logs from TestMetabolismCommand.kt
- Verify production mode (debug=false) has minimal logging
- Confirm FINE-level logs still work for metabolism ticks

**Files:**
- src/main/kotlin/com/livinglands/core/commands/ModuleCommand.kt
- src/main/kotlin/com/livinglands/modules/metabolism/commands/TestMetabolismCommand.kt
```

---

### MPC-XXX: Test metabolism balance changes in-game
**Priority:** Urgent (1) - BLOCKING  
**Estimate:** 3 points  
**Labels:** testing, metabolism

**Description:**
```
Validate expert-recommended balance changes through gameplay testing

**Changes to Test:**
- Activity multipliers: Combat 1.8x (was 2.5x), Sprinting 1.6x (was 2.0x)
- Thirst base: 2700s / 45 min (was 2160s / 36 min)
- Swimming reduces thirst: 0.8x (was 1.3x - inverted!)
- Survivalist ability: -8% depletion (nerfed from -15%)

**Acceptance Criteria:**
- Play 30+ minutes in survival mode
- Test sprinting/combat metabolism feel (should be less punishing)
- Verify Well-Fed buff triggers at 90%+
- Verify debuffs trigger at correct thresholds (75%/50%/25%)
- Swimming should feel less thirsty
- Document any balance issues

**Commands:**
- /ll stats - Monitor metabolism HUD
- /ll testmeta restore <stat> <amount> - Test restoration
- /ll testmeta modifier add professions:survivalist 0.92 - Test Survivalist nerf

**Config Files:**
- src/main/kotlin/com/livinglands/modules/metabolism/config/MetabolismConfig.kt
```

---

### MPC-XXX: Validate professions config file
**Priority:** High (2)  
**Estimate:** 2 points  
**Labels:** testing, professions, configuration

**Description:**
```
Verify professions.yml config loads correctly with updated values

**Config Values (from expert review):**
- XP Curve: baseXp=100, multiplier=1.10, maxLevel=75
- Total XP to max: ~1,155,234 XP per profession (~385 hours @ 50 XP/min)
- All 5 professions maxed: ~1925 hours
- Death penalty: baseXpLossPercent=0.10, progressiveIncrease=0.025, maxLoss=0.25
- Respawn immunity: 30 seconds
- Adaptive mercy: after 8 deaths (increased from 5)

**Acceptance Criteria:**
- Config loads without errors on server startup
- XP calculations match expected curve
- Level 3 unlocks at ~210 XP, Level 7 at ~771 XP, Level 10 at ~1356 XP
- Hot-reload works (/ll reload)
- No schema validation warnings in logs
- Death penalty math is correct

**Files:**
- src/main/resources/config/professions.yml (if exists)
- src/main/kotlin/com/livinglands/modules/professions/config/ProfessionsConfig.kt
```

---

## PHASE 11.1: Core Data Model (3-5 days)

### MPC-XXX: Create professions database schema
**Priority:** Urgent (1) - BLOCKER  
**Estimate:** 3 points  
**Labels:** professions, database, phase-11a

**Description:**
```
Implement global professions_stats table for player progression

**Schema:**
CREATE TABLE professions_stats (
    player_uuid TEXT NOT NULL,
    profession TEXT NOT NULL,
    xp INTEGER NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    last_updated INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, profession)
);

CREATE INDEX idx_professions_player ON professions_stats(player_uuid);

**Migration Tracking:**
- Add schema version to module_schemas table
- Support schema migrations (v1 → v2, etc.)

**Acceptance Criteria:**
- Schema created in GlobalPersistenceService (data/global/livinglands.db)
- Indices optimize player lookups
- SQL injection prevention (prepared statements)
- Migration versioning implemented
- Unit tests for schema creation

**Files to Create/Update:**
- Update src/main/kotlin/com/livinglands/core/persistence/GlobalPersistenceService.kt
- Add migration tracking in module_schemas table

**Technical Notes:**
- Uses GLOBAL database (stats follow player across all worlds)
- Per-world overrides still possible via config
```

---

### MPC-XXX: Implement ProfessionsRepository
**Priority:** Urgent (1) - BLOCKER  
**Estimate:** 4 points  
**Labels:** professions, database, phase-11a

**Description:**
```
Create repository for profession data persistence

**Methods:**
- `ensureStats(playerId: UUID)` - Initialize defaults for new players (all professions level 1, 0 XP)
- `updateXp(playerId: UUID, profession: Profession, xp: Long)` - Atomic XP updates
- `getStats(playerId: UUID, profession: Profession?)` - Load single or all 5 professions
- `saveAll(playerId: UUID, stats: Map<Profession, ProfessionStats>)` - Batch save on shutdown
- `deleteStats(playerId: UUID, profession: Profession?)` - Admin reset command

**Acceptance Criteria:**
- All CRUD operations work correctly
- Async operations use Dispatchers.IO (don't block game thread)
- Transaction safety (rollback on error)
- Prepared statements prevent SQL injection
- Unit tests for all repository methods
- Performance: < 50ms for player load

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/ProfessionsRepository.kt

**Dependencies:**
- GlobalPersistenceService
- Kotlin coroutines (async DB operations)
- ProfessionStats data class
```

---

### MPC-XXX: Create profession data classes
**Priority:** Urgent (1) - BLOCKER  
**Estimate:** 2 points  
**Labels:** professions, data-model, phase-11a

**Description:**
```
Define data models for professions system

**Classes to Create:**

1. **Profession enum** (5 professions)
   - COMBAT, MINING, LOGGING, BUILDING, GATHERING
   - Each with displayName, description, dbId

2. **ProfessionStats** (immutable, for persistence)
   ```kotlin
   data class ProfessionStats(
       val profession: Profession,
       val xp: Long,
       val level: Int,
       val lastUpdated: Long
   )
   ```

3. **PlayerProfessionState** (mutable hot path)
   ```kotlin
   class PlayerProfessionState(
       val profession: Profession,
       private val xpCounter: AtomicLong,
       private val lastProcessedLevel: AtomicInteger
   )
   ```

4. **Ability sealed interface** (3 tiers)
   ```kotlin
   sealed interface Ability {
       val id: String
       val name: String
       val description: String
       val profession: Profession
       val tier: Int
       val levelRequirement: Int
   }
   ```

**Acceptance Criteria:**
- Profession enum covers all 5 professions
- ProfessionStats is immutable (data class)
- PlayerProfessionState uses AtomicLong for thread-safe XP
- Ability interface is extensible for 15 abilities (3 per profession, 3 tiers each)
- All classes have proper toString(), equals(), hashCode()

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/data/Profession.kt
- src/main/kotlin/com/livinglands/modules/professions/data/ProfessionStats.kt
- src/main/kotlin/com/livinglands/modules/professions/data/PlayerProfessionState.kt
- src/main/kotlin/com/livinglands/modules/professions/abilities/Ability.kt
```

---

### MPC-XXX: Implement XP Calculator with precomputed table
**Priority:** Urgent (1) - BLOCKER  
**Estimate:** 3 points  
**Labels:** professions, performance, phase-11a

**Description:**
```
Create XP calculation system with O(1) lookups using precomputed array

**XP Curve Formula:**
XP_required(level) = baseXp * (multiplier ^ (level - 1))

**Parameters (from expert review):**
- baseXp = 100
- multiplier = 1.10 (gentler curve, down from 1.15)
- maxLevel = 75 (achievable, down from 100)

**Total XP to Max:**
- Level 75: ~1,155,234 XP (~385 hours @ 50 XP/min)
- All 5 professions: ~5.8M XP (~1925 hours)

**Key Levels:**
- Level 3 (Tier 1): ~210 XP (~4 min)
- Level 7 (Tier 2): ~771 XP (~15 min)
- Level 10 (Tier 3): ~1356 XP (~27 min)

**Methods:**
- `calculateLevel(xp: Long): Int` - O(1) binary search on precomputed array
- `xpForNextLevel(level: Int): Long` - XP required for next level
- `xpForLevel(level: Int): Long` - Total cumulative XP for level
- `xpIntoLevel(currentXp: Long, level: Int): Long` - Progress into current level

**Acceptance Criteria:**
- Precomputed array initialized on startup
- Level lookups are O(1) or O(log n) via binary search
- Level 75 requires exactly ~1,155,234 XP
- Edge cases handled (level 0, max level, negative XP)
- Unit tests for all levels 1-75
- Performance: < 1μs per lookup

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/XpCalculator.kt

**Performance Target:**
- Initialization: < 10ms
- Lookup: < 1μs (nanoseconds)
```

---

### MPC-XXX: Create professions config schema
**Priority:** High (2) - BLOCKER  
**Estimate:** 3 points  
**Labels:** professions, configuration, phase-11a

**Description:**
```
Define YAML configuration structure for professions module

**Config Sections:**

1. **XP Curve** (from expert review)
   - baseXp: 100.0
   - multiplier: 1.10
   - maxLevel: 75

2. **XP Rewards per Activity**
   - combat: { baseXp: 10, mobMultipliers: {...} }
   - mining: { baseXp: 5, oreMultipliers: {...} }
   - logging: { baseXp: 3 }
   - building: { baseXp: 2, antiCheatCooldown: 5000 }
   - gathering: { baseXp: 1, maxXpPerTick: 100 }

3. **Death Penalty** (updated from expert review)
   - baseXpLossPercent: 0.10 (10%)
   - progressiveIncreasePercent: 0.025 (+2.5% per death, down from 3%)
   - maxXpLossPercent: 0.25 (25% cap, down from 35%)
   - affectedProfessions: 2 (highest professions, not random)
   - canDropLevels: false
   - respawnImmunity: { enabled: true, durationSeconds: 30 }
   - adaptiveMercy: { enabled: true, deathThreshold: 8 }
   - decay: { enabled: true, decayRatePerHour: 0.25 }

4. **Ability Toggles**
   - tier1XpBoost: true
   - tier2ResourceRestore: true
   - tier3Passives: true
   - overrides: {} (per-ability disable)

5. **UI Settings**
   - showLevelUpTitle: true
   - showAbilityUnlocks: true
   - showXpGainMessages: true
   - minXpToShow: 5

**Acceptance Criteria:**
- Implements VersionedConfig interface
- Default professions.yml created with all sections
- All 5 professions configurable
- Config versioning (v1 initial)
- Validation for invalid values (negative XP, invalid multiplier)
- Hot-reload supported
- Migration support (future v1→v2)

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/config/ProfessionsConfig.kt
- src/main/resources/config/professions.yml (default config)

**Config Version:** 1 (initial)
```

---

## PHASE 11.2: Core Service (3-4 days)

### MPC-XXX: Implement ProfessionsService core
**Priority:** Urgent (1) - BLOCKER  
**Estimate:** 5 points  
**Labels:** professions, service, phase-11a

**Description:**
```
Create service for profession management and XP tracking

**Features:**
- In-memory state per player (ConcurrentHashMap of PlayerProfessionState)
- Thread-safe XP addition using AtomicLong
- Async DB operations (load/save)
- Level-up detection and notification
- Ability unlock triggers

**Key Methods:**
- `initializePlayer(playerId: UUID)` - Load from global DB async, create defaults if new
- `awardXp(playerId: UUID, profession: Profession, amount: Int)` - Thread-safe XP addition
- `getStats(playerId: UUID, profession: Profession?): ProfessionStats?` - Read current XP/level
- `savePlayer(playerId: UUID)` - Persist to global DB async
- `getUnlockedAbilities(playerId: UUID, profession: Profession): List<Ability>` - Based on level

**CRITICAL: Race Condition Prevention**
Use AtomicLong.compareAndSet() pattern for level-up detection:
```kotlin
val oldXp = xpCounter.get()
val oldLevel = calculateLevel(oldXp)
xpCounter.addAndGet(amount)
val newXp = xpCounter.get()
val newLevel = calculateLevel(newXp)

if (newLevel > oldLevel) {
    if (lastProcessedLevel.compareAndSet(oldLevel, newLevel)) {
        // We won the race - apply abilities ONCE
        unlockAbilities(playerId, profession, newLevel)
    }
}
```

**Acceptance Criteria:**
- XP awards are atomic (no race conditions with concurrent awards)
- Level-up triggers ability unlocks EXACTLY ONCE
- Stats persist across server restarts
- Performance: < 1ms per XP award
- Unit tests with multi-threaded XP awards (100+ concurrent)
- No memory leaks (cleanup on player disconnect)

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/ProfessionsService.kt

**Dependencies:**
- ProfessionsRepository
- XpCalculator
- AbilityRegistry
- Kotlin coroutines (async DB)

**Performance Targets:**
- XP award: < 1ms
- Player load: < 50ms
- Player save: < 100ms
```

---

### MPC-XXX: Create AbilityRegistry
**Priority:** Urgent (1) - BLOCKER  
**Estimate:** 3 points  
**Labels:** professions, abilities, phase-11a

**Description:**
```
Registry for all 15 profession abilities (for Phase 11.A, just stubs)

**Level Requirements:**
- Tier 1 (XP Boost): Level 3 (~210 XP)
- Tier 2 (Max Stat Increases): Level 7 (~771 XP)
- Tier 3 (Permanent Passives): Level 10 (~1356 XP)

**Abilities (stubs for Phase 11.A, implementation in Phase 11.B):**

**Combat:**
- Tier 1 (Lv 3): Warrior - +15% Combat XP gain
- Tier 2 (Lv 7): Iron Stomach - +15 max hunger capacity
- Tier 3 (Lv 10): Adrenaline Rush - +10% speed for 5s on kill

**Mining:**
- Tier 1 (Lv 3): Prospector - +15% Mining XP gain
- Tier 2 (Lv 7): Desert Nomad - +10 max thirst capacity
- Tier 3 (Lv 10): Ore Sense - +10% ore drop chance

**Logging:**
- Tier 1 (Lv 3): Lumberjack - +15% Logging XP gain
- Tier 2 (Lv 7): Tireless Woodsman - +10 max energy capacity
- Tier 3 (Lv 10): Timber! - 25% chance for extra log drop

**Building:**
- Tier 1 (Lv 3): Architect - +15% Building XP gain
- Tier 2 (Lv 7): Enduring Builder - +15 max stamina capacity
- Tier 3 (Lv 10): Efficient Architect - 12% chance to not consume block

**Gathering:**
- Tier 1 (Lv 3): Forager - +15% Gathering XP gain
- Tier 2 (Lv 7): Hearty Gatherer - +4 hunger/thirst per food pickup
- Tier 3 (Lv 10): Survivalist - -8% metabolism depletion (nerfed from -15%)

**Methods:**
- `getAbilitiesForProfession(profession: Profession): List<Ability>` - All abilities for profession
- `getUnlockedAbilities(playerId: UUID, profession: Profession): List<Ability>` - Based on player level
- `checkRequirements(playerId: UUID, ability: Ability): Boolean` - Verify level requirement met
- `getAbilityById(abilityId: String): Ability?` - Lookup by ID

**Acceptance Criteria:**
- All 15 abilities registered (stubs with ID, name, description, tier, level requirement)
- Ability lookup is O(1) hash map
- Config can disable individual abilities
- Thread-safe access (ConcurrentHashMap or immutable)
- Unit tests for registry operations

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/abilities/AbilityRegistry.kt
- src/main/kotlin/com/livinglands/modules/professions/abilities/Ability.kt (if not created in 11.1.3)

**Note:** Actual ability implementations (effects, triggers) come in Phase 11.B
```

---

### MPC-XXX: Implement level-up detection with race condition fix
**Priority:** Urgent (1) - CRITICAL BLOCKER  
**Estimate:** 4 points  
**Labels:** professions, concurrency, phase-11a

**Description:**
```
Detect level-ups using atomic operations to prevent double-application of abilities

**Problem:**
Two threads awarding XP concurrently could BOTH detect a level-up and apply abilities twice.

**Example Race Condition:**
```
Thread A: Player has 200 XP (level 2)
Thread B: Player has 200 XP (level 2)
Thread A: Awards 15 XP → 215 XP → Level 3! → Unlocks Warrior ability
Thread B: Awards 10 XP → 225 XP → Level 3! → Unlocks Warrior ability AGAIN (BUG!)
```

**Solution Pattern (from code review):**
```kotlin
val oldXp = xpCounter.get()
val oldLevel = xpCalculator.calculateLevel(oldXp)

val newXp = xpCounter.addAndGet(amount)
val newLevel = xpCalculator.calculateLevel(newXp)

if (newLevel > oldLevel) {
    // Atomic check: only ONE thread wins the race
    if (lastProcessedLevel.compareAndSet(oldLevel, newLevel)) {
        // We won the race - apply level-up logic ONCE
        onLevelUp(playerId, profession, oldLevel, newLevel)
        unlockAbilities(playerId, profession, newLevel)
        sendLevelUpNotification(playerId, profession, newLevel)
    }
}
```

**Acceptance Criteria:**
- No double level-up application (tested with 100+ concurrent XP awards)
- Level-up notifications sent exactly once
- Ability unlocks triggered exactly once per level
- Unit tests with multi-threading (simulate race conditions)
- Integration tests with rapid XP gains
- Stress test: 1000 concurrent XP awards, verify correct final level

**Files:**
- Update src/main/kotlin/com/livinglands/modules/professions/ProfessionsService.kt with atomic pattern
- Add unit tests in ProfessionsServiceTest.kt

**Testing Strategy:**
- Use `runBlocking` with multiple `launch` coroutines
- Award XP from 10+ threads simultaneously
- Verify final level is correct
- Verify abilities applied exactly once
```

---

### MPC-XXX: Register professions services in CoreModule
**Priority:** High (2) - BLOCKER  
**Estimate:** 2 points  
**Labels:** professions, integration, phase-11a

**Description:**
```
Integrate professions services into CoreModule service registry

**Services to Register:**
- ProfessionsService
- ProfessionsRepository
- AbilityRegistry

**Lifecycle Hooks:**
- `onPlayerJoin()` - Initialize with defaults, load stats from DB async
- `onPlayerDisconnect()` - Save stats to global DB async
- `onConfigReload()` - Update XP amounts, ability enable/disable flags

**Module Setup:**
```kotlin
override suspend fun onSetup(context: ModuleContext) {
    // Load config
    val config = configManager.getConfig<ProfessionsConfig>("professions")
    
    // Initialize services
    val repository = ProfessionsRepository(globalPersistence, logger)
    val xpCalculator = XpCalculator(config.xpCurve)
    val abilityRegistry = AbilityRegistry()
    val service = ProfessionsService(repository, xpCalculator, abilityRegistry, logger)
    
    // Register in CoreModule
    CoreModule.services.register<ProfessionsService>(service)
    CoreModule.services.register<ProfessionsRepository>(repository)
    CoreModule.services.register<AbilityRegistry>(abilityRegistry)
}
```

**Acceptance Criteria:**
- Services accessible via `CoreModule.services.get<T>()`
- Lifecycle hooks execute correctly (onPlayerJoin, onPlayerDisconnect)
- Async loading doesn't block player join (< 100ms)
- Shutdown saves all dirty stats before server stops
- Config reload updates service state
- Unit tests for module lifecycle

**Files to Update:**
- src/main/kotlin/com/livinglands/modules/professions/ProfessionsModule.kt
```

---

## PHASE 11.3: XP Systems (3-4 days)

### MPC-XXX: Implement CombatXpListener
**Priority:** High (2)  
**Estimate:** 3 points  
**Labels:** professions, xp-systems, phase-11a

**Description:**
```
Award Combat XP when players kill mobs

**Event:** `KillFeedEvent.KillerMessage`

**Logic:**
1. Check if killer is player, target is mob (not player)
2. Award Combat XP based on mob type
3. Apply Warrior ability bonus (+15% XP if unlocked)
4. Send XP gain notification (action bar or chat)

**XP Amounts (configurable):**
- Base XP: 10 per kill
- Mob multipliers:
  - Common mobs: 1.0x (10 XP)
  - Elite mobs: 2.0x (20 XP)
  - Boss mobs: 5.0x (50 XP)

**Acceptance Criteria:**
- XP awarded for mob kills
- Different mobs give different XP amounts
- No XP for PvP (player kills player)
- Event handler doesn't block gameplay (< 1ms)
- XP notification shown to player
- Config hot-reload updates XP amounts

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/listeners/CombatXpListener.kt

**Config:**
```yaml
xpRewards:
  combat:
    baseXp: 10
    mobMultipliers:
      boss: 5.0
      elite: 2.0
      common: 1.0
```

**Event Registration:**
```kotlin
eventRegistry.register(KillFeedEvent.KillerMessage::class.java) { event ->
    try {
        handleCombatKill(event)
    } catch (e: Exception) {
        logger.atSevere().withCause(e).log("Error in CombatXpListener")
    }
}
```
```

---

### MPC-XXX: Implement MiningXpListener
**Priority:** High (2)  
**Estimate:** 3 points  
**Labels:** professions, xp-systems, phase-11a

**Description:**
```
Award Mining XP when players break ore blocks

**Event:** `BreakBlockEvent`

**Logic:**
1. Filter ore blocks (blockType.identifier contains "ore")
2. Determine ore tier (iron, gold, diamond, etc.)
3. Award Mining XP based on ore tier
4. Apply Prospector ability bonus (+15% XP if unlocked)

**XP Amounts (configurable):**
- Base XP: 5 per ore
- Ore multipliers:
  - Coal: 1.0x (5 XP)
  - Iron: 1.5x (7.5 XP)
  - Gold: 2.0x (10 XP)
  - Diamond: 3.0x (15 XP)
  - Emerald: 4.0x (20 XP)
- Common blocks (stone, dirt): 0.1x (0.5 XP) - prevents spam but rewards all mining

**Acceptance Criteria:**
- XP awarded for ore mining
- Different ores give different XP
- Non-ore blocks don't trigger XP (or minimal XP for common blocks)
- Performance: < 1ms per block break
- Config hot-reload works

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/listeners/MiningXpListener.kt

**Config:**
```yaml
xpRewards:
  mining:
    baseXp: 5
    oreMultipliers:
      coal: 1.0
      iron: 1.5
      gold: 2.0
      diamond: 3.0
      emerald: 4.0
      stone: 0.1   # Prevent spam, but reward all mining
      dirt: 0.05
```
```

---

### MPC-XXX: Implement LoggingXpListener
**Priority:** High (2)  
**Estimate:** 2 points  
**Labels:** professions, xp-systems, phase-11a

**Description:**
```
Award Logging XP when players break log blocks

**Event:** `BreakBlockEvent`

**Logic:**
1. Filter log blocks (blockType.identifier contains "log")
2. Award Logging XP per log broken
3. Apply Lumberjack ability bonus (+15% XP if unlocked)

**XP Amounts (configurable):**
- Base XP: 3 per log
- Log types: All logs give same XP (simplicity)
  - Future: Different tree types could have multipliers

**Acceptance Criteria:**
- XP awarded for log breaking
- All log types supported (oak, birch, spruce, etc.)
- Non-log blocks don't trigger XP
- Performance: < 1ms per block break

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/listeners/LoggingXpListener.kt

**Config:**
```yaml
xpRewards:
  logging:
    baseXp: 3
    logMultipliers:
      default: 1.0   # All logs equal for simplicity
```
```

---

### MPC-XXX: Implement BuildingXpListener with anti-cheat
**Priority:** High (2)  
**Estimate:** 4 points  
**Labels:** professions, xp-systems, anti-cheat, phase-11a

**Description:**
```
Award Building XP when players place blocks with spam prevention

**Event:** `PlaceBlockEvent`

**Anti-Cheat System:**
Problem: Players could exploit by rapidly placing/breaking blocks for infinite XP
Solution: Cooldown per block location

```kotlin
private val blockCooldowns = ConcurrentHashMap<ChunkPosition, Long>()
private val cooldownMs = 5000L // 5 seconds

fun isOnCooldown(position: ChunkPosition): Boolean {
    val lastPlaced = blockCooldowns[position] ?: 0L
    return System.currentTimeMillis() - lastPlaced < cooldownMs
}

fun recordPlacement(position: ChunkPosition) {
    blockCooldowns[position] = System.currentTimeMillis()
    // Cleanup old entries periodically
}
```

**Logic:**
1. Check if block location is on cooldown
2. If on cooldown, ignore (no XP)
3. If not on cooldown, award Building XP
4. Apply Architect ability bonus (+15% XP if unlocked)
5. Record placement timestamp

**XP Amounts (configurable):**
- Base XP: 0.1 per block (low to prevent spam)
- Block value multipliers:
  - Common (dirt, cobblestone): 0.1x (0.01 XP per block)
  - Processed (planks, bricks): 1.0x (0.1 XP per block)
  - Crafted (doors, stairs): 3.0x (0.3 XP per block)

**Acceptance Criteria:**
- XP awarded for block placement
- Spam detection prevents exploit (break/place loop)
- Cooldown configurable (default 5 seconds)
- Cooldown map cleaned up periodically (prevent memory leak)
- Performance: < 1ms per placement

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/listeners/BuildingXpListener.kt

**Config:**
```yaml
xpRewards:
  building:
    baseXp: 0.1
    blockValueMultipliers:
      common: 0.1      # Dirt, cobblestone (0.01 XP per block)
      processed: 1.0   # Planks, bricks (0.1 XP per block)
      crafted: 3.0     # Doors, stairs (0.3 XP per block)
    antiCheatCooldown: 5000  # ms
```
```

---

### MPC-XXX: Implement GatheringXpListener with performance optimization
**Priority:** High (2)  
**Estimate:** 4 points  
**Labels:** professions, xp-systems, performance, phase-11a

**Description:**
```
Award Gathering XP when players pick up items (optimized for mass pickups)

**Event:** `InteractivelyPickupItemEvent`

**Performance Problem:**
Mass item pickups (100+ items at once) could cause lag if each triggers:
- XP calculation
- Level-up check
- Notification

**Solution: Batching**
```kotlin
private val pendingXp = ConcurrentHashMap<UUID, AtomicInteger>()
private val debouncer = CoroutineScope(Dispatchers.Default)

fun onItemPickup(playerId: UUID, amount: Int) {
    // Accumulate XP
    pendingXp.computeIfAbsent(playerId) { AtomicInteger(0) }
        .addAndGet(amount)
    
    // Debounce: Apply after 500ms of no pickups
    debouncer.launch {
        delay(500)
        flushXp(playerId)
    }
}
```

**Logic:**
1. Filter item type (only food/resources, not junk)
2. Accumulate XP in pending map
3. Apply batch after 500ms debounce
4. Apply Forager ability bonus (+15% XP if unlocked)
5. Send single notification for batch

**XP Amounts (configurable):**
- Base XP: 1 per item
- Item multipliers:
  - Food items: 2.0x (2 XP)
  - Resource items: 1.0x (1 XP)
  - Junk items: 0x (ignored)

**Acceptance Criteria:**
- XP awarded for item pickups
- Different items give different XP
- No lag with 100+ item pickups
- Notifications batched (max 1/second)
- Debouncer cleans up properly
- Performance: < 50ms for 100 items

**Files to Create:**
- src/main/kotlin/com/livinglands/modules/professions/listeners/GatheringXpListener.kt

**Config:**
```yaml
xpRewards:
  gathering:
    baseXp: 1
    itemMultipliers:
      food: 2.0      # Food items (apples, bread, etc.)
      resource: 1.0  # Resources (sticks, seeds, etc.)
    maxXpPerTick: 100  # Prevent single-tick exploits
```

**Performance Target:** < 50ms for 100 items
```

---

## Summary

**Total Issues: 19**

### By Phase:
- Immediate Fixes: 3 issues (6 points)
- Phase 11.1 (Data Model): 5 issues (15 points)
- Phase 11.2 (Core Service): 4 issues (14 points)
- Phase 11.3 (XP Systems): 5 issues (16 points)

### Total Estimate: ~51 points (~18-20 days)

### Priority Breakdown:
- Urgent (1): 13 issues (blockers)
- High (2): 5 issues
- Low (4): 1 issue

### Labels to Create in Linear:
- `professions`
- `phase-11a`
- `phase-11b` (for future)
- `phase-11c` (for future)
- `xp-systems`
- `abilities`
- `anti-cheat`
- `data-model`
- `database`
- `service`
- `configuration`

### Next Steps:
1. Create these 19 issues in Linear manually
2. Organize into cycles/milestones
3. Link dependencies (e.g., Service depends on Repository)
4. Start with Immediate Fixes
5. Then proceed through Phase 11.1 → 11.2 → 11.3
6. After 11.A complete, create issues for Phase 11.B (Tier 2/3 abilities)
