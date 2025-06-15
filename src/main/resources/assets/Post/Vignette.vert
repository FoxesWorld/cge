attribute vec4 inPosition;
attribute vec2 inTexCoord;
varying vec2 texCoord;

uniform mat4 g_WorldViewProjectionMatrix;

void main() {
    texCoord = inTexCoord;
    gl_Position = g_WorldViewProjectionMatrix * inPosition;
}
