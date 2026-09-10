#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in float aTemp;
layout(location = 3) in float aHumidity;
layout(location = 4) in float aBiomeCell;
layout(location = 5) in float aWeightTundra;
layout(location = 6) in float aWeightSavanna;
layout(location = 7) in float aWeightRainforest;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec3 worldPos;
out float temp;
out float humidity;
// NUR für debugMode 3 (Zell-Debugansicht) -- absichtlich flat, dort soll die Zellgrenze hart
// sichtbar sein. Für die normale Bodenfarbe zählen ausschließlich die drei Gewichte unten.
flat out int vBiomeCell;
// Bewusst je EIN eigenes, überall stetiges Gewicht pro designter Zelle statt einer diskreten
// "nächste/zweitnächste Zelle"-Zuordnung mit gemeinsamem Blendfaktor -- Letzteres bräuchte
// zwangsläufig ein "flat" Zell-Index-Attribut, dessen Wert OpenGL pro Dreieck nur vom
// "provoking vertex" übernimmt. Sobald sich zwei Vertices eines Dreiecks uneinig sind, welche
// Zelle die nächste ist (der Normalfall in der gesamten Übergangszone), bezog sich der separat
// interpolierte Blendfaktor dann auf die falsche Zellenpaarung -- sichtbar als harter Schnitt,
// nicht nur bei großen Höhenunterschieden. Mit drei fest zugeordneten Gewichten (keines davon
// flat) gibt es diese Mehrdeutigkeit nicht mehr; jedes interpoliert für sich korrekt.
out float vWeightTundra;
out float vWeightSavanna;
out float vWeightRainforest;

void main() {
    vec4 wp = model * vec4(aPos, 1.0);
    worldPos = wp.xyz;
    temp = aTemp;
    humidity = aHumidity;

    // Kommt direkt von Map.getBiomeCell()/getBiomeWeights() (CPU-Seite) statt hier aus
    // Roh-Temperatur/-Feuchte neu berechnet zu werden -- das respektiert Pool-Clamp und
    // Enabled-Filter (siehe BiomeLookUpTable.enabled) und stimmt dadurch garantiert mit der
    // Zuordnung überein, die Map.getHeight() tatsächlich für die Geometrie an diesem Punkt
    // verwendet hat.
    vBiomeCell = int(aBiomeCell + 0.5);
    vWeightTundra = aWeightTundra;
    vWeightSavanna = aWeightSavanna;
    vWeightRainforest = aWeightRainforest;

    gl_Position = projection * view * wp;
}
