package com.livinglands.modules.announcer

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.logging.LoggingManager
import com.livinglands.modules.announcer.commands.BroadcastCommand
import com.livinglands.modules.announcer.config.AnnouncerConfig
import com.livinglands.modules.announcer.config.PlaceholderResolver

/**
 * Announcer module for server messages.
 * Handles MOTD, welcome messages, and recurring announcements.
 */
class AnnouncerModule : AbstractModule(
    id = "announcer",
    name = "Announcer",
    version = "1.0.0",
    dependencies = emptySet()
) {
    private lateinit var config: AnnouncerConfig
    private lateinit var joinTracker: JoinTracker
    private lateinit var placeholderResolver: PlaceholderResolver
    private lateinit var service: AnnouncerService
    private lateinit var scheduler: AnnouncerScheduler
    
    override suspend fun onSetup() {
        LoggingManager.info(logger, "announcer") { "Setting up AnnouncerModule..." }
        
        // Register migrations first
        CoreModule.config.registerMigrations(
            AnnouncerConfig.MODULE_ID,
            emptyList()  // Version 1, no migrations yet
        )
        
        // Load config with migration support
        config = CoreModule.config.loadWithMigration(
            AnnouncerConfig.MODULE_ID,
            AnnouncerConfig(),
            AnnouncerConfig.CURRENT_VERSION
        )
        LoggingManager.info(logger, "announcer") { "Loaded announcer config (version ${config.configVersion})" }
        
        // Initialize components
        joinTracker = JoinTracker()
        placeholderResolver = PlaceholderResolver(serverName = "Living Lands")
        service = AnnouncerService(config, joinTracker, placeholderResolver, logger)
        scheduler = AnnouncerScheduler(config, service, placeholderResolver, logger)
        
        // Register services
        CoreModule.services.register(service)
        CoreModule.services.register(joinTracker)
        
        // Register commands using AbstractModule helper
        registerCommand(BroadcastCommand())
        
        // Register event handlers
        context.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            try {
                handlePlayerReady(event)
            } catch (e: Exception) {
                LoggingManager.error(logger, "announcer", e) { 
                    "Error handling PlayerReadyEvent in AnnouncerModule"
                }
            }
        }
        
        context.eventRegistry.register(PlayerDisconnectEvent::class.java) { event ->
            try {
                handlePlayerDisconnect(event)
            } catch (e: Exception) {
                LoggingManager.error(logger, "announcer", e) { 
                    "Error handling PlayerDisconnectEvent in AnnouncerModule"
                }
            }
        }
        
        // Register config reload callback
        CoreModule.config.onReload(AnnouncerConfig.MODULE_ID) {
            onConfigReloaded()
        }
        
        LoggingManager.info(logger, "announcer") { "AnnouncerModule setup complete" }
    }
    
    /**
     * Handle config reload (called by ConfigManager).
     * Runs in regular context (not suspend), so we can directly update config.
     */
    private fun onConfigReloaded() {
        LoggingManager.info(logger, "announcer") { "Reloading announcer config..." }
        
        // Reload config
        config = CoreModule.config.get(AnnouncerConfig.MODULE_ID) ?: AnnouncerConfig()
        
        // Restart scheduler with new config
        scheduler.stop()
        scheduler = AnnouncerScheduler(config, service, placeholderResolver, logger)
        scheduler.start()
        
        LoggingManager.info(logger, "announcer") { "Announcer config reloaded, scheduler restarted" }
    }
    
    override suspend fun onStart() {
        LoggingManager.info(logger, "announcer") { "Starting AnnouncerModule..." }
        scheduler.start()
        LoggingManager.info(logger, "announcer") { "AnnouncerModule started" }
    }
    
    override suspend fun onShutdown() {
        LoggingManager.info(logger, "announcer") { "Shutting down AnnouncerModule..." }
        scheduler.stop()
        LoggingManager.info(logger, "announcer") { "AnnouncerModule shutdown complete" }
    }
    
    /**
     * Handles player ready event (player joined the server).
     */
    @Suppress("DEPRECATION")
    private fun handlePlayerReady(event: PlayerReadyEvent) {
        val player = event.player
        val playerId = player.getUuid() ?: return
        val playerRef = player.getPlayerRef() ?: return
        
        // Record join and get info
        val joinInfo = joinTracker.recordJoin(playerId)
        
        // Send MOTD immediately (no delay)
        service.sendMotd(playerRef)
        
        // Send welcome message
        service.sendWelcomeMessage(playerRef, joinInfo)
        
        LoggingManager.debug(logger, "announcer") { 
            "Sent welcome messages to ${playerRef.username} (firstJoin=${joinInfo.isFirstJoin}, visitCount=${joinInfo.joinCount})"
        }
    }
    
    /**
     * Handles player disconnect event.
     * Join tracker automatically updates lastSeen on next join, so no action needed here.
     */
    private fun handlePlayerDisconnect(event: PlayerDisconnectEvent) {
        // Join tracker automatically updates lastSeen on next join
        // No action needed here
    }
}
