# Notification Module Design

## Overview

The Notification module provides a centralized notification service for Living Lands Reloaded, allowing other modules and admin commands to send in-game notifications using Hytale's native notification system.

**Design Philosophy:** Service-only architecture (no full Module class needed) that integrates directly with CoreModule's ServiceRegistry.

---

## Hytale Notification API Analysis

### Discovered API Classes

From `HytaleServer.jar` inspection:

```java
// Notification packet (protocol layer)
com.hypixel.hytale.protocol.packets.interface_.Notification
  - message: FormattedMessage           // Primary notification text
  - secondaryMessage: FormattedMessage  // Secondary/subtitle text (optional)
  - icon: String                        // Icon asset path (optional)
  - item: ItemWithAllMetadata           // Item to display (optional, alternative to icon)
  - style: NotificationStyle            // Visual style

// Notification styles
com.hypixel.hytale.protocol.packets.interface_.NotificationStyle
  - Default   // Standard blue/neutral style
  - Danger    // Red/error style
  - Warning   // Yellow/warning style
  - Success   // Green/success style

// Utility class for sending notifications
com.hypixel.hytale.server.core.util.NotificationUtil
  - sendNotification(PacketHandler, Message, ...)           // To single player
  - sendNotificationToWorld(Message, ..., Store<EntityStore>) // To all in world
  - sendNotificationToUniverse(Message, ...)                // To all online players
```

### Key Findings

1. **Native Notification Support:** Hytale has full notification support - no fallback to chat needed
2. **4 Built-in Styles:** Default, Danger, Warning, Success (maps to info/error/warning/success)
3. **Flexible Content:** Supports primary message, secondary message, icon path, or item display
4. **Multiple Targets:** Can send to single player (via PacketHandler), world, or universe
5. **Message Integration:** Uses `Message` class (same as chat), converts to `FormattedMessage` internally

### API Access Pattern

```kotlin
// Get PacketHandler from PlayerRef
val playerRef = player.getPlayerRef()
val packetHandler = playerRef.getPacketHandler()

// Send notification
NotificationUtil.sendNotification(
    packetHandler,
    Message.raw("Title"),
    Message.raw("Subtitle"),  // optional
    "icon_path",              // optional
    NotificationStyle.Success
)
```

---

## Architecture Decision

### Service-Only Approach (Recommended)

**Decision:** Implement as a **Service** registered in CoreModule, NOT as a full Module.

**Rationale:**
1. **No Lifecycle Needs:** Notifications don't require setup/start/shutdown phases
2. **No Persistence:** Notifications are ephemeral (fire-and-forget)
3. **No Config Required:** Notification types are fixed by Hytale API; no user config needed for MVP
4. **Lightweight:** Avoids Module overhead for a simple utility service
5. **Pattern Consistency:** Similar to how `SpeedManager` is a utility, not a module

**Registration:**
```kotlin
// In CoreModule.initialize()
services.register<NotificationService>(NotificationService(logger))
```

---

## Module Structure

```
src/main/kotlin/com/livinglands/core/
    notification/
        NotificationService.kt    # Main service with send methods
        NotificationType.kt       # Enum mapping to NotificationStyle
        NotificationBuilder.kt    # Optional fluent builder (future)
    commands/
        NotifyCommand.kt          # Admin test command (/ll notify)
```

---

## NotificationType Enum

```kotlin
package com.livinglands.core.notification

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle

/**
 * Notification types with semantic meaning and Hytale style mapping.
 */
enum class NotificationType(val style: NotificationStyle) {
    /** Informational notification (blue/neutral) */
    INFO(NotificationStyle.Default),
    
    /** Success notification (green) - e.g., buff activated, item obtained */
    SUCCESS(NotificationStyle.Success),
    
    /** Warning notification (yellow) - e.g., low stats, area boundary */
    WARNING(NotificationStyle.Warning),
    
    /** Error/danger notification (red) - e.g., debuff active, taking damage */
    DANGER(NotificationStyle.Danger),
    
    /** Achievement notification (green with special formatting) */
    ACHIEVEMENT(NotificationStyle.Success);
    
    companion object {
        /** Get type from string (case-insensitive), defaults to INFO */
        fun fromString(value: String): NotificationType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: INFO
        }
    }
}
```

---

## NotificationService API

