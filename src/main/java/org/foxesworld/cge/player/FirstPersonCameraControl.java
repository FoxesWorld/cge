package org.foxesworld.cge.player;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;

/**
 * FirstPersonCameraControl provides a smooth, artifact-free first-person
 * mouse look by decoupling raw input processing from camera updates.
 * It applies configurable smoothing and sensitivity and ensures the camera
 * stays at the correct eye height relative to the player spatial each frame.
 */
public class FirstPersonCameraControl extends AbstractControl implements AnalogListener {

    private final Camera cam;
    private final InputManager input;
    private final float eyeHeight;
    private final float hSensitivity;
    private final float vSensitivity;
    private final float smoothingFactor;

    private float targetYaw   = 0f;
    private float targetPitch = 0f;
    private float yaw         = 0f;
    private float pitch       = 0f;

    private static final String MAPPING_LEFT   = "FP_Mouse_Left";
    private static final String MAPPING_RIGHT  = "FP_Mouse_Right";
    private static final String MAPPING_UP     = "FP_Mouse_Up";
    private static final String MAPPING_DOWN   = "FP_Mouse_Down";

    /**
     * Constructs a new FirstPersonCameraControl.
     *
     * @param player        Reference to the Player node used to get camera and input
     * @param eyeHeight     Vertical offset of the camera above the player origin, in world units
     * @param hSensitivity  Horizontal mouse sensitivity multiplier
     * @param vSensitivity  Vertical mouse sensitivity multiplier
     * @param smoothing     How quickly the camera interpolates to the target rotation (0=no smoothing, 1=instant)
     */
    public FirstPersonCameraControl(Player player,
                                    float eyeHeight,
                                    float hSensitivity,
                                    float vSensitivity,
                                    float smoothing) {
        this.cam            = player.getCam();
        this.input          = player.getInput();
        this.eyeHeight      = eyeHeight;
        this.hSensitivity   = hSensitivity;
        this.vSensitivity   = vSensitivity;
        this.smoothingFactor = FastMath.clamp(smoothing, 0f, 1f);

        setupMouseMappings();
        input.setCursorVisible(false);
        // Enable raw cursor capture if supported:
        try {
            //input.setCursorCaptured(true);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private void setupMouseMappings() {
        input.addMapping(MAPPING_LEFT,  new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping(MAPPING_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping(MAPPING_UP,    new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addMapping(MAPPING_DOWN,  new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addListener(this, MAPPING_LEFT, MAPPING_RIGHT, MAPPING_UP, MAPPING_DOWN);
    }

    @Override
    protected void controlUpdate(float tpf) {
        // Smoothly interpolate yaw/pitch towards target
        float alpha = 1f - FastMath.pow(1f - smoothingFactor, tpf * 60f);
        yaw   = FastMath.interpolateLinear(alpha, yaw, targetYaw);
        pitch = FastMath.interpolateLinear(alpha, pitch, targetPitch);

        // Clamp pitch to avoid flipping
        float maxPitch = FastMath.HALF_PI - 0.01f;
        pitch = FastMath.clamp(pitch, -maxPitch, maxPitch);

        // Apply rotation
        Quaternion rotation = new Quaternion().fromAngles(pitch, yaw, 0f);
        cam.setRotation(rotation);

        // Update camera position to follow spatial + eyeHeight
        Vector3f pos = spatial.getWorldTranslation();
        cam.setLocation(pos.add(0, eyeHeight, 0));
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm,
                                 com.jme3.renderer.ViewPort vp) {
        // Not used
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        switch (name) {
            case MAPPING_LEFT  -> targetYaw   += value * hSensitivity;
            case MAPPING_RIGHT -> targetYaw   -= value * hSensitivity;
            case MAPPING_UP    -> targetPitch += value * vSensitivity;
            case MAPPING_DOWN  -> targetPitch -= value * vSensitivity;
        }
        // We update actual cam rotation in controlUpdate to avoid artifacts
    }

    @Override
    public void setSpatial(com.jme3.scene.Spatial spatial) {
        super.setSpatial(spatial);
        // Reset rotation targets when attaching
        targetYaw = yaw = 0f;
        targetPitch = pitch = 0f;
    }
}