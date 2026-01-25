# Hytale HUD Components Reference

**Version:** Extracted from Assets.zip and HytaleServer.jar  
**Package:** `com.hypixel.hytale.server.core.ui`  
**Date:** 2026-01-25  
**Assets Analyzed:** 62 UI templates, 100+ textures, 24 HUD components  
**Important:** This document is based on analysis of the official Hytale Assets.zip (3.2 GB) and server JAR inspection.

**Latest Update (2026-01-25):** Complete investigation of Hytale's UI system including all element types, layout modes, styling properties, and real examples from the asset pack. Discovered comprehensive UI DSL capabilities including TextField, Slider, DropdownBox, TimerLabel, CheckBox, ColorPicker, and advanced features like variables, imports, and mask textures.

---

## Table of Contents

1. [Overview](#overview)
2. [UI Element Types](#ui-element-types)
3. [Layout System](#layout-system)
4. [Styling System](#styling-system)
5. [Built-in HUD Components](#built-in-hud-components)
6. [Advanced Features](#advanced-features)
7. [Asset Pack Structure](#asset-pack-structure)
8. [Implementation Guide](#implementation-guide)
9. [Example Templates](#example-templates)
10. [Common Patterns](#common-patterns)
11. [Troubleshooting](#troubleshooting)
12. [Quick Reference](#quick-reference)

---

## Overview

### Hytale's UI System

Hytale uses a **custom UI DSL format** (Domain-Specific Language) for defining user interfaces. This is NOT XML, HTML, or any standard markup language.

**Key Characteristics:**
- Declarative syntax with curly braces `{}`
- Element-based hierarchy (Group, Label, Button, etc.)
- Support for variables, imports, and spread operators
- Integration with texture assets and sound effects
- Two-way data binding for interactive elements

### UI Types

| Type | Base Class | Purpose | Use Case |
|------|-----------|---------|----------|
| **Custom HUD** | `CustomUIHud` | Overlay UI elements | Health bars, timers, notifications |
| **Custom Page** | `CustomUIPage` | Full-screen menus | Settings, inventory, crafting |
| **Interactive Page** | `InteractiveCustomUIPage<T>` | Full-screen with events | Forms, configuration panels |
| **Entity UI** | `EntityUIComponent` | Above-entity displays | Nameplates, health bars, damage numbers |

### HUD vs Page

| Feature | Custom HUD | Custom Page |
|---------|-----------|-------------|
| **Display** | Overlay (non-blocking) | Full-screen (blocks input) |
| **Interaction** | Limited | Full (buttons, inputs, sliders) |
| **Persistence** | Always visible | Shown on demand |
| **Examples** | Stats display, minimap | Settings menu, shop |

---

## UI Element Types

### 2.1 Core Elements

#### Group

**Purpose:** Container element for layout and organization.

**Properties:**
- `LayoutMode` - Layout direction (Top, Left, Center, etc.)
- `Anchor` - Position and size
- `Background` - Background color or image
- `Padding` - Inner spacing
- `Visible` - Show/hide (default: true)
- `FlexWeight` - Flexible sizing in layouts

**Syntax:**
```
Group #MyContainer {
  LayoutMode: Top;
  Anchor: (Top: 20, Left: 20, Width: 200, Height: 80);
  Background: #1a1a1a(0.8);
  Padding: (Full: 10);
}
```

**Common Uses:**
- Layout containers
- Visual panels with backgrounds
- Spacing and alignment
- Nested hierarchies

---

#### Label

**Purpose:** Display static or dynamic text.

**Properties:**
- `Text` - Text content (string or localization key)
- `Style` - Text styling (font, color, alignment)
- `Anchor` - Position and size
- `MaskTexturePath` - Gradient/mask overlay
- `Visible` - Show/hide

**Syntax:**
```
Label #MyText {
  Text: "Hello World";
  Anchor: (Width: 100, Height: 20);
  Style: (
    FontSize: 14,
    TextColor: #ffffff,
    RenderBold: true,
    HorizontalAlignment: Center
  );
}
```

**Text Style Properties:**

| Property | Type | Values | Purpose |
|----------|------|--------|---------|
| `FontSize` | Integer | 8-64 | Text size in pixels |
| `FontName` | String | "Primary", "Secondary" | Font family |
| `TextColor` | Color | #RRGGBB or #RRGGBB(alpha) | Text color |
| `OutlineColor` | Color | #RRGGBB(alpha) | Text outline/shadow |
| `HorizontalAlignment` | Enum | Start, Center, End | Horizontal align |
| `VerticalAlignment` | Enum | Start, Center, End | Vertical align |
| `RenderBold` | Boolean | true, false | Bold text |
| `RenderUppercase` | Boolean | true, false | Uppercase text |
| `LetterSpacing` | Float | 0.0-5.0 | Letter spacing |
| `Wrap` | Boolean | true, false | Text wrapping |

**Localization:**
```
Label #Title {
  Text: %server.customUI.myPlugin.title;
}
```

---

#### Button

**Purpose:** Clickable button element (no text).

**Properties:**
- `Style` - Button states (Default, Hovered, Pressed, Disabled)
- `Anchor` - Position and size
- `Visible` - Show/hide

**Syntax:**
```
Button #MyButton {
  Anchor: (Width: 100, Height: 40);
  Style: (
    Default: (Background: "button_normal.png"),
    Hovered: (Background: "button_hover.png"),
    Pressed: (Background: "button_pressed.png"),
    Disabled: (Background: "button_disabled.png")
  );
}
```

---

#### TextButton

**Purpose:** Button with integrated text label.

**Properties:**
- `Text` - Button label
- `Style` - Button and label states
- `Anchor` - Position and size

**Syntax:**
```
TextButton #Submit {
  Text: "Submit";
  Anchor: (Width: 150, Height: 44);
  Style: (
    Default: (
      Background: PatchStyle(TexturePath: "Common/Buttons/Primary.png", Border: 12),
      LabelStyle: (FontSize: 17, TextColor: #bfcdd5, RenderBold: true)
    ),
    Hovered: (
      Background: PatchStyle(TexturePath: "Common/Buttons/Primary_Hovered.png", Border: 12),
      LabelStyle: (FontSize: 17, TextColor: #ffffff, RenderBold: true)
    ),
    Pressed: (
      Background: PatchStyle(TexturePath: "Common/Buttons/Primary_Pressed.png", Border: 12),
      LabelStyle: (FontSize: 17, TextColor: #ffffff, RenderBold: true)
    )
  );
}
```

**Using Common Styles:**
```
$Common = "../Common.ui";

TextButton #MyButton {
  Text: "Click Me";
  Style: $Common.@DefaultTextButtonStyle;
}
```

---

#### TextField

**Purpose:** Single-line text input field.

**Properties:**
- `Style` - Input field styling
- `PlaceholderStyle` - Placeholder text styling
- `Background` - Input box background
- `Anchor` - Position and size
- `MaxLength` - Maximum character limit

**Syntax:**
```
TextField #NameInput {
  Anchor: (Width: 200, Height: 32);
  Style: (FontSize: 14, TextColor: #ffffff);
  PlaceholderStyle: (FontSize: 14, TextColor: #6e7da1);
  Background: PatchStyle(TexturePath: "Common/InputBox.png", Border: 16);
}
```

**Setting Placeholder:**
```kotlin
builder.set("#NameInput.Placeholder", "Enter your name...")
```

**Reading Input Value:**
```kotlin
// In InteractiveCustomUIPage event handler
fun handleDataEvent(data: MyEventData) {
    val nameValue = data.nameInput
}
```

---

#### Slider

**Purpose:** Numeric slider for value selection.

**Properties:**
- `Min` - Minimum value
- `Max` - Maximum value
- `Step` - Increment step
- `Value` - Initial/current value
- `Style` - Slider styling
- `Anchor` - Position and size

**Syntax:**
```
Slider #VolumeSlider {
  Anchor: (Width: 150, Height: 5);
  Min: 0;
  Max: 100;
  Step: 1;
  Value: 50;
  Style: (
    TrackBackground: "slider_track.png",
    ThumbBackground: "slider_thumb.png"
  );
}
```

**Example from Assets (PlaySoundPage.ui):**
```
Slider #VolumeSlider {
  Anchor: (Left: 25, Width: 150, Height: 5);
  Style: $Common.@DefaultSliderStyle;
  Min: -100;
  Max: 10;
  Step: 1;
  Value: 0;
}

Label #VolumeValue {
  Text: "0";
}
```

**Updating Slider Value:**
```kotlin
builder.set("#VolumeSlider.Value", 75)
builder.set("#VolumeValue.Text", "75")
```

---

#### DropdownBox

**Purpose:** Dropdown selector with options.

**Properties:**
- `Style` - Dropdown styling (states)
- `Anchor` - Position and size
- `Entries` - List of options

**Syntax:**
```
DropdownBox #GameMode {
  Anchor: (Width: 200, Height: 32);
  Style: (
    DefaultBackground: PatchStyle(TexturePath: "Common/Dropdown.png", Border: 16),
    HoveredBackground: PatchStyle(TexturePath: "Common/DropdownHovered.png", Border: 16),
    PressedBackground: PatchStyle(TexturePath: "Common/DropdownPressed.png", Border: 16)
  );
}
```

**Setting Options Dynamically:**
```kotlin
// In CustomUIPage.build()
builder.set("#GameMode.Entries", listOf("Survival", "Creative", "Adventure"))
builder.set("#GameMode.SelectedIndex", 0)
```

---

#### TimerLabel

**Purpose:** Countdown timer display.

**Properties:**
- `Seconds` - Countdown duration
- `Style` - Label styling
- `Anchor` - Position and size

**Syntax:**
```
TimerLabel #CountdownTimer {
  Seconds: 900;  // 15 minutes
  Style: (FontSize: 32, Alignment: Center);
  Anchor: (Width: 120, Height: 40);
}
```

**Example from Assets (TimeLeft.ui):**
```
Group {
  Background: "Clock.png";
  Anchor: (Width: 40, Height: 40);
}

TimerLabel #TimeLabel {
  Style: (FontSize: 32, Alignment: Center);
  Seconds: 15 * 60;
}
```

---

#### CheckBox

**Purpose:** Toggle checkbox element.

**Properties:**
- `Style` - Checkbox states
- `Checked` - Initial state (true/false)
- `Anchor` - Position and size

**Syntax:**
```
CheckBox #EnableFeature {
  Anchor: (Width: 20, Height: 20);
  Checked: false;
  Style: (
    Frame: "Common/CheckBoxFrame.png",
    Checkmark: "Common/Checkmark.png"
  );
}
```

**Toggling State:**
```kotlin
builder.set("#EnableFeature.Checked", true)
```

---

#### ColorPicker

**Purpose:** Color selection widget.

**Properties:**
- `Style` - Color picker styling
- `Anchor` - Position and size
- `Value` - Initial color

**Syntax:**
```
ColorPicker #ThemeColor {
  Anchor: (Width: 200, Height: 150);
  Value: #ff5733;
  Style: (
    Background: "Common/ColorPickerFill.png",
    Button: "Common/ColorPickerButton.png"
  );
}
```

**Reading Selected Color:**
```kotlin
// In event handler
val selectedColor = data.themeColor
```

---

#### Image

**Purpose:** Display static image/icon.

**Properties:**
- `Background` - Image path
- `Anchor` - Position and size
- `Visible` - Show/hide

**Syntax:**
```
Image #Icon {
  Background: "path/to/icon.png";
  Anchor: (Width: 32, Height: 32);
}

// Or using Group
Group #Icon {
  Background: "path/to/icon.png";
  Anchor: (Width: 32, Height: 32);
}
```

---

### 2.2 Specialized Elements

#### ItemSlot

**Purpose:** Display inventory item slot.

**Properties:**
- `Item` - Item data
- `Anchor` - Position and size
- `Background` - Slot background texture

**Syntax:**
```
ItemSlot #Slot1 {
  Anchor: (Width: 48, Height: 48);
  Background: "Common/BlockSelectorSlotBackground.png";
}
```

---

#### ItemGrid

**Purpose:** Grid of item slots.

**Properties:**
- `Rows` - Number of rows
- `Columns` - Number of columns
- `SlotSize` - Size of each slot
- `Anchor` - Position and size

**Used in:** Container windows, inventory UIs

---

#### ScrollingPanel

**Purpose:** Container with scrollbar for overflow content.

**Syntax:**
```
Group #ContentList {
  LayoutMode: TopScrolling;
  ScrollbarStyle: $Common.@DefaultScrollbarStyle;
  Anchor: (Width: 300, Height: 400);
}
```

---

## Layout System

### 3.1 LayoutMode Options

Controls how child elements are arranged within a container.

| LayoutMode | Direction | Behavior | Use Case |
|------------|-----------|----------|----------|
| `Top` | Vertical | Stack top to bottom | Lists, forms |
| `Left` | Horizontal | Stack left to right | Button rows, toolbars |
| `Center` | Horizontal | Center horizontally | Centered content |
| `Middle` | Vertical | Center vertically | Splash screens |
| `TopScrolling` | Vertical | Stack with scrollbar | Long lists |
| `LeftCenterWrap` | Horizontal | Wrap to next row | Tag clouds, grids |

**Example:**
```
Group #VerticalList {
  LayoutMode: Top;
  // Children stack vertically
}

Group #HorizontalRow {
  LayoutMode: Left;
  // Children align horizontally
}

Group #CenteredContent {
  LayoutMode: Center;
  // Children centered horizontally
}
```

---

### 3.2 Anchor Properties

Defines element position and size.

#### Positioning

| Property | Type | Description |
|----------|------|-------------|
| `Top` | Integer | Distance from top edge (pixels) |
| `Left` | Integer | Distance from left edge (pixels) |
| `Right` | Integer | Distance/spacing from right (pixels) |
| `Bottom` | Integer | Distance from bottom edge (pixels) |

**Examples:**
```
// Absolute positioning
Anchor: (Top: 20, Left: 20);

// Bottom-right corner
Anchor: (Bottom: 20, Right: 20);

// Spacing between elements in layout
Anchor: (Right: 10);  // 10px gap to next element
```

#### Sizing

| Property | Type | Description |
|----------|------|-------------|
| `Width` | Integer | Fixed width (pixels) |
| `Height` | Integer | Fixed height (pixels) |
| `MinWidth` | Integer | Minimum width (flexible layouts) |
| `MaxWidth` | Integer | Maximum width (flexible layouts) |

**Examples:**
```
// Fixed size
Anchor: (Width: 200, Height: 80);

// Flexible width with constraints
Anchor: (MinWidth: 100, MaxWidth: 500);

// Auto-sized with height constraint
Anchor: (Height: 40);
```

#### Combined Examples

```
// Positioned panel with fixed size
Group #Panel {
  Anchor: (Top: 50, Left: 100, Width: 300, Height: 200);
}

// Full-width header
Group #Header {
  Anchor: (Top: 0, Left: 0, Right: 0, Height: 60);
}

// Centered with auto-size
Group #Content {
  LayoutMode: Center;
  Anchor: (Width: 400);  // Auto-height based on content
}
```

---

### 3.3 Padding

Defines inner spacing within an element.

| Syntax | Description |
|--------|-------------|
| `(Full: 10)` | 10px on all sides |
| `(Horizontal: 10, Vertical: 5)` | 10px left/right, 5px top/bottom |
| `(Left: 5, Right: 10, Top: 8, Bottom: 12)` | Individual sides |
| `(Top: 10)` | Only top padding |

**Examples:**
```
Group #Panel {
  Padding: (Full: 10);  // 10px all around
}

Group #Button {
  Padding: (Horizontal: 20, Vertical: 10);  // 20px sides, 10px top/bottom
}

Group #List {
  Padding: (Top: 15);  // 15px top spacing only
}
```

---

## Styling System

### 4.1 Background Styles

#### Solid Color

**Format:** `#RRGGBB` or `#RRGGBB(alpha)`

```
Background: #1a1a1a;           // Opaque black-gray
Background: #1a1a1a(0.8);      // 80% opacity
Background: #e67e22(0.5);      // 50% opacity orange
```

**Alpha Values:**
- `0.0` - Fully transparent
- `0.5` - 50% transparent
- `1.0` - Fully opaque

---

#### Image Background

**Simple:**
```
Background: "path/to/image.png";
```

**Object Notation:**
```
Background: (TexturePath: "path/to/image.png");
Background: (Color: #000000(0.5));
```

---

#### 9-Patch Background (PatchStyle)

For scalable panels with borders.

**Uniform Border:**
```
Background: (TexturePath: "Common/ContainerPatch.png", Border: 20);
```

**Separate Borders:**
```
Background: PatchStyle(
  TexturePath: "Common/Buttons/Primary.png",
  VerticalBorder: 12,
  HorizontalBorder: 80
);
```

**9-Patch Slicing:**
```
┌─────────┬─────────────┬─────────┐
│  TL     │     Top     │    TR   │  VerticalBorder
├─────────┼─────────────┼─────────┤
│  Left   │   Center    │  Right  │  (stretches)
│         │  (scales)   │         │
├─────────┼─────────────┼─────────┤
│  BL     │   Bottom    │    BR   │  VerticalBorder
└─────────┴─────────────┴─────────┘
          HorizontalBorder
```

**Example:**
```
Group #Panel {
  Background: PatchStyle(
    TexturePath: "Common/ContainerFullPatch.png",
    Border: 20
  );
}
```

---

### 4.2 Text Styles

#### LabelStyle Properties

```
Style: (
  FontSize: 14,                      // Text size (8-64)
  FontName: "Primary",               // Font family ("Primary", "Secondary")
  TextColor: #ffffff,                // Text color
  OutlineColor: #000000(0.5),        // Text outline/shadow
  HorizontalAlignment: Center,       // Start, Center, End
  VerticalAlignment: Center,         // Start, Center, End
  RenderBold: true,                  // Bold text
  RenderUppercase: true,             // Force uppercase
  LetterSpacing: 1.5,                // Letter spacing
  Wrap: true                         // Enable text wrapping
)
```

#### Alignment Values

| Horizontal | Vertical | Description |
|------------|----------|-------------|
| `Start` | `Start` | Top-left |
| `Center` | `Center` | Centered |
| `End` | `End` | Bottom-right |

**Note:** "Start" and "End" adapt to text direction (LTR/RTL).

---

#### Font Families

| Font | Characteristics | Use Case |
|------|----------------|----------|
| `Primary` | Standard game font | Body text, labels |
| `Secondary` | Stylized/decorative | Titles, headers |

---

#### Mask Textures

Apply gradient overlays to text.

```
Label #FancyTitle {
  Text: "GAME OVER";
  Style: (FontSize: 48, RenderBold: true);
  MaskTexturePath: "RespawnPageLabelGradient.png";
}
```

**Effect:** Text is masked/clipped by the gradient texture.

---

### 4.3 Button States

Buttons have 4 states with different visuals:

| State | Trigger | Purpose |
|-------|---------|---------|
| `Default` | Normal | Idle state |
| `Hovered` | Mouse over | Visual feedback |
| `Pressed` | Click/hold | Active state |
| `Disabled` | Not interactable | Greyed out |

**Example:**
```
ButtonStyle(
  Default: (
    Background: PatchStyle(TexturePath: "Common/Buttons/Primary.png", Border: 12),
    LabelStyle: (FontSize: 17, TextColor: #bfcdd5)
  ),
  Hovered: (
    Background: PatchStyle(TexturePath: "Common/Buttons/Primary_Hovered.png", Border: 12),
    LabelStyle: (FontSize: 17, TextColor: #ffffff)
  ),
  Pressed: (
    Background: PatchStyle(TexturePath: "Common/Buttons/Primary_Pressed.png", Border: 12),
    LabelStyle: (FontSize: 17, TextColor: #ffffff)
  ),
  Disabled: (
    Background: PatchStyle(TexturePath: "Common/Buttons/Disabled.png", Border: 12),
    LabelStyle: (FontSize: 17, TextColor: #797b7c)
  ),
  Sounds: (
    Activate: "UI/Button_Click.ogg",
    Cancel: "UI/Button_Cancel.ogg"
  )
)
```

---

## Built-in HUD Components

Hytale provides 24 built-in HUD components that can be toggled on/off.

**Class:** `com.hypixel.hytale.protocol.packets.interface_.HudComponent`

### 5.1 Component List

#### Player Stats

| Component | Purpose |
|-----------|---------|
| `Health` | Health bar |
| `Mana` | Mana/magic bar |
| `Stamina` | Stamina bar |
| `Oxygen` | Underwater oxygen meter |
| `Sleep` | Sleep/tiredness indicator |

#### Interface Elements

| Component | Purpose |
|-----------|---------|
| `Hotbar` | Item hotbar (bottom of screen) |
| `StatusIcons` | Status effect icons |
| `Reticle` | Crosshair/aiming reticle |
| `Chat` | Chat window |
| `Compass` | Compass/navigation |
| `AmmoIndicator` | Ammunition display |
| `UtilitySlotSelector` | Utility item selector |

#### Information Displays

| Component | Purpose |
|-----------|---------|
| `PlayerList` | Tab player list |
| `KillFeed` | Kill/death messages |
| `Notifications` | Game notifications |
| `Requests` | Player requests/invites |
| `EventTitle` | Event title display |
| `ObjectivePanel` | Quest/objective tracker |
| `PortalPanel` | Portal information |

#### Builder/Debug Tools

| Component | Purpose |
|-----------|---------|
| `BuilderToolsLegend` | Builder tools help |
| `Speedometer` | Speed indicator |
| `BlockVariantSelector` | Block variant picker |
| `BuilderToolsMaterialSlotSelector` | Material selector |

---

### 5.2 Usage Examples

#### Show/Hide Components

```kotlin
import com.hypixel.hytale.protocol.packets.interface_.HudComponent
import com.hypixel.hytale.server.core.entity.entities.Player

// Show specific components
player.hudManager.showHudComponents(
    playerRef,
    HudComponent.Health,
    HudComponent.Mana,
    HudComponent.Stamina
)

// Hide components
player.hudManager.hideHudComponents(
    playerRef,
    HudComponent.Hotbar,
    HudComponent.StatusIcons
)

// Set exact visible components (replaces all)
player.hudManager.setVisibleHudComponents(
    playerRef,
    setOf(
        HudComponent.Chat,
        HudComponent.Reticle,
        HudComponent.Health
    )
)
```

#### Get Visible Components

```kotlin
val visibleComponents = player.hudManager.getVisibleHudComponents()
if (HudComponent.Health in visibleComponents) {
    // Health bar is visible
}
```

#### Reset to Default

```kotlin
// Reset HUD to default visible components
player.hudManager.resetHud(playerRef)

// Reset entire UI (HUD + pages)
player.hudManager.resetUserInterface(playerRef)
```

---

### 5.3 Default Visible Components

The following components are visible by default:
- Chat
- Reticle
- Hotbar
- Health
- Stamina
- Oxygen (when underwater)

All others are hidden unless explicitly shown.

---

## Advanced Features

### 6.1 Variables & Imports

#### Importing Files

```
$Common = "../Common.ui";
$Sounds = "../Sounds.ui";
```

Use imported definitions:
```
TextButton #MyButton {
  Style: $Common.@DefaultTextButtonStyle;
  Sounds: $Sounds.@ButtonsLight;
}
```

---

#### Defining Variables

```
@MyColor = #ff5733;
@ButtonHeight = 44;
@TitleStyle = LabelStyle(FontSize: 32, RenderBold: true);
```

Use variables:
```
Label #Title {
  Style: @TitleStyle;
  TextColor: @MyColor;
}

Group #Button {
  Anchor: (Height: @ButtonHeight);
}
```

---

### 6.2 Spread Operator

Extend existing styles with overrides.

```
@BaseStyle = LabelStyle(
  FontSize: 14,
  TextColor: #ffffff
);

@TitleStyle = LabelStyle(
  ...@BaseStyle,
  FontSize: 24,      // Override
  RenderBold: true   // Add new property
);
```

**Result:** TitleStyle has FontSize: 24, TextColor: #ffffff, RenderBold: true

---

### 6.3 Sound Integration

#### Button Sounds

```
TextButton #Play {
  Style: (
    Sounds: (
      Activate: "UI/Crafting/Workbench/Workbench_Craft_01.ogg",
      Cancel: "UI/Crafting/Workbench/Workbench_Close_01.ogg"
    )
  );
}
```

#### Using Sound Presets

```
$Sounds = "../Sounds.ui";

TextButton #Submit {
  Style: (
    Sounds: (
      ...$Sounds.@ButtonsLight,
      Activate: "Custom/MyActivateSound.ogg"  // Override
    )
  );
}
```

#### Available Sound Categories

From `Common/Sounds/UI/`:
- Crafting sounds (alchemy, armor, furnace, etc.)
- Discovery sounds (zone discoveries)
- Durability sounds (item break/repair)
- Inventory sounds (drag/drop items)

---

### 6.4 Mask Textures

Apply gradient/alpha overlays to labels.

```
Label #GradientTitle {
  Text: "RESPAWN";
  Style: (
    FontSize: 38,
    RenderBold: true,
    HorizontalAlignment: Center
  );
  MaskTexturePath: "RespawnPageLabelGradient.png";
}
```

**Effect:** Text is visible only where the mask texture has alpha > 0. Creates gradient fade effects.

---

### 6.5 Event Binding

For `InteractiveCustomUIPage`, bind UI events to handlers.

**Kotlin (InteractiveCustomUIPage):**
```kotlin
class MyPage(playerRef: PlayerRef) : 
    InteractiveCustomUIPage<MyEventData>(
        playerRef,
        CustomPageLifetime.UntilDismissed,
        MyEventData.CODEC
    ) {
    
    override fun build(
        ref: Ref<EntityStore>,
        uiBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        uiBuilder.append("Pages/MyPage.ui")
        
        // Bind button click event
        eventBuilder.bind("#SubmitButton", "onClick", MyEventData.CODEC)
        
        uiBuilder.set("#InputField.Text", "Default value")
    }
    
    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: MyEventData
    ) {
        // Handle event
        val userInput = data.inputValue
        // Process...
    }
}
```

**Event Data Class:**
```kotlin
data class MyEventData(
    val inputValue: String,
    val sliderValue: Int
) {
    companion object {
        val CODEC: BuilderCodec<MyEventData> = ...
    }
}
```

---

## Asset Pack Structure

### 7.1 Directory Organization

```
Common/
├── UI/
│   ├── Custom/
│   │   ├── Common.ui              # Shared definitions
│   │   ├── Sounds.ui              # Sound presets
│   │   ├── Common/                # Common components
│   │   │   ├── ActionButton.ui
│   │   │   ├── TextButton.ui
│   │   │   └── Buttons/           # Button textures
│   │   │       ├── Primary.png
│   │   │       ├── Primary_Hovered.png
│   │   │       ├── Primary_Pressed.png
│   │   │       └── Disabled.png
│   │   ├── Hud/                   # HUD elements
│   │   │   └── TimeLeft.ui
│   │   └── Pages/                 # Full-screen pages
│   │       ├── RespawnPage.ui
│   │       ├── BarterPage.ui
│   │       └── ...
│   └── Crosshairs/                # Reticle textures
│       ├── Default.png
│       └── ...
└── Sounds/
    └── UI/
        ├── Crafting/
        ├── Discovery/
        ├── Durability/
        └── Inventory/
```

---

### 7.2 File Naming Conventions

#### UI Templates (.ui files)

| Pattern | Example | Purpose |
|---------|---------|---------|
| `[Name].ui` | `RespawnPage.ui` | Top-level page |
| `[Name]Button.ui` | `TextButton.ui` | Reusable component |
| `[Name]Element.ui` | `ItemRepairElement.ui` | Sub-component |

#### Textures

| Pattern | Example | Purpose |
|---------|---------|---------|
| `[Name].png` | `ContainerPatch.png` | Base texture |
| `[Name]@2x.png` | `BackButton@2x.png` | High-res version |
| `[Name]_[State].png` | `Primary_Hovered.png` | Button state |

---

### 7.3 Resource Organization

**Plugin Resources:**
```
src/main/resources/
├── Common/
│   └── UI/
│       └── Custom/
│           ├── Hud/
│           │   └── MyHud.ui
│           └── Pages/
│               └── MyPage.ui
├── Assets/                        # Custom textures/sounds (optional)
└── manifest.json                  # MUST include IncludesAssetPack: true
```

**Manifest Requirement:**
```json
{
  "IncludesAssetPack": true
}
```

---

## Implementation Guide

### 8.1 Critical Requirements

#### Manifest Configuration

**REQUIRED** in `src/main/resources/manifest.json`:

```json
{
  "IncludesAssetPack": true
}
```

Without this, UI files won't be loaded by the client.

---

#### File Placement

**Correct:**
```
src/main/resources/Common/UI/Custom/Hud/MyHud.ui
```

**Incorrect:**
```
src/main/resources/Hud/MyHud.ui          ❌ Missing Common/UI/Custom/
src/main/resources/ui/MyHud.ui           ❌ Wrong path
resources/Common/UI/Custom/Hud/MyHud.ui  ❌ Not in src/main/
```

---

#### File References

In Kotlin code:
```kotlin
builder.append("Hud/MyHud.ui")  // Relative to Common/UI/Custom/
```

**Full path resolution:**
- Code: `"Hud/MyHud.ui"`
- Actual: `Common/UI/Custom/Hud/MyHud.ui`

---

#### Element Naming Rules

**DO:**
- Use camelCase: `#HungerBar` ✅
- Keep simple: `#SubmitButton` ✅
- Use descriptive names: `#PlayerHealthLabel` ✅

**DON'T:**
- Use underscores: `#hunger_bar` ❌ (causes parse errors)
- Use hyphens: `#submit-button` ❌
- Use special chars: `#button$1` ❌
- Add ID to root Group: `Group #MyHud { }` ❌

**Root Group Rule:**
```
// CORRECT - No ID on root
Group {
  LayoutMode: Top;
  
  Group #Content {  // Child groups can have IDs
    ...
  }
}

// WRONG - ID on root
Group #MyHud {      ❌
  ...
}
```

---

### 8.2 Build Patterns

#### CustomUIHud Implementation

```kotlin
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef

class MyHud(playerRef: PlayerRef) : CustomUIHud(playerRef) {
    
    private var hunger: Float = 100f
    private var thirst: Float = 100f
    
    override fun build(builder: UICommandBuilder) {
        // ALWAYS append UI file every time build() is called
        builder.append("Hud/MyHud.ui")
        
        // Set dynamic values
        builder.set("#HungerBar.Text", buildBar(hunger))
        builder.set("#ThirstBar.Text", buildBar(thirst))
    }
    
    fun updateStats(newHunger: Float, newThirst: Float) {
        hunger = newHunger
        thirst = newThirst
        
        // Trigger update
        val builder = UICommandBuilder()
        builder.set("#HungerBar.Text", buildBar(hunger))
        builder.set("#ThirstBar.Text", buildBar(thirst))
        update(false, builder)
    }
    
    private fun buildBar(value: Float): String {
        val barLength = 10
        val filled = (value / 100f * barLength).toInt()
        val empty = barLength - filled
        return "[" + "|".repeat(filled) + "-".repeat(empty) + "] ${value.toInt()}"
    }
}
```

**Key Points:**
1. Call `builder.append()` **every time** build() is called (not just first time)
2. Set values in the **same** build() call after append
3. Use `update(false, builder)` to push changes to client

---

#### HUD Registration (Thread Safety)

**CORRECT - On World Thread:**
```kotlin
eventRegistry.register(PlayerReadyEvent::class.java) { event ->
    val player = event.player
    val playerRef = player.playerRef ?: return@register
    val world = player.world ?: return@register
    
    // Execute on world thread
    world.execute {
        try {
            val hud = MyHud(playerRef)
            player.hudManager.setCustomHud(playerRef, hud)
            hud.show()
            
            logger.atInfo().log("Registered HUD for player")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to register HUD")
        }
    }
}
```

**WRONG - Not on World Thread:**
```kotlin
eventRegistry.register(PlayerReadyEvent::class.java) { event ->
    val hud = MyHud(event.playerRef)
    player.hudManager.setCustomHud(event.playerRef, hud)  // ❌ Wrong thread!
    hud.show()
}
```

---

#### Update Patterns

**Periodic Updates (from Ticker):**
```kotlin
class MyTickSystem : EntityTickingSystem<EntityStore>() {
    override fun tick(...) {
        val playerIdStr = playerId.toCachedString()
        
        // Get HUD element
        val hudElement = myService.getHudElement(playerIdStr)
        
        // Update values
        hudElement?.updateStats(newHunger, newThirst)
        
        // Push to client
        hudElement?.show()
    }
}
```

**Immediate Update (from Command):**
```kotlin
class SetStatsCommand : CommandBase("setstats", "Set metabolism stats", false) {
    override fun executeSync(ctx: CommandContext) {
        val player = ctx.player
        val playerId = player.uuid
        
        // Update service state
        metabolismService.setHunger(playerId.toString(), 100f)
        
        // Force HUD update
        metabolismService.forceUpdateHud(playerId.toString())
    }
}
```

---

### 8.3 Common Errors

#### "Could not find document Hud/MyHud.ui"

**Cause:** manifest.json missing `"IncludesAssetPack": true`

**Solution:**
```json
{
  "IncludesAssetPack": true
}
```

---

#### "Failed to parse file" (underscore error)

**Cause:** Element ID contains underscore

**Error Line:**
```
Label #hunger_bar {  ❌
```

**Solution:**
```
Label #HungerBar {  ✅
```

---

#### "Selected element not found"

**Cause:** Wrong selector format or missing element

**Check:**
1. Element exists in .ui file: `Label #MyLabel { }`
2. Using correct selector: `builder.set("#MyLabel.Text", "value")`
3. Correct property: `.Text` not `.text`

---

#### HUD Shows But Never Updates

**Cause:** Not calling `update()` or `show()` after changes

**Solution:**
```kotlin
fun updateHud() {
    val builder = UICommandBuilder()
    builder.set("#HungerBar.Text", buildBar(hunger))
    update(false, builder)  // ✅ Push update
    // OR
    show()  // ✅ Re-build and show
}
```

---

#### Client Crash on Join

**Cause:** HUD setup not on world thread

**Solution:** Wrap in `world.execute { }`

---

## Example Templates

### 9.1 Simple HUD (TimeLeft.ui)

**From Assets.zip:**

```
Group {
  LayoutMode: Left;
  Anchor: (Top: 20, Height: 60);

  Group #TimeLeft {
    Background: #000000(0.2);
    Anchor: (Left: 20);
    Padding: (Horizontal: 20, Vertical: 10);
    LayoutMode: Left;

    Group {
      Background: "Clock.png";
      Anchor: (Width: 40, Height: 40, Right: 10);
    }

    TimerLabel #TimeLabel {
      Style: (FontSize: 32, Alignment: Center);
      Seconds: 15 * 60;
    }
  }
}
```

**Features:**
- Icon + timer label layout
- Semi-transparent background
- Horizontal layout (icon on left, timer on right)

---

### 9.2 Interactive Slider Page (PlaySoundPage.ui)

**From Assets.zip:**

```
$C = "../Common.ui";

$C.@Container {
  Anchor: (Left: 50, Top: 170, Width: 360, Bottom: 120);

  #Content {
    Group {
      LayoutMode: Left;
      Padding: (Horizontal: 15);

      Label {
        Anchor: (Width: 80);
        Text: "Volume (dB)";
        Style: (...$C.@DefaultLabelStyle, VerticalAlignment: Center);
      }

      Slider #VolumeSlider {
        Anchor: (Left: 25, Width: 150, Height: 5);
        Style: $C.@DefaultSliderStyle;
        Min: -100;
        Max: 10;
        Step: 1;
        Value: 0;
      }

      Label #VolumeValue {
        Anchor: (Left: 10, Width: 50);
        Text: "0";
        Style: (HorizontalAlignment: Center, VerticalAlignment: Center);
      }
    }
  }
}
```

**Features:**
- Import common styles
- Slider with min/max/step
- Label shows current value
- Horizontal layout (label + slider + value)

---

### 9.3 Complex Form (RespawnPage.ui)

**From Assets.zip:**

```
$Common = "../Common.ui";
$Sounds = "../Sounds.ui";

Group {
  Background: "RespawnPageBackground.png";

  Group {
    LayoutMode: Middle;

    Group {
      Anchor: (Width: 850, Height: 215, Top: -22);
      Background: "RespawnPageBackgroundInner.png";

      Group {
        LayoutMode: Center;

        Label #Title {
          Anchor: (Height: 48, Left: 36, Top: 45);
          Text: %server.customUI.respawnPage.title;
          Style: (
            FontSize: 38,
            LetterSpacing: 1.8,
            FontName: "Secondary",
            RenderBold: true,
            HorizontalAlignment: Center,
            RenderUppercase: true
          );
          MaskTexturePath: "RespawnPageLabelGradient.png";
        }
      }

      Group {
        LayoutMode: Center;

        Label #DeathReason {
          Anchor: (Height: 32, Left: 36, Top: 98);
          Style: (
            FontSize: 20,
            HorizontalAlignment: Center
          );
        }
      }

      Group {
        LayoutMode: Center;

        TextButton #RespawnButton {
          Anchor: (Width: 220, Height: 40, Left: 36, Bottom: 40);
          Style: (
            ...$Common.@DefaultTextButtonStyle,
            Sounds: (
              ...$Common.@ButtonSounds,
              ...$Sounds.@RespawnActivate
            )
          );
          Text: %server.customUI.respawnPage.respawn;
        }
      }
    }
  }
}
```

**Features:**
- Full-screen background
- Centered layout with Middle
- Gradient mask on title
- Localization keys (%server.customUI...)
- Spread operator for styles
- Custom sound on button

---

### 9.4 Living Lands Metabolism HUD

**Current Implementation:**

**File:** `src/main/resources/Common/UI/Custom/Hud/MetabolismHud.ui`

```
// Living Lands - Metabolism HUD
Group {
  LayoutMode: Top;
  Anchor: (Top: 20, Left: 20, Width: 220, Height: 80);

  Group #MetabolismBars {
    Background: #1a1a1a(0.8);
    Padding: (Full: 10);
    LayoutMode: Top;

    // Hunger row
    Group #HungerRow {
      LayoutMode: Left;

      Label #HungerLabel {
        Text: "Hunger";
        Style: (FontSize: 14, TextColor: #e67e22, RenderBold: true);
        Anchor: (Width: 60);
      }

      Label #HungerBar {
        Text: "[||||||||||] 100";
        Style: (FontSize: 14, TextColor: #e67e22);
      }
    }

    // Thirst row
    Group #ThirstRow {
      LayoutMode: Left;

      Label #ThirstLabel {
        Text: "Thirst";
        Style: (FontSize: 14, TextColor: #5dade2, RenderBold: true);
        Anchor: (Width: 60);
      }

      Label #ThirstBar {
        Text: "[||||||||||] 100";
        Style: (FontSize: 14, TextColor: #5dade2);
      }
    }

    // Energy row
    Group #EnergyRow {
      LayoutMode: Left;

      Label #EnergyLabel {
        Text: "Energy";
        Style: (FontSize: 14, TextColor: #f4d03f, RenderBold: true);
        Anchor: (Width: 60);
      }

      Label #EnergyBar {
        Text: "[||||||||||] 100";
        Style: (FontSize: 14, TextColor: #f4d03f);
      }
    }
  }
}
```

**Kotlin Implementation:**

**File:** `src/main/kotlin/.../MetabolismHudElement.kt`

```kotlin
class MetabolismHudElement(playerRef: PlayerRef) : CustomUIHud(playerRef) {
    
    private var hunger: Float = 100f
    private var thirst: Float = 100f
    private var energy: Float = 100f
    
    override fun build(builder: UICommandBuilder) {
        builder.append("Hud/MetabolismHud.ui")
        
        // Set initial values
        builder.set("#HungerBar.Text", buildTextBar(hunger))
        builder.set("#ThirstBar.Text", buildTextBar(thirst))
        builder.set("#EnergyBar.Text", buildTextBar(energy))
    }
    
    fun updateStats(newHunger: Float, newThirst: Float, newEnergy: Float) {
        hunger = newHunger
        thirst = newThirst
        energy = newEnergy
    }
    
    fun updateHud() {
        val builder = UICommandBuilder()
        builder.set("#HungerBar.Text", buildTextBar(hunger))
        builder.set("#ThirstBar.Text", buildTextBar(thirst))
        builder.set("#EnergyBar.Text", buildTextBar(energy))
        update(false, builder)
    }
    
    private fun buildTextBar(value: Float): String {
        val barLength = 10
        val filled = (value / 100f * barLength).toInt().coerceIn(0, barLength)
        val empty = barLength - filled
        return "[" + "|".repeat(filled) + "-".repeat(empty) + "] ${value.toInt()}"
    }
}
```

**Features:**
- Three stats in vertical layout
- Text-based progress bars
- Color-coded labels
- Semi-transparent background panel
- Dynamic updates via updateHud()

---

## Common Patterns

### 10.1 Text-Based Progress Bars

Since native progress bar widgets are unconfirmed, use text-based bars.

#### Simple Bar

```kotlin
fun buildBar(value: Float, length: Int = 10): String {
    val filled = (value / 100f * length).toInt().coerceIn(0, length)
    val empty = length - filled
    return "[" + "|".repeat(filled) + "-".repeat(empty) + "]"
}

// Usage
builder.set("#HealthBar.Text", buildBar(75f))  // [|||||||---]
```

#### Bar with Percentage

```kotlin
fun buildBarWithPercent(value: Float): String {
    val bar = buildBar(value)
    return "$bar ${value.toInt()}%"
}

// Usage
builder.set("#HealthBar.Text", buildBarWithPercent(75f))  // [|||||||---] 75%
```

#### Styled Bar (Unicode)

```kotlin
fun buildStyledBar(value: Float): String {
    val filled = (value / 100f * 10).toInt().coerceIn(0, 10)
    val empty = 10 - filled
    return "█".repeat(filled) + "░".repeat(empty)
}

// Usage
builder.set("#HealthBar.Text", buildStyledBar(75f))  // ███████░░░
```

---

### 10.2 Dynamic Color Updates

Change element colors based on value thresholds.

#### Color-Coded Stats

```kotlin
fun getStatColor(value: Float): String {
    return when {
        value >= 70f -> "#2ecc71"  // Green - good
        value >= 30f -> "#f39c12"  // Orange - warning
        else -> "#e74c3c"          // Red - critical
    }
}

fun updateHealthBar(value: Float) {
    val builder = UICommandBuilder()
    val color = getStatColor(value)
    
    builder.set("#HealthBar.Text", buildBar(value))
    builder.set("#HealthBar.Style.TextColor", color)
    update(false, builder)
}
```

#### Background Color Alerts

```kotlin
fun updateWithWarning(value: Float) {
    val builder = UICommandBuilder()
    
    if (value < 20f) {
        // Flash red background on critical
        builder.set("#Panel.Background", "#ff0000(0.3)")
    } else {
        builder.set("#Panel.Background", "#1a1a1a(0.8)")
    }
    
    update(false, builder)
}
```

---

### 10.3 Multi-Row Layouts

Organize multiple stats in rows.

#### Vertical Stack (Top)

```
Group {
  LayoutMode: Top;
  
  Group #Row1 { LayoutMode: Left; }
  Group #Row2 { LayoutMode: Left; }
  Group #Row3 { LayoutMode: Left; }
}
```

#### Horizontal Grid (LeftCenterWrap)

```
Group {
  LayoutMode: LeftCenterWrap;
  Anchor: (Width: 400);
  
  // Children wrap to next row when width exceeded
  Group #Item1 { Anchor: (Width: 120); }
  Group #Item2 { Anchor: (Width: 120); }
  Group #Item3 { Anchor: (Width: 120); }
  Group #Item4 { Anchor: (Width: 120); }  // Wraps to row 2
}
```

---

### 10.4 Icon + Label Combinations

Pair icons with text labels.

#### Icon Before Label

```
Group #StatRow {
  LayoutMode: Left;
  
  Group #Icon {
    Background: "heart_icon.png";
    Anchor: (Width: 24, Height: 24, Right: 8);
  }
  
  Label #Value {
    Text: "100";
    Style: (FontSize: 14, VerticalAlignment: Center);
  }
}
```

#### Label Above Icon

```
Group #Stat {
  LayoutMode: Top;
  
  Label #Name {
    Text: "Health";
    Style: (FontSize: 12, HorizontalAlignment: Center);
  }
  
  Group #Icon {
    Background: "heart.png";
    Anchor: (Width: 32, Height: 32, Top: 5);
  }
}
```

---

## Troubleshooting

### 11.1 File Not Found Errors

#### Error: "Could not find document Hud/MyHud.ui"

**Possible Causes:**

1. **Missing manifest flag**
   ```json
   // src/main/resources/manifest.json
   {
     "IncludesAssetPack": true  // ✅ REQUIRED
   }
   ```

2. **Wrong file path**
   ```
   ✅ src/main/resources/Common/UI/Custom/Hud/MyHud.ui
   ❌ src/main/resources/Hud/MyHud.ui
   ```

3. **Wrong reference in code**
   ```kotlin
   ✅ builder.append("Hud/MyHud.ui")
   ❌ builder.append("Common/UI/Custom/Hud/MyHud.ui")
   ```

4. **File not in JAR**
   - Check: `jar -tf build/libs/yourplugin.jar | grep .ui`
   - Rebuild: `./gradlew clean build`

---

### 11.2 Parse Errors

#### Error: "Failed to parse file" at line with underscore

**Cause:** Element IDs cannot contain underscores.

**Fix:**
```
❌ Label #hunger_bar { }
✅ Label #HungerBar { }
```

**Pattern:** Use camelCase for all element IDs.

---

#### Error: Unexpected token at "..."

**Common Causes:**

1. **Missing semicolons**
   ```
   ❌ Text: "Hello"
   ✅ Text: "Hello";
   ```

2. **Missing commas in Style**
   ```
   ❌ Style: (FontSize: 14 TextColor: #fff)
   ✅ Style: (FontSize: 14, TextColor: #fff)
   ```

3. **Unmatched braces**
   ```
   ❌ Group { LayoutMode: Top;
   ✅ Group { LayoutMode: Top; }
   ```

---

### 11.3 Element Not Found Errors

#### Error: "Selected element not found: #MyElement"

**Possible Causes:**

1. **Element doesn't exist in .ui file**
   - Check spelling: `#HungerBar` vs `#hungerBar`
   - Verify element is defined

2. **Wrong property name**
   ```kotlin
   ❌ builder.set("#MyLabel.text", "value")  // lowercase
   ✅ builder.set("#MyLabel.Text", "value")  // capitalized
   ```

3. **Missing # prefix**
   ```kotlin
   ❌ builder.set("MyLabel.Text", "value")
   ✅ builder.set("#MyLabel.Text", "value")
   ```

4. **Element inside conditional/hidden parent**
   - Element might not be rendered yet
   - Check parent's `Visible` property

---

### 11.4 Update Issues

#### HUD Shows But Never Updates

**Causes:**

1. **Not calling update()**
   ```kotlin
   ❌ hunger = 50f  // State changed but UI not notified
   
   ✅ hunger = 50f
      val builder = UICommandBuilder()
      builder.set("#HungerBar.Text", buildBar(hunger))
      update(false, builder)
   ```

2. **Updating too early**
   ```kotlin
   ❌ hud.show()
      hud.updateHud()  // Too soon, UI not loaded yet
   
   ✅ hud.show()
      // Wait for tick system to call updateHud()
   ```

3. **Wrong element reference**
   - Verify element ID matches .ui file

---

#### HUD Flickers or Shows Wrong Values

**Cause:** Race condition or update spam.

**Fix:** Implement threshold-based updates.
```kotlin
private var lastDisplayedHunger = 100f

fun updateHudIfNeeded() {
    if (abs(hunger - lastDisplayedHunger) > 0.5f) {
        val builder = UICommandBuilder()
        builder.set("#HungerBar.Text", buildBar(hunger))
        update(false, builder)
        lastDisplayedHunger = hunger
    }
}
```

---

### 11.5 Thread Safety Issues

#### Error: ConcurrentModificationException or Client Crash

**Cause:** HUD operations not on world thread.

**Fix:** Always use `world.execute { }`
```kotlin
❌ player.hudManager.setCustomHud(playerRef, hud)

✅ world.execute {
    player.hudManager.setCustomHud(playerRef, hud)
    hud.show()
}
```

---

### 11.6 Visual Issues

#### Background Not Showing

**Checks:**

1. **Texture exists**
   ```
   Background: "MyTexture.png";  // Must exist in resources
   ```

2. **Alpha not zero**
   ```
   ❌ Background: #000000(0.0);  // Fully transparent
   ✅ Background: #000000(0.8);  // Visible
   ```

3. **Size constraints**
   ```
   Group {
     Background: "panel.png";
     Anchor: (Width: 200, Height: 100);  // ✅ Has size
   }
   ```

---

#### Text Not Visible

**Checks:**

1. **Text color vs background**
   ```
   // White text on white background = invisible
   Background: #ffffff;
   Style: (TextColor: #ffffff);  // ❌
   
   // Fix with contrast
   Background: #1a1a1a;
   Style: (TextColor: #ffffff);  // ✅
   ```

2. **Font size too small**
   ```
   ❌ Style: (FontSize: 2);
   ✅ Style: (FontSize: 14);
   ```

3. **Element size too small**
   ```
   Label {
     Text: "Long text that won't fit";
     Anchor: (Width: 10);  // ❌ Too narrow
   }
   ```

---

## Quick Reference

### 12.1 Element Syntax Cheat Sheet

```
// Container
Group #ID {
  LayoutMode: Top | Left | Center | Middle | TopScrolling | LeftCenterWrap;
  Anchor: (Top: 0, Left: 0, Width: 100, Height: 50);
  Background: #RRGGBB(alpha) | "path.png" | PatchStyle(...);
  Padding: (Full: 10) | (Horizontal: 10, Vertical: 5);
  Visible: true | false;
}

// Text
Label #ID {
  Text: "string" | %localization.key;
  Style: (FontSize: 14, TextColor: #RRGGBB, RenderBold: true, ...);
  Anchor: (Width: 100, Height: 20);
  MaskTexturePath: "gradient.png";
}

// Button
TextButton #ID {
  Text: "string";
  Style: (Default: (...), Hovered: (...), Pressed: (...), Disabled: (...));
  Anchor: (Width: 150, Height: 40);
}

// Input
TextField #ID {
  Style: (FontSize: 14, TextColor: #RRGGBB);
  PlaceholderStyle: (FontSize: 14, TextColor: #RRGGBB);
  Background: PatchStyle(TexturePath: "input.png", Border: 16);
}

// Slider
Slider #ID {
  Min: 0;
  Max: 100;
  Step: 1;
  Value: 50;
  Style: (...);
}

// Timer
TimerLabel #ID {
  Seconds: 900;
  Style: (FontSize: 32);
}
```

---

### 12.2 Property Quick Reference

#### Anchor
| Property | Type | Description |
|----------|------|-------------|
| Top | Integer | Distance from top (px) |
| Left | Integer | Distance from left (px) |
| Right | Integer | Distance from right (px) |
| Bottom | Integer | Distance from bottom (px) |
| Width | Integer | Fixed width (px) |
| Height | Integer | Fixed height (px) |
| MinWidth | Integer | Minimum width (px) |
| MaxWidth | Integer | Maximum width (px) |

#### Padding
| Syntax | Example | Result |
|--------|---------|--------|
| Full | `(Full: 10)` | 10px all sides |
| Directional | `(Horizontal: 10, Vertical: 5)` | 10px left/right, 5px top/bottom |
| Individual | `(Left: 5, Right: 10, Top: 8, Bottom: 12)` | Custom per side |

#### Text Style
| Property | Values |
|----------|--------|
| FontSize | 8-64 (integer) |
| FontName | "Primary", "Secondary" |
| TextColor | #RRGGBB or #RRGGBB(alpha) |
| OutlineColor | #RRGGBB(alpha) |
| HorizontalAlignment | Start, Center, End |
| VerticalAlignment | Start, Center, End |
| RenderBold | true, false |
| RenderUppercase | true, false |
| LetterSpacing | 0.0-5.0 (float) |
| Wrap | true, false |

---

### 12.3 Color Format Guide

#### Hex Colors
```
#RRGGBB             // Opaque color
#ff5733             // Orange
#2ecc71             // Green
#e74c3c             // Red

#RRGGBB(alpha)      // With transparency
#1a1a1a(0.8)        // 80% opaque dark gray
#ffffff(0.5)        // 50% opaque white
#000000(0.0)        // Fully transparent black
```

#### Alpha Values
- `1.0` - Fully opaque (100%)
- `0.8` - 80% opaque
- `0.5` - 50% opaque (half transparent)
- `0.2` - 20% opaque (mostly transparent)
- `0.0` - Fully transparent (invisible)

#### Common Colors
| Color | Hex | Use |
|-------|-----|-----|
| White | #ffffff | Text, highlights |
| Black | #000000 | Backgrounds, shadows |
| Gray | #808080 | Disabled states |
| Red | #e74c3c | Health, errors, critical |
| Orange | #e67e22 | Hunger, warnings |
| Yellow | #f4d03f | Energy, gold |
| Green | #2ecc71 | Success, high values |
| Blue | #5dade2 | Thirst, mana, info |

---

### 12.4 Kotlin Integration Quick Reference

#### UICommandBuilder Methods

```kotlin
// Append UI file (relative to Common/UI/Custom/)
builder.append("Hud/MyHud.ui")

// Set element properties
builder.set("#ElementId.Text", "value")
builder.set("#ElementId.Visible", false)
builder.set("#ElementId.Style.TextColor", "#ff0000")
builder.set("#ElementId.Background", "#1a1a1a(0.8)")

// Clear/remove elements
builder.clear("#ElementId")
builder.remove("#ElementId")

// Insert elements (advanced)
builder.insertBefore("#TargetId", "#NewElementId")
```

#### CustomUIHud Pattern

```kotlin
class MyHud(playerRef: PlayerRef) : CustomUIHud(playerRef) {
    override fun build(builder: UICommandBuilder) {
        builder.append("Hud/MyHud.ui")
        builder.set("#Element.Text", "value")
    }
    
    fun updateHud() {
        val builder = UICommandBuilder()
        builder.set("#Element.Text", "new value")
        update(false, builder)
    }
}
```

#### HUD Registration

```kotlin
world.execute {
    val hud = MyHud(playerRef)
    player.hudManager.setCustomHud(playerRef, hud)
    hud.show()
}
```

#### Built-in Component Toggle

```kotlin
import com.hypixel.hytale.protocol.packets.interface_.HudComponent

// Show
player.hudManager.showHudComponents(playerRef, HudComponent.Health)

// Hide
player.hudManager.hideHudComponents(playerRef, HudComponent.Hotbar)

// Set exact visible
player.hudManager.setVisibleHudComponents(playerRef, setOf(
    HudComponent.Chat,
    HudComponent.Reticle
))
```

---

## See Also

- **[HYTALE_API_REFERENCE.md](HYTALE_API_REFERENCE.md)** - Complete Hytale API documentation
- **[TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md)** - Living Lands architecture
- **[IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)** - Development roadmap
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - General troubleshooting guide

---

**Document Version:** 1.0.0  
**Last Updated:** 2026-01-25  
**Contributors:** Investigation of Assets.zip (3.2 GB) and HytaleServer.jar

---

## Appendix: Enhancement Ideas for Living Lands

Based on available UI elements, here are enhancement possibilities for Living Lands modules:

### Metabolism Module

**Current:** Text-based HUD with hunger/thirst/energy bars

**Enhancements:**
1. **Icons** - Add hunger/thirst/energy icons before bars
2. **Dynamic colors** - Change text color based on stat level (green/yellow/red)
3. **Warning indicators** - Pulsing red background when critical (<20%)
4. **Stats page** - Full-screen `CustomUIPage` with detailed breakdown
5. **Admin panel** - `InteractiveCustomUIPage` with sliders to adjust stats

### Leveling Module (Planned)

**Potential UI:**
1. **XP Bar HUD** - Progress bar showing current level progress
2. **Level-up notification** - Temporary overlay with level-up message
3. **Skills page** - Full-screen menu showing all professions
4. **Skill tree** - Interactive page with unlock buttons

### Claims Module (Planned)

**Potential UI:**
1. **Claim indicator HUD** - Show current claim owner when entering
2. **Claim management page** - List of owned claims with controls
3. **Permission editor** - Interactive form for claim permissions
4. **Map view** - Visual representation of claimed areas

### Multi-Module Integration

**Potential UI:**
1. **Dashboard HUD** - Combined view of all modules (metabolism + XP + claims)
2. **Settings page** - Master settings menu for all modules
3. **Help/Tutorial pages** - Interactive guides for each module
4. **Leaderboards** - Rankings for metabolism survival time, levels, etc.

---

**End of Document**
