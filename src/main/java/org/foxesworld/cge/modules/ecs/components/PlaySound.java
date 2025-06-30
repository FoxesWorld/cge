// Файл: src/main/java/org/foxesworld/cge/modules/ecs/components/PlaySound.java
package org.foxesworld.cge.modules.ecs.components;

import com.simsilica.es.EntityComponent;

/**
 * Компонент-команда, который указывает SoundSystem проиграть звук для этой сущности.
 *
 * Это "одноразовый" компонент: SoundSystem обнаруживает его, выполняет действие
 * и немедленно удаляет компонент с сущности, чтобы избежать повторного
 * проигрывания в следующем кадре.
 */
public class PlaySound implements EntityComponent {

    private final String soundPath;
    private final float volume;

    /**
     * Создает команду на проигрывание звука.
     *
     * @param soundPath Путь к звуковому файлу в ассетах (например, "Sounds/bang.ogg").
     * @param volume    Громкость звука (обычно от 0.0 до 1.0).
     */
    public PlaySound(String soundPath, float volume) {
        if (soundPath == null || soundPath.isEmpty()) {
            throw new IllegalArgumentException("Sound path cannot be null or empty.");
        }
        this.soundPath = soundPath;
        this.volume = volume;
    }

    /**
     * Возвращает путь к звуковому файлу.
     */
    public String getSoundPath() {
        return soundPath;
    }

    /**
     * Возвращает громкость звука.
     */
    public float getVolume() {
        return volume;
    }

    @Override
    public String toString() {
        return "PlaySound[path='" + soundPath + "', volume=" + volume + "]";
    }
}