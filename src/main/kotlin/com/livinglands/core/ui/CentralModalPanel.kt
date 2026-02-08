package com.livinglands.core.ui

import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.codecs.simple.StringCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.logger.HytaleLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.annotation.Nonnull
import com.hypixel.hytale.server.core.ui.builder.EventData as HytaleEventData

/**
 * Central modal panel system for displaying module-specific content in a tabbed interface.
 *
 * This class follows the unified HUD pattern established in v1.4.x, using a single
 * UI file approach for consistency and simplicity.
 *
 * ## Architecture
 *
 * The modal panel provides a centralized UI framework where modules can register tabs.
 * Each tab represents a different module's interface (e.g., Metabolism, Professions, Claims).
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Create the modal panel
 * val panel = CentralModalPanel(playerRef)
 *
 * // Register module tabs
 * panel.registerTab(MetabolismTab())
 * panel.registerTab(ProfessionsTab())
 *
 * // Open the modal (shows first tab by default)
 * val player = store.getComponent(ref, Player.getComponentType())
 * player.pageManager.openCustomPage(ref, store, panel)
 *
 * // Switch to a specific tab programmatically
 * panel.switchToTab("professions")
 * ```
 *
 * ## Thread Safety
 *
 * Tab registration uses ConcurrentHashMap for thread-safe access.
 * Tab switching and rendering must occur on WorldThread.
 *
 * @param playerRef The player this modal panel is for
 */
