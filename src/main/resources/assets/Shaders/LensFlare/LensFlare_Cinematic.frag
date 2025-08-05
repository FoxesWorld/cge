#version 330 core

in vec2 texCoord;
uniform sampler2D m_Texture;
uniform float m_Time;

uniform sampler2D Bloom;
uniform sampler2D Dirt;
uniform sampler2D LensColor;

uniform vec2 LightPos;
uniform float GlobalIntensity;
uniform vec4 Tint;

uniform float AnamorphicIntensity;
uniform float AnamorphicStretch;
uniform vec4 AnamorphicTint;

uniform int GhostCount;
uniform float GhostIntensity;
uniform float GhostDispersal;

uniform float DirtIntensity;
uniform float ChromaticAberration;
uniform float FilmGrainAmount;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 textureDistorted(sampler2D tex, vec2 uv, vec2 direction, float amount) {
    float r = texture(tex, uv - direction * amount).r;
    float g = texture(tex, uv).g;
    float b = texture(tex, uv + direction * amount).b;
    return vec3(r, g, b);
}

void main() {
    vec2 resolution = vec2(textureSize(Bloom, 0));
    float aspectRatio = resolution.x / resolution.y;

    vec3 anamorphicColor = vec3(0.0);
    const int ANAMORPHIC_SAMPLES = 12;

    for (int i = 1; i <= ANAMORPHIC_SAMPLES; i++) {
        float offset = (float(i) / ANAMORPHIC_SAMPLES) * AnamorphicStretch;
        float weight = 1.0 - (offset / AnamorphicStretch);

        vec2 dir = vec2(offset * aspectRatio, 0.0);
        anamorphicColor += textureDistorted(Bloom, texCoord + dir, dir, ChromaticAberration * 0.1) * weight;
        anamorphicColor += textureDistorted(Bloom, texCoord - dir, dir, ChromaticAberration * 0.1) * weight;
    }
    anamorphicColor /= float(ANAMORPHIC_SAMPLES);
    anamorphicColor *= AnamorphicIntensity * AnamorphicTint.rgb;

    vec3 ghostColor = vec3(0.0);
    vec2 lightToCenter = LightPos - vec2(0.5);

    float ghost_denom = max(1.0, float(GhostCount - 1));

    for (int i = 0; i < GhostCount; i++) {
        float interpolation = (float(i) / ghost_denom) * 2.0 - 1.0;
        vec2 ghostVec = lightToCenter * interpolation * GhostDispersal;
        vec2 sampleCoord = texCoord - ghostVec;

        float ghostShape = texture(Bloom, sampleCoord).r;
        vec3 ghostTint = texture(LensColor, vec2(float(i) / float(GhostCount), 0.5)).rgb;

        ghostColor += ghostShape * ghostTint;
    }
    ghostColor *= GhostIntensity;

    vec3 flareComposite = anamorphicColor + ghostColor;

    float dirtMask = texture(Bloom, texCoord).g;
    vec3 dirtEffect = texture(Dirt, texCoord).rgb * dirtMask * DirtIntensity;

    flareComposite += dirtEffect;

    vec3 sceneColor = texture(m_Texture, texCoord).rgb;
    vec3 finalColor = sceneColor + flareComposite * Tint.rgb * GlobalIntensity;

    float grain = (hash(texCoord.xy + m_Time) - 0.5) * FilmGrainAmount;
    finalColor += grain;

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}