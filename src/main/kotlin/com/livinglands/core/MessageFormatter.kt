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
}
