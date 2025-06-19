package org.foxesworld.cge.modules.player;

import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.control.AbstractControl;

/**
 * Пример управления камерой игрока.
 */
public class PlayerCameraControl extends AbstractControl {
    private final PlayerContext ctx;
    private final float eyeHeight;
    private final float sensitivity;
    private final float smooth;
    private final Node rootNode;

    public PlayerCameraControl(PlayerContext ctx, float eyeHeight, float sensitivity, float smooth, Node rootNode) {
        this.ctx = ctx;
        this.eyeHeight = eyeHeight;
        this.sensitivity = sensitivity;
        this.smooth = smooth;
        this.rootNode = rootNode;
    }

    @Override
    protected void controlUpdate(float tpf) {
        Camera cam = ctx.getCam();
        // Реализуйте позиционирование и вращение камеры
    }

    @Override
    protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
}