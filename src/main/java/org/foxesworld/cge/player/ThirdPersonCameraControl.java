package org.foxesworld.cge.player;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;

public class ThirdPersonCameraControl extends AbstractControl {
    private final Camera cam;
    private final float eyeHeight;

    public ThirdPersonCameraControl(Player player, float eyeHeight) {
        this.cam = player.getCam();
        this.eyeHeight = eyeHeight;
    }

    @Override
    protected void controlUpdate(float tpf) {
        Vector3f pos = spatial.getWorldTranslation();
        // Камера за спиной
        Vector3f behind = pos.add(0, eyeHeight * 2, -4);
        cam.setLocation(behind);
        cam.lookAt(pos.add(0, eyeHeight, 0), Vector3f.UNIT_Y);
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) { }
}
