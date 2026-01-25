# Living Lands Reloaded

**Survival mechanics reimagined for Hytale**

Add realistic hunger, thirst, and energy systems to your Hytale server with per-world customization and smooth gameplay integration.

> ⚠️ **Compatibility Warning:** This mod includes its own leveling, economy, and profession systems. Not compatible with other mods that modify player stats, XP, or economy. See [Compatibility Notice](#️-compatibility-notice) for details.

---

## 🎮 What is Living Lands?

Living Lands Reloaded brings survival mechanics to Hytale servers. Players must manage their hunger, thirst, and energy stats while exploring, building, and adventuring. Low stats cause penalties, high stats grant bonuses, and eating food restores your vitals.

**Perfect for:**
- Survival servers wanting deeper mechanics
- RPG servers needing stat-based gameplay
- Multi-world servers with different difficulty levels

---

## ✨ Features

### Core Gameplay

**Metabolism System**
- Track hunger, thirst, and energy (0-100 scale)
- Stats drain faster when sprinting, swimming, or fighting
- Eat food to restore your vitals
- Real-time HUD shows current stats on-screen

**Dynamic Effects**
- **Low Stats** - Movement slowdown, stamina reduction, damage over time
- **High Stats** - Speed boost, enhanced performance (coming soon)
- Smart hysteresis prevents flickering on/off effects

**Food Integration**
- Automatically detects when you eat food
- Different foods restore different amounts
- Meat for hunger, water for thirst, stamina food for energy

### Server Features

**Per-World Configuration**
- Set different survival rules for each world
- Creative worlds can disable metabolism entirely
- Hardcore worlds can have 3x faster depletion
- PvP arenas can drain energy during combat

**Hot-Reload System**
- Change settings without restarting the server
- Use `/ll reload` to apply config changes instantly
- Players stay connected, no interruptions

**Global Player Stats**
- Metabolism stats follow players across all worlds
- Enter World A at 50% hunger, switch to World B still at 50%
- Per-world configs still work (different depletion rates per world)
- Perfect for multi-world setups with consistent progression

---

## 🎯 For Players

### Getting Started

When you join a Living Lands server, you'll see a HUD display showing your metabolism stats:
- **Hunger** - Red bar (depletes every ~48 minutes idle)
- **Thirst** - Blue bar (depletes every ~36 minutes idle)
- **Energy** - Yellow bar (depletes every ~40 minutes idle)

### Surviving

**Managing Your Stats:**
1. Eat food to restore hunger and thirst
2. Drink potions to restore energy
3. Avoid sprinting when stats are low
4. Watch for warning effects at 20% and below

**Effects You'll Experience:**

| Stat | Threshold | Effect |
|------|-----------|--------|
| Hunger < 20% | Starvation | Slowness, weakness |
| Hunger = 0% | Starvation | Damage over time |
| Thirst < 20% | Dehydration | Movement speed reduced |
| Energy < 15% | Exhaustion | Stamina drain, can't sprint long |

**Commands:**
- `/ll stats` - Check your current metabolism values

---

## 🛠️ For Server Admins

### Installation

1. Download `livinglands-1.0.0-beta.jar` from Files tab
2. Place in your server's `plugins/` or `mods/` folder
3. Start the server
4. Config files auto-generate in `LivingLandsReloaded/config/`

**Requirements:**
- Hytale Server (latest build)
- Java 25+

### ⚠️ Compatibility Notice

**This mod is NOT compatible with:**
- Mods that manipulate player stats (health, stamina, speed)
- Leveling or XP systems
- Economy plugins
- Profession/skill systems

Living Lands Reloaded manages its own leveling, economy, and profession systems. Running multiple mods that modify the same player stats can cause conflicts, duplicate effects, or unexpected behavior.

**If you're using another survival/RPG mod, choose one:**
- Use Living Lands Reloaded for complete survival mechanics
- OR use the other mod and disable Living Lands

### Configuration

#### Basic Setup

Edit `LivingLandsReloaded/config/metabolism.yml`:

```yaml
configVersion: 4
enabled: true

# Global defaults (all worlds)
hunger:
  enabled: true
  baseDepletionRateSeconds: 2880.0  # 48 minutes idle

thirst:
  enabled: true
  baseDepletionRateSeconds: 2160.0  # 36 minutes idle

energy:
  enabled: true
  baseDepletionRateSeconds: 2400.0  # 40 minutes idle
```

#### Per-World Customization

Set different rules for specific worlds:

```yaml
worldOverrides:
  hardcore:  # World name (case-insensitive)
    hunger:
      baseDepletionRateSeconds: 960.0   # 3x faster
    thirst:
      baseDepletionRateSeconds: 720.0   # 3x faster
  
  creative:
    hunger:
      enabled: false  # Disable entirely
    thirst:
      enabled: false
    energy:
      enabled: false
  
  pvparena:
    energy:
      baseDepletionRateSeconds: 300.0  # 5 minutes
      activityMultipliers:
        combat: 3.0  # 3x drain during combat
```

**Note:** World names are case-insensitive. You can also use world UUIDs.

#### Activity Multipliers

Control how activities affect depletion:

```yaml
hunger:
  activityMultipliers:
    idle: 1.0       # Normal rate
    walking: 1.2    # 20% faster
    sprinting: 2.0  # 2x faster
    swimming: 1.8   # 1.8x faster
    combat: 2.5     # 2.5x faster
```

### Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/ll stats` | All players | Show metabolism stats |
| `/ll reload` | Operator | Reload all configs |
| `/ll reload metabolism` | Operator | Reload specific config |

### Data Storage

```
LivingLandsReloaded/
├── config/
│   ├── core.yml           # Core settings
│   └── metabolism.yml     # Metabolism config
└── data/
    ├── {world-uuid-1}/
    │   └── livinglands.db # World 1 player data
    └── {world-uuid-2}/
        └── livinglands.db # World 2 player data
```

**Backup Recommendation:** Include `LivingLandsReloaded/data/` in server backups to preserve player progression.

---

## 🎨 Customization Examples

### Casual Survival Server

Slow depletion, forgiving penalties:

```yaml
hunger:
  baseDepletionRateSeconds: 3600.0  # 1 hour idle
thirst:
  baseDepletionRateSeconds: 2880.0  # 48 minutes
debuffs:
  enterThreshold: 10  # Debuffs only below 10%
```

### Hardcore Server

Fast depletion, harsh penalties:

```yaml
hunger:
  baseDepletionRateSeconds: 1200.0  # 20 minutes idle
thirst:
  baseDepletionRateSeconds: 900.0   # 15 minutes
debuffs:
  enterThreshold: 30  # Debuffs below 30%
```

### Multi-World Setup

Different rules per world type:

```yaml
worldOverrides:
  overworld:
    # Use global defaults
  
  nether:
    thirst:
      baseDepletionRateSeconds: 720.0  # 5x faster in heat
  
  skyblock:
    hunger:
      baseDepletionRateSeconds: 1440.0  # Slower depletion
    thirst:
      baseDepletionRateSeconds: 1080.0
```

---

## 🐛 Troubleshooting

### Stats not depleting?

1. Check if module is enabled: `/ll reload`
2. Verify `enabled: true` in `metabolism.yml`
3. Ensure you're not in Creative mode (metabolism pauses)

### HUD not showing?

1. Reconnect to the server
2. Check server logs for errors
3. Verify plugin loaded successfully

### Config changes not applying?

1. Run `/ll reload metabolism`
2. Check server console for config errors
3. Verify YAML syntax (indentation matters)

### Stats reset when changing worlds?

**This is intentional** - Living Lands uses per-world progression. Each world has separate player stats.

---

## 📊 Performance

Living Lands Reloaded is optimized for large servers:

- **100+ concurrent players** tested at 30 TPS
- **Zero-allocation hot paths** prevent lag
- **Async database I/O** keeps server responsive
- **Per-world databases** prevent lock contention

**Benchmark (100 players):**
- CPU: <1ms per tick
- Memory: ~2MB per world
- Disk: Minimal (stats saved on disconnect)

---

## 🗺️ Roadmap

### Current Version: 1.0.0-beta

**Completed:**
- ✅ Metabolism system (hunger, thirst, energy)
- ✅ Per-world configuration
- ✅ Live HUD display
- ✅ Food consumption integration
- ✅ Hot-reload support
- ✅ Debuffs for low stats

**Coming Soon:**
- ⏳ Buffs for high stats
- ⏳ Polish and optimization pass

### Planned Features (Not Yet Implemented)

> 🚧 **Note:** The features below are planned for future releases but are NOT currently available in v1.0.0-beta. If you need these features now, use a different mod.

**Leveling Module** (Planned)
- XP system with professions (Mining, Logging, Combat)
- Skill progression and rewards
- ⚠️ Until released, this mod is compatible with other leveling mods

**Claims Module** (Planned)
- Land protection system
- Trust management for players

**Economy Module** (Planned)
- Currency system
- Trading and shops
- ⚠️ Until released, this mod is compatible with other economy mods

**Advanced Metabolism** (Planned)
- Poison effects
- Custom food recipes
- Seasonal depletion rates

---

## 🆘 Support

**Need Help?**
- [Report Issues](https://github.com/MoshPitCodes/living-lands-reloaded/issues)
- [View Documentation](https://github.com/MoshPitCodes/living-lands-reloaded/blob/main/README.md)
- [Technical Design](https://github.com/MoshPitCodes/living-lands-reloaded/blob/main/docs/TECHNICAL_DESIGN.md)

**Found a Bug?**
Please report with:
1. Server version
2. Plugin version
3. Steps to reproduce
4. Server log excerpt

---

## 📜 License

Living Lands Reloaded is licensed under **Apache License 2.0**.

Free to use on your server. Modification and redistribution allowed with attribution.

---

## ❤️ Credits

**Author:** MoshPitCodes  
**Version:** 1.0.0-beta  
**Source Code:** [GitHub](https://github.com/MoshPitCodes/living-lands-reloaded)

Built from the ground up with a modular architecture for performance and extensibility.

---

*Bring survival mechanics to life on your Hytale server.*