```kotlin
package com.livinglands.core.notification

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.protocol.ItemWithAllMetadata
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.util.NotificationUtil
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import java.util.UUID

/**
 * Centralized notification service using Hytale's native notification system.
 * 
 * Provides methods to send notifications to:
 * - Individual players (by UUID or PlayerSession)
 * - All players in a specific world
 * - All online players (universe-wide)
 * 
 * Thread Safety: All methods validate session/entity state before sending.
 * Invalid targets are silently skipped (logged at FINE level).
 * 
 * Usage:
 * ```kotlin
 * val notificationService = CoreModule.services.get<NotificationService>()
 * 
 * // Simple notification
 * notificationService.send(playerId, "Buff activated!", NotificationType.SUCCESS)
 * 
 * // With subtitle and icon
 * notificationService.send(
 *     playerId,
 *     title = "Level Up!",
 *     subtitle = "Mining Level 5",
 *     type = NotificationType.ACHIEVEMENT,
 *     icon = "iron_pickaxe"
 * )
 * ```
 */
class NotificationService(private val logger: HytaleLogger) {
    
    // ============================================================
    // Single Player Methods
    // ============================================================
    
    /**
     * Send a notification to a single player by UUID.
     * 
     * @param playerId Target player's UUID
     * @param title Primary notification text
     * @param type Notification type (affects color/style)
     * @param subtitle Optional secondary text
     * @param icon Optional icon asset path
     * @param item Optional item to display (overrides icon)
     * @return true if notification was sent, false if player not found/invalid
     */
    fun send(
        playerId: UUID,
        title: String,
        type: NotificationType = NotificationType.INFO,
        subtitle: String? = null,
        icon: String? = null,
        item: ItemWithAllMetadata? = null
    ): Boolean {
        val session = CoreModule.players.getSession(playerId)
        if (session == null) {
            logger.atFine().log("Cannot send notification to $playerId - no active session")
            return false
        }
        return send(session, title, type, subtitle, icon, item)
    }
    
    /**
     * Send a notification to a player using their session.
     * Preferred when you already have the PlayerSession reference.
     * 
     * @param session Target player's session
     * @param title Primary notification text
     * @param type Notification type
     * @param subtitle Optional secondary text
     * @param icon Optional icon asset path
     * @param item Optional item to display
     * @return true if notification was sent
     */
    fun send(
        session: PlayerSession,
        title: String,
        type: NotificationType = NotificationType.INFO,
        subtitle: String? = null,
        icon: String? = null,
        item: ItemWithAllMetadata? = null
    ): Boolean {
        if (!session.entityRef.isValid) {
            logger.atFine().log("Cannot send notification - invalid entity ref for ${session.playerId}")
            return false
        }
        
        try {
            val player = session.store.getComponent(session.entityRef, Player.getComponentType())
            if (player == null) {
                logger.atFine().log("Cannot send notification - no Player component for ${session.playerId}")
                return false
            }
            
            @Suppress("DEPRECATION")
            val playerRef = player.getPlayerRef()
            if (playerRef == null) {
                logger.atFine().log("Cannot send notification - no PlayerRef for ${session.playerId}")
                return false
            }
            
            val packetHandler = playerRef.getPacketHandler()
            
            // Build messages
            val titleMessage = Message.raw(title)
            val subtitleMessage = subtitle?.let { Message.raw(it) }
            
            // Send using appropriate NotificationUtil overload
            when {
                item != null -> NotificationUtil.sendNotification(
                    packetHandler,
                    titleMessage,
                    subtitleMessage,
                    item,
                    type.style
                )
                icon != null -> NotificationUtil.sendNotification(
                    packetHandler,
                    titleMessage,
                    subtitleMessage,
                    icon,
                    type.style
                )
                subtitleMessage != null -> NotificationUtil.sendNotification(
                    packetHandler,
                    titleMessage,
                    subtitleMessage,
                    type.style
                )
                else -> NotificationUtil.sendNotification(
                    packetHandler,
                    titleMessage,
                    type.style
                )
            }
            
            logger.atFine().log("Sent ${type.name} notification to ${session.playerId}: $title")
            return true
            
        } catch (e: Exception) {
            logger.atWarning().log("Failed to send notification to ${session.playerId}: ${e.message}")
            return false
        }
    }
    
    // ============================================================
    // World-Wide Methods
    // ============================================================
    
    /**
     * Send a notification to all players in a specific world.
     * 
     * @param worldId Target world UUID
     * @param title Primary notification text
     * @param type Notification type
     * @param subtitle Optional secondary text
     * @param icon Optional icon asset path
     * @return Number of players who received the notification
     */
    fun sendToWorld(
        worldId: UUID,
        title: String,
        type: NotificationType = NotificationType.INFO,
        subtitle: String? = null,
        icon: String? = null
    ): Int {
        val sessions = CoreModule.players.getSessionsInWorld(worldId)
        var successCount = 0
        
        for (session in sessions) {
            if (send(session, title, type, subtitle, icon)) {
                successCount++
            }
        }
        
        logger.atFine().log("Sent ${type.name} notification to $successCount/${sessions.size} players in world $worldId: $title")
        return successCount
    }
    
    /**
     * Send a notification to all players in a world using Hytale's native world broadcast.
     * More efficient for large player counts as it uses a single packet broadcast.
     * 
     * @param worldId Target world UUID
     * @param title Primary notification text
     * @param type Notification type
     * @param subtitle Optional secondary text
     * @param icon Optional icon asset path
     * @return true if broadcast was initiated
     */
    fun broadcastToWorld(
        worldId: UUID,
        title: String,
        type: NotificationType = NotificationType.INFO,
        subtitle: String? = null,
        icon: String? = null
    ): Boolean {
        val worldContext = CoreModule.worlds.getContext(worldId)
        if (worldContext == null) {
            logger.atFine().log("Cannot broadcast to world $worldId - no world context")
            return false
        }
        
        try {
            val titleMessage = Message.raw(title)
            val subtitleMessage = subtitle?.let { Message.raw(it) }
            
            // Use native world broadcast
            NotificationUtil.sendNotificationToWorld(
                titleMessage,
                subtitleMessage,
                icon,
                null,  // item
                type.style,
                worldContext.store
            )
            
            logger.atFine().log("Broadcast ${type.name} notification to world $worldId: $title")
            return true
            
        } catch (e: Exception) {
            logger.atWarning().log("Failed to broadcast notification to world $worldId: ${e.message}")
            return false
        }
    }
    
    // ============================================================
    // Universe-Wide Methods (All Online Players)
    // ============================================================
    
    /**
     * Send a notification to all online players (universe-wide).
     * 
     * @param title Primary notification text
     * @param type Notification type
     * @param subtitle Optional secondary text
     * @param icon Optional icon asset path
     */
    fun sendToAll(
        title: String,
        type: NotificationType = NotificationType.INFO,
        subtitle: String? = null,
        icon: String? = null
    ) {
        try {
            val titleMessage = Message.raw(title)
            val subtitleMessage = subtitle?.let { Message.raw(it) }
            
            when {
                icon != null && subtitleMessage != null -> 
                    NotificationUtil.sendNotificationToUniverse(titleMessage, subtitleMessage, icon, type.style)
                subtitleMessage != null -> 
                    NotificationUtil.sendNotificationToUniverse(titleMessage, subtitleMessage, type.style)
                else -> 
                    NotificationUtil.sendNotificationToUniverse(titleMessage, type.style)
            }
            
            logger.atFine().log("Broadcast ${type.name} notification to universe: $title")
            
        } catch (e: Exception) {
            logger.atWarning().log("Failed to broadcast notification to universe: ${e.message}")
        }
    }
    
    // ============================================================
    // Convenience Methods for Common Notifications
    // ============================================================
    
    /** Send a success notification */
    fun success(playerId: UUID, title: String, subtitle: String? = null, icon: String? = null) =
        send(playerId, title, NotificationType.SUCCESS, subtitle, icon)
    
    /** Send an info notification */
    fun info(playerId: UUID, title: String, subtitle: String? = null, icon: String? = null) =
        send(playerId, title, NotificationType.INFO, subtitle, icon)
    
    /** Send a warning notification */
    fun warning(playerId: UUID, title: String, subtitle: String? = null, icon: String? = null) =
        send(playerId, title, NotificationType.WARNING, subtitle, icon)
    
    /** Send a danger/error notification */
    fun danger(playerId: UUID, title: String, subtitle: String? = null, icon: String? = null) =
        send(playerId, title, NotificationType.DANGER, subtitle, icon)
    
    /** 
     * Send an achievement notification with special formatting.
     * Achievement notifications use SUCCESS style with a trophy icon by default.
     */
    fun achievement(playerId: UUID, title: String, subtitle: String? = null, icon: String = "trophy") =
        send(playerId, title, NotificationType.ACHIEVEMENT, subtitle, icon)
    
    // ============================================================
    // Batch Operations
    // ============================================================
    
    /**
     * Send a notification to multiple specific players.
     * 
     * @param playerIds List of target player UUIDs
     * @param title Primary notification text
     * @param type Notification type
     * @param subtitle Optional secondary text
     * @param icon Optional icon asset path
     * @return Number of players who received the notification
     */
    fun sendToPlayers(
        playerIds: Collection<UUID>,
        title: String,
        type: NotificationType = NotificationType.INFO,
        subtitle: String? = null,
        icon: String? = null
    ): Int {
        var successCount = 0
        for (playerId in playerIds) {
            if (send(playerId, title, type, subtitle, icon)) {
                successCount++
            }
        }
        return successCount
    }
}
```

