package com.livinglands.modules.professions.data

/**
 * Immutable stats for a single profession.
 * 
 * This is the persistence model - used for database reads/writes.
 * For in-memory hot-path operations, use PlayerProfessionState instead.
 * 
 * Thread-safe by immutability.
 */
data class ProfessionStats(
    /** Player UUID as string (cached) */
    val playerId: String,
    
    /** The profession these stats belong to */
    val profession: Profession,
    
    /** Total XP earned in this profession */
    val xp: Long,
    
    /** Current level (calculated from XP) */
    val level: Int,
    
    /** Timestamp of last XP gain (milliseconds since epoch) */
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create default stats for a new player in a profession.
         * 
         * @param playerId Player UUID as cached string
         * @param profession The profession
         * @return Default stats (XP=0, Level=1)
         */
        fun createDefault(playerId: String, profession: Profession): ProfessionStats {
            return ProfessionStats(
                playerId = playerId,
                profession = profession,
                xp = 0L,
                level = 1,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}
