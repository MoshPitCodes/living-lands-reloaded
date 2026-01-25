# Living Lands - Deferred Features

**Purpose:** This document tracks features that have been designed but deferred until there's a concrete use case or dependency ready.

---

## Notification Module

**Status:** ⏸️ Deferred (Designed but not implemented)  
**Reason:** No concrete use cases yet - existing modules intentionally use chat messages  
**Prerequisite:** Leveling module or other feature requiring popup notifications

### Overview

A centralized notification service for sending Hytale native popup notifications (not chat messages) to players.

### Design Summary

**Architecture:** Service-only (registered in CoreModule, not a full Module)

```kotlin
// Accessed via ServiceRegistry
val notifications = CoreModule.services.get<NotificationService>()

// API examples
notifications.success(playerId, "Level Up!", "Mining Level 5")
notifications.warning(playerId, "Server restart in 5 minutes")
notifications.achievement(playerId, "Achievement Unlocked!", "First Diamond", icon = "diamond")

// Multi-target
notifications.sendToWorld(worldId, "Event starting!", NotificationType.INFO)
notifications.sendToAll("Server maintenance soon", NotificationType.WARNING)
```

### Hytale API Reference

From `HytaleServer.jar` (verified 2026-01-25):

```java
// com.hypixel.hytale.server.core.universe.notification.NotificationUtil
public static void sendNotification(PacketHandler handler, Message title, NotificationStyle style, Message subtitle, String icon)
public static void sendNotificationToWorld(Message title, NotificationStyle style, Message subtitle, String icon, Store store)
public static void sendNotificationToUniverse(Message title, NotificationStyle style, Message subtitle, String icon)

// com.hypixel.hytale.server.core.universe.notification.NotificationStyle (enum)
Default, Danger, Warning, Success
```

### When to Implement

Implement when **any** of these scenarios occur:

1. **Leveling Module** needs achievement popups (e.g., "Level Up! Mining Level 5")
2. **Server Events** need urgent attention-grabbing alerts (e.g., restart warnings)
3. **Achievements System** requires celebration notifications
4. **Admin Tools** need broadcast announcements distinct from chat

### When NOT to Use Notifications

**Important:** Popup notifications and chat messages serve different purposes.

| Use Case | Use Chat | Use Notification |
|----------|----------|------------------|
| Metabolism buff/debuff messages | ✅ Yes | ❌ No - players need chat log |
| Announcer MOTD/welcome | ✅ Yes | ❌ No - should persist in chat |
| Claims protection messages | ✅ Yes | ❌ No - need to reference later |
| Level up celebration | ❌ No | ✅ Yes - ephemeral, celebratory |
| Server restart warning | ⚠️ Both | ✅ Yes - urgent, attention-grabbing |
| Achievement unlocked | ❌ No | ✅ Yes - doesn't need chat log |

**Guideline:** Use notifications for **urgent, celebratory, or transient** information. Use chat for **contextual, persistent, or reference** information.

### Implementation Checklist

When implementing, address these code review findings:

**Critical Issues (Must Fix):**
- [ ] Fix race condition: Re-validate session inside `world.execute {}`
- [ ] Add try-catch around all `NotificationUtil` calls
- [ ] Fix `broadcastToWorld` to access store inside execute block
- [ ] Document icon parameter format (item ID? resource path?)

**Warnings (Should Fix):**
- [ ] Add logging at FINE level for failures
- [ ] Remove redundant methods (keep only `broadcastToWorld`)
- [ ] Add title/subtitle length validation (max 64/128 chars)
- [ ] Add rate limiting to prevent spam (1 second cooldown per unique title)

**Enhancements (Consider):**
- [ ] Add configuration file for notification styles
- [ ] Add builder pattern for complex notifications
- [ ] Add admin commands: `/ll notify test [type]`, `/ll notify broadcast`

### File Structure (When Implemented)

```
src/main/kotlin/com/livinglands/core/notification/
├── NotificationService.kt       # Main service
└── NotificationType.kt          # Enum (INFO, SUCCESS, WARNING, DANGER, ACHIEVEMENT)

src/main/kotlin/com/livinglands/core/commands/
└── NotifyCommand.kt             # Admin test commands

CoreModule.kt                    # Register NotificationService
```

### Integration Examples

**Leveling Module (future):**
```kotlin
// When player levels up
CoreModule.services.get<NotificationService>()
    .achievement(playerId, "Level Up!", "Mining Level ${newLevel}")
```

**Admin Tools:**
```kotlin
// Server restart warning
CoreModule.services.get<NotificationService>()
    .sendToAll("Server restart in 5 minutes", NotificationType.WARNING)
```

**DO NOT use for:**
```kotlin
// ❌ BAD - Metabolism buff (use chat instead)
notifications.success(playerId, "Energized")  // Players need this in chat log!

// ✅ GOOD - Use existing MessageFormatter
MessageFormatter.raw(playerRef, "You feel energized! (+10% speed)")
```

### Design Documentation

Full design document available in git history:
- Commit: [To be added when implemented]
- Design review: Session `ses_408eb6ea9ffe90aTh7zwjP9Kkz` (2026-01-25)
- Agents consulted: java-kotlin-backend, code-review

### Decision Record

**Date:** 2026-01-25  
**Decision:** Defer implementation until concrete use case exists  
**Rationale:**
- No modules currently need popup notifications
- Existing modules (Metabolism, Announcer) intentionally use chat messages for persistent visibility
- Popup notifications serve different UX purpose than chat (ephemeral vs persistent)
- Premature abstraction adds maintenance burden without benefit
- Design is 70% complete and can be implemented quickly when needed

**Alternatives Considered:**
1. Implement now as "experimental" - Rejected (dead code risk)
2. Implement minimal version - Rejected (still no use case)
3. Skip entirely - Rejected (will be useful for Leveling module)

**Next Review:** When Leveling module or Achievement system is planned

---

## Template for Future Deferred Features

When adding new deferred features, use this structure:

```markdown
## Feature Name

**Status:** ⏸️ Deferred / 🚧 Blocked / 📋 Planned  
**Reason:** Brief explanation  
**Prerequisite:** What needs to happen first

### Overview
Brief description

### When to Implement
Trigger conditions

### Implementation Checklist
- [ ] Task 1
- [ ] Task 2

### Design Documentation
Links to design docs, commits, etc.

### Decision Record
Date, decision, rationale
```