---

## Admin Commands

### NotifyCommand (`/ll notify`)

```kotlin
package com.livinglands.core.commands

import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.core.notification.NotificationService
import com.livinglands.core.notification.NotificationType

/**
 * Admin command for testing notifications.
 * 
 * Usage:
 *   /ll notify test                    - Send test notification to self
 *   /ll notify test <type>             - Send test notification with specific type
 *   /ll notify broadcast <message>     - Broadcast to all players
 *   /ll notify world <message>         - Broadcast to current world
 */
class NotifyCommand : CommandBase(
    "notify",
    "Notification testing commands",
    true  // Operator-only
) {
    
    init {
        setPermissionGroups(GameMode.Creative.toString())
        
        // Add subcommands
        addSubCommand(TestNotifyCommand())
        addSubCommand(BroadcastNotifyCommand())
        addSubCommand(WorldNotifyCommand())
    }
    
    override fun executeSync(ctx: CommandContext) {
        MessageFormatter.commandInfo(ctx, "Notification Commands:")
        MessageFormatter.commandRaw(ctx, "  /ll notify test [type] - Send test notification to yourself")
        MessageFormatter.commandRaw(ctx, "  /ll notify broadcast <message> - Broadcast to all players")
        MessageFormatter.commandRaw(ctx, "  /ll notify world <message> - Broadcast to current world")
        MessageFormatter.commandRaw(ctx, "")
        MessageFormatter.commandRaw(ctx, "Types: info, success, warning, danger, achievement")
    }
}

/**
 * Test notification subcommand.
 */
class TestNotifyCommand : CommandBase(
    "test",
    "Send a test notification to yourself",
    true
) {
    
    override fun executeSync(ctx: CommandContext) {
        val playerId = ctx.playerUuid
        if (playerId == null) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }
        
        val notificationService = CoreModule.services.get<NotificationService>()
        if (notificationService == null) {
            MessageFormatter.commandError(ctx, "Notification service not available")
            return
        }
        
        // Get type from arguments (default: INFO)
        val args = ctx.arguments
        val type = if (args.isNotEmpty()) {
            NotificationType.fromString(args[0])
        } else {
            NotificationType.INFO
        }
        
        val success = notificationService.send(
            playerId,
            title = "Test Notification",
            type = type,
            subtitle = "This is a ${type.name.lowercase()} notification"
        )
        
        if (success) {
            MessageFormatter.commandSuccess(ctx, "Sent ${type.name} test notification")
        } else {
            MessageFormatter.commandError(ctx, "Failed to send notification")
        }
    }
}

/**
 * Broadcast notification subcommand (all players).
 */
class BroadcastNotifyCommand : CommandBase(
    "broadcast",
    "Broadcast a notification to all online players",
    true
) {
    
    override fun executeSync(ctx: CommandContext) {
        val args = ctx.arguments
        if (args.isEmpty()) {
            MessageFormatter.commandError(ctx, "Usage: /ll notify broadcast <message>")
            return
        }
        
        val notificationService = CoreModule.services.get<NotificationService>()
        if (notificationService == null) {
            MessageFormatter.commandError(ctx, "Notification service not available")
            return
        }
        
        val message = args.joinToString(" ")
        notificationService.sendToAll(
            title = message,
            type = NotificationType.INFO
        )
        
        MessageFormatter.commandSuccess(ctx, "Broadcast notification sent")
    }
}

/**
 * World broadcast notification subcommand.
 */
class WorldNotifyCommand : CommandBase(
    "world",
    "Broadcast a notification to all players in your world",
    true
) {
    
    override fun executeSync(ctx: CommandContext) {
        val playerId = ctx.playerUuid
        if (playerId == null) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }
        
        val args = ctx.arguments
        if (args.isEmpty()) {
            MessageFormatter.commandError(ctx, "Usage: /ll notify world <message>")
            return
        }
        
        val session = CoreModule.players.getSession(playerId)
        if (session == null) {
            MessageFormatter.commandError(ctx, "Could not find your session")
            return
        }
        
        val notificationService = CoreModule.services.get<NotificationService>()
        if (notificationService == null) {
            MessageFormatter.commandError(ctx, "Notification service not available")
            return
        }
        
        val message = args.joinToString(" ")
        val count = notificationService.sendToWorld(
            worldId = session.worldId,
            title = message,
            type = NotificationType.INFO
        )
        
        MessageFormatter.commandSuccess(ctx, "Notification sent to $count players in this world")
    }
}
```

