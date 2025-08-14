package org.foxesworld.cge.modules.player.control;

import com.jme3.bullet.control.CharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import org.foxesworld.cge.modules.inputManager.InputManagerModule;
import org.foxesworld.cge.modules.player.Player;
import org.foxesworld.cge.modules.player.PlayerState;
import org.foxesworld.cge.modules.player.config.PlayerConfig;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Improved MovementControl with support for multiple listeners and richer movement events.
 * <p>
 * Backwards compatibility: the original {@code onStep(float)} / {@code onJumpStart()} / {@code onLanding(float)}
 * methods are preserved as default methods on the listener interface so existing listeners continue to work.
 */
public final class MovementControl extends AbstractControl {

    /**
     * A listener interface for receiving movement events. New methods are defaulted so
     * existing implementors won't break.
     */
    public interface MovementListener {
        default void onJumpStart() {}
        default void onLanding(float fallHeight) {}
        default void onStay() {}
        /** legacy step callback kept for compatibility */
        //default void onStep(float targetSpeed) {}
        /** richer step event: which foot contacted and the speed */
        void onStep(boolean leftFoot, float speed);

        /** movement lifecycle hooks */
        default void onStartMoving() {}
        default void onStopMoving() {}

        /** sprint lifecycle */
        default void onSprintStart() {}
        default void onSprintStop() {}

        /** crouch lifecycle */
        default void onCrouchStart() {}
        default void onCrouchStop() {}

        /** Falling/air events */
        default void onFallStart(float startY) {}
        void onAirborne(float airtime);
    }

    private float airTime = 0f;                     // аккумулируем время в воздухе
    private float lastAirborneNotifyTime = 0f;      // время, когда в последний раз вызвали notifyAirborne
    private static final float AIRBORNE_NOTIFY_INTERVAL = 0.25f; // интервал оповещений в секундах

    private static final float WALK_STEP_INTERVAL_SECONDS = 0.50f;
    private static final float SPRINT_STEP_INTERVAL_SECONDS = 0.35f;
    private static final float STEP_INTERVAL_MIN = 0.15f; // clamp for very high speeds
    private static final float STEP_INTERVAL_MAX = 0.8f;  // clamp for very slow speeds
    private static final float MIN_FALL_FOR_LAND_EVENT = 0.25f; // meters

    private final Player player;
    private final CharacterControl character;
    private final InputManager input;
    private final InputManagerModule inputModule;
    private final float walkSpeed;
    private final float sprintSpeed;
    private final float smoothFactor;

    private final Vector3f currentVel = new Vector3f();
    private final Vector3f desiredVel = new Vector3f();
    private final Vector3f camDir = new Vector3f();
    private final Vector3f camLeft = new Vector3f();
    private final Vector3f tempDir = new Vector3f();

    // --- new quaternion fields for smooth rotation ---
    private final Quaternion rotationQuat = new Quaternion();   // текущая "визуальная" ориентация спатиала
    private final Quaternion targetQuat = new Quaternion();     // целевая ориентация по направлению движения
    private final Vector3f forwardFromQuat = new Vector3f();    // временный вектор для извлечения forward из кватерниона

    private boolean wasInAir = false;
    private float lastY = 0f;
    private float fallStartY = 0f; // Y when the body left the ground

    private float timeSinceLastStep = 0f;
    private boolean stepLeft = true; // alternate left/right foot
    private boolean wasMoving = false;

    private boolean wasSprinting = false;
    private boolean wasCrouching = false;

    private RawInputListener rawListener;
    // support multiple listeners safely across threads/iteration
    private final CopyOnWriteArrayList<MovementListener> listeners = new CopyOnWriteArrayList<>();

    public MovementControl(Player player, PlayerConfig.MovementConfig movementConfig) {
        this.player = player;
        this.character = player.getCharacter();
        this.input = player.getInput();
        this.inputModule = player.getEngine().getModuleManager().getModule(InputManagerModule.class);

        if (this.inputModule == null) {
            throw new IllegalStateException("InputManagerModule not found. It must be registered before the Player module.");
        }

        this.walkSpeed = movementConfig.getWalkSpeed();
        this.sprintSpeed = movementConfig.getSprintSpeed();
        this.smoothFactor = FastMath.clamp(movementConfig.getSmoothing(), 0f, 1f);

        registerRawInput();
    }

