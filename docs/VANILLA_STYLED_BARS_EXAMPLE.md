# Vanilla-Styled Progress Bars for Living Lands HUD

## Comparison: Current vs Vanilla-Inspired

### Current Implementation (Simple)

```ui
// src/main/resources/Common/UI/Custom/Hud/LivingLandsHud.ui (current)
Group #HungerBar {
    Anchor: (Width: 200, Height: 6);
    Background: #21262d;
    
    Group #HungerFill {
        Anchor: (Width: 100, Height: 6);  // 50% width
        Background: #27ae60;
    }
}
```

**Issues:**
- ❌ Flat appearance (no depth)
- ❌ Hard to read against varied backgrounds
- ❌ Doesn't match Hytale's visual quality
- ❌ No visual polish (stroke, shadow, etc.)

---

### Vanilla-Inspired Implementation (Enhanced)

```ui
// Matches Hytale's experience bar style
Group #HungerBarContainer {
    Anchor: (Width: 256, Height: 20);  // Experience bar dimensions
    
    // Layer 1: Shadow/Depth (optional, below main bar)
    Group #HungerBarShadow {
        Anchor: (Top: 2, Width: 256, Height: 20);
        Background: #000000(0.1);  // 10% black shadow
    }
    
    // Layer 2: Background (matches XAML: 30% black)
    Group #HungerBarBackground {
        Anchor: (Width: 256, Height: 20);
        Background: #000000(0.3);  // Semi-transparent black
    }
    
    // Layer 3: Fill (dynamic width)
    Group #HungerBarFill {
        Anchor: (Width: {#HungerWidth}, Height: 20);
        // Hunger green (slightly darker than our current for contrast)
        Background: #27ae60;
    }
    
    // Layer 4: Highlight (top edge shine, optional)
    Group #HungerBarHighlight {
        Anchor: (Top: 0, Width: {#HungerWidth}, Height: 2);
        Background: #ffffff(0.2);  // Subtle white shine
    }
    
    // Layer 5: Stroke (matches XAML stroke)
    Group #HungerBarStroke {
        Anchor: (Width: 256, Height: 20);
        Border: (Color: #c9c9c9(0.2), Width: 1);  // Gray 20% opacity
    }
    
    // Layer 6: Text Label (centered)
    Label #HungerText {
        Text: "100";
        Anchor: (Width: 256, Height: 20);
        Style: (
            FontSize: 11,
            TextColor: #ffffff,
            HorizontalAlignment: Center,
            VerticalAlignment: Center,
            RenderShadow: true,
            RenderBold: true
        );
    }
    
    // Layer 7: Icon (left side, optional)
    // Note: Server-side HUD doesn't support images yet
    // Group #HungerIcon {
    //     Anchor: (Left: -24, Width: 20, Height: 20);
    //     Background: Image("../Textures/HUD/Icons/Hunger.png");
    // }
}
```

**Benefits:**
- ✅ Professional layered appearance
- ✅ Matches Hytale's visual style
- ✅ Readable against any background
- ✅ Visual polish (depth, stroke, highlight)
- ✅ Uses Hytale's exact dimensions and opacity values

---

## Complete Metabolism HUD (Vanilla-Styled)

