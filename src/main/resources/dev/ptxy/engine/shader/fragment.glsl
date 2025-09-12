#version 410 core

in vec2 fragTexCoords;
in vec4 staticFragmentColor;

uniform sampler2D spriteTexture;
uniform int useTexture;

out vec4 resultColor;

void main() {
    if (useTexture == 1) {
        resultColor = texture(spriteTexture, fragTexCoords);
    } else {
        resultColor = staticFragmentColor;
    }
}