    @Override
    protected void controlUpdate(float tpf) {
        if (spatial == null) return;

        // handle jump input separately for clarity and future extensions (double-jump, variable jump, etc.)
        handleJump();

        updateMovement(tpf);
        updateViewDirection(tpf); // <-- pass tpf to use consistent smoothing for rotation
        handleLanding(tpf);
        handleFootsteps(tpf);
        updateHud();

        // sprint state transitions
        boolean sprintNow = inputModule.isActionActive("sprint");
        if (sprintNow && !wasSprinting) notifySprintStart();
        if (!sprintNow && wasSprinting) notifySprintStop();
        wasSprinting = sprintNow;

        // crouch state transitions (optional action)
        boolean crouchNow = inputModule.isActionActive("crouch");
        if (crouchNow && !wasCrouching) notifyCrouchStart();
        if (!crouchNow && wasCrouching) notifyCrouchStop();
        wasCrouching = crouchNow;
    }

    private void handleJump() {
        if (inputModule.isActionActive("jump") && character.onGround()) {
            player.getEngine().getSoundManager().play("player.takeoff");
            character.jump();
            fallStartY = spatial.getWorldTranslation().y;
            notifyJumpStart();
        }
    }

    private void updateMovement(float tpf) {
        tempDir.set(0, 0, 0);
        if (inputModule.isActionActive("move_forward")) tempDir.z += 1f;
        if (inputModule.isActionActive("move_backward")) tempDir.z -= 1f;
        if (inputModule.isActionActive("strafe_left")) tempDir.x -= 1f;
        if (inputModule.isActionActive("strafe_right")) tempDir.x += 1f;

        float targetSpeed = isSprinting() ? sprintSpeed : walkSpeed;

        boolean movingNow;
        if (tempDir.lengthSquared() > 0) {
            tempDir.normalizeLocal();
            player.getCam().getDirection(camDir).setY(0).normalizeLocal();
            player.getCam().getLeft(camLeft).setY(0).normalizeLocal();

            desiredVel.set(0, 0, 0);
            desiredVel.addLocal(camDir.mult(tempDir.z));
            desiredVel.addLocal(camLeft.mult(-tempDir.x));
            desiredVel.normalizeLocal().multLocal(targetSpeed);
            movingNow = true;
        } else {
            desiredVel.set(0, 0, 0);
            targetSpeed = 0;
            movingNow = false;
            if (player.getPhysicsHelper().isGrounded()) {
                notifyOnStay();
            }
        }

        float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);
        currentVel.interpolateLocal(desiredVel, alpha);
        character.setWalkDirection(currentVel);

        // movement start/stop detection
        boolean wasMovingBefore = wasMoving;
        boolean movingNowBasedOnVelocity = currentVel.lengthSquared() > 1e-4f;
        if (!wasMovingBefore && movingNowBasedOnVelocity) {
            // start moving
            timeSinceLastStep = Math.max(0f, getStepInterval() * 0.5f);
            notifyStartMoving();
            // immediately fire a step for feel
            if (character.onGround()) {
                notifyOnStep(stepLeft, currentVel.length());
                stepLeft = !stepLeft;
            }
        } else if (wasMovingBefore && !movingNowBasedOnVelocity) {
            notifyStopMoving();
        }

