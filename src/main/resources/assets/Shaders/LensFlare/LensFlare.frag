// LensFlare.frag
// AAA-стиль: "ghosts", "halo", dirt, chromatic aberration
uniform sampler2D m_Scene;
uniform sampler2D m_Bloom;
uniform sampler2D m_Dirt;
uniform vec2 m_LightPos;
uniform vec2 m_Resolution;

varying vec2 texCoord;

#define GHOSTS 5
#define GHOST_DISP 0.38
#define HALO_WIDTH 0.45
#define HALO_INTENSITY 0.7

vec3 chromaticAberration(vec2 uv, float amount) {
    float r = texture2D(m_Scene, uv + amount * 0.003).r;
    float g = texture2D(m_Scene, uv).g;
    float b = texture2D(m_Scene, uv - amount * 0.003).b;
    return vec3(r, g, b);
}

void main() {
    vec2 center = vec2(0.5, 0.5);
    vec2 lightVec = m_LightPos - center;
    vec3 ghosts = vec3(0.0);
    for (int i = 0; i < GHOSTS; ++i) {
        float pos = float(i) / float(GHOSTS - 1);
        vec2 ghostUv = texCoord + lightVec * (pos - 0.5) * GHOST_DISP;
        ghosts += chromaticAberration(ghostUv, 0.01 + 0.03 * pos) * (1.0 - pos);
    }
    ghosts /= float(GHOSTS);

    float haloAngle = length(texCoord - center) / HALO_WIDTH;
    float halo = exp(-haloAngle * haloAngle * 8.0) * HALO_INTENSITY;

    vec3 dirtMask = texture2D(m_Dirt, texCoord * 1.2).rgb;
    vec3 dirtFlare = ghosts * dirtMask * 1.5;

    float star = texture2D(m_Bloom, m_LightPos).a;
    float intensity = clamp(star * 3.0, 0.0, 1.0);

    vec3 finalFlare = (ghosts + halo) * intensity + dirtFlare * intensity;
    gl_FragColor = vec4(finalFlare, 1.0);
}