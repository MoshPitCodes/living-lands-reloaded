# NoesisGUI Progress Bar Implementation Guide

## Overview

Hytale uses **NoesisGUI** (a XAML-based UI framework) for all client-side UI rendering. The game provides several pre-built progress bar styles with professional textures and animations that we can leverage for Living Lands.

**Key Discovery:** Instead of creating custom UI elements in our `.ui` format, we can reference Hytale's built-in XAML progress bar templates which already have:
- ✅ Animated fill bars
- ✅ Professional textures
- ✅ Smooth easing functions
- ✅ Damage indicators
- ✅ Color customization

---

## Available Progress Bar Styles

### 1. Health Bar (HealthBar.xaml)

**Location:** `Client/Data/Game/UI/DesignSystem/HealthBar.xaml`

**Features:**
- Angled design with diamond shape at end
- Built-in damage indicator (white notch that fades out)
- Two-layer system (main fill + drain layer for smooth damage visualization)
- Smooth QuarticEase animations (600ms duration)
- Visual state management for "Normal" and "Damaged" states

**Textures:**
- Background: `../Textures/HUD/HealthBar/HealthBarBackground.png`
- Fill: `../Textures/HUD/HealthBar/HealthBarFill.png`
- Icon: `../Textures/HUD/HealthBar/IconHealth.png`

**Key Code:**
```xaml
<!-- Main health bar -->
<ProgressBar 
    x:Name="HealthBar"
    Style="{StaticResource HealthBarStyle}"
    Value="{Binding Health.CurrentFactor}"
    Minimum="0"
    Maximum="1"/>

<!-- Drain effect (white overlay that fades when damaged) -->
<ProgressBar 
    x:Name="HealthBarDrain"
    Style="{StaticResource HealthBarDrainingStyle}"
    Value="{Binding Health.PreviousFactor}"
    Minimum="0"
    Maximum="1"/>
```

**Animation:**
```xaml
<Storyboard x:Key="HealthBarAnimation">
    <DoubleAnimation
        Storyboard.TargetName="HealthBar"
        Storyboard.TargetProperty="Value"
        To="{Binding Health.CurrentFactor}"
        Duration="0:0:0.6">
        <DoubleAnimation.EasingFunction>
            <QuarticEase EasingMode="EaseOut"/>
        </DoubleAnimation.EasingFunction>
    </DoubleAnimation>
</Storyboard>
```

**Dimensions:**
- Icon: 40px wide
- Bar: 280px wide x 24px tall
- Total: 320px wide

---

### 2. Experience Bar (ExperienceBar.xaml)

**Location:** `Client/Data/Game/UI/DesignSystem/ExperienceBar.xaml`

**Features:**
- Hexagonal/angled shape
- Simpler design than health bar
- Single layer (no drain effect)
- Uses dynamic color binding

**Key Code:**
```xaml
<ProgressBar 
    Style="{StaticResource Style.ExperienceBar}"
    Value="{Binding Experience.CurrentFactor}"
    Minimum="0"
    Maximum="1"/>
```

**Path Data:**
```xaml
<Path Data="M6.19355,0 0,6 6.19355,12 249.806,12 252.747,9.3197 253.051,9.04243 253.636,8.50953 256,6 249.807,0 6.19355,0z"
    Fill="{DynamicResource Color.Fill.ExperienceBar}"/>
```

**Dimensions:**
- Width: 256px
- Height: 20px

---

### 3. Crafting Bar (CraftingBar.xaml)

**Location:** `Client/Data/Game/UI/DesignSystem/CraftingBar.xaml`

**Features:**
- Ornate design with runic decoration background
- Diamond-shaped center ornament
- Three layers: decor, background, fill
- Animated crafting progress

**Textures:**
- Background: `../Textures/Controls/CraftingBarBackground.png`
- Fill: `../Textures/Controls/CraftingBarFill.png`
- Decor: `../Textures/Controls/CraftingBarDecor.png`

**Key Code:**
```xaml
<ProgressBar 
    x:Name="CraftingBar"
    Style="{StaticResource Style.CraftingBar}"
    Value="{Binding Progress}"
    Minimum="0"
    Maximum="1"/>
```

