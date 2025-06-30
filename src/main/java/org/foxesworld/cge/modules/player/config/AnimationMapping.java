package org.foxesworld.cge.modules.player.config;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import java.util.HashMap;

/**
 * Хранит карту соответствий между логическими именами анимаций (используемыми в коде)
 * и реальными именами анимационных клипов в 3D-модели.
 */
public class AnimationMapping {

    @SerializedName("mapping")
    private Map<String, String> mapping = new HashMap<>();

    /**
     * Преобразует логическое имя анимации в реальное имя из модели.
     * Если соответствие не найдено, возвращает исходное логическое имя.
     * @param logicalName Логическое имя (например, "sprint").
     * @return Реальное имя (например, "Run Forward") или logicalName, если нет в карте.
     */
    public String get(String logicalName) {
        return mapping.getOrDefault(logicalName, logicalName);
    }
}