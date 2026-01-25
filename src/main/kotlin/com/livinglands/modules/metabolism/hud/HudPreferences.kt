package com.livinglands.modules.metabolism.hud

/**
 * Player preferences for HUD display visibility.
 * Persisted to database per-player per-world.
 * 
 * Defaults match v2.6.0 behavior (all visible by default).
 */
data class HudPreferences(
    /** Show metabolism stats (hunger/thirst/energy bars) */
    var statsVisible: Boolean = true,
    
    /** Show active buffs */
    var buffsVisible: Boolean = true,
    
    /** Show active debuffs */
    var debuffsVisible: Boolean = true
)
