package com.livinglands.modules.announcer.config

import com.livinglands.core.config.VersionedConfig

/**
 * Configuration for the Announcer module.
 * Supports MOTD, welcome messages, and recurring announcements.
 */
data class AnnouncerConfig(
    override val configVersion: Int = CURRENT_VERSION,
    val enabled: Boolean = true,
    val motd: MotdConfig = MotdConfig(),
    val welcome: WelcomeConfig = WelcomeConfig(),
    val recurring: RecurringConfig = RecurringConfig(),
    val worldOverrides: Map<String, WorldOverride> = emptyMap()
) : VersionedConfig {
    companion object {
        const val CURRENT_VERSION = 1
        const val MODULE_ID = "announcer"
    }
}

/**
 * Message of the Day configuration.
 * Sent immediately when a player joins the server.
 */
data class MotdConfig(
    val enabled: Boolean = true,
    val message: String = "&6Welcome to {server_name}!"
)

/**
 * Welcome message configuration.
 * Differentiates between first-time joins and returning players.
 */
data class WelcomeConfig(
    val enabled: Boolean = true,
    val firstJoin: String = "&aWelcome for the first time, {player_name}!",
    val returning: String = "&7Welcome back, {player_name}! (Visit #{join_count})",
    val minAbsenceDuration: String = "1h"  // Don't show "welcome back" if player was just online
)

/**
 * Recurring announcements configuration.
 * Supports multiple concurrent announcement schedules.
 */
data class RecurringConfig(
    val enabled: Boolean = true,
    val announcements: List<AnnouncementConfig> = listOf(
        AnnouncementConfig(
            id = "tips",
            enabled = true,
            interval = "5m",
            messages = listOf(
                "&6[Tip] Stay hydrated! Thirst depletes faster than hunger.",
                "&6[Tip] Sprinting uses 3x more energy.",
                "&6[Tip] Cooked food restores more stats than raw."
            ),
            target = "all"
        ),
        AnnouncementConfig(
            id = "discord",
            enabled = true,
            interval = "10m",
            messages = listOf(
                "&bJoin our Discord: &fdiscord.gg/example"
            ),
            target = "all"
        )
    )
)

/**
 * Individual recurring announcement configuration.
 */
data class AnnouncementConfig(
    val id: String,
    val enabled: Boolean = true,
    val interval: String,  // "5m", "1h", "30s", etc.
    val messages: List<String>,
    val target: String = "all"  // "all" or "world:WorldName"
)

/**
 * Per-world announcement overrides.
 * Allows different messages for different worlds (e.g., creative vs survival).
 */
data class WorldOverride(
    val motd: MotdConfig? = null,
    val welcome: WelcomeConfig? = null,
    val recurring: RecurringConfig? = null
)
