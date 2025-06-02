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
 * FirstPersonCameraControl обеспечивает «реал‐тайм» mouse‐look без артефактов
 * и «мигания» прошлого кадра. Камера привязывается напрямую к объекту Player:
 * каждый кадр мы устанавливаем положение камеры на (worldX, worldY+eyeHeight, worldZ)
 * и поворачиваем её по yaw/pitch, без использования CameraNode.
 */
public class FirstPersonCameraControl extends AbstractControl implements AnalogListener {

    private final Camera       cam;
    private final InputManager input;
    private final float        eyeHeight;
    private float              yaw       = 0f;
    private float              pitch     = 0f;
    private final float        hSensitivity;
    private final float        vSensitivity;

    private static final String MOUSE_LEFT  = "FP_Mouse_Left";
    private static final String MOUSE_RIGHT = "FP_Mouse_Right";
    private static final String MOUSE_UP    = "FP_Mouse_Up";
    private static final String MOUSE_DOWN  = "FP_Mouse_Down";

    /**
     * @param player        ссылка на Player (даёт доступ к getInput() и getCam())
     * @param eyeHeight     высота камеры над головой игрока (например, 1.6f)
     * @param hSensitivity  чувствительность горизонтального поворота
     * @param vSensitivity  чувствительность вертикального поворота
     */
    public FirstPersonCameraControl(Player player, float eyeHeight, float hSensitivity, float vSensitivity) {
        this.cam           = player.getCam();
        this.input         = player.getInput();
        this.eyeHeight     = eyeHeight;
        this.hSensitivity  = hSensitivity;
        this.vSensitivity  = vSensitivity;

        initMouseMappings();
        input.setCursorVisible(false);
        // Если в вашей JME‐версии доступно захватить курсор, допишите:
        // input.setCursorCaptured(true);
    }

    private void initMouseMappings() {
        // Четыре маппинга: влево/вправо по оси X, вверх/вниз по оси Y
        input.addMapping(MOUSE_LEFT,  new MouseAxisTrigger(MouseInput.AXIS_X, true));
        input.addMapping(MOUSE_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        input.addMapping(MOUSE_UP,    new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        input.addMapping(MOUSE_DOWN,  new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        input.addListener(this, MOUSE_LEFT, MOUSE_RIGHT, MOUSE_UP, MOUSE_DOWN);
    }

    @Override
    protected void controlUpdate(float tpf) {
        // 1) Применяем yaw/pitch к камере
        float maxPitch = FastMath.HALF_PI - 0.01f;
        if (pitch > maxPitch)  pitch = maxPitch;
        if (pitch < -maxPitch) pitch = -maxPitch;
        Quaternion rot = new Quaternion().fromAngles(pitch, yaw, 0);
        cam.setRotation(rot);

        // 2) Устанавливаем положение камеры:
        //    берём мировую позицию объекта (spatial) и добавляем eyeHeight по Y
        Vector3f worldPos = spatial.getWorldTranslation();
        cam.setLocation(new Vector3f(
                worldPos.x,
                worldPos.y + eyeHeight,
                worldPos.z));
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {
        // Не используется
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        switch (name) {
            case MOUSE_LEFT  -> yaw   += value * hSensitivity;
            case MOUSE_RIGHT -> yaw   -= value * hSensitivity;
            case MOUSE_UP    -> pitch += value * vSensitivity;
            case MOUSE_DOWN  -> pitch -= value * vSensitivity;
        }
        // Обновление поворота произойдёт в controlUpdate(), избегая «мигания»
    }
}
