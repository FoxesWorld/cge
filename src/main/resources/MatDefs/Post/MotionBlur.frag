uniform sampler2D m_Texture;
uniform sampler2D PrevTex;
uniform float BlurStrength;
uniform vec2 g_FrameBufferSize;

varying vec2 texCoord;

void main() {
    vec2 uv = texCoord;
    vec2 motion = (uv - (texture2D(PrevTex, uv).rg)) * BlurStrength;

    vec4 color = vec4(0.0);
    int samples = 8;
    for (int i = 0; i < samples; i++) {
        vec2 off = motion * (float(i) / float(samples - 1) - 0.5);
        color += texture2D(m_Texture, uv + off);
    }
    color /= float(samples);
    gl_FragColor = color;
}
