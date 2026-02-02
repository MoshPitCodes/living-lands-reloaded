package com.livinglands.core.hud

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.modules.metabolism.buffs.BuffsSystem
import com.livinglands.modules.metabolism.buffs.DebuffsSystem
import com.livinglands.modules.professions.ProfessionsService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the unified Living Lands HUD for all players.
 * 
 * ## Architecture Decision
 * 
 * **IMPORTANT:** Hytale's CustomUI system can only handle ONE append() call per HudElement.
 * Therefore, we use a SINGLE unified LivingLandsHudElement per player that contains
 * ALL Living Lands UI elements (metabolism, professions, progress panels, etc.).
 * 
 * This unified HUD approach was chosen over composite patterns (like MHUD) for:
 * - **Simplicity:** No reflection, no CSS selector prefixing, standard Hytale API
 * - **Performance:** Direct method calls, no reflection overhead
 * - **Security:** No reflection risks, no SecurityManager concerns
 * - **Reliability:** Won't break on Hytale API changes (no reflection on private methods)
 * - **Maintainability:** Easier to understand, test, and debug
 * 
 * See `docs/internal/architecture-decisions/ADR-001-unified-hud-pattern.md` for details.
 * 
 * ## Thread Safety
 * 
 * Uses `ConcurrentHashMap` for player tracking to support concurrent access.
 * However, **all HUD operations (register/remove/update) must occur on WorldThread**.
 * 
 * Caller is responsible for ensuring WorldThread execution via `world.execute { }`.
 * 
 * ## Usage
 * 
 * ```kotlin
 * // Register the unified HUD for a player (WorldThread required)
 * world.execute {
 *     val player = store.getComponent(ref, Player.getComponentType())
 *     CoreModule.hudManager.registerHud(
 *         player, playerRef, playerId, 
 *         buffsSystem, debuffsSystem, 
 *         professionsService, abilityRegistry
 *     )
 * }
 * 
 * // Get the HUD element to update it
 * val hud = CoreModule.hudManager.getHud(playerId)
 * hud?.updateMetabolism(hunger, thirst, energy)
 * hud?.updateMetabolismHud()
 * 
 * // Remove when player disconnects
 * CoreModule.hudManager.removeHud(player, playerRef, playerId)
 * ```
 * 
 * @see LivingLandsHudElement
 */
