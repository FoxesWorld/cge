#version 150 core

out vec2 texCoord;

const vec2 positions[4] = vec2[](
vec2(-1.0, -1.0),
vec2( 1.0, -1.0),
vec2(-1.0,  1.0),
vec2( 1.0,  1.0)
);

const vec2 texCoords[4] = vec2[](
vec2(0.0, 0.0),
vec2(1.0, 0.0),
vec2(0.0, 1.0),
vec2(1.0, 1.0)
);

void main() {
    gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
    texCoord = texCoords[gl_VertexID];
}