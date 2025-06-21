package org.foxesworld.cge.core.module.health;

import org.foxesworld.cge.core.module.ModuleState;

/**
 * Class representing the health state of a module.
 */
public class ModuleHealth {
    /*
    public enum Status {
        INSTANTIATED,
        INITIALIZING,
        ACTIVE,
        INACTIVE,
        RELOADING,
        RECOVERING,
        FAILED,
        TIMEOUT
    } */

    private final String moduleName;
    private volatile ModuleState status = ModuleState.INITIALIZING;
    private volatile String statusMessage = "";
    private final long creationTime = System.currentTimeMillis();
    private volatile long lastStatusChangeTime = creationTime;

    public ModuleHealth(String moduleName) {
        this.moduleName = moduleName;
    }

    public synchronized void setStatus(ModuleState status) {
        setStatus(status, null);
    }

    public synchronized void setStatus(ModuleState status, String message) {
        this.status = status;
        this.statusMessage = message != null ? message : "";
        this.lastStatusChangeTime = System.currentTimeMillis();
    }

    public ModuleState getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastStatusChangeTime() {
        return lastStatusChangeTime;
    }

    public String getModuleName() {
        return moduleName;
    }
}