---

## Integration Examples

### Metabolism Module Integration

```kotlin
// In BuffsSystem.kt - when buff activates
private fun sendBuffNotification(playerId: UUID, buffName: String, activated: Boolean) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    if (activated) {
        notificationService.success(
            playerId,
            title = "$buffName Activated",
            subtitle = "Bonus effect applied",
            icon = when (buffName) {
                "Energized" -> "lightning_bolt"
                "Well-Fed" -> "heart"
                "Hydrated" -> "water_drop"
                else -> null
            }
        )
    } else {
        notificationService.info(
            playerId,
            title = "$buffName Ended",
            subtitle = "Effect removed"
        )
    }
}

// In DebuffsSystem.kt - when debuff activates
private fun sendDebuffNotification(playerId: UUID, debuffName: String, activated: Boolean) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    if (activated) {
        notificationService.danger(
            playerId,
            title = debuffName,
            subtitle = "Negative effect applied"
        )
    }
    // Don't notify on deactivation to reduce spam
}

// In FoodConsumptionProcessor.kt - when eating food
fun onFoodConsumed(playerId: UUID, foodName: String, tier: Int) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.success(
        playerId,
        title = "Consumed $foodName",
        subtitle = "Tier $tier food"
    )
}
```

### Leveling Module Integration (Future)

