package org.foxesworld.cge.player;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.*;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.control.AbstractControl;

/**
 * MovementControl: движение в направлении взгляда камеры,
 * с плавным ускорением/торможением, событиями прыжка и возможностью читать текущую скорость.
 */
public class MovementControl extends AbstractControl implements ActionListener {

    public interface JumpListener {
        void onJumpStart();
        void onLanding(float peakHeight);
    }

    // Максимальные параметры, можно подстроить под желаемую скорость
    private final float maxSpeed;       // м/с
    private final float acceleration;   // м/с²
    private final float deceleration;   // м/с²

    private final Player player;
    private final CharacterControl character;
    private final InputManager    input;
    private final Camera          cam;
    private JumpListener          jumpListener;

    // флаги ввода
    private boolean forward, backward, left, right;

    // текущая и целевая скорости
    private final Vector3f currentVel = new Vector3f();
    private final Vector3f targetVel  = new Vector3f();

    // Для прыжков и приземлений
    private boolean wasInAir = false;
    private float   lastY    = 0;
    private float   jumpPeak = 0;

    public MovementControl(Player player) {
        // Значения можно вытянуть из Player или задать конструктором
        this.player = player;
        this.maxSpeed     = 0.1f;     // ~5 м/с
        this.acceleration = 20f;    // ~20 м/с²
        this.deceleration = 16f;    // ~16 м/с²

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
        input.addListener(this,
                "Forward","Backward","Left","Right","Jump"
        );
    }

    @Override
    protected void controlUpdate(float tpf) {
        // 1) Определяем целевую скорость по вводу и направлению камеры
        Vector3f dir = new Vector3f();
        if (forward)  dir.z += 1;
        if (backward) dir.z -= 1;
        if (left)     dir.x += 1;
        if (right)    dir.x -= 1;

        if (!dir.equals(Vector3f.ZERO)) {
            dir.normalizeLocal();
            // преобразуем локальную камеру в мировое направление
            Vector3f camDir  = cam.getDirection().clone().setY(0).normalizeLocal();
            Vector3f camLeft = cam.getLeft().clone().setY(0).normalizeLocal();
            targetVel.set(camDir).multLocal(dir.z)
                    .addLocal(camLeft.mult(dir.x))
                    .multLocal(maxSpeed);
        } else {
            targetVel.set(0, 0, 0);
        }

        // 2) Плавное ускорение/торможение
        Vector3f delta = targetVel.subtract(currentVel);
        float accelValue = (targetVel.length() > currentVel.length()
                ? acceleration : deceleration) * tpf;

        if (delta.length() > accelValue) {
            delta.normalizeLocal().multLocal(accelValue);
        }
        currentVel.addLocal(delta);

        // 3) Применяем к CharacterControl
        if (currentVel.lengthSquared() > 1e-4f) {
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
        //((TextElement)this.player.getUiPanel().getElement("speedItem")).setText(String.valueOf(jumpPeak));
        player.getPlayerHud().setPlayerSpeed(getCurrentSpeed());
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "Forward" -> forward = isPressed;
            case "Backward" -> backward = isPressed;
            case "Left" -> left = isPressed;
            case "Right" -> right = isPressed;
            case "Jump" -> {
                if (isPressed && character.onGround()) {
                    character.jump();
                    if (jumpListener != null) {
                        jumpListener.onJumpStart();
                    }
                }
            }
        }
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm,
                                 com.jme3.renderer.ViewPort vp) {
        // не используется
    }

    public void setJumpListener(JumpListener listener) {
        this.jumpListener = listener;
    }

    /**
     * Возвращает текущую горизонтальную скорость персонажа (м/с).
     */
    public float getCurrentSpeed() {
        return currentVel.length();
    }

    /**
     * Возвращает максимальную скорость персонажа (м/с).
     */
    public float getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * true, если персонаж сейчас движется.
     */
    public boolean isMoving() {
        return currentVel.lengthSquared() > 1e-4f;
    }
}
