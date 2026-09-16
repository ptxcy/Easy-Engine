#version 410 core

in float fragHeightT;
in vec3 fragNormal;
flat in int fragBiomeId;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;

out vec4 fragColor;

vec3 baseColorForBiome(int biomeId) {
    if (biomeId == 8) return vec3(0.32, 0.34, 0.30);
    if (biomeId == 9) return vec3(0.55, 0.56, 0.58);
    return vec3(0.42, 0.41, 0.38);
}

void main() {
    vec3 N = normalize(fragNormal);
    float NdotL = max(dot(N, normalize(-lightDir)), 0.0);

    vec3 albedo = baseColorForBiome(fragBiomeId) * mix(0.85, 1.05, fragHeightT);

    vec3 color = albedo * (NdotL * lightColor + 0.35 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
