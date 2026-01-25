package com.livinglands.api

import com.hypixel.hytale.component.ComponentRegistryProxy
import com.hypixel.hytale.event.EventRegistry
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.CommandRegistry
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.LivingLandsReloadedPlugin
import java.nio.file.Path

/**
 * Context provided to modules during setup.
 * Contains all necessary references for module initialization.
 * 
 * This is an immutable data class - modules should not modify these references.
 */
data class ModuleContext(
    /** Reference to the main plugin instance */
    val plugin: LivingLandsReloadedPlugin,
    
    /** Logger for module-specific logging */
    val logger: HytaleLogger,
    
    /** Plugin's data directory (for storing data files) */
    val dataDir: Path,
    
    /** Plugin's config directory (for YAML configs) */
    val configDir: Path,
    
    /** Event registry for registering event listeners */
    val eventRegistry: EventRegistry,
    
    /** Command registry for registering commands */
    val commandRegistry: CommandRegistry,
    
    /** Entity store registry for registering ECS systems */
    val entityStoreRegistry: ComponentRegistryProxy<EntityStore>
)
