package org.foxesworld.cge.core;

import com.jme3.app.Application;
import com.jme3.app.state.AppState;
import com.jme3.app.state.AppStateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Менеджер модулей движка.
 */
public class ModuleManager {

    private final AppStateManager stateManager;
    private final TreeMap<Integer, EngineModule<?>> modules = new TreeMap<>();

    public ModuleManager(AppStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void register(EngineModule<?> module, int priority) {
        modules.put(priority, module);
    }

    public void initializeAll(Application app) {
        for (EngineModule<?> module : modules.values()) {
            stateManager.attach(module);
        }
    }

    public List<EngineModule<?>> getModules() {
        return new ArrayList<>(modules.values());
    }
}
