#version 410 core

in float fragHeightT;
in vec3 fragNormal;
flat in int fragBiomeId;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;

out vec4 fragColor;

vec3 baseColorForBiome(int biomeId) {
    if (biomeId == 0) return vec3(0.55, 0.58, 0.42);
    if (biomeId == 4) return vec3(0.45, 0.55, 0.12);
    if (biomeId == 8) return vec3(0.06, 0.32, 0.10);
    if (biomeId == 9) return vec3(0.35, 0.40, 0.30);
    return vec3(0.20, 0.45, 0.15);
}

vec3 tipColorForBiome(int biomeId) {
    if (biomeId == 0) return vec3(0.75, 0.78, 0.55);
    if (biomeId == 4) return vec3(0.75, 0.80, 0.30);
    if (biomeId == 8) return vec3(0.15, 0.55, 0.20);
    if (biomeId == 9) return vec3(0.55, 0.58, 0.48);
    return vec3(0.35, 0.65, 0.25);
}

void main() {
    vec3 N = normalize(fragNormal);

    float NdotL = abs(dot(N, normalize(-lightDir)));

    vec3 albedo = mix(baseColorForBiome(fragBiomeId), tipColorForBiome(fragBiomeId), fragHeightT);

    vec3 color = albedo * (NdotL * lightColor + 0.25 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