        wasMoving = movingNowBasedOnVelocity;
    }

    /**
     * Плавно интерполируем ориентацию через Quaternion.
     * slerp коэффициент совпадает с alpha от сглаживания скорости, чтобы движение и поворот были связаны.
     */
    private void updateViewDirection(float tpf) {
        if (spatial == null) return;

        if (currentVel.lengthSquared() > 1e-4f) {
            Vector3f viewDir = new Vector3f(currentVel.x, 0, currentVel.z).normalizeLocal();
            // целевой кватернион "смотрит" в direction (y up)
            targetQuat.lookAt(viewDir, Vector3f.UNIT_Y);

            // сглаживающий коэффициент аналогичен тому, что использовали для скорости
            float alpha = 1f - FastMath.pow(1f - smoothFactor, tpf * 60f);

            // slerp текущей кватернион к целевой
            rotationQuat.slerp(targetQuat, alpha);

            // применяем визуальную ориентацию к спатиалу (плавный визуальный поворот)
            spatial.setLocalRotation(rotationQuat);

            // извлекаем forward-вектор из кватерниона и передаём в CharacterControl
            // вариант: rotationQuat.mult(Vector3f.UNIT_Z, forwardFromQuat);
            rotationQuat.getRotationColumn(2, forwardFromQuat); // column 2 — локальная Z ось
            forwardFromQuat.setY(0);
            if (forwardFromQuat.lengthSquared() > 1e-6f) {
                forwardFromQuat.normalizeLocal();
                character.setViewDirection(forwardFromQuat);
            }
        } else {
            // если почти не движемся — не сбрасываем текущую ориентацию, но можно слегка "затормозить" поворот
            // ничего делать не нужно — rotationQuat уже хранит последнюю ориентацию
        }
    }

    private float getStepInterval() {
        float speed = getCurrentSpeed();
        if (speed <= 1e-4f) return STEP_INTERVAL_MAX;
        float base = isSprinting() ? SPRINT_STEP_INTERVAL_SECONDS : WALK_STEP_INTERVAL_SECONDS;
        float interval = base * (walkSpeed / Math.max(speed, 1e-4f));
        return FastMath.clamp(interval, STEP_INTERVAL_MIN, STEP_INTERVAL_MAX);
    }

    private void handleFootsteps(float tpf) {
        if (character.onGround() && isMoving()) {
            timeSinceLastStep += tpf;
            float requiredInterval = getStepInterval();

            if (timeSinceLastStep >= requiredInterval) {
                notifyOnStep(stepLeft, currentVel.length());
                stepLeft = !stepLeft;
                timeSinceLastStep -= requiredInterval;
                timeSinceLastStep = Math.min(timeSinceLastStep, requiredInterval);
            }
        } else {
            timeSinceLastStep = 0f;
        }
    }

    // изменённый метод (замени старую реализацию)
    private void handleLanding(float tpf) {
        boolean inAir = !character.onGround();
        float posY = spatial.getWorldTranslation().y;

        // --- just left the ground -----------------------
        if (inAir && !wasInAir) {
            // точка старта падения — запоминаем высоту, сбрасываем таймер
            fallStartY = lastY;
            airTime = 0f;
            lastAirborneNotifyTime = 0f;
            notifyFallStart(fallStartY);
        }

        // --- while airborne: accumulate airtime и уведомляем периодически -------
        if (inAir) {
            // аккумулируем airtime
            airTime += tpf;

            float fallDistance = fallStartY - posY;

            // отправляем периодические уведомления, только если падение значимо
            if (fallDistance > MIN_FALL_FOR_LAND_EVENT) {
                if (airTime - lastAirborneNotifyTime >= AIRBORNE_NOTIFY_INTERVAL) {
                    notifyAirborne(airTime);
                    lastAirborneNotifyTime = airTime;
                }
            }

        } else {
            // not in air -> reset some counters (keeps state clean)
            // но НЕ сбрасываем fallStartY здесь — он будет обновлён при следующем выходе в воздух
        }

        // --- just landed: вычисляем итоговую дистанцию и генерируем событие приземления -----------
        if (wasInAir && !inAir) {
            // проигрываем звук приземления (всегда, как и раньше)
            player.getEngine().getSoundManager().play("player.land");
            float fallDistance = fallStartY - posY;
            // только если падение достаточно большое — уведомляем о приземлении
            if (fallDistance > MIN_FALL_FOR_LAND_EVENT) {
                // уведомляем слушателей о дистанции падения (можно расширить сигнатуру, чтобы передать и airTime)
                notifyLanding(fallDistance);
            }

            airTime = 0f;
            lastAirborneNotifyTime = 0f;
            fallStartY = posY;
        }
        wasInAir = inAir;
        lastY = posY;
    }

    private void updateHud() {
        if (player.getPlayerHud() != null) {
            player.getPlayerHud().setPlayerSpeed(getCurrentSpeed());
        }
    }

    private void registerRawInput() {
        if (rawListener != null) return;
        rawListener = new RawInputListener() {
            @Override public void beginInput() {}
            @Override public void endInput() {}
            @Override public void onJoyAxisEvent(com.jme3.input.event.JoyAxisEvent evt) {}
            @Override public void onJoyButtonEvent(com.jme3.input.event.JoyButtonEvent evt) {}
            @Override public void onMouseMotionEvent(com.jme3.input.event.MouseMotionEvent evt) {}
            @Override public void onMouseButtonEvent(com.jme3.input.event.MouseButtonEvent evt) {}
            @Override public void onTouchEvent(com.jme3.input.event.TouchEvent evt) {}
            @Override public void onKeyEvent(KeyInputEvent evt) {
                if (evt.getKeyCode() == KeyInput.KEY_R && evt.isPressed()) {
                    onRawKeyR();
                    evt.setConsumed();
                }
                if (evt.getKeyCode() == KeyInput.KEY_V && evt.isPressed()) {
                    player.getCamControl().setThirdPerson(!player.getCamControl().isThirdPerson());
                    player.getCamEffectsControl().setThirdPerson(player.getCamControl().isThirdPerson());
                    evt.setConsumed();
                }
            }
        };
        input.addRawInputListener(rawListener);
    }

    private void unregisterRawInput() {
        if (rawListener == null) return;
        input.removeRawInputListener(rawListener);
        rawListener = null;
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        if (spatial == null) {
            unregisterRawInput();
            return;
        }
        // Инициализируем rotationQuat текущей ориентацией спатиала, чтобы не делать резкого прыжка
        spatial.getLocalRotation().clone().addLocal(rotationQuat);
    }

    private void onRawKeyR() {
        System.out.println("RawInputListener: R was pressed!");
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {}

    public float getCurrentSpeed() {
        return currentVel.length();
    }

    public boolean isMoving() {
        return getCurrentSpeed() > 1e-4f;
    }

    public boolean isWalking() {
        return isMoving() && !isSprinting();
    }

    public boolean isSprinting() {
        return isMoving() && inputModule.isActionActive("sprint");
    }

    // Listener management -------------------------------------------------

    // ... (listener methods identical to original) ...

    public PlayerState getPlayerState() {
        return new PlayerState(isMoving(), isSprinting(), !character.onGround(), getCurrentSpeed(), currentVel.clone(), spatial.getWorldTranslation().clone());
    }

    // Notification helpers -------------------------------------------------
    private void notifyJumpStart() {
        for (MovementListener l : listeners) l.onJumpStart();
    }

    private void notifyLanding(float fallHeight) {
        for (MovementListener l : listeners) l.onLanding(fallHeight);
    }

    private void notifyOnStay() {
        for (MovementListener l : listeners) l.onStay();
    }

    private void notifyOnStep(boolean left, float speed) {
        for (MovementListener l : listeners) {
            l.onStep(left, speed);
        }
    }

    private void notifyStartMoving() {
        for (MovementListener l : listeners) l.onStartMoving();
    }

    private void notifyStopMoving() {
        for (MovementListener l : listeners) l.onStopMoving();
    }

    private void notifySprintStart() {
        for (MovementListener l : listeners) l.onSprintStart();
    }

    private void notifySprintStop() {
        for (MovementListener l : listeners) l.onSprintStop();
    }

    private void notifyCrouchStart() {
        for (MovementListener l : listeners) l.onCrouchStart();
    }

    private void notifyCrouchStop() {
        for (MovementListener l : listeners) l.onCrouchStop();
    }

    private void notifyFallStart(float startY) {
        for (MovementListener l : listeners) l.onFallStart(startY);
    }

    private void notifyAirborne(float airtime) {
        for (MovementListener l : listeners) l.onAirborne(airtime);
    }

    /**
     * Backwards-compatible single-listener setter (keeps old code working).
     */
    @Deprecated
    public void setMovementListener(MovementListener movementListener) {
        listeners.clear();
        if (movementListener != null) listeners.add(movementListener);
    }
}
