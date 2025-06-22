package org.foxesworld.cge.modules.player;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.tween.action.BlendAction;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.RawInputListener;
import com.jme3.input.controls.*;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.Spatial;
import org.foxesworld.cge.modules.player.config.PlayerConfig;
import org.foxesworld.cge.modules.player.animation.AnimLayerControl;

import static java.lang.Math.max;

/**
 * MovementControl handles first-person character motion with smooth
 * acceleration, deceleration, and jump/landing callbacks. It relies on
 * CharacterControl for robust physics and collision handling.
 *
 * Подготовлен к интеграции с системой анимаций через AnimComposer и AnimLayerControl.
 */
public class MovementControl extends AbstractControl implements ActionListener {

    public interface JumpListener {
        void onJumpStart();
        void onLanding(float peakHeight);
    }

    public interface MovementListener {
        void move(float speed);
    }

    private static final String MAP_FORWARD   = "MoveForward";
    private static final String MAP_BACKWARD  = "MoveBackward";
    private static final String MAP_LEFT      = "MoveLeft";
    private static final String MAP_RIGHT     = "MoveRight";
    private static final String MAP_JUMP      = "Jump";
    private static final String MAP_SPRINT    = "Sprint";

    private final PlayerContext player;
    private final CharacterControl character;
    private final InputManager input;

    private final float walkSpeed;
    private final float sprintSpeed;
    private final float acceleration;
    private final float deceleration;
    private final float smoothFactor;

    private boolean forward, backward, left, right, sprint;
    private JumpListener jumpListener;
    private MovementListener movementListener;

    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camDir     = new Vector3f();
    private final Vector3f camLeft    = new Vector3f();
    private final Vector3f tempDir    = new Vector3f();

    private boolean wasInAir = false;
    private float lastY = 0f;
    private float peakY = 0f;
    private boolean inputRegistered = false;

    // Raw input гарантирует захват всех клавиш, даже если GUI или другие контролы их "съедают"
    private boolean rawRegistered = false;
    private RawInputListener rawListener;

    // --- Для анимации ---
    private AnimLayerControl animLayerControl;
    private AnimComposer animComposer;
    private float walkRunBlend = 0f;
    private boolean prevMoving = false;
    private boolean prevSprinting = false;

    public MovementControl(PlayerContext player, PlayerConfig.MovementConfig movementConfig) {
        this.player        = player;
        this.character     = player.getCharacter();
        this.input         = player.getInput();
        this.walkSpeed     = movementConfig.getWalkSpeed();
        this.sprintSpeed   = movementConfig.getSprintSpeed();
        this.acceleration  = movementConfig.getAcceleration();
        this.deceleration  = movementConfig.getDeceleration();
        this.smoothFactor  = FastMath.clamp(movementConfig.getSmoothing(), 0f, 1f);

        registerInput();
        registerRawInput();
        // Готовим к анимации: ищем AnimLayerControl и AnimComposer на модели игрока
        this.animLayerControl = player instanceof Player playerImpl ? playerImpl.getAnimLayerControl() : null;
        this.animComposer = player instanceof Player playerImpl ? playerImpl.getAnimComposer() : null;
    }

    private void registerInput() {
        if (inputRegistered) return;
        inputRegistered = true;
        if (!input.hasMapping(MAP_FORWARD))  input.addMapping(MAP_FORWARD,  new KeyTrigger(KeyInput.KEY_W));
        if (!input.hasMapping(MAP_BACKWARD)) input.addMapping(MAP_BACKWARD, new KeyTrigger(KeyInput.KEY_S));
        if (!input.hasMapping(MAP_LEFT))     input.addMapping(MAP_LEFT,     new KeyTrigger(KeyInput.KEY_A));
        if (!input.hasMapping(MAP_RIGHT))    input.addMapping(MAP_RIGHT,    new KeyTrigger(KeyInput.KEY_D));
        if (!input.hasMapping(MAP_JUMP))     input.addMapping(MAP_JUMP,     new KeyTrigger(KeyInput.KEY_SPACE));
        if (!input.hasMapping(MAP_SPRINT))   input.addMapping(MAP_SPRINT,   new KeyTrigger(KeyInput.KEY_LSHIFT));
        input.removeListener(this); // гарантируем, что нет дубля
        input.addListener(this, MAP_FORWARD, MAP_BACKWARD, MAP_LEFT, MAP_RIGHT, MAP_JUMP, MAP_SPRINT);
    }

