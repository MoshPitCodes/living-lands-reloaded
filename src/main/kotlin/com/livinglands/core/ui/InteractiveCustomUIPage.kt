package com.livinglands.core.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import javax.annotation.Nonnull

/**
 * Base class for interactive custom UI pages with event handling.
 * 
 * This is a simplified version adapted to match Hytale's actual CustomUIPage API.
 * Use this for pages with buttons, forms, or other interactive elements.
 * 
 * For simple static pages, use [BasicCustomUIPage].
 * 
 * ## Event Binding Pattern
 * 
 * Bind buttons to events and capture input values:
 * ```kotlin
 * override fun build(ref: Ref<EntityStore>, cmd: UICommandBuilder, 
 *                    evt: UIEventBuilder, store: Store<EntityStore>) {
 *     cmd.append("Pages/FormPage.ui")
 *     
 *     // Button with event binding
 *     evt.addEventBinding(
 *         CustomUIEventBindingType.Activating,
 *         "#SaveButton",
 *         EventData().append("action", "save")
 *     )
 * }
 * ```
 * 
 * ## Handling Events
 * 
 * Process the event data string and close the page:
 * ```kotlin
 * override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, 
 *                               eventData: String) {
 *     // Parse event data (JSON string, pipe-delimited, etc.)
 *     if (eventData.contains("save")) {
 *         playerRef.sendMessage(Message.raw("Saved!"))
 *     }
 *     
 *     // Close the page
 *     val player = store.getComponent(ref, Player.getComponentType())
 *     player.pageManager.setPage(ref, store, Page.None)
 * }
 * ```
 * 
 * @param playerRef The player this page is for
 * @param lifetime Controls how the page can be closed
 */
abstract class InteractiveCustomUIPage(
    @Nonnull playerRef: PlayerRef,
    lifetime: CustomPageLifetime = CustomPageLifetime.CanDismissOrCloseThroughInteraction
) : CustomUIPage(playerRef, lifetime) {
    
    /**
     * Build the UI and register event handlers.
     * 
     * Use [UICommandBuilder] to load UI and set values.
     * Use [UIEventBuilder] to bind button clicks to events.
     * 
     * @param ref Entity reference for this player
     * @param cmd UICommandBuilder for loading UI and setting values
     * @param evt UIEventBuilder for registering event bindings
     * @param store EntityStore for accessing components
     */
    @Nonnull
    abstract override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    )
    
    /**
     * Handle an event from the UI.
     * 
     * This is called when a button is clicked or form is submitted.
     * The [eventData] parameter contains the event data as a String.
     * 
     * Remember to close the page when done:
     * ```kotlin
     * val player = store.getComponent(ref, Player.getComponentType())
     * player.pageManager.setPage(ref, store, Page.None)
     * ```
     * 
     * @param ref Entity reference for this player
     * @param store EntityStore for accessing components
     * @param eventData The event data as a String
     */
    @Nonnull
    abstract override fun handleDataEvent(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull eventData: String
    )
}