```ui
// Living Lands Metabolism HUD - Vanilla Style
// Top-left corner, below hotbar

Group {
    // Anonymous root wrapper required for CustomUIHud.append()
    
    Group #MetabolismHudContainer {
        Anchor: (Top: 16, Left: 16, Width: 280, Height: 92);
        Visible: true;
        
        // Background panel (matches Hytale's semi-transparent backgrounds)
        Group #MetabolismPanel {
            Anchor: (Width: 280, Height: 92);
            Background: #0a1628(0.95);  // Dark blue, 95% opacity
            Padding: (Full: 8);
            
            // Bars container
            Group #BarsContainer {
                LayoutMode: Top;
                Anchor: (Width: 264, Height: 76);
                
                // ============ HUNGER BAR ============
                Group #HungerContainer {
                    Anchor: (Height: 22);
                    Padding: (Bottom: 2);
                    
                    // Icon/Label on left
                    Label #HungerLabel {
                        Text: "HUNGER";
                        Anchor: (Left: 0, Width: 60, Height: 22);
                        Style: (
                            FontSize: 9,
                            TextColor: #27ae60,
                            HorizontalAlignment: Start,
                            VerticalAlignment: Center,
                            RenderBold: true,
                            RenderUppercase: true,
                            LetterSpacing: 1
                        );
                    }
                    
                    // Bar on right
                    Group #HungerBarWrapper {
                        Anchor: (Left: 64, Width: 200, Height: 20);
                        
                        // Background layer
                        Group #HungerBg {
                            Anchor: (Width: 200, Height: 20);
                            Background: #000000(0.3);
                        }
                        
                        // Fill layer (dynamic)
                        Group #HungerFill {
                            Anchor: (Width: {#HungerWidth}, Height: 20);
                            Background: #27ae60;  // Green
                        }
                        
                        // Top highlight
                        Group #HungerHighlight {
                            Anchor: (Top: 0, Width: {#HungerWidth}, Height: 2);
                            Background: #ffffff(0.15);
                        }
                        
                        // Stroke
                        Group #HungerStroke {
                            Anchor: (Width: 200, Height: 20);
                            Border: (Color: #8b949e(0.2), Width: 1);
                        }
                        
                        // Value text
                        Label #HungerValue {
                            Text: "100";
                            Anchor: (Width: 200, Height: 20);
                            Style: (
                                FontSize: 11,
                                TextColor: #ffffff,
                                HorizontalAlignment: Center,
                                VerticalAlignment: Center,
                                RenderShadow: true,
                                RenderBold: true
                            );
                        }
                    }
                }
                
                // ============ THIRST BAR ============
                Group #ThirstContainer {
                    Anchor: (Height: 22);
                    Padding: (Bottom: 2);
                    
                    Label #ThirstLabel {
                        Text: "THIRST";
                        Anchor: (Left: 0, Width: 60, Height: 22);
                        Style: (
                            FontSize: 9,
                            TextColor: #3498db,  // Blue
                            HorizontalAlignment: Start,
                            VerticalAlignment: Center,
                            RenderBold: true,
                            RenderUppercase: true,
                            LetterSpacing: 1
                        );
                    }
                    
                    Group #ThirstBarWrapper {
                        Anchor: (Left: 64, Width: 200, Height: 20);
                        
                        Group #ThirstBg {
                            Anchor: (Width: 200, Height: 20);
                            Background: #000000(0.3);
                        }
                        
                        Group #ThirstFill {
                            Anchor: (Width: {#ThirstWidth}, Height: 20);
                            Background: #3498db;  // Blue
                        }
                        
                        Group #ThirstHighlight {
                            Anchor: (Top: 0, Width: {#ThirstWidth}, Height: 2);
                            Background: #ffffff(0.15);
                        }
                        
                        Group #ThirstStroke {
                            Anchor: (Width: 200, Height: 20);
                            Border: (Color: #8b949e(0.2), Width: 1);
                        }
                        
                        Label #ThirstValue {
                            Text: "100";
                            Anchor: (Width: 200, Height: 20);
                            Style: (
                                FontSize: 11,
                                TextColor: #ffffff,
                                HorizontalAlignment: Center,
                                VerticalAlignment: Center,
                                RenderShadow: true,
                                RenderBold: true
                            );
                        }
                    }
                }
                
                // ============ ENERGY BAR ============
                Group #EnergyContainer {
                    Anchor: (Height: 22);
                    
                    Label #EnergyLabel {
                        Text: "ENERGY";
                        Anchor: (Left: 0, Width: 60, Height: 22);
                        Style: (
                            FontSize: 9,
                            TextColor: #f39c12,  // Orange/Yellow
                            HorizontalAlignment: Start,
                            VerticalAlignment: Center,
                            RenderBold: true,
                            RenderUppercase: true,
                            LetterSpacing: 1
                        );
                    }
                    
                    Group #EnergyBarWrapper {
                        Anchor: (Left: 64, Width: 200, Height: 20);
                        
                        Group #EnergyBg {
                            Anchor: (Width: 200, Height: 20);
                            Background: #000000(0.3);
                        }
                        
                        Group #EnergyFill {
                            Anchor: (Width: {#EnergyWidth}, Height: 20);
                            Background: #f39c12;  // Orange/Yellow
                        }
                        
                        Group #EnergyHighlight {
                            Anchor: (Top: 0, Width: {#EnergyWidth}, Height: 2);
                            Background: #ffffff(0.15);
                        }
                        
                        Group #EnergyStroke {
                            Anchor: (Width: 200, Height: 20);
                            Border: (Color: #8b949e(0.2), Width: 1);
                        }
                        
                        Label #EnergyValue {
                            Text: "100";
                            Anchor: (Width: 200, Height: 20);
                            Style: (
                                FontSize: 11,
                                TextColor: #ffffff,
                                HorizontalAlignment: Center,
                                VerticalAlignment: Center,
                                RenderShadow: true,
                                RenderBold: true
                            );
                        }
                    }
                }
            }
        }
    }
}
```

---

## Color Palette Reference

### Hytale Vanilla Colors (from Colors.xaml)

```kotlin
// Use these exact colors to match Hytale's UI
object VanillaColors {
    // Background layers
    const val BACKGROUND_DARK = "#0a1628"      // Dark blue panel background
    const val BACKGROUND_BLACK = "#000000"     // Pure black for bar backgrounds
    
    // Stroke/Border
    const val STROKE_GRAY = "#8b949e"          // Gray stroke (use at 20% opacity)
    const val STROKE_LIGHT = "#c9c9c9"         // Light gray (alternative)
    
    // Highlight
    const val HIGHLIGHT_WHITE = "#ffffff"      // White highlight (use at 15% opacity)
    
    // Text colors
    const val TEXT_WHITE = "#ffffff"           // Primary text
    const val TEXT_GRAY = "#6e7681"            // Secondary text
    
    // Stat bar colors (use our current values, they're good)
    const val HUNGER_GREEN = "#27ae60"
    const val THIRST_BLUE = "#3498db"
    const val ENERGY_YELLOW = "#f39c12"
    
    // State colors
    const val CRITICAL_RED = "#e74c3c"
    const val WARNING_ORANGE = "#e67e22"
    const val HEALTHY_GREEN = "#2ecc71"
}
```

