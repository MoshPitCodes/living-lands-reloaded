package com.livinglands.core.config

/**
 * Interface for configurations that support versioning and migration.
 * 
 * Config classes implementing this interface can be automatically migrated
 * when breaking changes are introduced in new plugin versions.
 * 
 * Example:
 * ```kotlin
 * data class MyConfig(
 *     override val configVersion: Int = 2,
 *     val someSetting: String = "default"
 * ) : VersionedConfig
 * ```
 */
interface VersionedConfig {
    /**
     * The version of this configuration structure.
     * 
     * Increment this when making breaking changes to the config schema.
     * Provide a migration from old version to new version to preserve user customizations.
     */
    val configVersion: Int
}
