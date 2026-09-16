#version 410 core

in vec3 worldPos;
in float temp;
in float humidity;
flat in int vBiomeCell;
in float vWeightTundra;
in float vWeightSavanna;
in float vWeightRainforest;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;
uniform int debugMode;

uniform float worldMaxHeight;

out vec4 fragColor;

vec3 biomeCellColor(int cell) {

    if (cell == 0) return vec3(0.90, 0.90, 0.95);
    if (cell == 4) return vec3(0.95, 0.75, 0.20);
    if (cell == 8) return vec3(0.05, 0.55, 0.15);
                   return vec3(0.40, 0.40, 0.40);
}

vec3 biomeSoilColor(int cell) {
    if (cell == 0) return vec3(0.62, 0.57, 0.35);
    if (cell == 4) return vec3(0.45, 0.55, 0.12);
    if (cell == 8) return vec3(0.06, 0.32, 0.10);
                   return vec3(0.35, 0.38, 0.28);
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

        vec3 soil = vWeightTundra * biomeSoilColor(0)
                  + vWeightSavanna * biomeSoilColor(4)
                  + vWeightRainforest * biomeSoilColor(8);

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
    }

    vec3 color = albedo * (NdotL * lightColor + 0.15 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