**Animation:**
```xaml
<Storyboard x:Key="Animation.CraftingBar">
    <DoubleAnimation
        Storyboard.TargetName="CraftingBar"
        Storyboard.TargetProperty="Value"
        From="0"
        To="1"
        Duration="{Binding ActiveCraft.Duration}"/>
</Storyboard>
```

**Dimensions:**
- Wrapper: 474px wide x 88px tall (includes decoration)
- Bar: 444px wide x 54px tall

---

### 4. Radial Progress Bar (CraftingBar.xaml)

**Location:** `Client/Data/Game/UI/DesignSystem/CraftingBar.xaml` (Template.CraftingBar.Radial)

**Features:**
- Circular progress indicator
- Uses NoesisGUI's `Path.TrimEnd` extension
- Rotates -90° to start from top
- Perfect for cooldown timers

**Key Code:**
```xaml
<ControlTemplate x:Key="Template.CraftingBar.Radial">
    <Ellipse
        noesis:Path.TrimEnd="{Binding Value, RelativeSource={RelativeSource TemplatedParent}}"
        noesis:Path.TrimStart="0"
        RenderTransformOrigin="0.5,0.5"
        Stroke="{DynamicResource Color.Fill.RadialProgressBar}"
        StrokeThickness="6">
        <Ellipse.RenderTransform>
            <RotateTransform Angle="-90"/>
        </Ellipse.RenderTransform>
    </Ellipse>
</ControlTemplate>
```

---

### 5. Nameplate Health Bar (HealthBar.xaml)

**Location:** `Client/Data/Game/UI/DesignSystem/HealthBar.xaml` (Template.HealthBar.Nameplate)

**Features:**
- Compact design for entity nameplates
- Smaller than player health bar
- Stroke outline for visibility against any background
- Gradient fill (red to pink)

**Key Code:**
```xaml
<ProgressBar 
    Style="{StaticResource Style.HealthBar.Nameplate}"
    Value="{Binding Entity.Health.CurrentFactor}"/>
```

**Dimensions:**
- Width: 146px
- Height: 10px

---

## Color System

**Location:** `Client/Data/Game/UI/DesignSystem/Colors.xaml`

### Available Color Tokens

**Global Colors:**
```xaml
<Color x:Key="White">#FFFFFFFF</Color>
<Color x:Key="Blue500">#FF2F3A54</Color>
<Color x:Key="Blue900">#FF2B3343</Color>
<Color x:Key="Red500">#FFB13158</Color>
<Color x:Key="Red600">#FF903A48</Color>
<Color x:Key="Yellow300">#FFBD9050</Color>
<Color x:Key="Green500">#FF00E7B6</Color>
<Color x:Key="Cyan100">#FF10DDDF</Color>
```

**Dynamic Fill Colors:**
```xaml
<SolidColorBrush x:Key="Color.Fill.ExperienceBar" Color="{DynamicResource Yellow300}"/>
<SolidColorBrush x:Key="Color.Fill.RadialProgressBar" Color="{DynamicResource Cyan100}"/>
```

**Gradient Fills:**
```xaml
<LinearGradientBrush x:Key="Color.BackgroundPrimary" StartPoint="0,0" EndPoint="0,1">
    <GradientStop Color="#4675A1" Offset="0"/>
    <GradientStop Color="#4A5D9E" Offset="1.0"/>
</LinearGradientBrush>
```

---

## Implementation Strategy for Living Lands

### Option 1: Server-Side HUD (Current Approach)

**What we're doing now:**
- Using Hytale's server-side `CustomUIHud` API
- Custom `.ui` file format (not XAML)
- Limited to simple Groups, Labels, and layout properties

**Limitations:**
- Cannot directly use XAML progress bar templates
- Cannot use NoesisGUI animations
- Must manually implement progress bar logic with `Width` binding

**Why it works:**
- Server can control all UI state
- No client-side code needed
- Per-player customization easy

### Option 2: Client-Side XAML Mod (Requires Client Modding)

