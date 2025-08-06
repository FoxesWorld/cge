// ChromaticAberration.frag
#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D m_Texture;     // Входная текстура сцены
uniform float m_Strength;        // Сила аберрации (например, 0.005)
uniform vec2 m_Resolution;       // Разрешение экрана

varying vec2 texCoord;

void main() {
    // Смещение для каждого канала
    vec2 offset = m_Strength / m_Resolution;

    // Сдвигаем каналы: R влево, B вправо
    float r = texture2D(m_Texture, texCoord + vec2(-offset.x, 0.0)).r;
    float g = texture2D(m_Texture, texCoord).g;
    float b = texture2D(m_Texture, texCoord + vec2(offset.x, 0.0)).b;

    gl_FragColor = vec4(r, g, b, 1.0);
}