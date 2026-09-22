#version 410 core

in float fragHeightT;
in vec3 fragNormal;
flat in int fragBiomeId;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;
// Read from vegetation.treeTrunkHeightFraction in SceneConfig.json, set from the same Java
// constant used to build the trunk mesh — must match the geometry's actual trunk/canopy split
// or this color boundary drifts from where the trunk mesh really ends.
uniform float trunkHeightT;

out vec4 fragColor;

vec3 canopyColorForBiome(int biomeId) {
    if (biomeId == 0) return vec3(0.10, 0.22, 0.18);
    // Autumnal golden-yellow instead of olive-green, matching the prairie's new golden ground.
    if (biomeId == 4) return vec3(0.78, 0.58, 0.16);
    // A rich, natural forest green — went too far toward pale/washed-out last pass. What reads
    // as "toxic" is R and B sitting near zero next to a bright G (a pure, saturated hue); keeping
    // them at a real fraction of G instead keeps the color rich without going neon.
    if (biomeId == 5) return vec3(0.14, 0.36, 0.15);
    return vec3(0.15, 0.35, 0.15);
}

void main() {
    vec3 N = normalize(fragNormal);
    float NdotL = abs(dot(N, normalize(-lightDir)));

    float canopyMix = smoothstep(trunkHeightT - 0.03, trunkHeightT + 0.03, fragHeightT);
    vec3 albedo = mix(vec3(0.32, 0.22, 0.14), canopyColorForBiome(fragBiomeId), canopyMix);

    vec3 color = albedo * (NdotL * lightColor + 0.35 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
