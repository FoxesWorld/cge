package org.foxesworld.cge.modules.player.modules;


import com.jme3.renderer.Camera;
import org.foxesworld.cge.modules.player.PlayerContext;

/**
 * Пример модуля эффектов камеры (боббинг, прыжок и т.д.).
 */
public class CameraEffectsModule implements PlayerSubModule {
    private PlayerContext ctx;

    @Override
    public void onAttach(PlayerContext context) {
        this.ctx = context;
        // Здесь можно подписаться на события прыжка, посадки, движения
    }

    @Override
    public void onDetach() {
        // Очистка
    }

    @Override
    public void update(float tpf) {
        Camera cam = ctx.getCam();
        // Реализуйте эффекты камеры на основе состояния игрока
    }
}