package org.foxesworld.cge.core.module;

import java.util.ArrayList;
import java.util.List;

/**
 * Descriptor for engine modules loaded from JSON manifests.
 */
public class ModuleDescriptor {
    public String name;
    public String className;
    public int priority = 100; // Default priority
    public List<String> dependencies = new ArrayList<>();
    public boolean enabled = true;
    public String configFile;
}