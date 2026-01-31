package com.livinglands.core

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.awt.Color

/**
 * Centralized message formatting and sending utility for Living Lands.
 * 
 * Provides consistent, color-coded messages across all modules with a clean API.
 * Uses Hytale's Message API with proper color support.
 * 
 * Usage:
 * ```kotlin
 * MessageFormatter.success(playerRef, "Stats restored!", "hunger +10, thirst +5")
 * MessageFormatter.info(playerRef, "You consumed Meat (T3)", "+32.5 hunger")
 * MessageFormatter.error(playerRef, "Not enough hunger!", "Eat some food")
 * ```
 */
object MessageFormatter {
    
    // Color palette
    private val GRAY = Color(85, 85, 85)        // Dark gray for brackets
    private val GOLD = Color(255, 170, 0)       // Gold for plugin name
    private val GREEN = Color(85, 255, 85)      // Green for success
    private val RED = Color(255, 85, 85)        // Red for errors
    private val AQUA = Color(85, 255, 255)      // Aqua for info
    private val YELLOW = Color(255, 255, 85)    // Yellow for warnings
    private val DARK_GRAY = Color(170, 170, 170) // Light gray for details
    private val ORANGE = Color(255, 170, 85)    // Orange for hunger
    private val LIGHT_AQUA = Color(170, 255, 255) // Light aqua for thirst
    
    /**
     * Create the plugin prefix: [Living Lands]
     */
    private fun createPrefix(): Message {
        return Message.raw("[")
            .color(GRAY)
            .insert(Message.raw("Living Lands").color(GOLD))
            .insert(Message.raw("]").color(GRAY))
    }
    
