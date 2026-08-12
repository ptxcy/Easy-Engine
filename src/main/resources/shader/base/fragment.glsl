#version 410 core

in vec3 worldPos;
in float temp;
in float humidity;
flat in int vBiomeCell;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;
uniform int debugMode;

out vec4 fragColor;

vec3 biomeCellColor(int cell) {
    // Nur die 3 aktivierten Zellen (Scope-Entscheidung 2026-08-06, siehe SceneConfig.json
    // "enabled") bekommen kräftige, klar unterscheidbare Farben -- alle anderen, nicht designten
    // Zellen einheitlich neutral-grau. So ist auf einen Blick erkennbar, ob man gerade eine der 3
    // echten Zellen sieht oder (z.B. beim isolierten Testen im Editor) eine Platzhalter-Zelle.
    if (cell == 0) return vec3(0.90, 0.90, 0.95); // Tundra/Steppe (aktiv)          -- helles Weiß
    if (cell == 4) return vec3(0.95, 0.75, 0.20); // mittlere Zelle (aktiv)         -- kräftiges Gold
    if (cell == 8) return vec3(0.05, 0.55, 0.15); // Regenwald (aktiv)              -- kräftiges Grün
                   return vec3(0.40, 0.40, 0.40); // restliche 6 Zellen, nicht designt
}

void main() {
    // Flat-Shading-Normale aus Screen-Space-Derivatives statt Vertex-Attribut --
    // erlaubt volle Vertex-Deduplizierung via Index-Buffer. Falls die Beleuchtung
    // invertiert wirkt (dunkel wo hell erwartet), dFdx/dFdy tauschen oder Ergebnis negieren.
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
        float h = clamp(worldPos.y / 80.0, 0.0, 1.0);
        vec3 lowColor  = vec3(0.28, 0.42, 0.18); // grün (tal)
        vec3 midColor  = vec3(0.50, 0.44, 0.32); // braun (hang)
        vec3 highColor = vec3(0.75, 0.73, 0.70); // grau (gipfel)
        albedo = h < 0.45
            ? mix(lowColor,  midColor,  smoothstep(0.40, 0.44, h))
            : mix(midColor, highColor, smoothstep(0.56, 0.62, h));
    }

    vec3 color = albedo * (NdotL * lightColor + 0.15 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
