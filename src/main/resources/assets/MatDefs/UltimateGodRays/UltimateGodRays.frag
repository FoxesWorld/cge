#version 150

in vec2 texCoord;
in vec3 worldPos;
in vec3 normal;

out vec4 fragColor;

// Configurable uniforms
uniform vec3 LightPosition;    // World position of the light (e.g. sun)
uniform vec3 LightColor;       // Color of the main light
uniform vec3 SunColor;         // Color for god rays
uniform float SunIntensity;    // Intensity multiplier for sun
uniform float RayDecay;        // How quickly rays fade
uniform float RayExposure;     // Overall exposure of rays
uniform float RayDensity;      // Density of sampling for rays
uniform float RayWeight;       // Weight of each sample
uniform int RaySamples;        // Number of samples per ray
uniform float Time;            // For animation/flicker
uniform vec3 CameraPosition;

#ifdef USE_SHADOWMAP
uniform sampler2DShadow ShadowMap;
uniform float ShadowMapSize;
#endif

// Utility: Project position to screen
vec2 projectToScreen(vec3 pos) {
    // Assume projection matrix is handled by JME, so just use texCoord
    return texCoord;
}

// Utility: Basic shadow lookup (if enabled)
float getShadow(vec3 fragPos) {
#ifdef USE_SHADOWMAP
    // Project world position to [0,1] shadow map space
    vec3 shadowCoord = vec3(texCoord, 0.0); // Simplified, should use actual shadow matrix
    float shadow = texture(ShadowMap, shadowCoord);
    return shadow;
#else
    return 1.0;
#endif
}

// Main god rays calculation
vec3 computeGodRays(vec2 uv, vec3 sunScreen, float exposure, float decay, float density, float weight, int samples) {
    // Start from current fragment, march towards sun screen position
    vec2 delta = (sunScreen - uv) * density / float(samples);
    vec2 coord = uv;
    float illuminationDecay = 1.0;
    float ray = 0.0;

    for (int i = 0; i < 100; ++i) { // Max 100, but use samples for actual
        if (i >= samples) break;
        coord += delta;
        float sample = 1.0; // Could sample occlusion buffer if you have one
        ray += sample * illuminationDecay * weight;
        illuminationDecay *= decay;
    }
    return vec3(ray * exposure);
}

void main() {
    // Project sun to screen (assume orthogonal, use texCoord for simplicity)
    vec2 uv = texCoord;
    vec3 sunDir = normalize(LightPosition - CameraPosition);
    vec3 fragDir = normalize(worldPos - CameraPosition);
    float cosAngle = dot(normalize(normal), sunDir);

    // "Sun" on screen is at LightPosition, for demo use center
    vec2 sunScreen = vec2(0.5, 0.5); // Should be projected from LightPosition

    // Ray occlusion (optional): fake with normal alignment for demo
    float occlusion = clamp(cosAngle, 0.0, 1.0);

    // God Rays
    vec3 rays = computeGodRays(uv, sunScreen, RayExposure, RayDecay, RayDensity, RayWeight, RaySamples);

    // Sun disk
    float sunSize = 0.08;
    float distToSun = length(uv - sunScreen);
    float sunDisk = smoothstep(sunSize, sunSize*0.8, distToSun) * SunIntensity;

    // Dynamic flicker (for atmosphere)
    float flicker = 0.85 + 0.15 * sin(Time + uv.x*12.0 + uv.y*7.0);

    // Shadowing
    float shadow = getShadow(worldPos);

    // Final color: base + rays + sun disk, modulated by occlusion and shadow
    vec3 base = LightColor * occlusion * shadow;
    vec3 rayCol = rays * SunColor * flicker * occlusion * shadow;
    vec3 sunCol = SunColor * sunDisk * occlusion * shadow * flicker;

    fragColor = vec4(base + rayCol + sunCol, 1.0);
}