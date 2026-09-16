#version 410 core

in float fragHeightT;
in vec3 fragNormal;
flat in int fragBiomeId;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;

out vec4 fragColor;

const float TRUNK_HEIGHT_T = 0.65;

vec3 canopyColorForBiome(int biomeId) {
    if (biomeId == 0) return vec3(0.52, 0.56, 0.48);
    if (biomeId == 4) return vec3(0.50, 0.46, 0.22);
    if (biomeId == 8) return vec3(0.05, 0.28, 0.09);
    return vec3(0.15, 0.35, 0.15);
}

void main() {
    vec3 N = normalize(fragNormal);
    float NdotL = abs(dot(N, normalize(-lightDir)));

    float canopyMix = smoothstep(TRUNK_HEIGHT_T - 0.03, TRUNK_HEIGHT_T + 0.03, fragHeightT);
    vec3 albedo = mix(vec3(0.32, 0.22, 0.14), canopyColorForBiome(fragBiomeId), canopyMix);

    vec3 color = albedo * (NdotL * lightColor + 0.35 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