    private void registerRawInput() {
        if (rawRegistered) return;
        rawListener = new RawInputListener() {
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}

            @Override
            public void onKeyEvent(KeyInputEvent evt) {
                // пример: захватить всегда клавишу R для респауна (или любую другую)
                if (evt.getKeyCode() == KeyInput.KEY_R && evt.isPressed()) {
                    onRawKeyR();
                    evt.setConsumed(); // если не хотите, чтобы другие ловили R
                }
                if (evt.getKeyCode() == KeyInput.KEY_C && evt.isPressed()) {
                    if(!player.getCamControl().isThirdPerson()) {
                        player.getCamControl().setThirdPerson(true);
                        player.getCamEffectsControl().setThirdPerson(true);
                    } else {
                        player.getCamControl().setThirdPerson(false);
                        player.getCamEffectsControl().setThirdPerson(false);
                    }
                    evt.setConsumed();
                }
            }
        };
        input.addRawInputListener(rawListener);
        rawRegistered = true;
    }

    private void unregisterInput() {
        if (!inputRegistered) return;
        inputRegistered = false;
        input.removeListener(this);
    }

    private void unregisterRawInput() {
        if (!rawRegistered) return;
        input.removeRawInputListener(rawListener);
        rawRegistered = false;
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial == null) {
            unregisterInput();
            unregisterRawInput();
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null || player == null || character == null) return;

        tempDir.set(0, 0, 0);
        if (forward)  tempDir.z += 1f;
        if (backward) tempDir.z -= 1f;
        if (left)     tempDir.x += 1f;
        if (right)    tempDir.x -= 1f;

        if (tempDir.lengthSquared() > 0f) {
            tempDir.normalizeLocal();
            player.getCam().getDirection(camDir).setY(0).normalizeLocal();
            player.getCam().getLeft(camLeft).setY(0).normalizeLocal();
            desiredVel.set(camDir).multLocal(tempDir.z)
                    .addLocal(camLeft.mult(tempDir.x)).normalizeLocal();
            desiredVel.multLocal(sprint ? sprintSpeed : walkSpeed);
            movementListener.move(sprint ? sprintSpeed : walkSpeed);
        } else {
            desiredVel.set(0, 0, 0);
        }

        float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);
        currentVel.interpolateLocal(desiredVel, alpha);
        character.setWalkDirection(currentVel);

        if (currentVel.lengthSquared() > 1e-4f) {
            Vector3f viewDir = new Vector3f(currentVel.x, 0, currentVel.z).normalizeLocal();
            character.setViewDirection(viewDir);
        }

        boolean inAir = !character.onGround();
        float posY = spatial.getWorldTranslation().y;

        if (inAir && posY > lastY) {
            peakY = max(peakY, posY);
        }
        if (wasInAir && !inAir && jumpListener != null) {
            jumpListener.onLanding(FastMath.abs(peakY - lastY));
            peakY = 0f;
        }
        wasInAir = inAir;
        lastY    = posY;

        if (player.getPlayerHud() != null) {
            player.getPlayerHud().setPlayerSpeed(getCurrentSpeed());
        }

        updateAnimationState();
    }

    /**
     * Логика переключения и blending анимаций, полностью готова к AnimLayerControl/AnimComposer.
     */
    private void updateAnimationState() {
        if (animLayerControl == null || animComposer == null) return;

        boolean moving = isMoving();
        boolean sprinting = isSprinting();

        // Blend между walk и run
        if (moving) {
            walkRunBlend = sprinting ? 1f : 0f;

            if (animComposer.action("walk->run") instanceof BlendAction blend) {
                blend.getBlendSpace().setValue(walkRunBlend);
            }
        }

        // Переключение слоёв
        if (moving && !prevMoving) {
            //player.getAnimationController().setAnimation(sprinting ? "walk" : "sprint", 5f, "move", false);
        }
        if (!moving && prevMoving) {
            animLayerControl.exit("move");
        }

        prevMoving = moving;
        prevSprinting = sprinting;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case MAP_FORWARD   -> forward  = isPressed;
            case MAP_BACKWARD  -> backward = isPressed;
            case MAP_LEFT      -> left     = isPressed;
            case MAP_RIGHT     -> right    = isPressed;
            case MAP_SPRINT    -> sprint   = isPressed;
            case MAP_JUMP      -> {
                if (isPressed && character.onGround()) {
                    character.jump();
                    peakY = spatial.getWorldTranslation().y;
                    if (jumpListener != null) {
                        jumpListener.onJumpStart();
                    }
                }
            }
        }
    }

    protected void onRawKeyR() {
        // Пример: респаун игрока по клавише R (или любая другая логика)
        System.out.println("RawInputListener: R was pressed! (guaranteed catch)");
        // player.respawn(); // если реализовано
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {
        // Not used
    }

    public void setJumpListener(JumpListener listener) {
        this.jumpListener = listener;
    }

    public float getCurrentSpeed() {
        return currentVel.length();
    }

    public boolean isMoving() {
        return getCurrentSpeed() > 1e-4f;
    }

    public boolean isWalking() {
        return isMoving() && !sprint;
    }

    public boolean isSprinting() {
        return isMoving() && sprint;
    }

    public void setMovementListener(MovementListener movementListener) {
        this.movementListener = movementListener;
    }

    /**
     * Returns a snapshot of the current player state for UI or logic.
     */
    public PlayerState getPlayerState() {
        boolean moving = isMoving();
        boolean sprinting = sprint;
        boolean inAir = !character.onGround();
        float speed = getCurrentSpeed();
        Vector3f velocity = currentVel.clone();
        Vector3f position = spatial != null ? spatial.getWorldTranslation().clone() : new Vector3f();
        return new PlayerState(moving, sprinting, inAir, speed, velocity, position);
    }
}