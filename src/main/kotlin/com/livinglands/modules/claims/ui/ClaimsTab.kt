package com.livinglands.modules.claims.ui

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.livinglands.api.safeService
import com.livinglands.core.CoreModule
import com.livinglands.core.logging.LoggingManager
import com.livinglands.core.ui.ModuleTab
import com.livinglands.modules.claims.Claim
import com.livinglands.modules.claims.ChunkPosition
import com.livinglands.modules.claims.ClaimResult
import com.livinglands.modules.claims.ClaimsService
import com.livinglands.modules.claims.UnclaimResult
import com.livinglands.modules.claims.UpdateNameResult
import kotlinx.coroutines.runBlocking
import java.util.UUID
import com.hypixel.hytale.server.core.ui.builder.EventData as HytaleEventData

/**
 * Claims tab for the central modal panel.
 *
 * Displays a 6x6 chunk grid centered on the player's position with a
 * selection-based claim/unclaim workflow using multi-chunk plots:
 *
 * 1. Player clicks cells to toggle selection
 * 2. Confirm/Clear buttons appear when cells are selected
 * 3. Confirm creates a new plot from all selected unclaimed chunks
 * 4. Confirm removes selected owned chunks from their parent plots
 *    (if a plot loses all chunks, it is deleted)
 *
 * **Grid Color Scheme:**
 * - Blue (#2a4a7a): Owned by this player
 * - Red (#5a2727): Owned by another player
 * - Gray (#3a3a3a): Unclaimed
 * - Orange (#b87333): Selected for claiming (was unclaimed)
 * - Dark Red (#8b2020): Selected for unclaiming (was owned)
 *
 * **Data Loading:**
 * Data is pre-loaded in onActivate() to avoid blocking the render thread.
 * The render() method only reads from cached fields.
 *
 * @param playerId UUID of the player viewing the tab
 * @param worldId UUID of the world the player is in
 * @param centerChunkX Player's current chunk X coordinate
 * @param centerChunkZ Player's current chunk Z coordinate
 */
