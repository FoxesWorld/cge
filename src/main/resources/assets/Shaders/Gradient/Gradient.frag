// Файл: assets/Shaders/Gradient.frag
varying vec2 v_TexCoord;

// Эти переменные приходят из .j3m файла и Java-кода
uniform vec4 m_Color1; // Color1
uniform vec4 m_Color2; // Color2

void main(){
    // mix() - стандартная функция GLSL для линейной интерполяции.
    // Мы смешиваем Color1 и Color2 на основе вертикальной координаты (v_TexCoord.y).
    // Когда y=0 (низ), цвет = Color1. Когда y=1 (верх), цвет = Color2.
    gl_FragColor = mix(m_Color1, m_Color2, v_TexCoord.y);
}