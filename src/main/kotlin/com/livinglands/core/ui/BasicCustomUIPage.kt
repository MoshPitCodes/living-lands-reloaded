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
 * Base class for simple, static custom UI pages.
 * 
 * This pattern is adapted from hytale-basic-uis repository (trouble-dev).
 * Use this for pages that display static information without user interaction.
 * 
 * Example usage:
 * ```kotlin
 * class InfoPage(playerRef: PlayerRef) : BasicCustomUIPage(playerRef) {
 *     override fun buildPage(cmd: UICommandBuilder) {
 *         cmd.append("Pages/InfoPage.ui")
 *         cmd.set("#Title.Text", "Server Info")
 *         cmd.set("#Value.Text", "42")  // Numbers as String
 *     }
 * }
 * 
 * // Open the page
 * val player = store.getComponent(ref, Player.getComponentType())
 * player.pageManager.openCustomPage(ref, store, InfoPage(playerRef))
 * ```
 * 
 * @param playerRef The player this page is for
 * @param lifetime Controls how the page can be closed (default: CanDismiss with ESC key)
 */
abstract class BasicCustomUIPage(
    @Nonnull playerRef: PlayerRef,
    lifetime: CustomPageLifetime = CustomPageLifetime.CanDismiss
) : CustomUIPage(playerRef, lifetime) {
    
    /**
     * Build method required by CustomUIPage API.
     * Delegates to [buildPage] for simple API without needing ref/store/event builder.
     */
    @Nonnull
    final override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    ) {
        buildPage(cmd)
    }
    
    /**
     * Build the UI for this page.
     * 
     * Use [UICommandBuilder] to:
     * 1. Load UI definition files: `cmd.append("Pages/MyPage.ui")`
     * 2. Set dynamic values: `cmd.set("#ElementId.Property", value)`
     * 
     * Element selectors use `#ElementId.Property` format:
     * - `#Title.Text` - Set text content
     * - `#Value.Text` - Set numeric/text values (must convert to String)
     * - `#CheckBox.Value` - Set checkbox state (must convert Boolean to String)
     * 
     * @param cmd UICommandBuilder for loading UI and setting values
     */
    @Nonnull
    protected abstract fun buildPage(@Nonnull cmd: UICommandBuilder)
}