**What we would need:**
- Client-side mod to register custom XAML files
- Access to Hytale's XAML resource system
- Ability to bind to server-provided data

**Benefits:**
- ✅ Full NoesisGUI features (animations, effects, gradients)
- ✅ Use vanilla progress bar templates
- ✅ Professional polish matching Hytale's UI

**Challenges:**
- ❌ Requires client-side modding (not just server plugin)
- ❌ Players must install client mod
- ❌ More complex distribution

### Option 3: Hybrid Approach (Recommended)

**Strategy:**
1. Keep server-side HUD for basic stats display
2. Reference vanilla XAML styles where possible
3. Use simplified progress bars with manual width calculation
4. Add polished animations via CSS-like transitions in `.ui` files

**How to reference vanilla styles in `.ui` files:**

**Current `.ui` format:**
```ui
Group #HungerBar {
    Anchor: (Width: 256, Height: 20);
    Background: #21262d;  // Dark background
    
    Group #HungerFill {
        Anchor: (Width: 128, Height: 20);  // 50% fill
        Background: #27ae60;  // Green fill
    }
}
```

**Enhanced with vanilla-inspired design:**
```ui
// Use Hytale's experience bar dimensions and style
Group #HungerBarContainer {
    Anchor: (Width: 256, Height: 20);
    
    // Background layer (matches experience bar background)
    Group #HungerBarBackground {
        Anchor: (Width: 256, Height: 20);
        Background: #000000(0.3);  // Black 30% opacity (matches XAML)
        // TODO: Add path clipping when supported: 
        // Clip: "M10,4 4,10 10,16 246,16 252,10 246,4 10,4z"
    }
    
    // Fill layer (dynamic width based on hunger value)
    Group #HungerBarFill {
        Anchor: (Width: {#HungerWidth}, Height: 20);
        Background: #27ae60;  // Green
        // Add gradient when supported:
        // Background: LinearGradient(#2ecc71, #27ae60);
    }
    
    // Stroke layer for polish
    Group #HungerBarStroke {
        Anchor: (Width: 256, Height: 20);
        Border: (Color: #c9c9c9(0.2), Width: 1);
    }
}
```

---

## Available Textures for Custom Bars

**HUD Bar Textures:**
- `Textures/HUD/HealthBar/HealthBarBackground.png`
- `Textures/HUD/HealthBar/HealthBarFill.png`
- `Textures/HUD/HealthBar/HealthBarOutline.png`
- `Textures/HUD/StaminaBar/StaminaBarBackground.png`
- `Textures/HUD/StaminaBar/StaminaBarFill.png`
- `Textures/HUD/StaminaBar/StaminaBarOutline.png`
- `Textures/HUD/HUDBarsLinework.png`

**Control Textures:**
- `Textures/Controls/CraftingBarBackground.png`
- `Textures/Controls/CraftingBarFill.png`
- `Textures/Controls/CraftingBarDecor.png`

**How to use in server-side HUD:**

Currently, Hytale's server-side `CustomUIHud` API does **not support** direct image references. The `.ui` format only supports:
- Solid colors: `#RRGGBB` or `#RRGGBB(alpha)`
- Layout properties: `Anchor`, `Padding`, `LayoutMode`
- Text properties: `Text`, `Style`

**Workaround:** Use solid colors that match Hytale's palette to maintain visual consistency.

---

## Recommended Implementation for Living Lands

### Phase 1: Enhanced Simple Bars (Current)

Use current `.ui` format with improved styling:

```ui
// Metabolism Bars - Inspired by Hytale's experience bar
Group #MetabolismBars {
    LayoutMode: Top;
    Anchor: (Width: 256, Height: 72);
    Padding: (Full: 4);
    
    // Hunger Bar (Green)
    Group #HungerContainer {
        Anchor: (Height: 20);
        Padding: (Bottom: 2);
        
        Group #HungerBg {
            Anchor: (Width: 256, Height: 20);
            Background: #000000(0.3);
        }
        
        Group #HungerFill {
            Anchor: (Width: {#HungerWidth}, Height: 20);
            Background: #27ae60;  // Green
        }
        
        Group #HungerStroke {
            Anchor: (Width: 256, Height: 20);
            Border: (Color: #8b949e(0.2), Width: 1);
        }
        
        Label #HungerText {
            Text: "Hunger: 100%";
            Anchor: (Width: 256, Height: 20);
            Style: (
                FontSize: 10,
                TextColor: #ffffff,
                HorizontalAlignment: Center,
                VerticalAlignment: Center,
                RenderShadow: true
            );
        }
    }
    
    // Thirst Bar (Blue) - Same pattern
    // Energy Bar (Yellow) - Same pattern
}
```

