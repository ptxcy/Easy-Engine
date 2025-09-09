#version 410 core

layout(location = 0) in vec2 aPos;

uniform vec4 staticInputColor;
uniform mat4 projection;
uniform mat4 model;
uniform mat4 view;

out vec4 staticFragmentColor;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 0.0, 1.0);
    staticFragmentColor = staticInputColor;
}
