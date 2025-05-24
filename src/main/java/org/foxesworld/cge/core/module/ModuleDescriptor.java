package org.foxesworld.cge.core.module;

import java.util.ArrayList;
import java.util.List;

/**
 * Descriptor for engine modules loaded from JSON manifests.
 */
public class ModuleDescriptor {
    public String name;
    public String className;
    public List<String> dependencies = new ArrayList<>();
    public int priority;
    public boolean reloadable = false;
}