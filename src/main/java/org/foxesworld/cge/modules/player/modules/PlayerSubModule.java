package org.foxesworld.cge.modules.player.modules;

import org.foxesworld.cge.modules.player.PlayerContext;

/**
 * Базовый интерфейс для модулей игрока: движения, эффектов, UI, способностей и т.д.
 */
public interface PlayerSubModule {
    void onAttach(PlayerContext context);
    void onDetach();
    void update(float tpf);
}