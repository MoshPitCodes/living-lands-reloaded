# Living Lands - UI Patterns Guide

This guide documents the UI patterns used in Living Lands, adapted from the [hytale-basic-uis](https://github.com/trouble-dev/hytale-basic-uis) repository.

## Table of Contents

- [Overview](#overview)
- [Page Types](#page-types)
- [BasicCustomUIPage](#basiccustomuipage)
- [InteractiveCustomUIPage](#interactivecustomuipage)
- [UI File Format](#ui-file-format)
- [Event Data Codec Pattern](#event-data-codec-pattern)
- [Command Integration](#command-integration)
- [HUD vs Pages](#hud-vs-pages)

---

## Overview

Living Lands uses two types of custom UI elements:

1. **Full-Screen Pages** - Modal dialogs, stats screens, forms (using `CustomUIPage`)
2. **Persistent HUD** - Always-visible overlays like stat bars (using `CustomUIHud`)

This document focuses on **full-screen pages** using the `CustomUIPage` API.

---

## Page Types

### BasicCustomUIPage

For **static display** pages with no user interaction.

**Use cases:**
- Information panels
- Stats displays
- Read-only dialogs
- Simple notifications

**Example:**
```kotlin
class InfoPage(playerRef: PlayerRef) : BasicCustomUIPage(playerRef) {
    override fun build(cmd: UICommandBuilder) {
        cmd.append("Pages/InfoPage.ui")
        cmd.set("#Title.Text", "Server Information")
        cmd.set("#Players.Text", "42 online")
    }
}
```

### InteractiveCustomUIPage

For **interactive** pages with buttons, forms, or user input.

**Use cases:**
- Forms with text input
- Button dialogs
- Settings panels
- Interactive menus

**Example:**
```kotlin
class SettingsPage(
    playerRef: PlayerRef
) : InteractiveCustomUIPage<SettingsPage.SettingsData>(
    playerRef,
    CustomPageLifetime.CanDismissOrCloseThroughInteraction,
    SettingsData.CODEC
) {
    data class SettingsData(
        var action: String = "",
        var playerName: String = "",
        var enabled: Boolean = false
    ) {
        companion object {
            val CODEC = BuilderCodec.builder(SettingsData::class.java) { SettingsData() }
                .append(
                    KeyedCodec("Action", Codec.STRING),
                    { obj, val -> obj.action = val },
                    { obj -> obj.action }
                )
                .add()
                .append(
                    KeyedCodec("@PlayerName", Codec.STRING),  // @ = UI input
                    { obj, val -> obj.playerName = val },
                    { obj -> obj.playerName }
                )
                .add()
                .append(
                    KeyedCodec("@Enabled", Codec.BOOLEAN),
                    { obj, val -> obj.enabled = val },
                    { obj -> obj.enabled }
                )
                .add()
                .build()
        }
    }
    
    override fun build(ref: Ref<EntityStore>, cmd: UICommandBuilder, 
                       evt: UIEventBuilder, store: Store<EntityStore>) {
        cmd.append("Pages/SettingsPage.ui")
        
        // Bind save button with form data capture
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveButton",
            EventData()
                .append("Action", "Save")
                .append("@PlayerName", "#NameInput.Value")
                .append("@Enabled", "#EnableCheckbox.Value")
        )
    }
    
    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, 
                                  data: SettingsData) {
        val player = store.getComponent(ref, Player.getComponentType())
        
        if (data.action == "Save") {
            playerRef.sendMessage(Message.raw("Settings saved: ${data.playerName}"))
        }
        
        // Close the page
        player.pageManager.setPage(ref, store, Page.None)
    }
}
```

---

## BasicCustomUIPage

### Constructor Parameters

```kotlin
BasicCustomUIPage(
    playerRef: PlayerRef,                           // Required: Player reference
    lifetime: CustomPageLifetime = CanDismiss       // Optional: How page can be closed
)
```

### Lifetime Options

- `CustomPageLifetime.CanDismiss` - Player can press ESC to close (default)
- `CustomPageLifetime.CannotDismiss` - Player cannot close (force interaction)

### Building the UI

The `build()` method constructs the UI:

```kotlin
override fun build(cmd: UICommandBuilder) {
    // 1. Load UI definition file
    cmd.append("Pages/MyPage.ui")
    
    // 2. Set dynamic values
    cmd.set("#Title.Text", "My Title")
    cmd.set("#Value.Text", "42")                    // Numbers as String
    cmd.set("#Checkbox.Value", "true")              // Booleans as String
}
```

### Element Selectors

Format: `#ElementId.Property`

**Common properties:**
- `.Text` - Label/button text content
- `.Value` - Input field values, checkbox states
- `.Visible` - Show/hide elements (true/false as String)
- `.Background` - Background color

---

## InteractiveCustomUIPage

### Constructor Parameters

```kotlin
InteractiveCustomUIPage<T>(
    playerRef: PlayerRef,
    lifetime: CustomPageLifetime = CanDismissOrCloseThroughInteraction,
    codec: BuilderCodec<T>
)
```

### Event Data Pattern

Define a data class with a `BuilderCodec` to capture form/button data:

```kotlin
data class FormData(
    var action: String = "",       // Regular field (set explicitly)
    var playerName: String = "",   // Input binding (from UI element)
    var enabled: Boolean = false   // Checkbox binding
) {
    companion object {
        val CODEC = BuilderCodec.builder(FormData::class.java) { FormData() }
            // Regular field - set explicitly in EventData
            .append(
                KeyedCodec("Action", Codec.STRING),
                { obj, val -> obj.action = val },
                { obj -> obj.action }
            )
            .add()
            // Input binding - prefixed with @ in KeyedCodec
            .append(
                KeyedCodec("@PlayerName", Codec.STRING),  // @ = UI input
                { obj, val -> obj.playerName = val },
                { obj -> obj.playerName }
            )
            .add()
            .append(
                KeyedCodec("@Enabled", Codec.BOOLEAN),
                { obj, val -> obj.enabled = val },
                { obj -> obj.enabled }
            )
            .add()
            .build()
    }
}
```

### The @ Prefix Convention

Fields prefixed with `@` in the `KeyedCodec` represent **UI input bindings**:

- `KeyedCodec("@PlayerName", ...)` - Read from `#NameInput.Value`
- `KeyedCodec("Action", ...)` - Set explicitly in `EventData`

### Event Binding

Bind UI elements to event handlers:

```kotlin
override fun build(ref: Ref<EntityStore>, cmd: UICommandBuilder, 
                   evt: UIEventBuilder, store: Store<EntityStore>) {
    cmd.append("Pages/FormPage.ui")
    
    // Button with form data capture
    evt.addEventBinding(
        CustomUIEventBindingType.Activating,      // Button click
        "#SaveButton",                             // Element selector
        EventData()
            .append("Action", "Save")              // Constant value
            .append("@PlayerName", "#NameInput.Value")    // TextField
            .append("@Enabled", "#EnableCheckbox.Value")  // CheckBox
    )
    
    // Cancel button (no data)
    evt.addEventBinding(
        CustomUIEventBindingType.Activating,
        "#CancelButton",
        EventData().append("Action", "Cancel")
    )
}
```

### Handling Events

Process the event data:

```kotlin
override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, 
                              data: FormData) {
    val player = store.getComponent(ref, Player.getComponentType())
    
    when (data.action) {
        "Save" -> {
            playerRef.sendMessage(Message.raw("Saved: ${data.playerName}"))
        }
        "Cancel" -> {
            playerRef.sendMessage(Message.raw("Cancelled"))
        }
    }
    
    // Always close the page when done
    player.pageManager.setPage(ref, store, Page.None)
}
```

---

## UI File Format

UI files use Hytale's declarative UI syntax.

### Example: Static Info Panel

```ui
// Pages/InfoPage.ui
Group {
    Anchor: (Width: 400, Height: 200);
    Background: #1a1a2e(0.95);          // Color with alpha
    LayoutMode: Top;                    // Vertical stacking
    Padding: (Full: 20);

    Label #Title {
        Text: "Server Information";
        Anchor: (Height: 40);
        Style: (FontSize: 24, TextColor: #ffffff, Alignment: Center);
    }

    Label #Players {
        Text: "0 online";               // Set dynamically from Java/Kotlin
        Anchor: (Height: 30);
        Style: (FontSize: 16, TextColor: #888888);
    }
}
```

### Example: Interactive Form

```ui
$C = "../Common.ui";  // Reference to built-in templates

Group {
    Anchor: (Width: 400, Height: 280);
    Background: #1a1a2e(0.95);
    LayoutMode: Top;
    Padding: (Full: 20);

    Label #Title {
        Text: "Settings";
        Anchor: (Height: 40);
        Style: (FontSize: 24, TextColor: #ffffff);
    }

    // Text input field
    $C.@TextField #NameInput {
        Anchor: (Height: 40);
        PlaceholderText: "Enter your name...";
    }

    // Checkbox
    $C.@CheckBoxWithLabel #EnableCheckbox {
        @Text = "Enable feature";
        @Checked = true;                // Default state
        Anchor: (Height: 28);
    }

    // Save button
    TextButton #SaveButton {
        Anchor: (Height: 45);
        Text: "Save";
        Style: @SaveButtonStyle;        // Reference to style below
    }
}

// Button style definition
@SaveButtonStyle = TextButtonStyle(
    Default: (Background: #3a7bd5, LabelStyle: (FontSize: 16, TextColor: #ffffff)),
    Hovered: (Background: #4a8be5),
    Pressed: (Background: #2a6bc5)
);
```

### Layout Modes

- `LayoutMode: Top` - Vertical stacking (most common)
- `LayoutMode: Left` - Horizontal stacking
- `LayoutMode: Center` - Centered content
- `LayoutMode: Right` - Right-aligned

### Sizing

- `Anchor: (Width: 400, Height: 200)` - Fixed size
- `FlexWeight: 1` - Takes remaining space proportionally
- `Anchor: (Height: 40)` - Fixed height, flexible width

### Built-in Templates (Common.ui)

- `$C.@TextField` - Standard text input
- `$C.@NumberField` - Numeric input
- `$C.@CheckBoxWithLabel` - Checkbox with label

---

## Event Data Codec Pattern

### Available Codec Types

- `Codec.STRING` - Text fields
- `Codec.BOOLEAN` - Checkboxes, toggles
- `Codec.INT` / `Codec.LONG` - Number fields
- `Codec.FLOAT` / `Codec.DOUBLE` - Decimal numbers

### Empty EventData (Close-Only Buttons)

For buttons that just trigger an action without sending data:

```kotlin
data class CloseEventData(val dummy: String = "") {
    companion object {
        val CODEC = BuilderCodec.builder(CloseEventData::class.java) { CloseEventData() }
            .build()  // No fields
    }
}

// Bind close button
evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton")
```

### Multiple Button Actions

Different buttons sending different actions:

```kotlin
// Save button - captures all form data
evt.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#SaveButton",
    EventData()
        .append("Action", "Save")
        .append("@PlayerName", "#NameInput.Value")
)

// Cancel button - just sets action
evt.addEventBinding(
    CustomUIEventBindingType.Activating,
    "#CancelButton",
    EventData().append("Action", "Cancel")
)

// Handle in handleDataEvent():
when (data.action) {
    "Save" -> { /* save logic */ }
    "Cancel" -> { /* cancel logic */ }
}
```

---

## Command Integration

### Opening a Page

```kotlin
class StatsCommand : CommandBase("stats", "View your stats", false) {
    override fun executeSync(ctx: CommandContext) {
        val entityRef = ctx.senderAsPlayerRef() ?: return
        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }
        
        if (session != null) {
            session.world.execute {
                val player = session.store.getComponent(
                    session.entityRef,
                    Player.getComponentType()
                )
                
                if (player != null) {
                    @Suppress("DEPRECATION")
                    val playerRef = player.playerRef
                    
                    if (playerRef != null) {
                        val page = StatsPage(playerRef, hunger = 75f, thirst = 80f)
                        player.pageManager.openCustomPage(session.entityRef, session.store, page)
                    }
                }
            }
        }
    }
}
```

### Key Points

1. **World Thread Required** - Always wrap in `world.execute { }`
2. **Get Player Component** - Use `store.getComponent(ref, Player.getComponentType())`
3. **Get PlayerRef** - Use `player.playerRef` (deprecated but necessary)
4. **Open Page** - Use `player.pageManager.openCustomPage(ref, store, page)`

---

## HUD vs Pages

### When to Use HUD (CustomUIHud)

- Always-visible stat bars
- Persistent overlays
- Real-time updating displays
- Non-intrusive information

**Example:** Metabolism HUD with hunger/thirst/energy bars (top-left corner)

### When to Use Pages (CustomUIPage)

- Full-screen modal dialogs
- Forms requiring user input
- Detailed stat breakdowns
- Settings panels

**Example:** Metabolism stats page with detailed breakdown (opened with `/ll show`)

### The Metabolism Pattern

Living Lands uses **both**:

1. **Persistent HUD** (`MetabolismHudElement` extends `CustomUIHud`)
   - Always visible in top-left corner
   - Shows current hunger/thirst/energy bars
   - Updates automatically every tick
   - Managed by `MultiHudManager`

2. **Stats Page** (`MetabolismStatsPage` extends `BasicCustomUIPage`)
   - Opened with `/ll show` command
   - Full-screen detailed view
   - Shows buffs/debuffs, exact values
   - Player closes with ESC

---

## Real-World Examples

### Example 1: Metabolism Stats Page (BasicCustomUIPage)

**File:** `src/main/kotlin/com/livinglands/modules/metabolism/pages/MetabolismStatsPage.kt`

```kotlin
class MetabolismStatsPage(
    playerRef: PlayerRef,
    private val hunger: Float,
    private val thirst: Float,
    private val energy: Float,
    private val activeBuffs: List<String> = emptyList(),
    private val activeDebuffs: List<String> = emptyList()
) : BasicCustomUIPage(playerRef) {
    
    override fun build(cmd: UICommandBuilder) {
        cmd.append("Pages/MetabolismStatsPage.ui")
        
        // Set stat values
        cmd.set("#HungerValue.Text", hunger.toInt().toString())
        cmd.set("#ThirstValue.Text", thirst.toInt().toString())
        cmd.set("#EnergyValue.Text", energy.toInt().toString())
        
        // Set progress bar widths
        cmd.set("#HungerBar.Value", (hunger / 100f).toString())
        cmd.set("#ThirstBar.Value", (thirst / 100f).toString())
        cmd.set("#EnergyBar.Value", (energy / 100f).toString())
        
        // Set buffs/debuffs
        val buffsText = if (activeBuffs.isEmpty()) "None" else activeBuffs.joinToString("\n")
        cmd.set("#BuffsList.Text", buffsText)
        
        val debuffsText = if (activeDebuffs.isEmpty()) "None" else activeDebuffs.joinToString("\n")
        cmd.set("#DebuffsList.Text", debuffsText)
    }
}
```

**UI File:** `src/main/resources/Common/UI/Custom/Pages/MetabolismStatsPage.ui`

See the full UI file in the codebase for layout details.

### Example 2: Opening the Page from a Command

**File:** `src/main/kotlin/com/livinglands/modules/metabolism/commands/StatsCommand.kt`

```kotlin
class StatsCommand(
    private val metabolismService: MetabolismService
) : CommandBase("show", "Display your metabolism stats", false) {
    
    override fun executeSync(ctx: CommandContext) {
        val entityRef = ctx.senderAsPlayerRef() ?: return
        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }
        
        if (session != null) {
            val stats = metabolismService.getStats(session.playerId.toString())
            
            if (stats != null) {
                val buffs = metabolismService.getBuffsSystem()?.getActiveBuffNames(session.playerId) ?: emptyList()
                val debuffs = metabolismService.getDebuffsSystem()?.getActiveDebuffNames(session.playerId) ?: emptyList()
                
                session.world.execute {
                    val player = session.store.getComponent(session.entityRef, Player.getComponentType())
                    
                    if (player != null) {
                        @Suppress("DEPRECATION")
                        val playerRef = player.playerRef
                        
                        if (playerRef != null) {
                            val page = MetabolismStatsPage(
                                playerRef,
                                stats.hunger,
                                stats.thirst,
                                stats.energy,
                                buffs,
                                debuffs
                            )
                            player.pageManager.openCustomPage(session.entityRef, session.store, page)
                        }
                    }
                }
            }
        }
    }
}
```

---

## Best Practices

1. **Always close pages programmatically** - Call `player.pageManager.setPage(ref, store, Page.None)` in event handlers
2. **Convert numbers to strings** - All `cmd.set()` values must be strings: `value.toString()`
3. **Use @ prefix for UI inputs** - In `KeyedCodec`, use `"@FieldName"` for values read from UI elements
4. **World thread for ECS access** - Always wrap Player/Entity operations in `world.execute { }`
5. **Handle null PlayerRef** - Always check for null when getting `player.playerRef`
6. **Validate user input** - In `handleDataEvent()`, validate data before processing
7. **Use type-safe data classes** - Define event data with Kotlin data classes for compile-time safety

---

## Resources

- **Reference Repository:** [hytale-basic-uis](https://github.com/trouble-dev/hytale-basic-uis)
- **Living Lands Examples:**
  - `src/main/kotlin/com/livinglands/core/ui/` - Base classes
  - `src/main/kotlin/com/livinglands/modules/metabolism/pages/` - Metabolism stats page
  - `src/main/resources/Common/UI/Custom/Pages/` - UI definition files

---

## Summary

| Pattern | Use Case | Base Class | Event Handling |
|---------|----------|------------|----------------|
| Static Page | Info display, stats | `BasicCustomUIPage` | No |
| Interactive Page | Forms, buttons | `InteractiveCustomUIPage<T>` | Yes |
| Persistent HUD | Always-visible overlay | `CustomUIHud` | No |

**Key Concepts:**
- `.ui` files define layout
- `cmd.append()` loads UI
- `cmd.set()` updates values
- `evt.addEventBinding()` binds buttons
- `@` prefix for UI input fields in Codec
- `world.execute {}` for ECS access
