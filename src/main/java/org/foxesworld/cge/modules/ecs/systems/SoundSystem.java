// Файл: src/main/java/org/foxesworld/cge/modules/ecs/systems/SoundSystem.java
package org.foxesworld.cge.modules.ecs.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import org.foxesworld.cge.modules.ecs.EntitySystem;
import org.foxesworld.cge.modules.ecs.components.PlaySound;
import org.foxesworld.cge.modules.ecs.components.Position;
import org.foxesworld.cge.modules.sound.SoundModule;

import java.util.Objects;

/**
 * Проигрывает звуки по запросу.
 * Находит сущности с компонентом-командой PlaySound, проигрывает звук
 * и немедленно удаляет компонент, чтобы избежать повторного проигрывания.
 */
public class SoundSystem implements EntitySystem {
    private final SoundModule soundModule;
    private EntitySet entities;

    // >>> ИЗМЕНЕНИЕ 1: Добавляем поле для хранения EntityData <<<
    private EntityData ed;

    public SoundSystem(SoundModule soundModule) {
        this.soundModule = Objects.requireNonNull(soundModule, "SoundModule cannot be null");
    }

    @Override
    public void initialize(EntityData ed) {
        // >>> ИЗМЕНЕНИЕ 2: Сохраняем ссылку на EntityData <<<
        this.ed = ed;

        // Следим за сущностями, которым нужно проиграть звук и у которых есть позиция
        this.entities = ed.getEntities(PlaySound.class, Position.class);
    }

    @Override
    public void start() {}

    @Override
    public void update(float tpf) {
        if (entities.applyChanges()) {
            for (Entity e : entities.getAddedEntities()) {
                PlaySound command = e.get(PlaySound.class);
                Position pos = e.get(Position.class);

                soundModule.playSound(command.getSoundPath(), pos.getValue(), true, command.getVolume());

                // >>> ИЗМЕНЕНИЕ 3: Используем EntityData для удаления компонента <<<
                // Мы берем ID у сущности (e.getId()) и передаем его в EntityData
                ed.removeComponent(e.getId(), PlaySound.class);
            }
        }
    }

    @Override
    public void stop() {
        entities.release();
        entities = null;
    }
}