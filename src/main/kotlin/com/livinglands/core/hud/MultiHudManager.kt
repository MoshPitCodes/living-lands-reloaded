package com.livinglands.core.hud

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages multiple HUD elements per player using a composite pattern.
 * 
 * Hytale's HudManager only supports a single CustomUIHud per player at a time.
 * When one module sets a custom HUD, it overwrites any existing HUD from other modules.
 * 
 * This manager implements the MHUD pattern (based on Buuz135's MHUD) to combine
 * multiple HUD elements into a single CompositeHud that delegates to all registered elements.
 * 
 * Usage:
 * ```kotlin
 * // Register a HUD element
 * CoreModule.hudManager.setHud(player, playerRef, "mymod:myhud", myHudElement)
 * 
 * // Remove a HUD element
 * CoreModule.hudManager.removeHud(player, playerRef, "mymod:myhud")
 * ```
 * 
 * @see CompositeHud
 */
class MultiHudManager(
    private val logger: HytaleLogger
) {
    
    /** Track composite HUDs by player UUID for cleanup on disconnect */
    private val playerHuds = ConcurrentHashMap<UUID, CompositeHud>()
    
    /** Track individual HUD elements by player UUID and namespace for updates */
    private val hudElements = ConcurrentHashMap<UUID, ConcurrentHashMap<String, CustomUIHud>>()
    
    /**
     * Reflection cache for calling the protected build() method on CustomUIHud.
     * This is needed because build() is protected but we need to call it on child HUDs.
     */
    private val buildMethod: Method? by lazy {
        try {
            CustomUIHud::class.java.getDeclaredMethod(
                "build",
                UICommandBuilder::class.java
            ).apply {
                isAccessible = true
            }
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to get CustomUIHud.build() method via reflection")
            null
        }
    }
    
    /**
     * Register a HUD element for a player.
     * 
     * If the player already has a CompositeHud, the element is added to it.
     * If not, a new CompositeHud is created to wrap all HUD elements.
     * 
     * @param player The player entity (used to access HudManager)
     * @param playerRef The player reference (needed for CustomUIHud constructor)
     * @param namespace Unique identifier for this HUD (e.g., "livinglands:metabolism")
     * @param hud The CustomUIHud implementation to register
     */
    fun setHud(
        player: Player,
        playerRef: PlayerRef,
        namespace: String,
        hud: CustomUIHud
    ) {
        logger.atInfo().log("=== MultiHudManager.setHud() called ===")
        logger.atInfo().log("Player: $player")
        logger.atInfo().log("PlayerRef: $playerRef")
        logger.atInfo().log("Namespace: $namespace")
        logger.atInfo().log("HUD: ${hud.javaClass.name}")
        
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        logger.atInfo().log("Player UUID: $playerId")
        
        val hudManager = player.hudManager
        logger.atInfo().log("HudManager: ${hudManager.javaClass.name}")
        
        // Track the element for later access
        val playerElements = hudElements.getOrPut(playerId) { ConcurrentHashMap() }
        playerElements[namespace] = hud
        logger.atInfo().log("Tracked HUD element")
        
        // Get or create composite HUD
        val existingComposite = playerHuds[playerId]
        logger.atInfo().log("Existing composite: $existingComposite")
        
        if (existingComposite != null) {
            // Add to existing composite
            logger.atInfo().log("Adding to existing composite")
            existingComposite.addHud(namespace, hud)
            // Refresh the composite to show new element
            try {
                hudManager.setCustomHud(playerRef, existingComposite)
                existingComposite.show()
                logger.atInfo().log("Refreshed composite HUD")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to refresh composite HUD for player $playerId")
            }
        } else {
            // Check for existing non-composite HUD
            logger.atInfo().log("Creating new composite HUD")
            val existingHud = hudManager.customHud
            logger.atInfo().log("Existing HUD: $existingHud")
            
            val huds = ConcurrentHashMap<String, CustomUIHud>()
            huds[namespace] = hud
            
            // Preserve any existing external HUD
            if (existingHud != null && existingHud !is CompositeHud) {
                huds["_external"] = existingHud
                logger.atInfo().log("Preserved external HUD")
            }
            
            // Create new composite
            val composite = CompositeHud(playerRef, huds, buildMethod, logger)
            playerHuds[playerId] = composite
            logger.atInfo().log("Created composite: $composite")
            
            try {
                logger.atInfo().log("Setting custom HUD...")
                hudManager.setCustomHud(playerRef, composite)
                logger.atInfo().log("Showing composite...")
                composite.show()
                logger.atInfo().log("Created composite HUD for player $playerId with namespace: $namespace")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to set composite HUD for player $playerId")
                e.printStackTrace()
            }
        }
        logger.atInfo().log("=== MultiHudManager.setHud() complete ===")
    }
    
    /**
     * Remove a HUD element by namespace.
     * 
     * If this was the last HUD element, the composite is removed entirely.
     * 
     * @param player The player entity
     * @param playerRef The player reference
     * @param namespace The namespace of the HUD to remove
     */
    fun removeHud(player: Player, playerRef: PlayerRef, namespace: String) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        val hudManager = player.hudManager
        
        // Remove from element tracking
        hudElements[playerId]?.remove(namespace)
        
        val composite = playerHuds[playerId] ?: return
        composite.removeHud(namespace)
        
        if (composite.isEmpty()) {
            // No more HUDs, clear completely
            try {
                hudManager.setCustomHud(playerRef, null)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to clear HUD for player $playerId")
            }
            playerHuds.remove(playerId)
            hudElements.remove(playerId)
            logger.atFine().log("Removed all HUDs for player $playerId")
        } else {
            // Refresh the composite without the removed element
            try {
                hudManager.setCustomHud(playerRef, composite)
                composite.show()
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to refresh composite HUD for player $playerId")
            }
        }
    }
    
    /**
     * Clear all HUD elements for a player.
     * 
     * @param player The player entity
     * @param playerRef The player reference
     */
    fun clearAllHuds(player: Player, playerRef: PlayerRef) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        val hudManager = player.hudManager
        
        // Clear tracking
        hudElements.remove(playerId)
        playerHuds.remove(playerId)
        
        // Clear the HUD
        try {
            hudManager.setCustomHud(playerRef, null)
            logger.atFine().log("Cleared all HUDs for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to clear HUDs for player $playerId")
        }
    }
    
    /**
     * Check if a HUD namespace is registered for a player.
     * 
     * @param playerId The player's UUID
     * @param namespace The namespace to check
     * @return true if the namespace is registered
     */
    fun hasHud(playerId: UUID, namespace: String): Boolean {
        return hudElements[playerId]?.containsKey(namespace) == true
    }
    
    /**
     * Get a specific HUD element by namespace.
     * 
     * @param playerId The player's UUID
     * @param namespace The namespace of the HUD
     * @return The HUD element, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : CustomUIHud> getHud(playerId: UUID, namespace: String): T? {
        return hudElements[playerId]?.get(namespace) as? T
    }
    
    /**
     * Get all registered HUD namespaces for a player.
     * 
     * @param playerId The player's UUID
     * @return Set of registered namespaces
     */
    fun getNamespaces(playerId: UUID): Set<String> {
        return hudElements[playerId]?.keys?.toSet() ?: emptySet()
    }
    
    /**
     * Clean up when a player disconnects.
     * This should be called from a PlayerDisconnectEvent handler.
     * 
     * @param playerId The UUID of the disconnecting player
     */
    fun onPlayerDisconnect(playerId: UUID) {
        playerHuds.remove(playerId)
        hudElements.remove(playerId)
        logger.atFine().log("Cleaned up HUD state for disconnecting player $playerId")
    }
    
    /**
     * Refresh a player's composite HUD.
     * Call this after updating HUD element state to push changes to the client.
     * 
     * @param player The player entity
     * @param playerRef The player reference
     */
    fun refreshHud(player: Player, playerRef: PlayerRef) {
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        val composite = playerHuds[playerId] ?: return
        
        try {
            composite.show()
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to refresh HUD for player $playerId")
        }
    }
    
    /**
     * Clear all state. Called during shutdown.
     */
    fun clear() {
        playerHuds.clear()
        hudElements.clear()
        logger.atFine().log("MultiHudManager cleared")
    }
}
