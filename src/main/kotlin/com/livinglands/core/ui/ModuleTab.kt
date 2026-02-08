package com.livinglands.core.ui

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder

/**
 * Interface defining the contract for a module tab in the central modal panel.
 *
 * Each module can register tabs that appear in the modal, allowing users to access
 * module-specific functionality through a unified interface.
 *
 * ## Implementation Example
 *
 * ```kotlin
 * class MetabolismTab : ModuleTab {
 *     override val tabId: String = "metabolism"
 *     override val tabLabel: String = "Metabolism"
 *
 *     override fun render(builder: UICommandBuilder) {
 *         builder.set("#TabContent.Text", "Metabolism stats here")
 *     }
 *
 *     override fun onActivate() {
 *         println("Metabolism tab activated")
 *     }
 *
 *     override fun onDeactivate() {
 *         println("Metabolism tab deactivated")
 *     }
 * }
 * ```
 *
 * ## Usage
 *
 * Register your tab with the central modal panel:
 *
 * ```kotlin
 * val panel = CentralModalPanel(playerRef)
 * panel.registerTab(MetabolismTab())
 * panel.registerTab(ProfessionsTab())
 * ```
 *
 * ## Content Rendering
 *
 * The [render] method is called when the tab is activated. Use [UICommandBuilder]
 * to populate the content area with tab-specific UI elements.
 *
 * Each tab has access to a dedicated content area in the modal panel where it can
 * display information, forms, buttons, or other interactive elements.
 */
interface ModuleTab {

    /**
     * Unique identifier for this tab.
     *
     * Must be unique across all tabs in the modal panel.
     * Used internally for tab tracking and switching.
     *
     * Example: "metabolism", "professions", "claims"
     */
    val tabId: String

    /**
     * Display label shown on the tab button.
     *
     * This is the text users see when selecting tabs.
     * Keep it short and descriptive (8-12 characters recommended).
     *
     * Example: "Metabolism", "Professions", "Claims"
     */
    val tabLabel: String

    /**
     * Render the tab's content into the modal panel.
     *
     * Called when the tab becomes active. Use the [UICommandBuilder] to populate
     * the content area with your tab's UI elements.
     *
     * The content area is a dedicated section of the modal panel where you can:
     * - Display information (labels, stats, progress bars)
     * - Show forms (text inputs, checkboxes)
     * - Add interactive elements (buttons, lists)
     *
     * Content should be designed to fit within the modal's content area dimensions.
     * Refer to CentralModalPanel.ui for content area size and element IDs.
     *
     * @param builder UICommandBuilder for setting content values and visibility
     */
    fun render(builder: UICommandBuilder)

    /**
     * Called when this tab becomes the active tab.
     *
     * Use this lifecycle method to:
     * - Initialize tab state
     * - Fetch fresh data from services
     * - Start timers or listeners
     * - Log tab activation for analytics
     *
     * This is called BEFORE [render], so you can prepare data here that
     * will be displayed in the render method.
     */
    fun onActivate()

    /**
     * Called when this tab is no longer the active tab.
     *
     * Use this lifecycle method to:
     * - Clean up resources
     * - Stop timers or listeners
     * - Save tab state
     * - Log tab deactivation for analytics
     *
     * This is called AFTER the new tab's [onActivate], so the new tab
     * is already active when this method runs.
     */
    fun onDeactivate()

    /**
     * Handle a UI event routed from the CentralModalPanel.
     *
     * Called when the panel receives an event action that is not a standard
     * tab switch or close action. Interactive tabs (e.g., Claims grid) can
     * override this to handle custom events like cell clicks.
     *
     * The default implementation does nothing, so non-interactive tabs
     * (Metabolism, Professions) don't need to override this.
     *
     * @param action The event action string (e.g., "cell:2,3")
     * @param value The event value string (may be empty)
     * @return true if the event was handled and the panel should refresh, false otherwise
     */
    fun onEvent(action: String, value: String): Boolean = false

    /**
     * Bind custom event handlers for this tab.
     *
     * Called during [CentralModalPanel.build] to allow interactive tabs
     * to register additional event bindings beyond the standard tab buttons.
     *
     * The default implementation does nothing. Override this in tabs that
     * need custom UI interactions (e.g., grid cell clicks).
     *
     * @param evt UIEventBuilder for registering event bindings
     */
    fun bindEvents(evt: UIEventBuilder) { /* no-op by default */ }
}
