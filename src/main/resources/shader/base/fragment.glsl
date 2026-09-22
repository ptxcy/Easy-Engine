#version 410 core

in vec3 worldPos;
in float temp;
in float humidity;
flat in int vBiomeCell;
in float vWeightTundra;
in float vWeightSavanna;
in float vWeightDeciduous;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;
uniform int debugMode;

uniform float worldMaxHeight;

out vec4 fragColor;

vec3 biomeCellColor(int cell) {

    if (cell == 0) return vec3(0.90, 0.90, 0.95);
    if (cell == 4) return vec3(0.95, 0.75, 0.20);
    if (cell == 5) return vec3(0.05, 0.55, 0.15);
                   return vec3(0.40, 0.40, 0.40);
}

vec3 biomeSoilColor(int cell) {
    if (cell == 0) return vec3(0.56, 0.53, 0.42);
    if (cell == 4) return vec3(0.82, 0.72, 0.42);
    // Warm, bright chestnut brown instead of the earlier dark muted olive-brown — that one still
    // read as a shaded jungle floor; now that this is a proper deciduous forest, a cheerful
    // spring/summer brown fits better than a dim, damp one.
    if (cell == 5) return vec3(0.52, 0.38, 0.20);
                   return vec3(0.35, 0.38, 0.28);
}

// Ground style unified across all three biomes, savanna as the reference: base soil color plus
// two accents that are only a lighter and a darker variant of the SAME hue, never a contrasting
// color (a separate green/brown/grey accent reads as patchwork, not ground variation). Only the
// literal RGB differs per biome — the light/dark structure and coverage (see accentLighter/
// accentDarker below) are identical for all three.
vec3 biomeSoilAccentLighter(int cell) {
    if (cell == 0) return vec3(0.70, 0.67, 0.54);
    if (cell == 4) return vec3(0.90, 0.82, 0.55);
    if (cell == 5) return vec3(0.68, 0.50, 0.28);
                   return vec3(0.50, 0.53, 0.43);
}

vec3 biomeSoilAccentDarker(int cell) {
    if (cell == 0) return vec3(0.42, 0.38, 0.28);
    if (cell == 4) return vec3(0.68, 0.56, 0.26);
    if (cell == 5) return vec3(0.31, 0.23, 0.12);
                   return vec3(0.22, 0.24, 0.17);
}

// --- World-space value noise, used both for surface grain and for dithering the biome blend.
// No UVs exist on terrain vertices and none are needed: sampling in world XZ means it never
// tiles or seams across chunk borders on an arbitrarily large landscape.
float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 34.45);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm2(vec2 p) {
    return valueNoise(p) * 0.65 + valueNoise(p * 2.13) * 0.35;
}

void main() {

    vec3 N = normalize(cross(dFdx(worldPos), dFdy(worldPos)));
    float NdotL = max(dot(N, normalize(-lightDir)), 0.0);

    vec3 albedo;
    if (debugMode == 1) {
        albedo = mix(vec3(0.1, 0.3, 0.9), vec3(0.9, 0.2, 0.1), (temp + 1.0) / 2.0);
    } else if (debugMode == 2) {
        albedo = mix(vec3(0.8, 0.6, 0.3), vec3(0.1, 0.7, 0.8), (humidity + 1.0) / 2.0);
    } else if (debugMode == 3) {
        albedo = biomeCellColor(vBiomeCell);
    } else {
        float h = clamp(worldPos.y / worldMaxHeight, 0.0, 1.0);

        // Stochastic biome splatting instead of a linear weighted average: three very
        // different-hued soil colors mixed by plain interpolation collapse into a washed-out,
        // muddy in-between right at the boundary (the "watercolor" look). Perturbing each
        // biome's weight with its own noise and taking the winner outright means the color stays
        // fully saturated everywhere; only near a boundary, where weights are close, does the
        // winner flip fragment-to-fragment, reading as a mottled, patchy transition instead of a
        // smooth wash.
        vec2 splatCoord = worldPos.xz * 0.08;
        float wTundra = vWeightTundra + (fbm2(splatCoord) - 0.5) * 0.55;
        float wSavanna = vWeightSavanna + (fbm2(splatCoord + vec2(19.7, 5.3)) - 0.5) * 0.55;
        float wDeciduous = vWeightDeciduous + (fbm2(splatCoord + vec2(-31.1, 12.4)) - 0.5) * 0.55;

        int winningBiome;
        if (wTundra >= wSavanna && wTundra >= wDeciduous) {
            winningBiome = 0;
        } else if (wSavanna >= wDeciduous) {
            winningBiome = 4;
        } else {
            winningBiome = 5;
        }
        vec3 soil = biomeSoilColor(winningBiome);

        // First accent: lighter patches, same coverage rule for every biome.
        float accentLighter = fbm2(worldPos.xz * 0.8 + vec2(41.0, 7.0));
        soil = mix(
                soil,
                biomeSoilAccentLighter(winningBiome),
                smoothstep(0.6, 0.85, accentLighter) * 0.6);

        // Second accent: darker patches, same coverage rule for every biome.
        float accentDarker = fbm2(worldPos.xz * 0.5 + vec2(-13.0, 88.0));
        soil = mix(
                soil,
                biomeSoilAccentDarker(winningBiome),
                smoothstep(0.62, 0.88, accentDarker) * 0.45);

        vec3 rock = vec3(0.40, 0.39, 0.41);
        vec3 snow = vec3(0.90, 0.90, 0.92);

        vec3 lowColor  = soil;
        vec3 midColor  = mix(soil, rock, 0.6);
        vec3 highColor = mix(rock, snow, 0.6);
        vec3 heightAlbedo = h < 0.45
            ? mix(lowColor,  midColor,  smoothstep(0.30, 0.44, h))
            : mix(midColor, highColor, smoothstep(0.50, 0.68, h));

        float steepness = 1.0 - N.y;
        albedo = mix(heightAlbedo, rock, smoothstep(0.35, 0.75, steepness));

        // Fine surface grain so flat ground doesn't read as a single flat color — sampled in
        // world XZ, so it holds still under the player instead of swimming like a screen-space
        // effect would.
        float grain = fbm2(worldPos.xz * 0.6) * 0.65 + fbm2(worldPos.xz * 4.0) * 0.35;
        albedo *= 0.86 + 0.28 * grain;
    }

    // Hemispherical ambient (sky tint from above, warm ground-bounce tint from below) instead of
    // a flat gray constant — gives faces their ambient light color from which way they face, not
    // just a uniform wash, which reads as far less flat on rolling terrain.
    vec3 skyAmbient = vec3(0.20, 0.19, 0.19);
    vec3 groundAmbient = vec3(0.17, 0.13, 0.09);
    float hemi = N.y * 0.5 + 0.5;
    vec3 ambient = mix(groundAmbient, skyAmbient, hemi) * ao;

    vec3 color = albedo * (NdotL * lightColor + ambient);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
