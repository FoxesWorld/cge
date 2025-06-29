// LensFlare.frag
// Фрагментный шейдер для создания кинематографичного эффекта Lens Flare.

#version 120 // Используем GLSL версии 120 для широкой совместимости

// Юниформы, передаваемые из JME (через FilterPostProcessor)
uniform sampler2D m_Texture; // Оригинальная текстура сцены

// Наши кастомные юниформы, определенные в .j3md
uniform sampler2D Bloom;
uniform sampler2D Dirt;
uniform vec2 LightPos;   // Позиция света в экранных координатах (0.0 до 1.0)
uniform vec2 Resolution; // Разрешение экрана

// Координаты текселя, приходящие из вершинного шейдера
varying vec2 texCoord;

// Константы для настройки эффекта. Можете изменять их для достижения нужного вида.
const int GHOST_SAMPLES = 4;           // Количество "призрачных" бликов (ghosts)
const float GHOST_DISPERSION = 0.35;   // Насколько сильно блики разлетаются от центра
const float GHOST_INTENSITY = 0.8;     // Интенсивность бликов

const int STREAK_SAMPLES = 8;          // Количество сэмплов для анаморфных полос
const float STREAK_LENGTH = 0.05;      // Длина полос
const float STREAK_INTENSITY = 1.5;    // Интенсивность полос

const float DIRT_INTENSITY = 2.0;      // Интенсивность эффекта грязной линзы

void main() {
    // 1. Получаем оригинальный цвет сцены
    vec4 sceneColor = texture2D(m_Texture, texCoord);
    vec3 finalColor = sceneColor.rgb;

    // --- РАСЧЕТ БЛИКОВ (GHOSTS) ---
    // Вектор от центра экрана (0.5, 0.5) до источника света
    vec2 lightToCenter = vec2(0.5) - LightPos;

    vec3 ghostColor = vec3(0.0);

    // Создаем несколько бликов, отраженных через центр экрана
    for (int i = 0; i < GHOST_SAMPLES; i++) {
        // Располагаем блик вдоль линии, проходящей через центр
        float interpolation = (float(i) / float(GHOST_SAMPLES - 1)) * 2.0 - 1.0; // от -1.0 до 1.0
        interpolation *= GHOST_DISPERSION;

        vec2 ghostVec = lightToCenter * interpolation;
        vec2 sampleCoord = texCoord + ghostVec;

        // Добавляем эффект хроматической аберрации, сэмплируя R,G,B каналы с небольшим смещением
        float R = texture2D(Bloom, sampleCoord + ghostVec * 0.01).r;
        float G = texture2D(Bloom, sampleCoord).g;
        float B = texture2D(Bloom, sampleCoord - ghostVec * 0.01).b;

        ghostColor += vec3(R, G, B) * GHOST_INTENSITY;
    }

    // --- РАСЧЕТ АНАМОРФНЫХ ПОЛОС (STREAKS) ---
    // Создаем горизонтальные полосы, размывая Bloom-текстуру по горизонтали
    vec3 streakColor = vec3(0.0);
    for (int i = -STREAK_SAMPLES; i <= STREAK_SAMPLES; i++) {
        if (i == 0) continue;
        // Смещение по оси X. Делим на Resolution.x, чтобы длина была одинаковой при разном разрешении.
        vec2 offset = vec2(float(i) * STREAK_LENGTH * (Resolution.y / Resolution.x) / float(STREAK_SAMPLES), 0.0);
        // Чем дальше сэмпл, тем меньше его вес
        float weight = 1.0 - abs(float(i) / float(STREAK_SAMPLES));
        streakColor += texture2D(Bloom, texCoord + offset).rgb * weight;
    }
    streakColor *= STREAK_INTENSITY;

    // --- РАСЧЕТ ЭФФЕКТА ГРЯЗНОЙ ЛИНЗЫ (DIRT) ---
    // Эффект грязи должен быть виден только там, где есть яркий свет
    vec3 dirt = texture2D(Dirt, texCoord).rgb;
    // Используем яркость из Bloom-текстуры как маску для грязи
    float dirtMask = texture2D(Bloom, LightPos).g; // Общая яркость от источника света
    dirtMask = clamp(dirtMask * DIRT_INTENSITY, 0.0, 1.0);
    vec3 dirtEffect = dirt * dirtMask;

    // 2. Смешиваем все эффекты и добавляем к оригинальному цвету
    finalColor += ghostColor + streakColor + dirtEffect;

    // 3. Выводим итоговый цвет
    gl_FragColor = vec4(finalColor, sceneColor.a);
}