class MultiHudManager(
    @PublishedApi
    internal val logger: HytaleLogger
) {
    
    /** Track unified HUDs by player UUID */
    private val playerHuds = ConcurrentHashMap<UUID, LivingLandsHudElement>()
    
    /** Track Player/PlayerRef pairs for cleanup */
    private val playerRefs = ConcurrentHashMap<UUID, Pair<Player, PlayerRef>>()
    
    /**
     * Register the unified HUD for a player.
     * 
     * Creates a new LivingLandsHudElement and sets it as the player's custom HUD.
     * This is the ONLY HUD element - all Living Lands UI is managed through it.
     * 
     * @param player The player entity
     * @param playerRef The player reference
     * @param playerId Player's UUID
     * @param buffsSystem Optional buffs system for buff display
     * @param debuffsSystem Optional debuffs system for debuff display
     * @param professionsService Optional professions service for profession data
     * @param abilityRegistry Optional ability registry for ability data
     */
    fun registerHud(
        player: Player,
        playerRef: PlayerRef,
        playerId: UUID,
        buffsSystem: BuffsSystem? = null,
        debuffsSystem: DebuffsSystem? = null,
        professionsService: ProfessionsService? = null,
        abilityRegistry: AbilityRegistry? = null
    ) {
        logger.atFine().log("=== MultiHudManager.registerHud() called ===")
        logger.atFine().log("Player UUID: $playerId")
        
        // Store refs for cleanup
        playerRefs[playerId] = Pair(player, playerRef)
        
        // Create the unified HUD element
        val hudElement = LivingLandsHudElement(
            playerRef = playerRef,
            playerId = playerId,
            buffsSystem = buffsSystem,
            debuffsSystem = debuffsSystem,
            professionsService = professionsService,
            abilityRegistry = abilityRegistry
        )
        
        // Store the HUD element
        playerHuds[playerId] = hudElement
        
        // Set it as the player's custom HUD
        try {
            val hudManager = player.hudManager
            hudManager.setCustomHud(playerRef, hudElement)
            hudElement.show()
            
            // NOTE: Initial data population will happen on the next metabolism tick
            // (every 2 seconds). We don't populate immediately because the client
            // needs time to process the append() command and create the UI elements.
            
            logger.atFine().log("Registered unified HUD for player $playerId (initial data will populate on next tick)")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Failed to register unified HUD for player $playerId")
        }
        
        logger.atFine().log("=== MultiHudManager.registerHud() complete ===")
    }
    
    /**
     * Get the unified HUD element for a player.
     * 
     * @param playerId The player's UUID
     * @return The HUD element, or null if not registered
     */
    fun getHud(playerId: UUID): LivingLandsHudElement? {
        return playerHuds[playerId]
    }
    
    /**
     * Check if a player has a registered HUD.
     * 
     * @param playerId The player's UUID
     * @return true if the player has a registered HUD
     */
    fun hasHud(playerId: UUID): Boolean {
        return playerHuds.containsKey(playerId)
    }
    
    /**
     * Update profession services for all registered HUDs.
     * Call this when ProfessionsModule starts up.
     * 
     * @param service The ProfessionsService instance
     * @param registry The AbilityRegistry instance
     */
    fun setProfessionServicesForAll(service: ProfessionsService, registry: AbilityRegistry) {
        logger.atFine().log("Updating profession services for ${playerHuds.size} registered HUDs")
        playerHuds.values.forEach { hud ->
            hud.setProfessionServices(service, registry)
        }
    }
    
    /**
     * Clear profession services from all registered HUDs.
     * Call this when ProfessionsModule is disabled via config hot-reload.
     * 
     * This disables profession panels in the HUD but keeps the HUD itself active.
     */
    fun clearProfessionServicesForAll() {
        logger.atFine().log("Clearing profession services for ${playerHuds.size} registered HUDs")
        playerHuds.values.forEach { hud ->
            hud.clearProfessionServices()
        }
    }
    
    /**
     * Clear metabolism services from all registered HUDs.
     * Call this when MetabolismModule is disabled via config hot-reload.
     * 
     * This hides metabolism stats, buffs, and debuffs in the HUD but keeps the HUD itself active.
     */
    fun clearMetabolismServicesForAll() {
        logger.atFine().log("Clearing metabolism services for ${playerHuds.size} registered HUDs")
        playerHuds.values.forEach { hud ->
            hud.clearMetabolismServices()
        }
    }
    
    /**
     * Remove the HUD for a player.
     * 
     * **Thread Safety:** Must be called from WorldThread
     * 
     * @param player The player entity
     * @param playerRef The player reference
     * @param playerId Player's UUID
     */
    fun removeHud(player: Player, playerRef: PlayerRef, playerId: UUID) {
        logger.atFine().log("Removing HUD for player $playerId")
        
        try {
            val hudManager = player.hudManager
            hudManager.setCustomHud(playerRef, null)
            logger.atFine().log("Removed HUD for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to remove HUD for player $playerId")
        } finally {
            // ALWAYS clean up maps, even if setCustomHud() fails
            // This prevents memory leaks from failed HUD removal
            playerHuds.remove(playerId)
            playerRefs.remove(playerId)
        }
    }
    
    /**
     * Refresh a player's HUD.
     * Call this after updating HUD state to push changes to the client.
     * 
     * @param player The player entity
     * @param playerRef The player reference
     */
    fun refreshHud(player: Player, playerRef: PlayerRef) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        val hud = playerHuds[playerId] ?: return
        
        try {
            hud.show()
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to refresh HUD for player $playerId")
        }
    }
    
    /**
     * Clean up when a player disconnects.
     * This should be called from a PlayerDisconnectEvent handler.
     * 
     * @param playerId The UUID of the disconnecting player
     */
    fun onPlayerDisconnect(playerId: UUID) {
        playerHuds.remove(playerId)
        playerRefs.remove(playerId)
        logger.atFine().log("Cleaned up HUD state for disconnecting player $playerId")
    }
    
    /**
     * Clear all state. Called during shutdown.
     */
    fun clear() {
        playerHuds.clear()
        playerRefs.clear()
        logger.atFine().log("MultiHudManager cleared")
    }
    
    // ============ Legacy Compatibility Methods ============
    // These methods exist for backward compatibility with the old composite pattern.
    // They delegate to the unified HUD element.
    
    /**
     * Legacy method - use registerHud() instead.
     * 
     * This method is kept for backward compatibility but internally just
     * logs a warning since the composite pattern is no longer used.
     */
    @Deprecated("Use registerHud() instead. The composite HUD pattern is no longer used.")
    fun setHud(
        player: Player,
        playerRef: PlayerRef,
        namespace: String,
        hud: com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
    ) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        
        logger.atWarning().log(
            "setHud() called with namespace '$namespace' for player $playerId. " +
            "This method is deprecated - the unified HUD should already be registered via registerHud(). " +
            "Individual HUD elements are no longer supported."
        )
        
        // If the unified HUD isn't registered yet and this is a LivingLandsHudElement, register it
        if (!playerHuds.containsKey(playerId) && hud is LivingLandsHudElement) {
            playerHuds[playerId] = hud
            playerRefs[playerId] = Pair(player, playerRef)
            
            try {
                val hudManager = player.hudManager
                hudManager.setCustomHud(playerRef, hud)
                hud.show()
                logger.atFine().log("Registered unified HUD via legacy setHud() for player $playerId")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to register HUD via legacy setHud() for player $playerId")
            }
        }
    }
    
    /**
     * Legacy method - use removeHud(player, playerRef, playerId) instead.
     */
    @Deprecated("Use removeHud(player, playerRef, playerId) instead. Namespaces are no longer used.")
    fun removeHud(player: Player, playerRef: PlayerRef, namespace: String) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        
        logger.atWarning().log(
            "removeHud() called with namespace '$namespace' for player $playerId. " +
            "Namespace is ignored - removing the unified HUD."
        )
        
        removeHud(player, playerRef, playerId)
    }
    
    /**
     * Legacy method - always returns null since we use unified HUD.
     */
    @Deprecated("The unified HUD doesn't use namespaces. Use getHud(playerId) instead.")
    @Suppress("UNCHECKED_CAST")
    fun <T : com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud> getHud(
        playerId: UUID,
        namespace: String
    ): T? {
        // For backward compatibility, if they ask for the old metabolism namespace,
        // return the unified HUD (it contains metabolism functionality)
        return when (namespace) {
            LivingLandsHudElement.NAMESPACE,
            "livinglands:metabolism",
            "livinglands:professions",
            "livinglands:progress" -> playerHuds[playerId] as? T
            else -> null
        }
    }
    
    /**
     * Legacy method - always returns false for specific namespaces.
     */
    @Deprecated("The unified HUD doesn't use namespaces. Use hasHud(playerId) instead.")
    fun hasHud(playerId: UUID, namespace: String): Boolean {
        return hasHud(playerId)
    }
    
    /**
     * Legacy method - returns the unified HUD namespace.
     */
    @Deprecated("The unified HUD doesn't use namespaces.")
    fun getNamespaces(playerId: UUID): Set<String> {
        return if (playerHuds.containsKey(playerId)) {
            setOf(LivingLandsHudElement.NAMESPACE)
        } else {
            emptySet()
        }
    }
    
    /**
     * Legacy method - no longer needed with unified HUD.
     */
    @Deprecated("Composite integrity verification is no longer needed with unified HUD.")
    fun verifyAndRestoreComposite(player: Player, playerRef: PlayerRef): Boolean {
        return true
    }
    
    /**
     * Legacy method - no longer needed with unified HUD.
     */
    @Deprecated("Composite integrity verification is no longer needed with unified HUD.")
    fun verifyAllComposites(): Int {
        return 0
    }
    
    /**
     * Legacy method - no-op with unified HUD.
     */
    @Deprecated("clearAllHuds is deprecated. Use removeHud() instead.")
    fun clearAllHuds(player: Player, playerRef: PlayerRef) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        removeHud(player, playerRef, playerId)
    }
    
    /**
     * Legacy method - returns unified HUD.
     */
    @Deprecated("updateHud is deprecated. Get the HUD and call methods on it directly.")
    @Suppress("DEPRECATION")
    inline fun <reified T : com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud> updateHud(
        playerId: UUID,
        namespace: String,
        update: (T) -> Unit
    ): Boolean {
        val hud = getHud(playerId) ?: return false
        
        return if (hud is T) {
            update(hud)
            true
        } else {
            false
        }
    }
}
