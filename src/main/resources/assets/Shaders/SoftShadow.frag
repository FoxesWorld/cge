in vec2 v_TexCoord;

uniform vec4 m_ShadowColor;
uniform float m_CornerRadius;
uniform float m_Blur;

out vec4 fragColor;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) - r;
}

void main() {
    vec2 p = v_TexCoord - 0.5;
    vec2 b = vec2(0.5, 0.5);

    float distance = sdRoundedBox(p, b, m_CornerRadius);
    float alpha = 1.0 - smoothstep(-m_Blur, m_Blur, distance);

    fragColor = vec4(m_ShadowColor.rgb, m_ShadowColor.a * alpha);
}
