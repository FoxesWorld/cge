package org.foxesworld.cge.core.module;

/**
 * Enumeration of possible module lifecycle states.
 */
public enum ModuleState {
    /**
     * Module has been instantiated but not yet initialized
     */
    UNLOADED,

    /**
     * Module is loading its configuration
     */
    LOADING_CONFIG,

    /**
     * Module is performing initialization
     */
    INITIALIZING,

    /**
     * Module is running normally
     */
    RUNNING,

    /**
     * Module is temporarily paused due to an error but may be recoverable
     */
    PAUSED,

    /**
     * Module is in the process of being recovered after failure
     */
    RECOVERING,

    /**
     * Module has failed permanently and cannot be recovered
     */
    FAILED,

    /**
     * Module is shutting down
     */
    SHUTTING_DOWN,

    /**
     * Module has been cleaned up and is no longer active
     */
    CLEANED_UP
}