```kotlin
// In LevelingService.kt
fun onLevelUp(playerId: UUID, profession: String, newLevel: Int) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.achievement(
        playerId,
        title = "Level Up!",
        subtitle = "$profession Level $newLevel",
        icon = when (profession.lowercase()) {
            "mining" -> "pickaxe"
            "woodcutting" -> "axe"
            "farming" -> "hoe"
            "combat" -> "sword"
            else -> "star"
        }
    )
}

fun onSkillUnlock(playerId: UUID, skillName: String) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.success(
        playerId,
        title = "Skill Unlocked",
        subtitle = skillName,
        icon = "unlock"
    )
}
```

### Announcer Module Integration (Future)

```kotlin
// In AnnouncerModule.kt
fun scheduleServerRestart(minutesUntil: Int) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.sendToAll(
        title = "Server Restart",
        type = NotificationType.WARNING,
        subtitle = "Restarting in $minutesUntil minutes"
    )
}

fun announceEvent(eventName: String, worldId: UUID? = null) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    if (worldId != null) {
        notificationService.sendToWorld(
            worldId,
            title = "Event Starting",
            type = NotificationType.INFO,
            subtitle = eventName
        )
    } else {
        notificationService.sendToAll(
            title = "Event Starting",
            type = NotificationType.INFO,
            subtitle = eventName
        )
    }
}
```

### Claims Module Integration (Future)

