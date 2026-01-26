package com.livinglands.core.hud

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal composite HUD that delegates build() calls to multiple child HUDs.
 * 
 * This class extends CustomUIHud and combines the UI elements from all registered
 * child HUDs into a single unified HUD. When build() is called, it iterates through
 * all child HUDs and calls their build() methods via reflection.
 * 
 * Child HUDs are identified by namespace (e.g., "livinglands:metabolism").
 * The special namespace "_external" is used to preserve any pre-existing HUD
 * that was set before the composite was created.
 * 
 * Thread Safety: Uses ConcurrentHashMap for thread-safe HUD management.
 * 
 * @param playerRef The player this HUD is for
 * @param huds Initial map of namespace to HUD element
 * @param buildMethod Cached reflection method for calling protected build()
 * @param logger Logger for error reporting
 */
internal class CompositeHud(
    playerRef: PlayerRef,
    private val huds: ConcurrentHashMap<String, CustomUIHud>,
    private val buildMethod: Method?,
    private val logger: HytaleLogger
) : CustomUIHud(playerRef) {
    
    /**
     * Build the composite HUD by delegating to all child HUDs.
     * Each child HUD's build() method is called to append its UI elements.
     * 
     * If a child HUD fails to build, the error is logged but other HUDs
     * continue to build - one bad HUD shouldn't break the entire UI.
     */
    override fun build(builder: UICommandBuilder) {
        if (buildMethod == null) {
            logger.atWarning().log("CompositeHud: buildMethod is null, cannot delegate to child HUDs")
            return
        }
        
        // Delegate to all child HUDs - they append their own files
        huds.forEach { (namespace, hud) ->
            try {
                buildMethod.invoke(hud, builder)
            } catch (e: Exception) {
                // Log but don't crash - one bad HUD shouldn't break others
                logger.atWarning().withCause(e).log("Failed to build HUD '$namespace': ${e.message}")
            }
        }
    }
    
    /**
     * Add a HUD element to the composite.
     * 
     * @param namespace The unique namespace for this HUD
     * @param hud The HUD element to add
     */
    fun addHud(namespace: String, hud: CustomUIHud) {
        huds[namespace] = hud
    }
    
    /**
     * Remove a HUD element from the composite.
     * 
     * @param namespace The namespace of the HUD to remove
     * @return The removed HUD, or null if not found
     */
    fun removeHud(namespace: String): CustomUIHud? {
        return huds.remove(namespace)
    }
    
    /**
     * Check if a namespace is registered.
     */
    fun hasNamespace(namespace: String): Boolean {
        return huds.containsKey(namespace)
    }
    
    /**
     * Get all registered namespaces.
     */
    fun getNamespaces(): Set<String> {
        return huds.keys.toSet()
    }
    
    /**
     * Check if the composite has no HUDs.
     */
    fun isEmpty(): Boolean {
        return huds.isEmpty()
    }
    
    /**
     * Get the number of registered HUDs.
     */
    fun size(): Int {
        return huds.size
    }
}