class CentralModalPanel(
    @Nonnull playerRef: PlayerRef
) : InteractiveCustomUIPage<CentralModalPanel.EventData>(
    playerRef,
    CustomPageLifetime.CanDismissOrCloseThroughInteraction,
    EVENT_DATA_CODEC
) {

    /**
     * EventData class for capturing UI events (tab clicks, etc).
     *
     * This follows the pattern from v2.6.0's ClaimSelectorPage, using
     * KeyedCodec for encoding/decoding event data.
     */
    data class EventData(
        var action: String = "",
        var value: String = ""
    )

    companion object {
        private val logger = HytaleLogger.getLogger()

        /** Maximum number of tabs supported by the UI */
        const val MAX_TABS = 6

        /** Codec for action field */
        private val ACTION_CODEC = KeyedCodec("Action", StringCodec())

        /** Codec for value field */
        private val VALUE_CODEC = KeyedCodec("Value", StringCodec())

        /** BuilderCodec for EventData */
        @JvmStatic
        val EVENT_DATA_CODEC: BuilderCodec<EventData> = BuilderCodec.builder(
            EventData::class.java
        ) { EventData() }
            .addField(ACTION_CODEC, { data, value -> data.action = value }, { it.action })
            .addField(VALUE_CODEC, { data, value -> data.value = value }, { it.value })
            .build()

        /**
         * Active tab background color.
         *
         * Background is set on #TabNBg Group elements (not on the TextButton directly).
         * TextButton.Background is controlled by its Style and cannot be overridden
         * via builder.set(). Using a Group behind a transparent TextButton is the
         * workaround for dynamic background colors.
         */
        private const val TAB_COLOR_ACTIVE = "#2a4a6a"

        /** Inactive tab background color */
        private const val TAB_COLOR_INACTIVE = "#1a2a3a"
    }

    /** Registered tabs by tabId */
    private val tabs = ConcurrentHashMap<String, ModuleTab>()

    /** Ordered list of tab IDs for consistent rendering */
    private val tabOrder = mutableListOf<String>()

    /** Currently active tab ID */
    private val activeTabId = AtomicReference<String?>(null)

    /** Whether the modal is currently open */
    @Volatile
    private var isOpen = false

    /**
     * Register a tab with the modal panel.
     *
     * Tabs are displayed in the order they are registered.
     * Maximum of [MAX_TABS] can be registered.
     *
     * @param tab The ModuleTab to register
     * @throws IllegalStateException if maximum tabs exceeded or duplicate tabId
     */
    fun registerTab(tab: ModuleTab) {
        require(tabs.size < MAX_TABS) {
            "Cannot register more than $MAX_TABS tabs"
        }
        require(!tabs.containsKey(tab.tabId)) {
            "Tab with ID '${tab.tabId}' is already registered"
        }

        tabs[tab.tabId] = tab
        tabOrder.add(tab.tabId)

        LoggingManager.debug(logger, "core") {
            "Registered tab '${tab.tabId}' (${tab.tabLabel})"
        }

        // If this is the first tab and modal is open, activate it
        if (tabOrder.size == 1 && isOpen) {
            switchToTab(tab.tabId)
        }
    }

    /**
     * Unregister a tab from the modal panel.
     *
     * If the tab is currently active, switches to the first available tab.
     *
     * @param tabId The ID of the tab to unregister
     * @return true if tab was removed, false if not found
     */
    fun unregisterTab(tabId: String): Boolean {
        val tab = tabs.remove(tabId) ?: return false
        tabOrder.remove(tabId)

        LoggingManager.debug(logger, "core") {
            "Unregistered tab '$tabId'"
        }

        // If this was the active tab, switch to first available
        if (activeTabId.get() == tabId && tabOrder.isNotEmpty()) {
            switchToTab(tabOrder.first())
        }

        return true
    }

    /**
     * Switch to a specific tab.
     *
     * Calls onDeactivate() on the current tab and onActivate() on the new tab,
     * then renders the new tab's content.
     *
     * @param tabId The ID of the tab to switch to
     * @return true if switch succeeded, false if tab not found
     */
    fun switchToTab(tabId: String): Boolean {
        val newTab = tabs[tabId]
        if (newTab == null) {
            LoggingManager.warn(logger, "core") {
                "Cannot switch to tab '$tabId': not found"
            }
            return false
        }

        val currentTabId = activeTabId.get()

        // Deactivate current tab
        if (currentTabId != null && currentTabId != tabId) {
            tabs[currentTabId]?.onDeactivate()
        }

        // Activate new tab
        newTab.onActivate()
        activeTabId.set(tabId)

        LoggingManager.debug(logger, "core") {
            "Switched to tab '$tabId'"
        }

        // Render the new tab if modal is open
        if (isOpen) {
            renderActiveTab()
        }

        return true
    }

    /**
     * Open the modal panel.
     *
     * If no tab is active, activates the first registered tab.
     */
    fun open() {
        isOpen = true

        // Activate first tab if none active
        if (activeTabId.get() == null && tabOrder.isNotEmpty()) {
            switchToTab(tabOrder.first())
        }

        LoggingManager.debug(logger, "core") {
            "Modal panel opened"
        }
    }

    /**
     * Close the modal panel.
     *
     * Deactivates the current tab but keeps it selected for next open.
     */
    override fun close() {
        isOpen = false

        // Deactivate current tab
        val currentTabId = activeTabId.get()
        if (currentTabId != null) {
            tabs[currentTabId]?.onDeactivate()
        }

        LoggingManager.debug(logger, "core") {
            "Modal panel closed"
        }
    }

    /**
     * Check if the modal is currently open.
     */
    fun isOpen(): Boolean = isOpen

    /**
     * Get the currently active tab ID, or null if none active.
     */
    fun getActiveTabId(): String? = activeTabId.get()

    /**
     * Bind tab button click events.
     *
     * Registers click handlers for each tab button, encoding the tab ID
     * in the event data for processing in handleDataEvent().
     */
    private fun bindTabEvents(evt: UIEventBuilder) {
        try {
            for ((index, tabId) in tabOrder.withIndex()) {
                val selector = "#Tab${index + 1}"

                // Create event data using Hytale's EventData class
                // IMPORTANT: Field names must match codec exactly (case-sensitive)
                // Codec uses "Action" (uppercase), so we must use "Action" here
                val eventData = HytaleEventData()
                    .append("Action", "tab:$tabId")

                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector,
                    eventData
                )

                LoggingManager.trace(logger, "core") {
                    "Bound click event for $selector -> Action: tab:$tabId"
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "core", e) {
                "Failed to bind tab events"
            }
        }
    }

    /**
     * Update tab button visual states (active vs inactive).
     *
     * Sets individual style properties since Style objects cannot be set dynamically.
     *
     * Note: Only Background can be set dynamically. Properties nested inside LabelStyle
     * (like RenderBold, TextColor) cannot be set via builder.set() and must be defined
     * in the UI file's style definitions.
     */
    private fun updateTabStyles(cmd: UICommandBuilder) {
        val currentTabId = activeTabId.get()

        for ((index, tabId) in tabOrder.withIndex()) {
            val isActive = tabId == currentTabId
            val bgSelector = "#Tab${index + 1}Bg"

            // Set background color on the Group element (dynamic Background works on Groups)
            cmd.set("$bgSelector.Background", if (isActive) TAB_COLOR_ACTIVE else TAB_COLOR_INACTIVE)

            LoggingManager.trace(logger, "core") {
                "Updated style for $bgSelector: active=$isActive, background=${if (isActive) TAB_COLOR_ACTIVE else TAB_COLOR_INACTIVE}"
            }
        }
    }

    /**
     * Render the currently active tab's content.
     *
     * @param cmd Optional UICommandBuilder for initial rendering during build().
     *            If null, creates a new builder (won't work - tabs can't update after build)
     */
    private fun renderActiveTab(cmd: UICommandBuilder? = null) {
        val currentTabId = activeTabId.get() ?: return
        val currentTab = tabs[currentTabId] ?: return

        val builder = cmd ?: UICommandBuilder()

        // Update tab button states
        for ((index, tabId) in tabOrder.withIndex()) {
            val tab = tabs[tabId]

            if (tab != null && index < MAX_TABS) {
                // Show tab button
                builder.set("#Tab${index + 1}Container.Visible", true)
                builder.set("#Tab${index + 1}.Text", tab.tabLabel)
            } else {
                // Hide unused tab buttons
                builder.set("#Tab${index + 1}Container.Visible", false)
            }
        }

        // Hide remaining tab slots
        for (i in tabOrder.size until MAX_TABS) {
            builder.set("#Tab${i + 1}Container.Visible", false)
        }

        // Update tab styles (active indicator)
        updateTabStyles(builder)

        // Reset content area visibility before rendering active tab.
        // This ensures clean state: each tab's render() controls what it shows.
        // Text content elements (used by Metabolism, Professions)
        builder.set("#ContentTitle.Visible", false)
        builder.set("#ContentText.Visible", false)
        // Claims grid content (used by Claims tab)
        builder.set("#ClaimsContent.Visible", false)

        // Render the active tab's content
        currentTab.render(builder)
    }

    /**
     * Build the modal panel UI.
     */
    @Nonnull
    override fun build(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull cmd: UICommandBuilder,
        @Nonnull evt: UIEventBuilder,
        @Nonnull store: Store<EntityStore>
    ) {
        // Load the modal panel UI file
        cmd.append("Panels/CentralModalPanel.ui")

        // Bind tab button click events
        bindTabEvents(evt)

        // Bind custom events for all registered tabs (e.g., grid cell clicks)
        for (tab in tabs.values) {
            try {
                tab.bindEvents(evt)
            } catch (e: Exception) {
                LoggingManager.error(logger, "core", e) {
                    "Failed to bind events for tab '${tab.tabId}'"
                }
            }
        }

        // Modal opens automatically when page is shown
        open()

        // Render initial tab state with command builder
        renderActiveTab(cmd)

        LoggingManager.debug(logger, "core") {
            "Built modal panel with ${tabs.size} tabs (interactive)"
        }
    }

    /**
     * Handle data events from the UI (tab clicks, close button, etc).
     *
     * This overrides InteractiveCustomUIPage<EventData>.handleDataEvent()
     * which provides a properly decoded EventData object.
     */
    @Nonnull
    override fun handleDataEvent(
        @Nonnull ref: Ref<EntityStore>,
        @Nonnull store: Store<EntityStore>,
        @Nonnull eventData: EventData
    ) {
        val action = eventData.action

        if (action.isBlank()) {
            LoggingManager.warn(logger, "core") {
                "Received event with blank action"
            }
            return
        }

        LoggingManager.debug(logger, "core") {
            "Modal event received: action='$action', value='${eventData.value}'"
        }

        try {
            when {
                action.startsWith("tab:") -> {
                    // Tab switch event - pattern: "tab:metabolism", "tab:professions", "tab:claims"
                    val tabId = action.substringAfter("tab:")

                    LoggingManager.info(logger, "core") {
                        "Switching to tab: $tabId"
                    }

                    // Switch to the requested tab
                    if (switchToTab(tabId)) {
                        // Tab switch succeeded - need to rebuild the page to show new content
                        val player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                        if (player != null) {
                            // Reopen with new tab active (closes current page and reopens)
                            player.pageManager.openCustomPage(ref, store, this)

                            LoggingManager.debug(logger, "core") {
                                "Reopened modal panel to refresh content for tab: $tabId"
                            }
                        }
                    } else {
                        LoggingManager.warn(logger, "core") {
                            "Failed to switch to tab: $tabId (not found)"
                        }
                    }
                }
                action == "close" -> {
                    // Close modal
                    close()
                    LoggingManager.debug(logger, "core") {
                        "Modal panel closed by user"
                    }
                }
                else -> {
                    // Route unknown actions to the active tab's onEvent() handler
                    val currentTabId = activeTabId.get()
                    val currentTab = if (currentTabId != null) tabs[currentTabId] else null

                    if (currentTab != null) {
                        val handled = currentTab.onEvent(action, eventData.value)
                        if (handled) {
                            // Tab handled the event and wants a refresh - rebuild the page
                            val player = store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                            if (player != null) {
                                player.pageManager.openCustomPage(ref, store, this)
                            }
                        } else {
                            LoggingManager.debug(logger, "core") {
                                "Action '$action' not handled by tab '$currentTabId'"
                            }
                        }
                    } else {
                        LoggingManager.warn(logger, "core") {
                            "Unknown action received with no active tab: $action"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LoggingManager.error(logger, "core", e) {
                "Error handling modal event: action='$action'"
            }
        }
    }
}
