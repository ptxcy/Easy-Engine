#version 410 core

in vec2 fragTexCoords;

uniform vec4 staticInputColor;
uniform sampler2D textureSampler;
uniform bool useTexture;

out vec4 resultColor;

void main() {
    if (useTexture) {
        resultColor = texture(textureSampler, fragTexCoords);
    } else {
        resultColor = staticInputColor;
    }
}
