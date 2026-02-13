#version 410 core

in vec2 fragTexCoords;
in vec3 worldPos;
in vec3 normal;

uniform vec3 camPos;
uniform vec3 lightDir;
uniform vec3 lightColor;
uniform vec3 albedo;
uniform float metallic;
uniform float roughness;
uniform float ao;

uniform sampler2D baseColorTexture;
uniform sampler2D metallicRoughnessTexture;
uniform sampler2D normalMapTexture;

uniform bool useBaseColor;
uniform bool useMetallicRoughness;
uniform bool useNormalMap;

out vec4 resultColor;

void main() {
    // Normal
    vec3 N = normalize(normal);
    if(useNormalMap) {
        vec3 normalTex = texture(normalMapTexture, fragTexCoords).rgb;
        normalTex = normalTex * 2.0 - 1.0; // from [0,1] -> [-1,1]
        N = normalize(normalTex);
    }

    // View / Light
    vec3 V = normalize(camPos - worldPos);
    vec3 L = normalize(-lightDir);
    vec3 H = normalize(V + L);
    vec3 radiance = lightColor;

    // Material
    vec3 baseColor = albedo;
    if(useBaseColor) {
        baseColor = texture(baseColorTexture, fragTexCoords).rgb;
    }

    float finalMetallic = metallic;
    float finalRoughness = roughness;
    if(useMetallicRoughness) {
        vec2 mr = texture(metallicRoughnessTexture, fragTexCoords).rg;
        finalMetallic = mr.r;
        finalRoughness = mr.g;
    }

    // GGX PBR calculations
    float a = finalRoughness * finalRoughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;
    float nom = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = 3.14159265 * denom * denom;
    float D = nom / denom;

    float k = (finalRoughness + 1.0) * (finalRoughness + 1.0) / 8.0;
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float G = NdotV / (NdotV * (1.0 - k) + k);
    G *= NdotL / (NdotL * (1.0 - k) + k);

    vec3 F0 = mix(vec3(0.04), baseColor, finalMetallic);
    vec3 F = F0 + (1.0 - F0) * pow(1.0 - max(dot(H, V), 0.0), 5.0);
    vec3 specular = (D * G * F) / (4.0 * max(dot(N,V),0.0) * max(dot(N,L),0.0) + 0.001);

    vec3 kS = F;
    vec3 kD = (1.0 - kS) * (1.0 - finalMetallic);

    vec3 Lo = (kD * baseColor / 3.14159265 + specular) * radiance * NdotL;
    vec3 ambient = vec3(0.03) * baseColor * ao;
    vec3 color = ambient + Lo;

    color = pow(color, vec3(1.0/2.2));
    resultColor = vec4(color, 1.0);
}
