#version 410 core

layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aTexCoords;

uniform mat4 projection;
uniform mat4 model;
uniform mat4 view;

out vec2 fragTexCoords;
out vec4 staticFragmentColor;

uniform vec4 staticInputColor;
uniform int useTexture;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 0.0, 1.0);
    fragTexCoords = aTexCoords;
    staticFragmentColor = staticInputColor;
}
