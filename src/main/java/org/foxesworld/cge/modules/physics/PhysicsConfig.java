package org.foxesworld.cge.modules.physics;

import com.jme3.math.Vector3f;
import com.jme3.bullet.PhysicsSpace.BroadphaseType;
import org.foxesworld.cge.core.module.ModuleConfig;

/**
 * Configuration for the PhysicsModule and its sub-modules.
 */
public class PhysicsConfig extends ModuleConfig {
    // === Global settings ===
    public Vector3f gravity = new Vector3f(0f, -9.81f, 0f);
    public BroadphaseType broadphaseType = BroadphaseType.DBVT;
    public int solverIterations = 10;
    public boolean debug = false;
    public float timeStep = 1f / 60f;
    public boolean useThreading = true;

    // === RigidBodyModule specific ===
    public float rigidDefaultMass       = 1f;
    public float rigidFriction          = 0.5f;
    public float rigidRestitution       = 0.3f;
    public float rigidLinearDamping     = 0.05f;
    public float rigidAngularDamping    = 0.05f;

    // === SoftBodyModule specific ===
    /** Default mass applied to soft bodies when none is specified */
    public float softDefaultMass        = 1f;
    /** Default stiffness coefficient for soft bodies */
    public float softStiffness          = 1f;
    /** Default damping coefficient for soft bodies */
    public float softDamping            = 0.01f;
    /** Default internal pressure for soft bodies */
    public float softPressure           = 0f;
    /** Number of solver iterations for soft body world */
    public int   softSolverIterations   = 10;
}
