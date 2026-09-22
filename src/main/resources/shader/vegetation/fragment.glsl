#version 410 core

in vec2 fragUV;
in vec3 fragNormal;
in float fragColorJitter;
flat in int fragBiomeId;

uniform sampler2D grassTexture;
uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;

out vec4 fragColor;

// The texture itself is already a warm gold-olive gradient (savanna's native look) — other
// biomes get the same texture pushed toward their own hue via a multiply tint instead of a
// separate texture per biome.
vec3 tintForBiome(int biomeId) {
    if (biomeId == 0) return vec3(0.85, 0.95, 0.80);
    if (biomeId == 4) return vec3(1.0, 1.0, 1.0);
    // Rich but natural green, matching the canopy — the previous pass went too far toward pale.
    if (biomeId == 5) return vec3(0.45, 0.85, 0.48);
    if (biomeId == 9) return vec3(0.75, 0.80, 0.85);
    return vec3(0.6, 1.0, 0.55);
}

void main() {
    vec4 texColor = texture(grassTexture, fragUV);
    if (texColor.a < 0.5) discard;

    vec3 N = normalize(fragNormal);
    float NdotL = abs(dot(N, normalize(-lightDir)));

    // Per-blade variation — a warm/cool hue mix plus a brightness jitter — so a patch of grass
    // isn't every single blade the exact same tone.
    vec3 variation = mix(vec3(1.08, 0.98, 0.82), vec3(0.85, 1.05, 0.92), fragColorJitter);
    float brightness = mix(0.85, 1.15, fract(fragColorJitter * 17.0));

    vec3 albedo = texColor.rgb * tintForBiome(fragBiomeId) * variation * brightness;
    vec3 color = albedo * (NdotL * lightColor + 0.25 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
