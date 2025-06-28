package org.foxesworld.cge.core.module;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO for module descriptor JSON.
 */
public class ModuleDescriptor {
    public String name;
    public String className;
    public List<String> dependencies = List.of();
    public int priority = 100;

    public ModuleDescriptor() {
    }

    public ModuleDescriptor(String name, String className, List<String> dependencies, int priority) {
        this.name = name;
        this.className = className;
        this.dependencies = dependencies;
        this.priority = priority;
    }
}