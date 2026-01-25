package com.livinglands.modules.groups.config

import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig

/**
 * Configuration for the groups system.
 * Loaded from groups.yml
 * 
 * Defines group limits, permissions, and chat settings.
 */
data class GroupsConfig(
    /**
     * Configuration version for migration support.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /** Master enable/disable for the entire groups system */
    val enabled: Boolean = true,
    
    /** Group creation and management settings */
    val groups: GroupSettingsConfig = GroupSettingsConfig(),
    
    /** Group chat configuration */
    val chat: ChatConfig = ChatConfig(),
    
    /** Invite system settings */
    val invites: InvitesConfig = InvitesConfig()
    
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION, enabled = true)
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 1
        
        /** Config module ID for migration registry */
        const val MODULE_ID = "groups"
        
        /**
         * Get all migrations for GroupsConfig.
         */
        fun getMigrations(): List<ConfigMigration> = emptyList()
    }
}

/**
 * Group settings configuration.
 */
data class GroupSettingsConfig(
    /** Maximum members per group */
    val maxMembers: Int = 20,
    
    /** Minimum group name length */
    val minNameLength: Int = 3,
    
    /** Maximum group name length */
    val maxNameLength: Int = 16,
    
    /** Allow group tags (e.g., [CLAN]) */
    val enableTags: Boolean = true,
    
    /** Maximum tag length */
    val maxTagLength: Int = 5,
    
    /** Allow group descriptions */
    val enableDescriptions: Boolean = true,
    
    /** Maximum description length */
    val maxDescriptionLength: Int = 100
)

/**
 * Group chat configuration.
 */
data class ChatConfig(
    /** Enable group chat channel */
    val enabled: Boolean = true,
    
    /** Chat prefix for group messages */
    val prefix: String = "[G]",
    
    /** Show group tag in player chat (requires enableTags) */
    val showTagInChat: Boolean = true,
    
    /** Chat command shortcut (e.g., /g message) */
    val commandShortcut: String = "g"
)

/**
 * Invite system settings.
 */
data class InvitesConfig(
    /** Invite expiration time in seconds */
    val expirationSeconds: Int = 300,  // 5 minutes
    
    /** Maximum pending invites per player */
    val maxPendingInvites: Int = 5,
    
    /** Require confirmation before joining */
    val requireConfirmation: Boolean = true
)
