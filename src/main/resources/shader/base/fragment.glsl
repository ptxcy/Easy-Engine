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
// MUSS mit ALPINE_HEIGHT_FRACTION in Map.java übereinstimmen (0.62 dort ist relativ zu genau
// diesem Wert gemeint) -- kommt aus SceneConfig.json terrain.heightAmplitude.
uniform float heightAmplitude;
// Fester Weltmaßstab für die Höhenfärbung (SceneConfig.json terrain.worldMaxHeightMeters) --
// EIN gemeinsamer Bezugswert für alle Biome statt eines pro Biom relativen Maximums. Eine frühere
// Fassung normierte je Biom auf dessen eigenes Maximum (Biom-amplitude*heightAmplitude); dadurch
// zeigte ein 30m hoher Savanne-Hügel an seinem eigenen Gipfel bereits "Schnee" (100% seines
// winzigen eigenen Maximums), und die Farbe sprang an Zellgrenzen hart, weil sich dort das
// Referenzmaximum sprunghaft änderte, obwohl die Höhe selbst stetig blieb (siehe Map.java,
// resolveCellBlend). Mit einem einzigen festen Bezugswert ist die Färbung überall eine stetige
// Funktion der tatsächlichen Höhe -- niedrige Biome (Savanne/Regenwald) bleiben durchgängig im
// Bodenfarbbereich, nur Tundra/Steppe erreicht ihn.
uniform float worldMaxHeight;

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

// Bodenfarbe für die normale (nicht-Debug) Darstellung -- dieselben Grundtöne wie die
// Gras-/Baumkronenfarben (shader/vegetation/fragment.glsl, shader/tree/fragment.glsl), damit Boden
// und Bewuchs farblich zusammenpassen statt wie separate Systeme zu wirken.
// Tundra/Steppe an reale Permafrost-Tundra angelehnt: blasses, kühles Khaki (Flechten/
// Trockengras), bewusst deutlich wärmer/heller als das Gestein darüber -- vorher beide zu
// ähnliche Grüntöne, kaum unterscheidbar. Savanne/Regenwald unverändert.
vec3 biomeSoilColor(int cell) {
    if (cell == 0) return vec3(0.62, 0.57, 0.35); // TUNDRA_STEPPE
    if (cell == 4) return vec3(0.45, 0.55, 0.12); // SAVANNA_PRAIRIE
    if (cell == 8) return vec3(0.06, 0.32, 0.10); // RAINFOREST
                   return vec3(0.35, 0.38, 0.28); // nicht designte Platzhalterzellen
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
        float h = clamp(worldPos.y / worldMaxHeight, 0.0, 1.0);

        // Bodenfarbe als gewichtete Summe der drei designten Zellen -- dieselben Gewichte, mit
        // denen Map.getHeight() bereits die GEOMETRIE zweier benachbarter Zellen mischt. Bewusst
        // keine "flat" Zell-ID im Spiel (siehe vertex.glsl): jedes Gewicht interpoliert für sich
        // stetig, unabhängig davon, welche Zelle an einem gegebenen Vertex gerade "die nächste"
        // ist.
        vec3 soil = vWeightTundra * biomeSoilColor(0)
                  + vWeightSavanna * biomeSoilColor(4)
                  + vWeightRainforest * biomeSoilColor(8);
        // Kühleres, neutraleres Grau als vorher -- deutlicher Kontrast zum warmen Tundra-Khaki.
        vec3 rock = vec3(0.40, 0.39, 0.41); // nacktes Gestein, Höhenband UND Steilheit
        vec3 snow = vec3(0.90, 0.90, 0.92); // Gipfel-Schnee/Reif

        // Höhenband: Talboden in Biomfarbe, Hang zunehmend fels-durchsetzt, Gipfel Fels/Schnee --
        // ersetzt die vorherige, für alle Biome identische Grün/Braun/Grau-Abfolge.
        vec3 lowColor  = soil;
        vec3 midColor  = mix(soil, rock, 0.6);
        vec3 highColor = mix(rock, snow, 0.6);
        vec3 heightAlbedo = h < 0.45
            ? mix(lowColor,  midColor,  smoothstep(0.30, 0.44, h))
            : mix(midColor, highColor, smoothstep(0.50, 0.68, h));

        // Steilheit unabhängig von Höhe/Biom: auch ein steiler Taleinschnitt zeigt Fels statt
        // Bewuchsfarbe, weil Boden/Vegetation an sehr steilen Hängen nicht haften bleibt.
        float steepness = 1.0 - N.y;
        albedo = mix(heightAlbedo, rock, smoothstep(0.35, 0.75, steepness));
    }

    vec3 color = albedo * (NdotL * lightColor + 0.15 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