    /**
     * Send a success message (green).
     * 
     * @param playerRef Target player
     * @param text Main message text
     * @param detail Optional detail text
     */
    fun success(playerRef: PlayerRef, text: String, detail: String? = null) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(GREEN))
        
        if (detail != null) {
            msg.insert(Message.raw(" ($detail)").color(DARK_GRAY))
        }
        
        playerRef.sendMessage(msg)
    }
    
    /**
     * Send an error message (red).
     * 
     * @param playerRef Target player
     * @param text Main message text
     * @param detail Optional detail text
     */
    fun error(playerRef: PlayerRef, text: String, detail: String? = null) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(RED))
        
        if (detail != null) {
            msg.insert(Message.raw(" ($detail)").color(DARK_GRAY))
        }
        
        playerRef.sendMessage(msg)
    }
    
    /**
     * Send an info message (aqua).
     * 
     * @param playerRef Target player
     * @param text Main message text
     * @param detail Optional detail text
     */
    fun info(playerRef: PlayerRef, text: String, detail: String? = null) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(AQUA))
        
        if (detail != null) {
            msg.insert(Message.raw(" ($detail)").color(DARK_GRAY))
        }
        
        playerRef.sendMessage(msg)
    }
    
    /**
     * Send a warning message (yellow).
     * 
     * @param playerRef Target player
     * @param text Main message text
     * @param detail Optional detail text
     */
    fun warning(playerRef: PlayerRef, text: String, detail: String? = null) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(YELLOW))
        
        if (detail != null) {
            msg.insert(Message.raw(" ($detail)").color(DARK_GRAY))
        }
        
        playerRef.sendMessage(msg)
    }
    
    /**
     * Send a food consumption message (specialized format).
     * 
     * Format: [Living Lands] You consumed <Food> (T<tier>) -> +stats
     * 
     * @param playerRef Target player
     * @param foodName Name of the food
     * @param tier Food tier (1-3)
     * @param hungerRestore Hunger restored
     * @param thirstRestore Thirst restored
     * @param energyRestore Energy restored
     */
    fun foodConsumption(
        playerRef: PlayerRef,
        foodName: String,
        tier: Int,
        hungerRestore: Double,
        thirstRestore: Double,
        energyRestore: Double
    ) {
        val message = createPrefix()
            .insert(Message.raw(" You consumed ").color(AQUA))
            .insert(Message.raw(foodName).color(YELLOW))
            .insert(Message.raw(" (T$tier)").color(DARK_GRAY))
        
        // Add stat changes if any
        val parts = mutableListOf<Message>()
        if (hungerRestore > 0) {
            parts.add(Message.raw("+%.1f hunger".format(hungerRestore)).color(ORANGE))
        }
        if (thirstRestore > 0) {
            parts.add(Message.raw("+%.1f thirst".format(thirstRestore)).color(LIGHT_AQUA))
        }
        if (energyRestore > 0) {
            parts.add(Message.raw("+%.1f energy".format(energyRestore)).color(YELLOW))
        }
        
        if (parts.isNotEmpty()) {
            message.insert(Message.raw(" -> ").color(GREEN))
            for (i in parts.indices) {
                message.insert(parts[i])
                if (i < parts.size - 1) {
                    message.insert(Message.raw(", ").color(DARK_GRAY))
                }
            }
        }
        
        playerRef.sendMessage(message)
    }
    
    /**
     * Send a raw message without prefix (for special cases).
     * 
     * @param playerRef Target player
     * @param message Raw message text
     */
    fun raw(playerRef: PlayerRef, message: String) {
        playerRef.sendMessage(Message.raw(message))
    }
    
    /**
     * Send a command success message (for CommandContext).
     * Format: [Living Lands] <message>
     * 
     * @param ctx Command context
     * @param text Main message text
     */
    fun commandSuccess(ctx: com.hypixel.hytale.server.core.command.system.CommandContext, text: String) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(GREEN))
        ctx.sendMessage(msg)
    }
    
    /**
     * Send a command error message (for CommandContext).
     * Format: [Living Lands] <message>
     * 
     * @param ctx Command context
     * @param text Main message text
     */
    fun commandError(ctx: com.hypixel.hytale.server.core.command.system.CommandContext, text: String) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(RED))
        ctx.sendMessage(msg)
    }
    
    /**
     * Send a command info message (for CommandContext).
     * Format: [Living Lands] <message>
     * 
     * @param ctx Command context
     * @param text Main message text
     * @param color Optional color (defaults to AQUA)
     */
    fun commandInfo(
        ctx: com.hypixel.hytale.server.core.command.system.CommandContext, 
        text: String,
        color: Color = AQUA
    ) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(color))
        ctx.sendMessage(msg)
    }
    
    /**
     * Send a command warning message (for CommandContext).
     * Format: [Living Lands] <message>
     * 
     * @param ctx Command context
     * @param text Main message text
     */
    fun commandWarning(ctx: com.hypixel.hytale.server.core.command.system.CommandContext, text: String) {
        val msg = createPrefix()
            .insert(Message.raw(" $text").color(YELLOW))
        ctx.sendMessage(msg)
    }
    
    /**
     * Send a raw command message (no prefix, for special formatting like headers).
     * 
     * @param ctx Command context
     * @param message Raw message text
     * @param color Optional color
     */
    fun commandRaw(
        ctx: com.hypixel.hytale.server.core.command.system.CommandContext,
        message: String,
        color: Color? = null
    ) {
        val msg = if (color != null) {
            Message.raw(message).color(color)
        } else {
            Message.raw(message)
        }
        ctx.sendMessage(msg)
    }
    
    /**
     * Send a buff activation/deactivation message.
     * 
     * Format: [Living Lands] Buff: <BuffName> (applied/removed)
     * 
     * @param playerRef Target player
     * @param buffName Name of the buff
     * @param activated True if buff applied, false if removed
     */
    fun buff(playerRef: PlayerRef, buffName: String, activated: Boolean) {
        val message = createPrefix()
            .insert(Message.raw(" Buff: ").color(GREEN))
            .insert(Message.raw(buffName).color(YELLOW))
            .insert(Message.raw(" (").color(DARK_GRAY))
            .insert(Message.raw(if (activated) "applied" else "removed").color(if (activated) GREEN else RED))
            .insert(Message.raw(")").color(DARK_GRAY))
        
        playerRef.sendMessage(message)
    }
    
    /**
     * Send a debuff activation/deactivation message.
     * 
     * Format: [Living Lands] Debuff: <DebuffName> (applied/removed)
     * 
     * @param playerRef Target player
     * @param debuffName Name of the debuff
     * @param activated True if debuff applied, false if removed
     */
    fun debuff(playerRef: PlayerRef, debuffName: String, activated: Boolean) {
        val message = createPrefix()
            .insert(Message.raw(" Debuff: ").color(RED))
            .insert(Message.raw(debuffName).color(YELLOW))
            .insert(Message.raw(" (").color(DARK_GRAY))
            .insert(Message.raw(if (activated) "applied" else "removed").color(if (activated) RED else GREEN))
            .insert(Message.raw(")").color(DARK_GRAY))
        
        playerRef.sendMessage(message)
    }
    
    // ============ Professions Messages ============
    
    /**
     * Send a profession level-up message with ability unlock.
     * 
     * Format: [Living Lands] <Profession> leveled up! (Level X)
     *         [Living Lands] ✓ Ability Unlocked: <AbilityName>
     *         [Living Lands]   → <AbilityDescription>
     * 
     * @param playerRef Target player
     * @param professionName Name of the profession
     * @param newLevel New level achieved
     * @param abilityName Name of the ability unlocked
     * @param abilityDescription Description of the ability effect
     */
    fun professionLevelUpWithAbility(
        playerRef: PlayerRef,
        professionName: String,
        newLevel: Int,
        abilityName: String,
        abilityDescription: String
    ) {
        // First message: Level up
        val levelUpMsg = createPrefix()
            .insert(Message.raw(" $professionName leveled up! ").color(GREEN))
            .insert(Message.raw("(Level $newLevel)").color(DARK_GRAY))
        playerRef.sendMessage(levelUpMsg)
        
        // Second message: Ability unlocked
        val abilityMsg = createPrefix()
            .insert(Message.raw(" ✓ Ability Unlocked: ").color(GOLD))
            .insert(Message.raw(abilityName).color(YELLOW))
        playerRef.sendMessage(abilityMsg)
        
        // Third message: Ability description
        val descMsg = createPrefix()
            .insert(Message.raw("   → $abilityDescription").color(AQUA))
        playerRef.sendMessage(descMsg)
    }
    
    /**
     * Send a profession level-up message without ability unlock.
     * 
     * Format: [Living Lands] <Profession> leveled up! (Level X → Y)
     * 
     * @param playerRef Target player
     * @param professionName Name of the profession
     * @param oldLevel Previous level
     * @param newLevel New level achieved
     */
    fun professionLevelUp(
        playerRef: PlayerRef,
        professionName: String,
        oldLevel: Int,
        newLevel: Int
    ) {
        val message = createPrefix()
            .insert(Message.raw(" $professionName leveled up! ").color(GREEN))
            .insert(Message.raw("(Level $oldLevel → $newLevel)").color(DARK_GRAY))
        
        playerRef.sendMessage(message)
    }
    
    /**
     * Send a death penalty message for XP loss.
     * 
     * Format: [Living Lands] Death Penalty: Lost <XP> XP in <Profession> (-X%)
     * 
     * @param playerRef Target player
     * @param professionName Name of the profession
     * @param xpLost Amount of XP lost
     * @param penaltyPercent Percentage of XP lost (0.0 to 1.0)
     */
    fun professionDeathPenalty(
        playerRef: PlayerRef,
        professionName: String,
        xpLost: Long,
        penaltyPercent: Double
    ) {
        val message = createPrefix()
            .insert(Message.raw(" Death Penalty: ").color(RED))
            .insert(Message.raw("Lost $xpLost XP in $professionName ").color(YELLOW))
            .insert(Message.raw("(-${(penaltyPercent * 100).toInt()}%)").color(DARK_GRAY))
        
        playerRef.sendMessage(message)
    }
    
    // ============ Announcer Messages ============
    
    /**
     * Send an announcement message without the [Living Lands] prefix.
     * 
     * Announcements are server-wide broadcasts that should feel like official server messages,
     * not plugin-specific chatter. This method parses Minecraft-style color codes (&a, &6, etc.)
     * and converts them to Hytale's Message API.
     * 
     * Supported color codes:
     * - &0-&9, &a-&f - Standard Minecraft colors
     * - &l - Bold (not supported in Hytale, ignored)
     * - &r - Reset to white
     * 
     * Examples:
     * - "&6Welcome to Living Lands!" → Gold text
     * - "&a[Tip] &fStay hydrated!" → Green [Tip] + White text
     * - "&bJoin Discord: &fdiscord.gg/example" → Aqua + White
     * 
     * @param playerRef Target player
     * @param text Message text with & color codes
     */
    fun announcement(playerRef: PlayerRef, text: String) {
        val message = parseColorCodes(text)
        playerRef.sendMessage(message)
    }
    
    /**
     * Parse Minecraft-style color codes (&a, &6, etc.) into Hytale Message API.
     * 
     * @param text Text with & color codes
     * @return Hytale Message with colors applied
     */
    private fun parseColorCodes(text: String): Message {
        val colorMap = mapOf(
            '0' to Color(0, 0, 0),          // Black
            '1' to Color(0, 0, 170),        // Dark Blue
            '2' to Color(0, 170, 0),        // Dark Green
            '3' to Color(0, 170, 170),      // Dark Aqua
            '4' to Color(170, 0, 0),        // Dark Red
            '5' to Color(170, 0, 170),      // Dark Purple
            '6' to Color(255, 170, 0),      // Gold
            '7' to Color(170, 170, 170),    // Gray
            '8' to Color(85, 85, 85),       // Dark Gray
            '9' to Color(85, 85, 255),      // Blue
            'a' to Color(85, 255, 85),      // Green
            'b' to Color(85, 255, 255),     // Aqua
            'c' to Color(255, 85, 85),      // Red
            'd' to Color(255, 85, 255),     // Light Purple
            'e' to Color(255, 255, 85),     // Yellow
            'f' to Color(255, 255, 255)     // White
        )
        
        // Split by color codes
        val parts = mutableListOf<Pair<Color?, String>>()
        var currentColor: Color? = null
        var currentText = StringBuilder()
        
        var i = 0
        while (i < text.length) {
            if (i < text.length - 1 && text[i] == '&') {
                val code = text[i + 1].lowercaseChar()
                
                // Save current segment
                if (currentText.isNotEmpty()) {
                    parts.add(currentColor to currentText.toString())
                    currentText = StringBuilder()
                }
                
                // Update color
                when {
                    code in colorMap -> currentColor = colorMap[code]
                    code == 'r' -> currentColor = null  // Reset
                    code == 'l' -> { } // Bold not supported, ignore
                    else -> {
                        // Unknown code, treat as literal
                        currentText.append('&').append(text[i + 1])
                    }
                }
                
                i += 2  // Skip & and code
            } else {
                currentText.append(text[i])
                i++
            }
        }
        
        // Add final segment
        if (currentText.isNotEmpty()) {
            parts.add(currentColor to currentText.toString())
        }
        
        // Build Message
        if (parts.isEmpty()) {
            return Message.raw("")
        }
        
        val (firstColor, firstText) = parts[0]
        val message = if (firstColor != null) {
            Message.raw(firstText).color(firstColor)
        } else {
            Message.raw(firstText)
        }
        
        // Insert remaining parts
        for (i in 1 until parts.size) {
            val (color, text) = parts[i]
            if (color != null) {
                message.insert(Message.raw(text).color(color))
            } else {
                message.insert(Message.raw(text))
            }
        }
        
        return message
    }
}
