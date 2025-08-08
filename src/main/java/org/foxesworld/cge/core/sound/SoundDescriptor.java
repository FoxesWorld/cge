package org.foxesworld.cge.core.sound;

import com.jme3.audio.AudioNode;

/**
 * POJO descriptor для звуков. Gson корректно десериализует этот класс.
 *
 * Поля public чтобы Gson мог заполнять их напрямую (простая POJO-модель).
 * AudioNode помечен transient — он создаётся в рантайме при предзагрузке.
 */
public class SoundDescriptor {
    public String path;            // путь в assets, например "Sounds/ui/hover.ogg"
    public float volume = 1.0f;    // 0..1
    public float pitch = 1.0f;     // базовый pitch
    public float pitchVariance = 0f; // ±variance для рандома
    public boolean loop = false;   // looping?
    public long cooldownMs = 0L;   // минимальный интервал между проигрываниями этого дескриптора

    // transient runtime field (не сериализуется)
    public transient AudioNode audioNode;

    public SoundDescriptor() {
    }

    @Override
    public String toString() {
        return "SoundDescriptor{" +
                "path='" + path + '\'' +
                ", volume=" + volume +
                ", pitch=" + pitch +
                ", pitchVariance=" + pitchVariance +
                ", loop=" + loop +
                ", cooldownMs=" + cooldownMs +
                '}';
    }
}