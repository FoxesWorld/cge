// ColorGrading.frag
// Шейдер для цветокоррекции с использованием 3D LUT, представленной в виде 2D текстуры.

uniform sampler2D m_Texture;  // Текстура сцены (входное изображение)
uniform sampler2D m_ColorLUT;   // Наша LUT-текстура
uniform float m_LutSize;      // Размер LUT (например, 16.0 для куба 16x16x16)

varying vec2 texCoord;

void main() {
    // 1. Получаем оригинальный цвет пикселя из сцены
    vec4 originalColor = texture2D(m_Texture, texCoord);

    // 2. Рассчитываем UV-координаты для поиска в LUT.
    // Это самая сложная часть. Мы эмулируем сэмплирование 3D-текстуры.

    // Масштабируем синий канал, чтобы определить, между какими двумя "слоями" LUT мы находимся.
    float blue_scaled = originalColor.b * (m_LutSize - 1.0);

    // Первый слой (целая часть) и второй слой (целая часть + 1)
    float slice1_z = floor(blue_scaled);
    float slice2_z = ceil(blue_scaled);

    // Фактор смешивания между двумя слоями (дробная часть)
    float blend_factor = fract(blue_scaled);

    // Рассчитываем координаты (x, y) для каждого из двух слоев.
    // Сетки слоев в нашей текстуре идут как 4x4 для LUT 16x16x16.
    float slices_per_row = sqrt(m_LutSize);

    // UV для первого слоя
    float u1 = (originalColor.r * (m_LutSize - 1.0) + mod(slice1_z, slices_per_row)) / slices_per_row;
    float v1 = (originalColor.g * (m_LutSize - 1.0) + floor(slice1_z / slices_per_row)) / slices_per_row;
    vec2 uv1 = vec2(u1, v1);

    // UV для второго слоя
    float u2 = (originalColor.r * (m_LutSize - 1.0) + mod(slice2_z, slices_per_row)) / slices_per_row;
    float v2 = (originalColor.g * (m_LutSize - 1.0) + floor(slice2_z / slices_per_row)) / slices_per_row;
    vec2 uv2 = vec2(u2, v2);

    // 3. Получаем новые цвета из LUT
    vec4 newColor1 = texture2D(m_ColorLUT, uv1);
    vec4 newColor2 = texture2D(m_ColorLUT, uv2);

    // 4. Смешиваем (интерполируем) два цвета для плавного перехода
    vec4 finalColor = mix(newColor1, newColor2, blend_factor);

    // Сохраняем оригинальную прозрачность
    finalColor.a = originalColor.a;

    gl_FragColor = finalColor;
}