---

## Implementation in Kotlin

```kotlin
// src/main/kotlin/com/livinglands/core/hud/LivingLandsHudElement.kt

/**
 * Calculate bar width for vanilla-styled progress bars.
 * 
 * @param value Current stat value (0-100)
 * @param maxWidth Maximum bar width in pixels (200px for our bars)
 * @return Width in pixels, clamped to 1-maxWidth (never 0 for visibility)
 */
private fun calculateBarWidth(value: Double, maxWidth: Int = 200): Int {
    val percentage = (value / 100.0).coerceIn(0.0, 1.0)
    val width = (percentage * maxWidth).toInt()
    // Return minimum 1px if value > 0, otherwise 0
    return if (value > 0.0) width.coerceAtLeast(1) else 0
}

/**
 * Get color for stat value based on thresholds (vanilla experience bar uses dynamic colors).
 * 
 * @param value Current stat value (0-100)
 * @return Hex color string
 */
private fun getStatColor(value: Double, baseColor: String): String {
    return when {
        value >= 70.0 -> baseColor               // Healthy: base color
        value >= 40.0 -> "#e67e22"               // Warning: orange
        value >= 20.0 -> "#e74c3c"               // Critical: red
        else -> "#c0392b"                        // Danger: dark red
    }
}

override fun build(builder: UICommandBuilder) {
    builder.append("Hud/LivingLandsHud.ui")
    
    // Get current stats
    val stats = getMetabolismStats(playerId) ?: return
    
    // Hunger bar
    val hungerWidth = calculateBarWidth(stats.hunger)
    val hungerColor = getStatColor(stats.hunger, "#27ae60")
    builder.set("#HungerWidth", hungerWidth)
    builder.set("#HungerFill.Background", hungerColor)
    builder.set("#HungerHighlight.Width", hungerWidth)  // Sync highlight
    builder.set("#HungerValue.Text", stats.hunger.toInt().toString())
    
    // Thirst bar
    val thirstWidth = calculateBarWidth(stats.thirst)
    val thirstColor = getStatColor(stats.thirst, "#3498db")
    builder.set("#ThirstWidth", thirstWidth)
    builder.set("#ThirstFill.Background", thirstColor)
    builder.set("#ThirstHighlight.Width", thirstWidth)
    builder.set("#ThirstValue.Text", stats.thirst.toInt().toString())
    
    // Energy bar
    val energyWidth = calculateBarWidth(stats.energy)
    val energyColor = getStatColor(stats.energy, "#f39c12")
    builder.set("#EnergyWidth", energyWidth)
    builder.set("#EnergyFill.Background", energyColor)
    builder.set("#EnergyHighlight.Width", energyWidth)
    builder.set("#EnergyValue.Text", stats.energy.toInt().toString())
}
```

---

## Visual Comparison

### Before (Simple Flat Bars)
```
[████████████████████████████          ] 70%
```
- Flat green rectangle
- Hard to distinguish from background
- No depth or polish

### After (Vanilla-Styled Layered Bars)
```
┌────────────────────────────────────────┐
│ HUNGER  [▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░] 70│
│ THIRST  [▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░] 70│
│ ENERGY  [▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░] 70│
└────────────────────────────────────────┘
```
- Layered depth (shadow, background, fill, highlight, stroke)
- Clear labels with proper typography
- Matches Hytale's visual quality
- Professional appearance

---

## Key Dimensions (Match Vanilla)

| Element | Width | Height | Notes |
|---------|-------|--------|-------|
| Experience Bar | 256px | 20px | Vanilla reference |
| Health Bar | 280px | 24px | Vanilla reference |
| Our Metabolism Bars | 200px | 20px | Slightly smaller for compact HUD |
| Background Opacity | - | - | 30% black (vanilla) |
| Stroke Opacity | - | - | 20% gray (vanilla) |
| Highlight Opacity | - | - | 15% white |
| Panel Opacity | - | - | 95% (semi-transparent) |

---

## Next Steps

1. **Update `LivingLandsHud.ui`** with vanilla-styled bar structure
2. **Update `LivingLandsHudElement.kt`** with new binding logic
3. **Test on client** to verify visual consistency
4. **Add color transitions** for stat thresholds (healthy → warning → critical)
5. **Consider adding icons** when server-side HUD supports images

**Note:** This approach gives us ~90% of the vanilla quality using only server-side HUD capabilities. The remaining 10% (animations, gradients, custom shapes) would require client-side XAML modding.
