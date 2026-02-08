package com.livinglands.modules.claims

import com.livinglands.core.logging.LoggingManager
import com.hypixel.hytale.server.core.entity.entities.Player
import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.modules.claims.config.ClaimsConfig
import com.livinglands.modules.claims.config.ClaimsConfigValidator
import com.livinglands.modules.claims.cache.ClaimsCache
import com.livinglands.modules.claims.cache.GroupsCache
import com.livinglands.modules.claims.repository.ClaimsRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.livinglands.modules.claims.systems.BlockBreakProtectionSystem
import com.livinglands.modules.claims.systems.BlockPlaceProtectionSystem
import com.livinglands.modules.claims.commands.TrustCommand
import com.livinglands.modules.claims.commands.UntrustCommand
import com.livinglands.modules.claims.commands.ClaimsListCommand


/**
 * Claims Module - Land Protection System
 * 
 * **Features:**
 * - Claim land chunks to protect from griefing
 * - Trust system for allowing specific players to build
 * - Claim boundaries visualization
 * - Maximum claim limits per player
 * - Admin bypass permissions
 * 
 * **Dependencies:**
 * - None (standalone system)
 * 
 * **MVP Scope:**
 * - Multi-chunk plot claiming via grid UI with block protection
 * - Trust management (add/remove trusted players per plot)
 * - Visual boundaries when entering claim
 * - Configurable limits: max plots, max chunks per plot, max total chunks
 * - Commands: /ll trust, /ll untrust, /ll claims
 * - Grid UI: Claim/unclaim chunks via the central menu panel
 * 
 * @property id Module identifier
 * @property name Display name
 * @property version Module version
 * @property dependencies Module dependencies (none)
 */
class ClaimsModule : AbstractModule(
    id = "claims",
    name = "Claims",
    version = "1.0.0",
    dependencies = emptySet()  // Standalone module
) {
    
    /**
     * Safety flag to prevent accidental enablement of incomplete module.
     * Set to `true` when implementation is complete (Phase 3).
     */
    private val isImplemented = true
    
    // Module-specific fields
    private lateinit var config: ClaimsConfig
    private lateinit var service: ClaimsService
    private lateinit var cache: ClaimsCache
    private lateinit var groupsCache: GroupsCache
    private lateinit var repository: ClaimsRepository
    private lateinit var blockBreakProtectionSystem: BlockBreakProtectionSystem
    private lateinit var blockPlaceProtectionSystem: BlockPlaceProtectionSystem
    
    override suspend fun onSetup() {
        // Safety guard: Prevent enabling incomplete module
        if (!isImplemented) {
            LoggingManager.error(logger, "claims") { "❌ ClaimsModule is NOT IMPLEMENTED - refusing to start" }
            throw UnsupportedOperationException("ClaimsModule is not fully implemented yet")
        }

        LoggingManager.debug(logger, "claims") { "Claims module setting up..." }

        // Load configuration
        config = CoreModule.config.loadWithMigration(
            "claims",
            ClaimsConfig.default(),
            ClaimsConfig.CURRENT_VERSION
        )

        // Validate configuration
        ClaimsConfigValidator.validate(config, logger)

        // Initialize caches
        cache = ClaimsCache()
        groupsCache = GroupsCache()

        // Initialize repository
        // Note: Claims are global (not per-world), so we use global persistence
        repository = ClaimsRepository(
            persistence = CoreModule.globalPersistence,
            logger = logger
        )

        // Initialize repository tables
        repository.initialize()

        // Create service
        service = ClaimsService(
            repository = repository,
            cache = cache,
            config = config,
            logger = logger
        )

        // Register service
        CoreModule.services.register<ClaimsService>(service)

        // Register ECS systems for block protection
        blockBreakProtectionSystem = BlockBreakProtectionSystem(service, logger)
        registerSystem(blockBreakProtectionSystem)

        blockPlaceProtectionSystem = BlockPlaceProtectionSystem(service, logger)
        registerSystem(blockPlaceProtectionSystem)

        // Register commands as subcommands to /ll
        // Note: /ll claim and /ll unclaim removed - claiming is now via grid UI only
        CoreModule.mainCommand.registerSubCommand(TrustCommand())
        CoreModule.mainCommand.registerSubCommand(UntrustCommand())
        CoreModule.mainCommand.registerSubCommand(ClaimsListCommand())

        LoggingManager.debug(logger, "claims") { "Claims module setup complete with all commands" }
    }
    
    override suspend fun onStart() {
        LoggingManager.debug(logger, "claims") { "Claims module started" }
        // Cache is warmed on-demand when players join
    }

    override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
        // Load player's claims into cache
        val playerClaims = service.getClaimsByOwner(playerId)
        LoggingManager.debug(logger, "claims") {
            "Player $playerId joined - loaded ${playerClaims.size} claims"
        }
    }

    override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        // Claims persist in cache and database - no special handling needed on disconnect
        LoggingManager.debug(logger, "claims") { "Player $playerId disconnected" }
    }

    override suspend fun onShutdown() {
        LoggingManager.debug(logger, "claims") { "Claims module shutting down" }
        cache.clear()
        groupsCache.clear()
        // Repository uses GlobalPersistenceService which manages its own lifecycle
    }
}

/**
 * Represents a chunk position in the world.
 */
data class ChunkPosition(
    val worldId: UUID,
    val chunkX: Int,
    val chunkZ: Int
)

/**
 * Represents a land claim (plot) consisting of one or more chunks.
 * 
 * **Immutable** - Use helper methods to create modified copies.
 * All collections are immutable Sets to prevent concurrent modification.
 * 
 * A single Claim/plot can span multiple chunk positions. Players create
 * plots by selecting multiple chunks in the grid UI. Trust is per-plot,
 * not per-chunk.
 * 
 * @property id Unique claim identifier
 * @property owner UUID of the player who owns this claim
 * @property chunks Set of chunk positions that belong to this plot (immutable)
 * @property name Optional friendly name for this claim
 * @property trustedPlayers Set of player UUIDs with build permission (immutable)
 * @property createdAt Unix timestamp when claim was created
 * @property updatedAt Unix timestamp when claim was last modified
 */
data class Claim(
    val id: UUID,
    val owner: UUID,
    val chunks: Set<ChunkPosition>,
    val name: String? = null,
    val trustedPlayers: Set<UUID> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if this plot contains a specific chunk position.
     */
    fun containsChunk(position: ChunkPosition): Boolean {
        return chunks.contains(position)
    }

    /**
     * Create a copy with an additional chunk added to this plot.
     */
    fun withChunk(position: ChunkPosition): Claim {
        return copy(
            chunks = chunks + position,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy with a chunk removed from this plot.
     */
    fun withoutChunk(position: ChunkPosition): Claim {
        return copy(
            chunks = chunks - position,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Create a copy with an additional trusted player.
     */
    fun withTrustedPlayer(playerId: UUID): Claim {
        return copy(
            trustedPlayers = trustedPlayers + playerId,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with a trusted player removed.
     */
    fun withoutTrustedPlayer(playerId: UUID): Claim {
        return copy(
            trustedPlayers = trustedPlayers - playerId,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with a new name.
     */
    fun withName(newName: String?): Claim {
        return copy(
            name = newName,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Check if a player can build here.
     * Returns true if player is owner or trusted.
     */
    fun canBuild(playerId: UUID): Boolean {
        return playerId == owner || trustedPlayers.contains(playerId)
    }
}

// ClaimsService moved to separate file: com.livinglands.modules.claims.ClaimsService
