// Passthrough.vert
// Стандартный вершинный шейдер для пост-эффектов.
// Просто отрисовывает полноэкранный прямоугольник и передает координаты текстуры.

#version 120

// Атрибуты вершины, приходящие из JME
attribute vec3 inPosition;
attribute vec2 inTexCoord;

// Матрица проекции, приходящая из JME
uniform mat4 g_WorldViewProjectionMatrix;

// Переменная для передачи данных во фрагментный шейдер
varying vec2 texCoord;

void main() {
    // Преобразуем позицию вершины в экранные координаты
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    // Просто передаем текстурные координаты дальше
    texCoord = inTexCoord;
}