```kotlin
// In ClaimsModule.kt
fun onEnterClaim(playerId: UUID, claimName: String, ownerName: String) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.info(
        playerId,
        title = "Entering Claim",
        subtitle = "$claimName (owned by $ownerName)"
    )
}

fun onLeaveClaim(playerId: UUID) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.info(
        playerId,
        title = "Wilderness",
        subtitle = "You left the claimed area"
    )
}

fun onClaimProtected(playerId: UUID, action: String) {
    val notificationService = CoreModule.services.get<NotificationService>() ?: return
    
    notificationService.warning(
        playerId,
        title = "Protected Area",
        subtitle = "Cannot $action here"
    )
}
```

---

## CoreModule Registration

```kotlin
// In CoreModule.initialize(), after other services:

// Initialize notification service
val notificationService = NotificationService(logger)
services.register<NotificationService>(notificationService)

// In CoreModule.initializeCommands() or similar:
mainCommand.registerSubCommand(NotifyCommand())
```

---

## Thread Safety

The NotificationService is inherently thread-safe:

1. **Stateless:** The service maintains no mutable state
2. **Session Validation:** Each send operation validates the session before use
3. **Entity Ref Validation:** Checks `entityRef.isValid` before accessing components
4. **Exception Handling:** All operations wrapped in try/catch to prevent cascade failures
5. **Fire-and-Forget:** Notifications don't require response tracking

---

## Performance Considerations

### Single Player Notifications

- **Cost:** Single packet per player (minimal)
- **When to Use:** Individual player events (buffs, debuffs, level ups)

### World Broadcast (via sendToWorld)

- **Cost:** Iterates all players in world, sends individual packets
- **When to Use:** Small player counts (<50 players)

### World Broadcast (via broadcastToWorld)

- **Cost:** Single native world broadcast (Hytale handles distribution)
- **When to Use:** Larger player counts, world events

### Universe Broadcast

- **Cost:** Single native universe broadcast
- **When to Use:** Server-wide announcements (restart warnings, events)

### Recommendations

1. **Avoid Rapid Fire:** Don't send multiple notifications per tick to same player
2. **Debounce if Needed:** For rapidly changing states, consider debouncing (not in MVP)
3. **Use World Broadcast:** For world-wide events, prefer `broadcastToWorld` over iteration
4. **Minimize Metadata:** Keep titles short, subtitles optional

---

## Configuration (Optional - Not for MVP)

If configuration becomes needed in the future:

```yaml
# notifications.yml
enabled: true

# Rate limiting (future)
rateLimit:
  enabled: false
  maxPerSecond: 5
  
# Default icons by type (future)
defaultIcons:
  success: "check_circle"
  warning: "warning_triangle"
  danger: "x_circle"
  achievement: "trophy"
  
# Debug mode
debug: false
```

For MVP, no configuration file is needed - all settings are code-based.

---

## FAQ / Design Decisions

### Q: Why not a full Module?

**A:** Notifications have no lifecycle requirements (no setup, no database, no tick system). A simple service registered in CoreModule is sufficient and avoids unnecessary complexity.

### Q: Should notifications queue if sent rapidly?

**A:** Not for MVP. Hytale's client handles notification display; rapid notifications may stack or replace. If this becomes a UX issue, add debouncing in a future iteration.

### Q: Should we use chat fallback?

**A:** No. Hytale's native notification system is fully functional. Chat is for conversation; notifications are for transient alerts.

### Q: Why use NotificationUtil instead of raw packets?

**A:** `NotificationUtil` handles all the packet construction, FormattedMessage conversion, and distribution. It's the official API.

### Q: How do icons work?

**A:** The `icon` parameter is a string asset path. Common icons may include item IDs or UI asset names. Exact paths need testing in-game to determine valid values.

### Q: Can we show items instead of icons?

**A:** Yes! `ItemWithAllMetadata` can be passed to show an actual item icon. This is useful for "You received X" style notifications.

---

## Implementation Checklist

- [ ] Create `NotificationType.kt` enum
- [ ] Create `NotificationService.kt` service class
- [ ] Register service in `CoreModule.initialize()`
- [ ] Create `NotifyCommand.kt` with subcommands
- [ ] Register command in `CoreModule` or `LLCommand`
- [ ] Update BuffsSystem to optionally use notifications (opt-in)
- [ ] Update DebuffsSystem to optionally use notifications (opt-in)
- [ ] Test all notification types in-game
- [ ] Test world broadcast
- [ ] Test universe broadcast
- [ ] Document icon paths that work (via testing)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01-25 | Initial design based on Hytale API analysis |