**Benefits:**
- ✅ Works with current server-side API
- ✅ Matches Hytale's visual style
- ✅ Clean, professional appearance
- ✅ No client-side modding required

### Phase 2: Client-Side XAML Extension (Future)

If client-side modding becomes viable:

1. Create `LivingLands.xaml` resource dictionary
2. Define custom progress bar styles referencing vanilla templates
3. Register with Hytale's resource system
4. Bind to server-provided data via custom view models

**Example XAML (future):**
```xaml
<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation">
    
    <!-- Hunger Bar (green variant of health bar) -->
    <Style x:Key="Style.LivingLands.HungerBar" 
           TargetType="{x:Type ProgressBar}"
           BasedOn="{StaticResource HealthBarStyle}">
        <Setter Property="Template">
            <Setter.Value>
                <ControlTemplate TargetType="{x:Type ProgressBar}">
                    <!-- Copy health bar template structure -->
                    <!-- Change fill color to green -->
                    <Path Fill="#27ae60" Data="..."/>
                </ControlTemplate>
            </Setter.Value>
        </Setter>
    </Style>
    
    <!-- Thirst Bar (blue) -->
    <Style x:Key="Style.LivingLands.ThirstBar" 
           BasedOn="{StaticResource Style.LivingLands.HungerBar}">
        <!-- Override fill color to blue -->
    </Style>
    
    <!-- Energy Bar (yellow) -->
    <Style x:Key="Style.LivingLands.EnergyBar" 
           BasedOn="{StaticResource Style.LivingLands.HungerBar}">
        <!-- Override fill color to yellow -->
    </Style>
</ResourceDictionary>
```

---

## Key Takeaways

### What Works Now (Server-Side)
1. ✅ Simple colored rectangles with dynamic width
2. ✅ Text overlays with shadows
3. ✅ Layered backgrounds/fills for depth
4. ✅ Color matching Hytale's palette
5. ✅ Professional layout using Hytale's dimensions

### What Requires Client-Side Modding
1. ❌ XAML progress bar templates
2. ❌ NoesisGUI animations (easing functions)
3. ❌ Image-based textures
4. ❌ Path clipping for custom shapes
5. ❌ Gradient fills
6. ❌ Visual state management

### Best Approach for Living Lands
- **Keep server-side HUD** for broad compatibility
- **Style bars to match vanilla** using colors and dimensions
- **Add polish with layering** (background, fill, stroke, text)
- **Consider client-side mod** as optional enhancement for users who want maximum visual quality

---

## Testing the Vanilla Bars

To see Hytale's progress bars in action:

1. **Health Bar:** Take damage to see the white notch animation
2. **Experience Bar:** Gain XP to see smooth fill animation
3. **Crafting Bar:** Start crafting to see ornate progress animation
4. **Radial Bar:** Use abilities with cooldowns

**Inspect XAML files:**
```bash
# View health bar template
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/install/release/package/game/latest/Client/Data/Game/UI/DesignSystem/HealthBar.xaml"

# View all available colors
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/install/release/package/game/latest/Client/Data/Game/UI/DesignSystem/Colors.xaml"
```

---

## Next Steps

1. **Update current HUD bars** to use Hytale's color palette and dimensions
2. **Add layered backgrounds** for depth (match experience bar's 30% black background)
3. **Test on client** to verify visual consistency with vanilla UI
4. **Document color scheme** in `metabolism.yml` for user customization
5. **Consider future XAML mod** if client modding API becomes available

**Note:** Server-side HUD is the correct approach for now. Client-side XAML would be an enhancement, not a requirement.
