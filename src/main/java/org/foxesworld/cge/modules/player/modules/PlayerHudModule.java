package org.foxesworld.cge.modules.player.modules;


import org.foxesworld.cge.modules.player.Player;
import org.foxesworld.cge.modules.player.PlayerContext;

/**
 * Пример модуля для HUD игрока.
 */
public class PlayerHudModule implements PlayerSubModule {
    private PlayerContext ctx;

    @Override
    public void onAttach(PlayerContext context) {
        this.ctx = context;
        // Можно добавить/инициализировать HUD
    }

    @Override
    public void onDetach() {
        // Очистить HUD
    }

    @Override
    public void update(float tpf) {
        ctx.getPlayerHud().update(tpf);
    }
}