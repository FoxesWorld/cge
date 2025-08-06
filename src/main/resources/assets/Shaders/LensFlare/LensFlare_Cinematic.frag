uniform sampler2D m_Texture;
varying vec2 texCoord;

uniform vec2  m_Resolution;
uniform float g_Time;

// Флаги и параметры
uniform bool  m_Enabled;
uniform float m_StartTime;
uniform vec2  m_ClickPoint;
uniform float m_Duration;

// Bloom
uniform bool  m_EnabledFakeBloom;
uniform bool  m_BloomFadeOut;
uniform float m_BloomStrength;
uniform float m_BloomStrengthDynamic;
uniform float m_BloomRange;
uniform vec4  m_ColorBloom;

// Anamorphic
uniform bool  m_EnabledAnamorphic;
uniform float m_AnamStrength;
uniform float m_AnamStrengthDynamic;
uniform float m_AnamRange;
uniform vec4  m_ColorAnam;

// Ghosts
uniform bool  m_EnabledGhosts;
uniform bool  m_EnabledDistortion;
uniform vec4  m_ColorGhosts;

// Streaks
uniform bool  m_EnabledStreaks;
uniform int   m_StreaksType;
uniform float m_StreaksCount;
uniform vec4  m_ColorStreaks;
uniform float m_StreaksLength;
uniform float m_StreaksStrengthDynamic;

// Утилиты
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898,78.233))) * 43758.5453);
}

vec3 cc(vec3 color, float f1, float f2) {
    float w = color.x + color.y + color.z;
    return mix(color, vec3(w)*f1, w*f2);
}

// --- Ghosts ---
vec3 classicGhosts(vec2 uv, vec2 pos) {
    const float intensity = 1.5;
    vec2 main = uv - pos;
    vec2 uvd  = uv * length(uv);

    float f2  = max(1.0/(1.0+32.0*pow(length(uvd+0.8*pos),2.0)),0.0)*0.1;
    float f22 = max(1.0/(1.0+32.0*pow(length(uvd+0.85*pos),2.0)),0.0)*0.08;
    float f23 = max(1.0/(1.0+32.0*pow(length(uvd+0.9*pos),2.0)),0.0)*0.06;

    vec3 c = vec3(0.0);
    c.r += f2 + f22 + f23;
    c.g += f22 + f23;
    c.b += f23;
    c = c * 1.3 - vec3(length(uvd)*0.05);
    return c * intensity;
}

vec4 classicLens() {
    vec2 uv = gl_FragCoord.xy / m_Resolution.xy - 0.5;
    uv.x *= m_Resolution.x / m_Resolution.y;
    vec2 pos = vec2(m_ClickPoint.x*2.0 - 1.0, m_ClickPoint.y - 0.5);

    vec3 col = cc(m_ColorGhosts.rgb * classicGhosts(uv, pos), 0.5, 0.1);
    float alpha = (col.r + col.g + col.b) / 3.0;
    return vec4(col, alpha);
}

// --- Anamorphic ---
float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 ab = b - a;
    float t = clamp(dot(p-a,ab)/dot(ab,ab), 0.0, 1.0);
    return length((a+t*ab)-p) - r;
}

vec3 anamFlare(vec2 spos, vec2 fpos) {
    vec3 clr = m_ColorAnam.rgb;
    vec2 dd;
    dd.x = abs(spos.x - fpos.x);
    dd.y = abs(spos.y - fpos.y);
    float v = (0.015 * m_AnamStrength) / max(dd.y, 1e-4);
    float w = max((m_AnamRange*m_AnamStrengthDynamic)/3.0 - dd.x, 0.0);
    return clr * v * w;
}

// --- Streaks ---
vec3 rayStreaks() {
    vec3 col = vec3(0.0);
    float pi = 3.14159265359;
    vec2 pos = gl_FragCoord.xy/m_Resolution.xy - m_ClickPoint;
    pos.y *= m_Resolution.y/m_Resolution.x;
    float ang = atan(pos.x, pos.y);
    float dist = length(pos);

    for (float ray = 0.5; ray < m_StreaksCount; ray += 1.0) {
        float rayAng = mod(ray*1.2, pi*2.0);
        float br = 0.05 - abs(ang - rayAng);
        br -= dist * (1.0 - m_StreaksLength*clamp(m_StreaksStrengthDynamic,0.0,0.3));
        if (br > 0.0) {
            col += m_ColorStreaks.rgb * br;
        }
    }
    return col;
}

// --- Lens Distortion (simple) ---
vec3 lensDistortion() {
    // Повторное использование classicLens без alpha
    vec4 cf = classicLens();
    return cf.rgb * 0.5;
}

void main() {
    vec4 scene = texture2D(m_Texture, texCoord);
    vec3 outColor = scene.rgb;

    if (m_Enabled) {
        // Fake bloom
        if (m_EnabledFakeBloom) {
            vec2 uv = texCoord;
            vec2 dp = m_ClickPoint - uv;
            dp.y /= (m_Resolution.x/m_Resolution.y);
            float d = 1.0/length(dp);
            d = pow(d*m_BloomStrength*m_BloomStrengthDynamic*0.1,
                   3.0/(m_BloomRange*m_BloomStrengthDynamic));
            if (m_BloomFadeOut) {
                d *= max(0.0, 1.0 - ((g_Time - m_StartTime)/m_Duration));
            }
            vec3 bloomCol = 1.0 - exp(-d * m_ColorBloom.rgb);
            outColor += bloomCol;
        }

        // Anamorphic
        if (m_EnabledAnamorphic) {
            vec2 uv2 = gl_FragCoord.xy/m_Resolution.xy*2.0;
            uv2.y *= 0.5;
            outColor += anamFlare(uv2, vec2(m_ClickPoint.x*2.0, m_ClickPoint.y));
        }

        // Streaks
        if (m_EnabledStreaks) {
            outColor += rayStreaks();
        }

        // Ghosts + optional distortion
        if (m_EnabledGhosts) {
            vec4 lensFx = classicLens();
            outColor += lensFx.rgb;
            if (m_EnabledDistortion) {
                outColor += lensDistortion();
            }
        }

        // Film grain
        outColor += (hash(texCoord + g_Time) - 0.5) * 0.02;
    }

    gl_FragColor = vec4(clamp(outColor, 0.0, 1.0), scene.a);
}
