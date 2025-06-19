package org.foxesworld.cge.modules.player.config;

import com.jme3.math.Vector3f;

public class PlayerConfig {
    private PhysicsConfig physics = new PhysicsConfig();
    private MovementConfig movement = new MovementConfig();
    private ModelConfig model = new ModelConfig();
    private Vector3f spawnPosition = new Vector3f(0,20,0);

    public PhysicsConfig getPhysics() { return physics; }
    public MovementConfig getMovement() { return movement; }
    public ModelConfig getModel() { return model; }

    public Vector3f getSpawnPosition() {
        return spawnPosition;
    }

    public static class PhysicsConfig {
        private float eyeHeight = 1.6f;
        private float radius = 0.45f;
        private float height = 1.7f;
        private float jumpSpeed = 5.2f;
        private float fallSpeed = 16.5f;
        private float gravity = 13.8f;
        private float stepHeight = 0.05f;

        // Getters and setters
        public float getEyeHeight() { return eyeHeight; }
        public void setEyeHeight(float eyeHeight) { this.eyeHeight = eyeHeight; }

        public float getRadius() {
            return radius;
        }

        public float getHeight() {
            return height;
        }

        public float getJumpSpeed() {
            return jumpSpeed;
        }

        public float getFallSpeed() {
            return fallSpeed;
        }

        public float getGravity() {
            return gravity;
        }

        public float getStepHeight() {
            return stepHeight;
        }
    }

    public static class MovementConfig {
        private float walkSpeed = 0.13f;
        private float sprintSpeed = 0.18f;
        private float acceleration = 0.75f;
        private float deceleration = 0.92f;
        private float smoothing = 2.2f;

        public float getWalkSpeed() {
            return walkSpeed;
        }

        public float getSprintSpeed() {
            return sprintSpeed;
        }

        public float getAcceleration() {
            return acceleration;
        }

        public float getDeceleration() {
            return deceleration;
        }

        public float getSmoothing() {
            return smoothing;
        }
    }

    public static class ModelConfig {
        private String modelPath = "meshes/YBot.j3o";
        private float scale = 0.01f;
        private float backOffset = 0.3f;
        private float downOffset = -1.6f;

        public String getModelPath() {
            return modelPath;
        }

        public float getScale() {
            return scale;
        }

        public float getBackOffset() {
            return backOffset;
        }

        public float getDownOffset() {
            return downOffset;
        }
    }
}