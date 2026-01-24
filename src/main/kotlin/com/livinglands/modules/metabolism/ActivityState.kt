package com.livinglands.modules.metabolism

import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent

/**
 * Represents the current activity state of a player.
 * Used to determine activity multipliers for metabolism depletion.
 */
enum class ActivityState {
    /** Player is standing still or not moving */
    IDLE,
    
    /** Player is walking (moving but not sprinting) */
    WALKING,
    
    /** Player is sprinting (fast movement) */
    SPRINTING,
    
    /** Player is swimming (in water) */
    SWIMMING,
    
    /** Player is in combat (attacking or being attacked) */
    COMBAT;
    
    companion object {
        /**
         * Determine activity state from MovementStatesComponent.
         * Priority order: SPRINTING > SWIMMING > WALKING > IDLE
         * 
         * Note: COMBAT detection requires additional logic outside this function
         * (e.g., tracking recent damage dealt/received).
         * 
         * @param movement The player's movement states component, or null if unavailable
         * @return The detected activity state (defaults to IDLE if component is null)
         */
        fun fromMovementStates(movement: MovementStatesComponent?): ActivityState {
            if (movement == null) return IDLE
            
            // Get the actual movement states from the component
            val states = movement.movementStates ?: return IDLE
            
            return when {
                // Check sprinting first (highest priority)
                states.sprinting -> SPRINTING
                
                // Check swimming (in water movement)
                states.swimming -> SWIMMING
                
                // Check if walking (moving but not running/sprinting)
                states.walking || states.running -> WALKING
                
                // Default to idle
                else -> IDLE
            }
        }
        
        /**
         * Get the config key for this activity state.
         * Used to look up multipliers in StatConfig.activityMultipliers.
         */
        fun ActivityState.configKey(): String = name.lowercase()
    }
}
