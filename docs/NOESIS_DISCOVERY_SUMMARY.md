# NoesisGUI Discovery Summary

## Key Finding

**Hytale uses NoesisGUI (XAML) for client-side rendering**, not a custom UI system. This means:

✅ **Professional progress bar templates already exist** in the client
✅ **Animations and effects are built-in** (QuarticEase, gradients, strokes)
✅ **We can match vanilla visual quality** using their exact dimensions and colors
✅ **Server-side HUD works**, but has limitations compared to full XAML

---

## What We Found

### 1. Pre-Built Progress Bar Templates

**Location:** `Client/Data/Game/UI/DesignSystem/`

| Template | File | Features |
|----------|------|----------|
| Health Bar | HealthBar.xaml | Angled design, damage indicator, dual-layer |
| Experience Bar | ExperienceBar.xaml | Hexagonal shape, single layer |
| Crafting Bar | CraftingBar.xaml | Ornate decoration, diamond center |
| Radial Bar | CraftingBar.xaml | Circular, perfect for cooldowns |
| Nameplate Bar | HealthBar.xaml | Compact, 146px wide |

**All include:**
- XAML `<ProgressBar>` controls with `PART_Indicator` (auto-scaling fill)
- Smooth animations (600ms with QuarticEase)
- Professional textures and gradients
- Visual state management

### 2. Available Textures

**HUD Bar Assets:**
```
Textures/HUD/HealthBar/HealthBarBackground.png
Textures/HUD/HealthBar/HealthBarFill.png
Textures/HUD/HealthBar/IconHealth.png
Textures/HUD/StaminaBar/StaminaBarBackground.png
Textures/HUD/StaminaBar/StaminaBarFill.png
```

**Control Assets:**
```
Textures/Controls/CraftingBarBackground.png
Textures/Controls/CraftingBarFill.png
Textures/Controls/CraftingBarDecor.png
```

### 3. Color System

**Location:** `Client/Data/Game/UI/DesignSystem/Colors.xaml`

```xaml
<!-- Key colors for Living Lands -->
<Color x:Key="Blue900">#FF2B3343</Color>      <!-- Panel backgrounds -->
<Color x:Key="Blue800">#FF434E65</Color>      <!-- Borders -->
<Color x:Key="Red500">#FFB13158</Color>       <!-- Critical state -->
<Color x:Key="Yellow300">#FFBD9050</Color>    <!-- Experience bar -->
<Color x:Key="Green500">#FF00E7B6</Color>     <!-- Healthy state -->

<!-- Dynamic resources -->
<SolidColorBrush x:Key="Color.Fill.ExperienceBar" Color="{DynamicResource Yellow300}"/>
```

---

## What This Means for Living Lands

### Current Approach: Server-Side HUD ✅

**What we can do:**
- ✅ Simple rectangular bars with dynamic width
- ✅ Text labels and value displays
- ✅ Layered groups for depth (background, fill, stroke)
- ✅ Color matching vanilla palette
- ✅ Use vanilla dimensions (256x20 for experience bar style)

**Limitations:**
- ❌ No image/texture support (yet)
- ❌ No XAML animations
- ❌ No path clipping for custom shapes
- ❌ No gradient fills
- ❌ Manual width calculation instead of `<ProgressBar>` auto-scaling

**Quality:** ~90% of vanilla visual quality using layering and colors

### Future Approach: Client-Side XAML Mod

**What we could add:**
- Full `<ProgressBar>` controls with PART_Indicator
- QuarticEase animations (smooth 600ms transitions)
- Image-based textures from Hytale's asset pack
- Visual state management (Normal/Damaged states)
- Gradient fills and custom shapes

**Challenge:** Requires client-side modding, not just server plugin

---

## Recommended Action Plan

### Phase 1: Enhanced Server-Side Bars (Immediate) ✅

**Update current HUD to vanilla-inspired style:**

```ui
// Use Hytale's experience bar as reference
Group #HungerBar {
    Anchor: (Width: 200, Height: 20);
    
    // Layer 1: Background (30% black - matches vanilla)
    Group #Background {
        Anchor: (Width: 200, Height: 20);
        Background: #000000(0.3);
    }
    
    // Layer 2: Fill (dynamic width)
    Group #Fill {
        Anchor: (Width: {#HungerWidth}, Height: 20);
        Background: #27ae60;
    }
    
    // Layer 3: Highlight (15% white - matches vanilla)
    Group #Highlight {
        Anchor: (Top: 0, Width: {#HungerWidth}, Height: 2);
        Background: #ffffff(0.15);
    }
    
    // Layer 4: Stroke (20% gray - matches vanilla)
    Group #Stroke {
        Anchor: (Width: 200, Height: 20);
        Border: (Color: #8b949e(0.2), Width: 1);
    }
    
    // Layer 5: Text
    Label #Value {
        Text: "100";
        Style: (FontSize: 11, TextColor: #ffffff, RenderBold: true, RenderShadow: true);
    }
}
```

**Benefits:**
- Professional appearance matching vanilla
- Works with current server-side API
- No client-side modding required
- Maintains compatibility

### Phase 2: Optional Client-Side XAML (Future)

**If Hytale adds client modding API:**

1. Create `LivingLands.xaml` resource dictionary
2. Extend vanilla `<ProgressBar>` templates
3. Reference vanilla textures and animations
4. Bind to server-provided data

**Would add:**
- Full NoesisGUI animations
- Texture-based bars
- Visual state management
- ~10% additional polish

---

## Documentation Created

1. **NOESIS_PROGRESSBAR_GUIDE.md** - Complete XAML reference
   - All vanilla progress bar templates
   - Color system documentation
   - Implementation strategies
   - Testing procedures

2. **VANILLA_STYLED_BARS_EXAMPLE.md** - Practical implementation
   - Complete HUD example with layered bars
   - Kotlin binding code
   - Color palette reference
   - Visual comparison

3. **This summary** - Quick reference for future work

---

## Key Takeaways

1. **NoesisGUI is powerful** - XAML provides professional UI capabilities
2. **Vanilla templates exist** - Health, experience, and crafting bars already built
3. **Server-side works well** - Can achieve 90% vanilla quality with layering
4. **Client-side is future** - Would add final 10% polish (animations, textures)
5. **Use vanilla dimensions** - 256x20 (experience bar) or 280x24 (health bar)
6. **Match vanilla colors** - 30% black background, 20% gray stroke, 15% white highlight

**Recommendation:** Implement Phase 1 (enhanced server-side bars) now. The visual quality will be excellent and compatible with all players. Consider Phase 2 (client-side XAML) only if Hytale adds client modding support.

---

## References

**Client Data Location:**
```
C:\Users\moshpit\AppData\Roaming\Hytale\install\release\package\game\latest\Client\Data
```

**Key Directories:**
- `Game/UI/DesignSystem/` - XAML templates
- `Game/UI/Textures/` - UI assets
- `Game/UI/Common/` - Shared components

**Key Files:**
- `DesignSystem/HealthBar.xaml` - Best reference for metabolism bars
- `DesignSystem/ExperienceBar.xaml` - Simplest bar template
- `DesignSystem/Colors.xaml` - Complete color palette
- `DesignSystem/CraftingBar.xaml` - Ornate bar + radial progress
