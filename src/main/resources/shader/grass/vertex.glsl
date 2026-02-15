#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in vec3 aNormal;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;
uniform float time;
uniform float windStrength;

out vec2 fragTexCoords;
out vec3 worldPos;
out vec3 normal;

void main() {
    fragTexCoords = aTexCoords;

    normal = normalize(mat3(transpose(inverse(model))) * aNormal);

    // Wind
    vec3 pos = aPos;
    float wind = sin(time * 2.0 + aPos.x * 5.0) * windStrength;

    // Basis leicht bewegen, Spitze stärker
    pos.x += wind * (0.2 + 0.8 * aPos.y);

    vec4 wp = model * vec4(pos, 1.0);
    worldPos = wp.xyz;

    gl_Position = projection * view * wp;
}