class ClaimsTab(
    private val playerId: UUID,
    private val worldId: UUID,
    private val centerChunkX: Int,
    private val centerChunkZ: Int
) : ModuleTab {

    override val tabId: String = "claims"
    override val tabLabel: String = "Claims"

    companion object {
        private val logger: HytaleLogger = CoreModule.logger

        /** Grid size (6x6 = 36 cells) */
        private const val GRID_SIZE = 6

        /** Color constants */
        private const val COLOR_OWNED = "#2a4a7a"          // Blue - player's claimed chunks
        private const val COLOR_OTHER = "#5a2727"           // Red - other player's chunks
        private const val COLOR_UNCLAIMED = "#3a3a3a"       // Gray - free chunks
        private const val COLOR_SELECT_CLAIM = "#b87333"    // Orange - selected for claiming
        private const val COLOR_SELECT_UNCLAIM = "#8b2020"  // Dark Red - selected for unclaiming

        /** Auto-generated plot names that cycle on RENAME clicks */
        private val PLOT_NAMES = listOf(
            "Homestead", "Outpost", "Haven", "Sanctuary", "Frontier",
            "Bastion", "Retreat", "Stronghold", "Settlement", "Enclave",
            "Hideaway", "Citadel", "Lookout", "Camp", "Lodge",
            "Hamlet", "Keep", "Garrison", "Waypoint", "Refuge"
        )
    }

    private var service: ClaimsService? = null

    /** Pre-loaded claims data (populated in onActivate) */
    private var ownedClaims: List<Claim> = emptyList()
    private var trustedClaims: List<Claim> = emptyList()
    private var areaClaims: Map<Pair<Int, Int>, Claim> = emptyMap()

    /** Cells selected for claiming (grid coords of currently unclaimed chunks) */
    private val selectedToClaim = mutableSetOf<Pair<Int, Int>>()

    /** Cells selected for unclaiming (grid coords of player's owned chunks) */
    private val selectedToUnclaim = mutableSetOf<Pair<Int, Int>>()

    /** Status message shown after confirm action */
    private var statusMessage: String = ""

    /** Currently selected plot ID (for plot management buttons) */
    private var selectedPlotId: UUID? = null

    override fun onActivate() {
        service = safeService("claims")
        selectedToClaim.clear()
        selectedToUnclaim.clear()
        statusMessage = ""
        loadData()
    }

    override fun onDeactivate() {
        service = null
        ownedClaims = emptyList()
        trustedClaims = emptyList()
        areaClaims = emptyMap()
        selectedToClaim.clear()
        selectedToUnclaim.clear()
        statusMessage = ""
        selectedPlotId = null
    }

    /**
     * Bind grid cell click events and action button events.
     */
    override fun bindEvents(evt: UIEventBuilder) {
        // Grid cell buttons (6x6) - bind to TextButton inside each Group cell
        for (x in 0 until GRID_SIZE) {
            for (z in 0 until GRID_SIZE) {
                evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#CellBtn${x}x$z",
                    HytaleEventData().append("Action", "cell:$x,$z")
                )
            }
        }

        // Confirm button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfirmBtn",
            HytaleEventData().append("Action", "confirm_claims")
        )

        // Clear button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ClearBtn",
            HytaleEventData().append("Action", "clear_selection")
        )

        // Plot list items
        for (i in 0..6) {
            evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PlotItem$i",
                HytaleEventData().append("Action", "plot:$i")
            )
        }

        // Plot management buttons
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#RenameBtn",
            HytaleEventData().append("Action", "rename_plot")
        )

        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DeleteBtn",
            HytaleEventData().append("Action", "delete_plot")
        )
    }

    /**
     * Handle UI events routed from CentralModalPanel.
     *
     * - cell:X,Z -> toggle selection
     * - confirm_claims -> create plot from selected unclaimed + remove selected owned
     * - clear_selection -> deselect all
     *
     * Always returns true to trigger page refresh.
     */
    override fun onEvent(action: String, value: String): Boolean {
        when {
            action.startsWith("cell:") -> handleCellClick(action)
            action == "confirm_claims" -> handleConfirm()
            action == "clear_selection" -> handleClear()
            action.startsWith("plot:") -> handlePlotSelection(action)
            action == "rename_plot" -> handleRenamePlot()
            action == "delete_plot" -> handleDeletePlot()
            else -> return false
        }
        return true
    }

    override fun render(builder: UICommandBuilder) {
        if (service == null) {
            builder.set("#ContentTitle.Visible", true)
            builder.set("#ContentTitle.Text", "Claims")
            builder.set("#ContentText.Visible", true)
            builder.set("#ContentText.Text", "Claims module is not enabled.")
            builder.set("#ClaimsContent.Visible", false)
            return
        }

        // Hide text content, show claims grid
        builder.set("#ContentTitle.Visible", false)
        builder.set("#ContentText.Visible", false)
        builder.set("#ClaimsContent.Visible", true)

        // Claim counter - show total chunks across all plots
        val totalChunks = ownedClaims.sumOf { it.chunks.size }
        val maxChunks = 100  // TODO: read from config
        builder.set("#ClaimsCounter.Text", "$totalChunks/$maxChunks chunks")

        // Coordinate range
        val minX = centerChunkX - GRID_SIZE / 2
        val minZ = centerChunkZ - GRID_SIZE / 2
        val maxX = centerChunkX + GRID_SIZE / 2 - 1
        val maxZ = centerChunkZ + GRID_SIZE / 2 - 1
        builder.set("#ClaimsCoords.Text", "Chunks ($minX,$minZ) to ($maxX,$maxZ)")

        // Color grid cells
        for (x in 0 until GRID_SIZE) {
            for (z in 0 until GRID_SIZE) {
                val cell = Pair(x, z)
                val color = when {
                    selectedToClaim.contains(cell) -> COLOR_SELECT_CLAIM
                    selectedToUnclaim.contains(cell) -> COLOR_SELECT_UNCLAIM
                    else -> {
                        val claim = areaClaims[cell]
                        when {
                            claim == null -> COLOR_UNCLAIMED
                            claim.owner == playerId -> COLOR_OWNED
                            else -> COLOR_OTHER
                        }
                    }
                }
                builder.set("#Cell${x}x$z.Background", color)
            }
        }

        // Show/hide action buttons based on selection
        val hasSelection = selectedToClaim.isNotEmpty() || selectedToUnclaim.isNotEmpty()
        builder.set("#ActionButtons.Visible", hasSelection)

        // Status message (visible when message exists)
        val showStatus = statusMessage.isNotEmpty()
        builder.set("#ClaimsStatus.Visible", showStatus)
        if (showStatus) {
            builder.set("#ClaimsStatus.Text", statusMessage)
        }

        // Info panel - static "how to claim" steps (dynamic summary when selecting)
        val stepsText = buildString {
            if (hasSelection) {
                if (selectedToClaim.isNotEmpty()) {
                    appendLine("Claiming ${selectedToClaim.size} chunk(s)")
                }
                if (selectedToUnclaim.isNotEmpty()) {
                    appendLine("Unclaiming ${selectedToUnclaim.size} chunk(s)")
                }
                appendLine()
                append("Press Confirm or Clear.")
            } else {
                appendLine("1. Click gray cells to select")
                appendLine("2. Press Confirm to claim")
                appendLine("3. Click your chunks to unclaim")
                appendLine()
                append("Manage plots on the right.")
            }
        }
        builder.set("#InfoSteps.Text", stepsText)

        // Render plot list panel
        renderPlotList(builder)
    }

    /**
     * Render the plot list panel on the right side.
     */
    private fun renderPlotList(builder: UICommandBuilder) {
        if (ownedClaims.isEmpty()) {
            // Hide all items, show "no plots" message
            for (i in 0..6) {
                builder.set("#PlotItemBg$i.Visible", false)
            }
            builder.set("#NoPlots.Visible", true)
            builder.set("#PlotButtons.Visible", false)
            return
        }

        builder.set("#NoPlots.Visible", false)

        // Show up to 7 plots
        for (i in 0..6) {
            if (i < ownedClaims.size) {
                val claim = ownedClaims[i]
                val plotName = claim.name ?: "Plot ${i + 1}"
                val isSelected = claim.id == selectedPlotId
                
                builder.set("#PlotItemBg$i.Visible", true)
                builder.set("#PlotItem$i.Text", "$plotName (${claim.chunks.size})")
                
                // Highlight selected plot via Group background (TextButton is transparent)
                if (isSelected) {
                    builder.set("#PlotItemBg$i.Background", "#2a4a6a")
                } else {
                    builder.set("#PlotItemBg$i.Background", "#1a2a3a")
                }
            } else {
                builder.set("#PlotItemBg$i.Visible", false)
            }
        }

        // Show buttons only if a plot is selected
        builder.set("#PlotButtons.Visible", selectedPlotId != null)
    }

    // ========== Private Handlers ==========

    /**
     * Toggle cell selection.
     * - Unclaimed cell -> add/remove from selectedToClaim
     * - Owned cell -> add/remove from selectedToUnclaim
     * - Other player's cell -> ignore
     */
    private fun handleCellClick(action: String) {
        val coords = action.substringAfter("cell:").split(",")
        if (coords.size != 2) return

        val gridX = coords[0].toIntOrNull() ?: return
        val gridZ = coords[1].toIntOrNull() ?: return
        if (gridX !in 0 until GRID_SIZE || gridZ !in 0 until GRID_SIZE) return

        val cell = Pair(gridX, gridZ)
        val existingClaim = areaClaims[cell]

        // Clear status message on new interaction
        statusMessage = ""

        when {
            // Already selected for claiming -> deselect
            selectedToClaim.contains(cell) -> {
                selectedToClaim.remove(cell)
            }
            // Already selected for unclaiming -> deselect
            selectedToUnclaim.contains(cell) -> {
                selectedToUnclaim.remove(cell)
            }
            // Unclaimed -> select for claiming
            existingClaim == null -> {
                selectedToClaim.add(cell)
            }
            // Owned by player -> select for unclaiming
            existingClaim.owner == playerId -> {
                selectedToUnclaim.add(cell)
            }
            // Owned by someone else -> ignore
            else -> {
                statusMessage = "That chunk is claimed by another player."
            }
        }
    }

    /**
     * Apply all pending claims and unclaims.
     *
     * Claiming: Creates a single new plot from all selected unclaimed chunks.
     * Unclaiming: Groups selected chunks by their parent plot and removes them.
     *             If a plot loses all its chunks, it is deleted.
     */
    private fun handleConfirm() {
        val claimsService = service
        if (claimsService == null) {
            statusMessage = "Claims service unavailable."
            return
        }

        val minX = centerChunkX - GRID_SIZE / 2
        val minZ = centerChunkZ - GRID_SIZE / 2

        var claimed = 0
        var unclaimed = 0
        var failed = 0

        runBlocking {
            // Process claims - create a single plot from all selected unclaimed chunks
            if (selectedToClaim.isNotEmpty()) {
                val chunksToClaimSet = selectedToClaim.map { cell ->
                    ChunkPosition(worldId, minX + cell.first, minZ + cell.second)
                }.toSet()

                when (val result = claimsService.createPlot(playerId, chunksToClaimSet)) {
                    is ClaimResult.Success -> {
                        claimed = chunksToClaimSet.size
                    }
                    is ClaimResult.AlreadyClaimed -> {
                        statusMessage = "Some chunks were already claimed."
                        failed = chunksToClaimSet.size
                    }
                    is ClaimResult.LimitReached -> {
                        statusMessage = "Too many chunks per plot (max ${result.max})."
                        failed = chunksToClaimSet.size
                    }
                    is ClaimResult.PlotLimitReached -> {
                        statusMessage = "Plot limit reached (${result.max} max)."
                        failed = chunksToClaimSet.size
                    }
                    is ClaimResult.ChunkLimitReached -> {
                        statusMessage = "Total chunk limit reached (${result.max} max)."
                        failed = chunksToClaimSet.size
                    }
                    is ClaimResult.DatabaseError -> {
                        statusMessage = "Database error creating plot."
                        failed = chunksToClaimSet.size
                    }
                }
            }

            // Process unclaims - group by parent plot and remove
            if (selectedToUnclaim.isNotEmpty()) {
                // Group selected chunks by their parent claim
                val chunksByPlot = mutableMapOf<UUID, MutableSet<ChunkPosition>>()
                for (cell in selectedToUnclaim) {
                    val claim = areaClaims[cell]
                    if (claim != null && claim.owner == playerId) {
                        val absPos = ChunkPosition(worldId, minX + cell.first, minZ + cell.second)
                        chunksByPlot.getOrPut(claim.id) { mutableSetOf() }.add(absPos)
                    }
                }

                // Remove chunks from each plot
                for ((claimId, chunks) in chunksByPlot) {
                    when (claimsService.removeChunksFromPlot(playerId, claimId, chunks)) {
                        is UnclaimResult.Success -> unclaimed += chunks.size
                        else -> failed += chunks.size
                    }
                }
            }
        }

        // Build status message (only if not already set by error)
        if (statusMessage.isEmpty()) {
            val parts = mutableListOf<String>()
            if (claimed > 0) parts.add("Claimed $claimed")
            if (unclaimed > 0) parts.add("Unclaimed $unclaimed")
            if (failed > 0) parts.add("$failed failed")
            statusMessage = parts.joinToString(" | ")
        }

        LoggingManager.info(logger, "claims") {
            "Player $playerId confirmed: claimed=$claimed, unclaimed=$unclaimed, failed=$failed"
        }

        // Clear selections and refresh data
        selectedToClaim.clear()
        selectedToUnclaim.clear()
        loadData()
    }

    /**
     * Clear all selections.
     */
    private fun handleClear() {
        selectedToClaim.clear()
        selectedToUnclaim.clear()
        statusMessage = ""
    }

    /**
     * Handle plot selection from the list.
     */
    private fun handlePlotSelection(action: String) {
        val index = action.substringAfter("plot:").toIntOrNull() ?: return
        if (index !in ownedClaims.indices) return

        val claim = ownedClaims[index]
        
        // Toggle selection (deselect if already selected)
        selectedPlotId = if (selectedPlotId == claim.id) null else claim.id
        
        statusMessage = ""
    }

    /**
     * Handle rename plot action.
     * Cycles through auto-generated names since there's no text input UI.
     * Each click picks the next name from PLOT_NAMES that isn't already used.
     */
    private fun handleRenamePlot() {
        val plotId = selectedPlotId ?: return
        val claim = ownedClaims.find { it.id == plotId } ?: return
        val claimsService = service
        
        if (claimsService == null) {
            statusMessage = "Claims service unavailable."
            return
        }

        // Find next available name not already used by this player's plots
        val usedNames = ownedClaims.mapNotNull { it.name }.toSet()
        val currentName = claim.name
        
        // Find the current name's index in the list (or -1 if not in list)
        val currentIndex = if (currentName != null) {
            PLOT_NAMES.indexOf(currentName)
        } else {
            -1
        }
        
        // Find the next unused name starting after current index
        var newName: String? = null
        for (offset in 1..PLOT_NAMES.size) {
            val candidate = PLOT_NAMES[(currentIndex + offset) % PLOT_NAMES.size]
            if (candidate !in usedNames) {
                newName = candidate
                break
            }
        }
        
        // Fallback: if all names are taken, append a number
        if (newName == null) {
            var counter = 1
            do {
                newName = "Plot $counter"
                counter++
            } while (newName in usedNames)
        }
        
        runBlocking {
            when (claimsService.updateClaimName(playerId, plotId, newName)) {
                is UpdateNameResult.Success -> {
                    statusMessage = "Renamed to \"$newName\" (click again to cycle)"
                    loadData()
                }
                is UpdateNameResult.ClaimNotFound -> {
                    statusMessage = "Plot not found."
                }
                is UpdateNameResult.NotOwner -> {
                    statusMessage = "You don't own this plot."
                }
                is UpdateNameResult.DatabaseError -> {
                    statusMessage = "Failed to rename plot."
                }
            }
        }
        
        LoggingManager.debug(logger, "claims") {
            "Player $playerId renamed plot $plotId to \"$newName\""
        }
    }

    /**
     * Handle delete plot action.
     */
    private fun handleDeletePlot() {
        val plotId = selectedPlotId ?: return
        val claim = ownedClaims.find { it.id == plotId } ?: return
        val claimsService = service
        
        if (claimsService == null) {
            statusMessage = "Claims service unavailable."
            return
        }

        // Delete entire plot (remove all chunks)
        runBlocking {
            when (claimsService.removeChunksFromPlot(playerId, claim.id, claim.chunks)) {
                is UnclaimResult.Success -> {
                    statusMessage = "Plot deleted successfully."
                    selectedPlotId = null
                    loadData()
                }
                else -> {
                    statusMessage = "Failed to delete plot."
                }
            }
        }
        
        LoggingManager.info(logger, "claims") {
            "Player $playerId deleted plot $plotId (${claim.chunks.size} chunks)"
        }
    }

    /**
     * Load/refresh all claims data from the service.
     */
    private fun loadData() {
        val claimsService = service
        if (claimsService != null) {
            runBlocking {
                ownedClaims = claimsService.getClaimsByOwner(playerId)
                trustedClaims = claimsService.getClaimsWhereTrusted(playerId)
                areaClaims = claimsService.getClaimsInArea(
                    worldId, centerChunkX, centerChunkZ, GRID_SIZE / 2
                )
            }
        } else {
            ownedClaims = emptyList()
            trustedClaims = emptyList()
            areaClaims = emptyMap()
        }
    }
}
