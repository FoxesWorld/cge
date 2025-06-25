#version 150

in vec3 inPosition;
in vec3 inNormal;
in vec2 inTexCoord;

out vec2 texCoord;
out vec3 worldPos;
out vec3 normal;

uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_ViewMatrix;

void main() {
    texCoord = inTexCoord;
    worldPos = (g_ViewMatrix * vec4(inPosition, 1.0)).xyz;
    normal = mat3(g_ViewMatrix) * inNormal;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}