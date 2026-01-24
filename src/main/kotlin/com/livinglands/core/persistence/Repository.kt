package com.livinglands.core.persistence

/**
 * Base repository interface for data access operations.
 * Provides a consistent API for CRUD operations across all repositories.
 * 
 * @param T Entity type
 * @param ID Entity identifier type
 */
interface Repository<T, ID> {
    
    /**
     * Find an entity by its ID.
     * 
     * @param id Entity identifier
     * @return Entity if found, null otherwise
     */
    suspend fun findById(id: ID): T?
    
    /**
     * Save an entity (insert or update).
     * 
     * @param entity Entity to save
     */
    suspend fun save(entity: T)
    
    /**
     * Delete an entity by its ID.
     * 
     * @param id Entity identifier
     */
    suspend fun delete(id: ID)
    
    /**
     * Check if an entity exists by its ID.
     * 
     * @param id Entity identifier
     * @return true if entity exists, false otherwise
     */
    suspend fun existsById(id: ID): Boolean
}
