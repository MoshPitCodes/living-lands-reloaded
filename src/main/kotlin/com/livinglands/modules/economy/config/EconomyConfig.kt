package com.livinglands.modules.economy.config

import com.livinglands.core.config.ConfigMigration
import com.livinglands.core.config.VersionedConfig
import java.math.BigDecimal

/**
 * Configuration for the economy system.
 * Loaded from economy.yml
 * 
 * Defines currency settings, transaction limits, and money sources.
 */
data class EconomyConfig(
    /**
     * Configuration version for migration support.
     */
    override val configVersion: Int = CURRENT_VERSION,
    
    /** Master enable/disable for the entire economy system */
    val enabled: Boolean = true,
    
    /** Currency settings */
    val currency: CurrencyConfig = CurrencyConfig(),
    
    /** Transaction settings */
    val transactions: TransactionsConfig = TransactionsConfig(),
    
    /** Starting balance for new players */
    val startingBalance: BigDecimal = BigDecimal("100.00")
    
) : VersionedConfig {
    
    /** No-arg constructor for Jackson deserialization */
    constructor() : this(configVersion = CURRENT_VERSION, enabled = true)
    
    companion object {
        /** Current config version */
        const val CURRENT_VERSION = 1
        
        /** Config module ID for migration registry */
        const val MODULE_ID = "economy"
        
        /**
         * Get all migrations for EconomyConfig.
         */
        fun getMigrations(): List<ConfigMigration> = emptyList()
    }
}

/**
 * Currency configuration.
 */
data class CurrencyConfig(
    /** Currency name (singular) */
    val nameSingular: String = "coin",
    
    /** Currency name (plural) */
    val namePlural: String = "coins",
    
    /** Currency symbol */
    val symbol: String = "$",
    
    /** Decimal places for display */
    val decimalPlaces: Int = 2
)

/**
 * Transaction settings.
 */
data class TransactionsConfig(
    /** Minimum transaction amount */
    val minAmount: BigDecimal = BigDecimal("0.01"),
    
    /** Maximum transaction amount (0 = unlimited) */
    val maxAmount: BigDecimal = BigDecimal.ZERO,
    
    /** Transaction fee percentage (0.0 - 1.0) */
    val feePercent: Double = 0.0,
    
    /** Enable transaction history logging */
    val logTransactions: Boolean = true,
    
    /** Maximum transaction history entries per player */
    val maxHistoryEntries: Int = 100
)
