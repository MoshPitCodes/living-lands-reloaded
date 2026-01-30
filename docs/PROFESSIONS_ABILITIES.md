# Living Lands - Professions & Abilities Reference

## Overview

The professions system in Living Lands provides 5 distinct progression paths with 15 passive abilities total (3 per profession). All abilities unlock automatically at specific levels and remain active permanently once unlocked.

**Professions:**
- Combat
- Mining
- Logging
- Building
- Gathering

**Ability Tiers:**
- **Tier 1 (Level 3):** XP boost (+15% XP gain)
- **Tier 2 (Level 7):** Max stat increases (permanently expand survival capacity)
- **Tier 3 (Level 10):** Unique permanent passives

---

## Design Philosophy

### Tier 1: XP Acceleration
All Tier 1 abilities provide a consistent +15% XP boost, helping you level faster in that profession.

### Tier 2: Survival Expansion
**Unified Theme:** All Tier 2 abilities increase your "tank size" for survival stats.

This allows you to survive longer between resource consumption, making each profession valuable for long expeditions:
- **Combat** → More hunger storage (eating)
- **Mining** → More thirst storage (desert mining)
- **Logging** → More energy storage (physical labor)
- **Building** → More stamina capacity (construction work)
- **Gathering** → Restore hunger/thirst on food pickup (only exception to max stat theme)

### Tier 3: Unique Passives
Each profession gets a unique powerful passive that enhances their playstyle.

---

## Combat Abilities

### Tier 1: Warrior (Level 3)
**Effect:** +15% Combat XP gain  
**Description:** Gain experience 15% faster when fighting mobs  
**Implementation:** XP multiplier applied on kill

### Tier 2: Iron Stomach (Level 7)
**Effect:** Permanently +15 max hunger capacity  
**Description:** Your maximum hunger increases from 100 to 115  
**Why Iron Stomach?** Warriors need extra food capacity for extended battles  
**Implementation:** Permanent max stat increase

### Tier 3: Adrenaline Rush (Level 10)
**Effect:** +10% movement speed for 5 seconds on kill  
**Description:** Killing a mob triggers a temporary speed boost  
**Implementation:** Temporary buff using `SpeedManager`, triggered on `KillFeedEvent`

---

## Mining Abilities

### Tier 1: Prospector (Level 3)
**Effect:** +15% Mining XP gain  
**Description:** Gain experience 15% faster when mining ores  
**Implementation:** XP multiplier applied on ore break

### Tier 2: Desert Nomad (Level 7)
**Effect:** Permanently +10 max thirst capacity  
**Description:** Your maximum thirst increases from 100 to 110  
**Why Desert Nomad?** Miners work in hot, dry mines and need extra hydration  
**Implementation:** Permanent max stat increase

### Tier 3: Ore Sense (Level 10)
**Effect:** +10% ore drop chance  
**Description:** 10% chance to get an additional ore when mining  
**Implementation:** Extra drop chance on `BreakBlockEvent` for ore blocks

---

## Logging Abilities

### Tier 1: Lumberjack (Level 3)
**Effect:** +15% Logging XP gain  
**Description:** Gain experience 15% faster when chopping logs  
**Implementation:** XP multiplier applied on log break

### Tier 2: Tireless Woodsman (Level 7)
**Effect:** Permanently +10 max energy capacity  
**Description:** Your maximum energy increases from 100 to 110  
**Why Tireless?** Chopping trees is physically demanding work requiring more stamina  
**Implementation:** Permanent max stat increase

### Tier 3: Timber! (Level 10)
**Effect:** 25% chance for extra log drop  
**Description:** When chopping logs, 25% chance to get an additional log  
**Why "Timber!"?** Classic lumberjack shout when a tree falls  
**Implementation:** Extra drop chance on `BreakBlockEvent` for log blocks

---

## Building Abilities

### Tier 1: Architect (Level 3)
**Effect:** +15% Building XP gain  
**Description:** Gain experience 15% faster when placing blocks  
**Implementation:** XP multiplier applied on block placement

### Tier 2: Enduring Builder (Level 7)
**Effect:** Permanently +15 max stamina capacity  
**Description:** Your maximum stamina (Hytale built-in stat) increases by 15  
**Why Enduring?** Construction work requires sustained physical effort  
**Note:** Stamina is NOT part of the metabolism system - it's a vanilla Hytale stat  
**Implementation:** Permanent max stat modifier on `EntityStatMap`

### Tier 3: Efficient Architect (Level 10)
**Effect:** 12% chance to not consume block on placement  
**Description:** When placing blocks, 12% chance the block isn't removed from inventory  
**Why 12%?** Balanced to save ~1 in 8 blocks without being overpowered  
**Implementation:** Random chance check on `PlaceBlockEvent`, cancel item consumption

---

## Gathering Abilities

### Tier 1: Forager (Level 3)
**Effect:** +15% Gathering XP gain  
**Description:** Gain experience 15% faster when picking up items  
**Implementation:** XP multiplier applied on item pickup

### Tier 2: Hearty Gatherer (Level 7)
**Effect:** +4 hunger and +4 thirst per food item pickup  
**Description:** Picking up food items immediately restores hunger and thirst  
**Exception:** This is the ONLY Tier 2 ability that's NOT a permanent max stat increase  
**Why the exception?** Gathering is about finding food, so it makes thematic sense to restore stats  
**Implementation:** Triggered effect on `InteractivelyPickupItemEvent` for food items only

### Tier 3: Survivalist (Level 10)
**Effect:** -15% metabolism depletion rate  
**Description:** ALL metabolism stats (hunger, thirst, energy) deplete 15% slower  
**Why powerful?** Because it's the capstone ability for the survival profession  
**Implementation:** Applies a `0.85` multiplier to all metabolism depletion via `MetabolismService` modifier system

