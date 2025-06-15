package org.foxesworld.cge.modules;

import org.foxesworld.cge.CalistaGameEngine;
import org.foxesworld.cge.core.module.EngineModule;

import java.util.function.Function;

/**
 * Конфигурация модуля: фабрика и приоритет регистрации
 */
public class ModuleConfig {
    private final Function<CalistaGameEngine, ? extends EngineModule<?>> factory;
    private final int priority;

    public ModuleConfig(Function<CalistaGameEngine, ? extends EngineModule<?>> factory, int priority) {
        this.factory = factory;
        this.priority = priority;
    }

    /**
     * Создаёт экземпляр модуля, привязанного к движку
     */
    public EngineModule<?> create(CalistaGameEngine engine) {
        return factory.apply(engine);
    }

    public int getPriority() {
        return priority;
    }
}