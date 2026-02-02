package com.livinglands.core.commands

import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase
import com.livinglands.core.MessageFormatter

/**
 * Parent command for scanning registries.
 * 
 * Usage: /ll scan <subcommand>
 * 
 * Subcommands:
 * - consumables: Scan Item registry for unrecognized consumable items
 * 
 * This is a container command for extensibility - future scans can be added here.
 */
class ScanCommand : CommandBase(
    "scan",
    "Scan registries for unrecognized items",
    true  // Operator only
) {
    
    init {
        // Add subcommands
        addSubCommand(com.livinglands.modules.metabolism.commands.ScanConsumablesCommand())
    }
    
    override fun executeSync(ctx: CommandContext) {
        // Show help when /ll scan is typed without subcommand
        MessageFormatter.commandRaw(ctx, "=== Living Lands Registry Scanner ===", java.awt.Color(255, 170, 0))
        MessageFormatter.commandRaw(ctx, "", java.awt.Color(170, 170, 170))
        MessageFormatter.commandRaw(ctx, "Available scans:", java.awt.Color(255, 170, 0))
        MessageFormatter.commandRaw(ctx, "/ll scan consumables [--save] [--section Name]", java.awt.Color(85, 255, 255))
        MessageFormatter.commandRaw(ctx, "  Scan Item registry for new consumable items", java.awt.Color(170, 170, 170))
        MessageFormatter.commandRaw(ctx, "", java.awt.Color(170, 170, 170))
        MessageFormatter.commandRaw(ctx, "Options:", java.awt.Color(255, 170, 0))
        MessageFormatter.commandRaw(ctx, "  --save        Save discovered items to config", java.awt.Color(170, 170, 170))
        MessageFormatter.commandRaw(ctx, "  --section     Custom section name (default: ManualScan_YYYY-MM-DD)", java.awt.Color(170, 170, 170))
    }
}
