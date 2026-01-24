package com.livinglands.core

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Type-safe service locator for cross-module communication.
 * Services are registered by interface type and retrieved by type.
 */
class ServiceRegistry {
    
    @PublishedApi
    internal val services = ConcurrentHashMap<KClass<*>, Any>()
    
    /**
     * Register a service instance.
     */
    inline fun <reified T : Any> register(service: T) {
        services[T::class] = service
    }
    
    /**
     * Get a service by type. Returns null if not registered.
     */
    inline fun <reified T : Any> get(): T? {
        @Suppress("UNCHECKED_CAST")
        return services[T::class] as? T
    }
    
    /**
     * Get a service by type. Throws if not registered.
     */
    inline fun <reified T : Any> require(): T {
        return get<T>() ?: throw IllegalStateException(
            "Service ${T::class.simpleName} not registered"
        )
    }
    
    /**
     * Check if a service is registered.
     */
    inline fun <reified T : Any> has(): Boolean {
        return services.containsKey(T::class)
    }
    
    /**
     * Unregister a service.
     */
    inline fun <reified T : Any> unregister() {
        services.remove(T::class)
    }
    
    /**
     * Clear all registered services.
     */
    fun clear() {
        services.clear()
    }
}
