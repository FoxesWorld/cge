in vec3 inPosition;
in vec2 inTexCoord;

uniform mat4 g_WorldViewProjectionMatrix;

out vec2 v_TexCoord;

void main() {
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    v_TexCoord = inTexCoord;
}