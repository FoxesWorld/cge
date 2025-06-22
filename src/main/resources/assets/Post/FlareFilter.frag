#version 150

uniform sampler2D m_Texture;
uniform float m_FlareIntensity;
uniform float m_FlareThreshold;
uniform vec4  m_FlareColor;

uniform float m_GhostDist1;    // Расстояние до ghost flare #1
uniform float m_GhostDist2;    // Расстояние до ghost flare #2
uniform float m_GhostDist3;    // Расстояние до ghost flare #3
uniform float m_HaloRadius;    // Радиус halo
uniform float m_HaloIntensity; // Яркость halo

in vec2 texCoord;
out vec4 fragColor;

// Вспомогательная функция для вычисления позиции относительно центра экрана
vec2 flareOffset(vec2 uv, float dist) {
    vec2 center = vec2(0.5, 0.5);
    return center + (uv - center) * dist;
}

// Оценка яркости пикселя
float luminance(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec4 baseColor = texture(m_Texture, texCoord);

    float lum = luminance(baseColor.rgb);

    // Вычисляем flare mask: flare появляется только на ярких пикселях
    float flareMask = smoothstep(m_FlareThreshold, 1.0, lum);

    // --- GHOST FLARES ---
    vec3 ghosts = vec3(0.0);

    ghosts += texture(m_Texture, flareOffset(texCoord, m_GhostDist1)).rgb * 0.25;
    ghosts += texture(m_Texture, flareOffset(texCoord, m_GhostDist2)).rgb * 0.18;
    ghosts += texture(m_Texture, flareOffset(texCoord, m_GhostDist3)).rgb * 0.10;

    ghosts *= m_FlareIntensity * flareMask;

    // --- HALO ---
    vec2 center = vec2(0.5, 0.5);
    float distToCenter = distance(texCoord, center);
    float halo = exp(-pow(distToCenter / m_HaloRadius, 2.0)) * m_HaloIntensity * flareMask;

    // --- Смешивание flare ---
    vec3 flare = ghosts * m_FlareColor.rgb + halo * m_FlareColor.rgb;

    vec3 result = baseColor.rgb + flare;

    fragColor = vec4(result, baseColor.a);
}