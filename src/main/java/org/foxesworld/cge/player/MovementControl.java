package org.foxesworld.cge.player;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;

/**
 * MovementControl: движение в направлении взгляда камеры,
 * с плавным ускорением/торможением и событиями прыжка.
 */
public class MovementControl extends AbstractControl implements ActionListener {

    public interface JumpListener {
        void onJumpStart();
        void onLanding(float peakHeight);
    }

    private static final float MAX_SPEED    = 0.1f;   // м/с
    private static final float ACCELERATION = 20f;  // м/с²
    private static final float DECELERATION = 16f;  // м/с²

    private final CharacterControl character;
    private final InputManager    input;
    private final Camera          cam;
    private JumpListener jumpListener;

    private boolean forward, backward, left, right;
    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredDir = new Vector3f();

    // Для прыжков
    private boolean wasInAir = false;
    private float   lastY    = 0;
    private float   jumpPeak = 0;

    public MovementControl(Player player) {
        this.character = player.getCharacter();
        this.input     = player.getInput();
        this.cam       = player.getCam();
        initMappings();
    }

    private void initMappings() {
        input.addMapping("Forward",  new KeyTrigger(KeyInput.KEY_W));
        input.addMapping("Backward", new KeyTrigger(KeyInput.KEY_S));
        input.addMapping("Left",     new KeyTrigger(KeyInput.KEY_A));
        input.addMapping("Right",    new KeyTrigger(KeyInput.KEY_D));
        input.addMapping("Jump",     new KeyTrigger(KeyInput.KEY_SPACE));
        input.addListener(this, "Forward","Backward","Left","Right","Jump");
    }

    public void setJumpListener(JumpListener listener) {
        this.jumpListener = listener;
    }

    @Override
    protected void controlUpdate(float tpf) {
        // 1) Определяем желаемое направление в локальных осях камеры
        desiredDir.set(0, 0, 0);
        if (forward)  desiredDir.z += 1;
        if (backward) desiredDir.z -= 1;
        if (left)     desiredDir.x += 1;
        if (right)    desiredDir.x -= 1;

        Vector3f targetVel = new Vector3f();
        if (!desiredDir.equals(Vector3f.ZERO)) {
            // нормализуем и масштабируем
            desiredDir.normalizeLocal();
            // преобразуем из так называемых "камера-локальных" координат в мировые
            Vector3f camDir  = cam.getDirection().clone().setY(0).normalizeLocal();
            Vector3f camLeft = cam.getLeft().clone().setY(0).normalizeLocal();
            targetVel.set(camDir).multLocal(desiredDir.z)
                    .addLocal(camLeft.mult(desiredDir.x))
                    .multLocal(MAX_SPEED);
        }

        // 2) Плавное ускорение/торможение к targetVel
        Vector3f delta = targetVel.subtract(currentVel);
        float accelAmount = (targetVel.length() > currentVel.length()
                ? ACCELERATION : DECELERATION) * tpf;
        if (delta.length() > accelAmount) {
            delta.normalizeLocal().multLocal(accelAmount);
        }
        currentVel.addLocal(delta);

        // 3) Применяем к CharacterControl
        if (currentVel.lengthSquared() > 0.0001f) {
            character.setWalkDirection(currentVel.clone());
        } else {
            character.setWalkDirection(Vector3f.ZERO);
        }

        // 4) Детекция прыжка/приземления
        float currentY = character.getPhysicsLocation().y;
        boolean inAir = !character.onGround();
        if (inAir) {
            jumpPeak = Math.max(jumpPeak, currentY - lastY);
        }
        if (wasInAir && !inAir && jumpListener != null) {
            jumpListener.onLanding(jumpPeak);
            jumpPeak = 0;
        }
        wasInAir = inAir;
        lastY    = currentY;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Forward":  forward  = isPressed; break;
            case "Backward": backward = isPressed; break;
            case "Left":     left     = isPressed; break;
            case "Right":    right    = isPressed; break;
            case "Jump":
                if (isPressed && character.onGround()) {
                    character.jump();
                    if (jumpListener != null) jumpListener.onJumpStart();
                }
                break;
        }
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm,
                                 com.jme3.renderer.ViewPort vp) {
        // не используется
    }

    public boolean isMoving() {
        return !currentVel.equals(Vector3f.ZERO);
    }
}