---

## Ability Unlock Levels

| Tier | Unlock Level | Theme |
|------|--------------|-------|
| **Tier 1** | Level 3 | XP boost (+15%) |
| **Tier 2** | Level 7 | Max stat increase (expand survival capacity) |
| **Tier 3** | Level 10 | Unique permanent passive |

**Design Rationale:**
- Level 3 is achievable quickly (encourages early engagement)
- Level 7 is mid-game (survival expansion becomes relevant)
- Level 10 is aspirational (powerful endgame passives)

---

## Ability Synergies

### Best for Long Expeditions
1. **Survivalist (Gathering T3)** - 15% slower depletion on ALL stats
2. **Desert Nomad (Mining T2)** - Extra thirst capacity
3. **Tireless Woodsman (Logging T2)** - Extra energy capacity
4. **Iron Stomach (Combat T2)** - Extra hunger capacity

**Result:** Massively extended survival time between food/water consumption

### Best for Resource Gathering
1. **Ore Sense (Mining T3)** - +10% ore drops
2. **Timber! (Logging T3)** - 25% extra log drops
3. **Efficient Architect (Building T3)** - 12% block saving

**Result:** More resources from the same amount of work

### Best for Leveling Speed
All 5 Tier 1 abilities stack to create +15% XP in all activities, making you level 15% faster across the board.

---

## Implementation Details

### XP Multipliers (Tier 1)
```kotlin
// Applied in XP award logic
val baseXp = 50
val tier1Multiplier = if (hasWarriorAbility) 1.15 else 1.0
val finalXp = (baseXp * tier1Multiplier).toLong()
```

### Max Stat Increases (Tier 2)
```kotlin
// Iron Stomach example
data object IronStomachAbility : Tier2Ability() {
    override val id = "combat_iron_stomach"
    override val name = "Iron Stomach"
    override val description = "Permanently +15 max hunger capacity"
    override val profession = Profession.COMBAT
    val maxHungerBonus = 15.0
}
```

### Triggered Effects (Tier 3)
```kotlin
// Adrenaline Rush example
fun onKill(playerId: UUID, entityRef: Ref<EntityStore>, store: Store<EntityStore>) {
    if (hasAdrenalineRushAbility(playerId)) {
        speedManager.setMultiplier(playerId, "ability:adrenaline", 1.10)
        scheduleRemoval(playerId, 5000L) // Remove after 5 seconds
    }
}
```

---

## Progression Example

**Player starts:** Level 1 in all professions

### Early Game (Levels 1-3)
- **Goal:** Reach level 3 in your main activity
- **Reward:** +15% XP boost accelerates further leveling

### Mid Game (Levels 3-7)
- **Goal:** Reach level 7 in multiple professions
- **Reward:** Expanded survival capacity for longer expeditions

### Late Game (Levels 7-10)
- **Goal:** Reach level 10 in your favorite professions
- **Reward:** Powerful unique passives that enhance your playstyle

### Endgame (Level 10+)
- **Goal:** Max out all 5 professions to level 10
- **Result:** 
  - +15% XP in all activities
  - Massively expanded survival capacity
  - All 5 unique Tier 3 passives active

---

## Balance Notes

### Why +15% XP for Tier 1?
- Not overpowered (still requires 85% of the work)
- Meaningful enough to feel impactful
- Same across all professions for consistency

### Why max stat increases for Tier 2?
- **Cohesive design:** All Tier 2 abilities share the same theme
- **Easy to understand:** "This profession expands this stat"
- **Always useful:** More capacity is never wasted
- **Thematic fit:** Each profession's bonus matches its activity

### Why different Tier 3 effects?
- **Variety:** Keeps each profession feeling unique
- **Playstyle expression:** Choose professions that match your goals
- **Power fantasy:** Tier 3 should feel powerful and special

---

## Death Penalty

**On player death:**
- **Penalty:** Lose 85% of XP in 2 random professions
- **Minimum:** Cannot drop below level 1
- **Notification:** "You lost XP in Mining and Combat due to death"

**Why 85%?**
- Significant penalty that makes death matter
- Not so harsh that it feels unfair (you keep 15%)
- Encourages careful play while maintaining progression

**Why random professions?**
- Prevents "safe" professions from being immune
- Adds an element of risk/reward
- Average penalty is 34% total XP loss (2 out of 5 professions)

---

## UI Display

### Professions Panel (`/ll professions`)
Displays all abilities with unlock status:

```
COMBAT                  Level 5
XP: 2,500 / 10,000 (25%)

* Warrior (Level 3)
  +15% Combat XP gain

* Iron Stomach (Level 7)
  Permanently +15 max hunger capacity

? Adrenaline Rush (Level 10)
  +10% speed for 5 seconds on kill
  [Unlock at Level 10]
```

**Legend:**
- `*` = Unlocked ability (active)
- `?` = Locked ability (shows unlock level)

### Progress Panel (`/ll progress`)
Compact single-line view:

```
PROFESSIONS
Combat    Lv 5  [|||||.....] 25%
Mining    Lv 3  [||........] 15%
Logging   Lv 7  [||||||....] 60%
Building  Lv 1  [..........] 0%
Gathering Lv 10 [||||||||||] 100%
```

---

## See Also

- [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) - Development roadmap
- [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md) - System architecture
- [Ability.kt](../src/main/kotlin/com/livinglands/modules/professions/abilities/Ability.kt) - Ability definitions
- [AbilityRegistry.kt](../src/main/kotlin/com/livinglands/modules/professions/abilities/AbilityRegistry.kt) - Ability lookup system
