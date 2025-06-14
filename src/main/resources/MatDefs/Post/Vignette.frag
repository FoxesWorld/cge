uniform sampler2D m_Texture;
varying vec2 texCoord;

uniform vec2 Resolution;
uniform float Radius;
uniform float Softness;
uniform float Strength;

void main() {
    vec4 color = texture2D(m_Texture, texCoord);
    vec2 pos = gl_FragCoord.xy / Resolution - vec2(0.5);
    float len = length(pos);
    float vign = smoothstep(Radius, Radius - Softness, len);
    color.rgb = mix(color.rgb, color.rgb * vign, Strength);
    gl_FragColor = color;
}
