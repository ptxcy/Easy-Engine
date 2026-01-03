#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in vec3 aNormal;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

out vec2 fragTexCoords;
out vec3 worldPos;
out vec3 normal;

void main() {
    fragTexCoords = aTexCoords;

    mat3 normalMatrix = transpose(inverse(mat3(model)));
    normal = normalize(normalMatrix * aNormal);

    vec4 wp = model * vec4(aPos, 1.0);
    worldPos = wp.xyz;

    gl_Position = projection * view * wp;
}
