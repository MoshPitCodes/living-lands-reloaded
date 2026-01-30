package com.livinglands.modules.metabolism.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.modules.metabolism.MetabolismService
import java.awt.Color
import java.util.UUID

/**
 * Test command for validating Metabolism API extensions (Phase 0 - Professions Prerequisites).
 * 
 * Usage:
 * - /ll testmeta restore <type> <amount> - Test restoration APIs
 * - /ll testmeta modifier add <id> <multiplier> - Test adding depletion modifier
 * - /ll testmeta modifier remove <id> - Test removing depletion modifier
 * - /ll testmeta modifier list - List active modifiers
 * - /ll testmeta modifier clear - Clear all modifiers
 * 
 * Examples:
 * - /ll testmeta restore energy 25 - Restore 25 energy
 * - /ll testmeta modifier add professions:survivalist 0.85 - Apply 15% slower depletion
 * - /ll testmeta modifier add buff:endurance 0.90 - Stack another modifier (10% slower)
 * - /ll testmeta modifier list - See combined multiplier
 * - /ll testmeta modifier remove professions:survivalist - Remove first modifier
 * - /ll testmeta modifier clear - Reset to normal depletion
 */
class TestMetabolismCommand : CommandBase(
    "testmeta",
    "Test Metabolism API extensions (restore, modifiers)",
    true // Requires operator
) {
    
    private val green = Color(85, 255, 85)
    private val yellow = Color(255, 255, 85)
    private val red = Color(255, 85, 85)
    private val gray = Color(170, 170, 170)
    
    override fun executeSync(ctx: CommandContext) {
        // Parse command arguments from input string
        val args = ctx.inputString.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        if (args.isEmpty()) {
            sendUsage(ctx)
            return
        }
        
        // Check if sender is a player
        if (!ctx.isPlayer) {
            MessageFormatter.commandError(ctx, "This command can only be used by players")
            return
        }
        
        // Get MetabolismService
        val metabolismService = CoreModule.services.get<MetabolismService>() ?: run {
            MessageFormatter.commandError(ctx, "MetabolismService not found - module not loaded?")
            return
        }
        
        val subcommand = args[0].lowercase()
        
        when (subcommand) {
            "restore" -> handleRestore(ctx, metabolismService, args)
            "modifier" -> handleModifier(ctx, metabolismService, args)
            else -> sendUsage(ctx)
        }
    }
    
    private fun handleRestore(ctx: CommandContext, service: MetabolismService, args: List<String>) {
        if (args.size < 3) {
            MessageFormatter.commandError(ctx, "Usage: /ll testmeta restore <hunger|thirst|energy> <amount>")
            return
        }
        
        val type = args[1].lowercase()
        val amount = args[2].toDoubleOrNull()
        
        if (amount == null || amount <= 0) {
            MessageFormatter.commandError(ctx, "Amount must be a positive number")
            return
        }
        
        // Get player UUID from context
        val playerId = getPlayerUuid(ctx)
        if (playerId == null) {
            MessageFormatter.commandError(ctx, "Unable to get player reference")
            return
        }
        
        // Call appropriate restoration method
        when (type) {
            "hunger" -> {
                service.restoreHunger(playerId, amount)
                MessageFormatter.commandRaw(ctx, "[Test] Restored $amount hunger", green)
            }
            "thirst" -> {
                service.restoreThirst(playerId, amount)
                MessageFormatter.commandRaw(ctx, "[Test] Restored $amount thirst", green)
            }
            "energy" -> {
                service.restoreEnergy(playerId, amount)
                MessageFormatter.commandRaw(ctx, "[Test] Restored $amount energy", green)
            }
            else -> {
                MessageFormatter.commandError(ctx, "Type must be: hunger, thirst, or energy")
            }
        }
    }
    
    private fun handleModifier(ctx: CommandContext, service: MetabolismService, args: List<String>) {
        if (args.size < 2) {
            MessageFormatter.commandError(ctx, "Usage: /ll testmeta modifier <add|remove|list|clear>")
            return
        }
        
        val action = args[1].lowercase()
        val playerId = getPlayerUuid(ctx)
        
        if (playerId == null) {
            MessageFormatter.commandError(ctx, "Unable to get player reference")
            return
        }
        
        when (action) {
            "add" -> {
                if (args.size < 4) {
                    MessageFormatter.commandError(ctx, "Usage: /ll testmeta modifier add <id> <multiplier>")
                    MessageFormatter.commandRaw(ctx, "Example: /ll testmeta modifier add professions:survivalist 0.85", gray)
                    return
                }
                
                val modifierId = args[2]
                val multiplier = args[3].toDoubleOrNull()
                
                if (multiplier == null || multiplier <= 0) {
                    MessageFormatter.commandError(ctx, "Multiplier must be a positive number")
                    MessageFormatter.commandRaw(ctx, "0.85 = 15% slower depletion, 1.5 = 50% faster depletion", gray)
                    return
                }
                
                service.applyDepletionModifier(playerId, modifierId, multiplier)
                
                val activeModifiers = service.getActiveModifiers(playerId)
                val combined = activeModifiers.values.fold(1.0) { acc, m -> acc * m }
                
                MessageFormatter.commandRaw(ctx, "[Test] Applied modifier '$modifierId' with multiplier ${multiplier}x", green)
                MessageFormatter.commandRaw(ctx, "Combined multiplier: ${String.format("%.4f", combined)}x (${activeModifiers.size} modifiers active)", gray)
            }
            
            "remove" -> {
                if (args.size < 3) {
                    MessageFormatter.commandError(ctx, "Usage: /ll testmeta modifier remove <id>")
                    return
                }
                
                val modifierId = args[2]
                val removed = service.removeDepletionModifier(playerId, modifierId)
                
                if (removed) {
                    val activeModifiers = service.getActiveModifiers(playerId)
                    val combined = if (activeModifiers.isEmpty()) 1.0 else activeModifiers.values.fold(1.0) { acc, m -> acc * m }
                    
                    MessageFormatter.commandRaw(ctx, "[Test] Removed modifier '$modifierId'", green)
                    MessageFormatter.commandRaw(ctx, "Combined multiplier: ${String.format("%.4f", combined)}x (${activeModifiers.size} modifiers active)", gray)
                } else {
                    MessageFormatter.commandError(ctx, "Modifier '$modifierId' not found")
                }
            }
            
            "list" -> {
                val activeModifiers = service.getActiveModifiers(playerId)
                
                if (activeModifiers.isEmpty()) {
                    MessageFormatter.commandRaw(ctx, "[Test] No active depletion modifiers", gray)
                } else {
                    val combined = activeModifiers.values.fold(1.0) { acc, m -> acc * m }
                    
                    MessageFormatter.commandRaw(ctx, "[Test] Active Depletion Modifiers:", yellow)
                    activeModifiers.forEach { (id, multiplier) ->
                        val percentage = (1.0 - multiplier) * 100
                        val sign = if (percentage > 0) "-" else "+"
                        MessageFormatter.commandRaw(ctx, "  - $id: ${multiplier}x ($sign${String.format("%.1f", Math.abs(percentage))}%)", gray)
                    }
                    MessageFormatter.commandRaw(ctx, "Combined: ${String.format("%.4f", combined)}x depletion rate", gray)
                }
            }
            
            "clear" -> {
                service.clearDepletionModifiers(playerId)
                MessageFormatter.commandRaw(ctx, "[Test] Cleared all depletion modifiers", green)
                MessageFormatter.commandRaw(ctx, "Depletion rate reset to normal (1.0x)", gray)
            }
            
            else -> {
                MessageFormatter.commandError(ctx, "Unknown action: $action")
                MessageFormatter.commandRaw(ctx, "Valid actions: add, remove, list, clear", gray)
            }
        }
    }
    
    private fun sendUsage(ctx: CommandContext) {
        MessageFormatter.commandRaw(ctx, "[Test] Metabolism API Test Commands:", yellow)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta restore <hunger|thirst|energy> <amount>", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier add <id> <multiplier>", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier remove <id>", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier list", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier clear", gray)
        MessageFormatter.commandRaw(ctx, "", gray)
        MessageFormatter.commandRaw(ctx, "Examples:", yellow)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta restore energy 25", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier add professions:survivalist 0.85", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier add buff:endurance 0.90", gray)
        MessageFormatter.commandRaw(ctx, "  /ll testmeta modifier list", gray)
    }
    
    /**
     * Get the player UUID from the command context.
     * Returns null if sender is not a player.
     */
    private fun getPlayerUuid(ctx: CommandContext): UUID? {
        val entityRef = ctx.senderAsPlayerRef() ?: return null
        val session = CoreModule.players.getAllSessions().find { it.entityRef == entityRef }
        return session?.playerId
    }
}
