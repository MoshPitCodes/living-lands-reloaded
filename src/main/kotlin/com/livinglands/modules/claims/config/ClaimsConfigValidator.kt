package com.livinglands.modules.claims.config

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager

/**
 * Validator for claims configuration.
 *
 * Validates configuration values and logs warnings for invalid settings.
 */
object ClaimsConfigValidator {

    /**
     * Validate claims configuration.
     * Checks limits, protection settings, and visualization options.
     */
    fun validate(config: ClaimsConfig, logger: HytaleLogger) {
        LoggingManager.debug(logger, "claims") { "Validating claims configuration..." }

        // Validate limits
        if (config.limits.maxPlotsPerPlayer < 1) {
            LoggingManager.warn(logger, "claims") {
                "maxPlotsPerPlayer (${config.limits.maxPlotsPerPlayer}) is less than 1, setting to 1"
            }
        }

        if (config.limits.maxChunksPerPlot < 1) {
            LoggingManager.warn(logger, "claims") {
                "maxChunksPerPlot (${config.limits.maxChunksPerPlot}) is less than 1, setting to 1"
            }
        }

        if (config.limits.maxTotalChunks < 1) {
            LoggingManager.warn(logger, "claims") {
                "maxTotalChunks (${config.limits.maxTotalChunks}) is less than 1, setting to 1"
            }
        }

        if (config.limits.maxTotalChunks < config.limits.maxChunksPerPlot) {
            LoggingManager.warn(logger, "claims") {
                "maxTotalChunks (${config.limits.maxTotalChunks}) is less than maxChunksPerPlot (${config.limits.maxChunksPerPlot})"
            }
        }

        if (config.limits.startingClaims < 0) {
            LoggingManager.warn(logger, "claims") {
                "startingClaims (${config.limits.startingClaims}) is negative, setting to 0"
            }
        }

        if (config.limits.claimsPerHourPlayed < 0) {
            LoggingManager.warn(logger, "claims") {
                "claimsPerHourPlayed (${config.limits.claimsPerHourPlayed}) is negative, setting to 0"
            }
        }

        if (config.limits.maxTrustedPerClaim < 1) {
            LoggingManager.warn(logger, "claims") {
                "maxTrustedPerClaim (${config.limits.maxTrustedPerClaim}) is less than 1, setting to 1"
            }
        }

        // Validate group limits
        if (config.limits.maxGroupsPerPlayer < 1) {
            LoggingManager.warn(logger, "claims") {
                "maxGroupsPerPlayer (${config.limits.maxGroupsPerPlayer}) is less than 1, setting to 1"
            }
        }

        if (config.limits.maxMembersPerGroup < 1) {
            LoggingManager.warn(logger, "claims") {
                "maxMembersPerGroup (${config.limits.maxMembersPerGroup}) is less than 1, setting to 1"
            }
        }

        // Validate visualization
        if (config.visualization.boundaryDurationSeconds < 0) {
            LoggingManager.warn(logger, "claims") {
                "boundaryDurationSeconds (${config.visualization.boundaryDurationSeconds}) is negative, setting to 0"
            }
        }

        LoggingManager.debug(logger, "claims") { "Claims configuration validation complete" }
    }
}
