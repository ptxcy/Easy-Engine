#version 410 core

in vec2 fragTexCoords;
in vec3 worldPos;
in vec3 normal;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform vec3 albedo;
uniform float ao;

uniform sampler2D baseColorTexture;
uniform bool useBaseColor;

out vec4 resultColor;

void main() {
    // Normal
    vec3 N = normalize(normal);

    // Licht
    vec3 L = normalize(-lightDir);
    float NdotL = max(dot(N, L), 0.0);

    // Base Color
    vec3 baseColorRGB = albedo;
    if(useBaseColor){
        baseColorRGB = texture(baseColorTexture, fragTexCoords).rgb;
    }

    // Einfaches diffuse Licht
    vec3 diffuse = baseColorRGB * NdotL;

    // Ambient
    vec3 ambient = baseColorRGB * 0.3 * ao;

    // Endfarbe
    vec3 color = ambient + diffuse;

    // Gamma-Korrektur
    color = pow(color, vec3(1.0/2.2));

    resultColor = vec4(color, 1.0);
}
