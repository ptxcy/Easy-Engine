#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in vec3 aNormal;
layout(location = 3) in vec3 aTangent;
layout(location = 4) in vec3 bTangent;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

out vec2 fragTexCoords;
out vec3 worldPos;
out mat3 TBN;

void main() {
    fragTexCoords = aTexCoords;

    // Normal in Weltkoordinaten
    vec3 N = normalize(mat3(transpose(inverse(model))) * aNormal);
    vec3 T = normalize(mat3(model) * aTangent);
    vec3 B = normalize(mat3(model) * bTangent);

    TBN = mat3(T, B, N);

    vec4 wp = model * vec4(aPos, 1.0);
    worldPos = wp.xyz;

    gl_Position = projection * view * wp;
}
