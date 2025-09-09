#version 410 core

in vec4 staticFragmentColor;

out vec4 resultColor;

void main() {
    resultColor = staticFragmentColor;
}