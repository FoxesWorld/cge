package org.foxesworld.cge.modules.physics.body.rigid;

import org.foxesworld.cge.modules.physics.PhysicsConfig;
import org.foxesworld.cge.modules.physics.PhysicsModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class RigidBodyModuleTest {
    private RigidBodyModule rigidBodyModule;
    private PhysicsModule physicsModuleMock;

    @BeforeEach
    void setUp() {
        physicsModuleMock = Mockito.mock(PhysicsModule.class, Mockito.RETURNS_DEEP_STUBS);
        Mockito.when(physicsModuleMock.getConfig()).thenReturn(new PhysicsConfig());
        rigidBodyModule = new RigidBodyModule(physicsModuleMock);
    }

    @Test
    void testAddRigidBody() {
        Spatial spatial = Mockito.mock(Spatial.class);
        RigidBodyControl ctrl = rigidBodyModule.addRigidBody(spatial, 5.0f);
        assertNotNull(ctrl, "RigidBodyControl should be created");
        assertTrue(rigidBodyModule.hasRigidBody(spatial), "RigidBody should be registered");
    }

    @Test
    void testPreventDuplicateRigidBody() {
        Spatial spatial = Mockito.mock(Spatial.class);
        rigidBodyModule.addRigidBody(spatial, 2.0f);
        RigidBodyControl ctrl2 = rigidBodyModule.addRigidBody(spatial, 2.0f);
        assertNotNull(ctrl2, "Second call should return existing control, not null");
    }

    @Test
    void testRemoveRigidBody() {
        Spatial spatial = Mockito.mock(Spatial.class);
        rigidBodyModule.addRigidBody(spatial, 1.0f);
        rigidBodyModule.removeRigidBody(spatial);
        assertFalse(rigidBodyModule.hasRigidBody(spatial), "RigidBody should be removed");
    }
}