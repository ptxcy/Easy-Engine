#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in float aTemp;
layout(location = 3) in float aHumidity;
layout(location = 4) in float aBiomeCell;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec3 worldPos;
out float temp;
out float humidity;
flat out int vBiomeCell;

void main() {
    vec4 wp = model * vec4(aPos, 1.0);
    worldPos = wp.xyz;
    temp = aTemp;
    humidity = aHumidity;

    // Kommt direkt von Map.getBiomeCell() (CPU-Seite) statt hier aus Roh-Temperatur/-Feuchte neu
    // berechnet zu werden -- das respektiert Pool-Clamp und Enabled-Filter (siehe
    // BiomeLookUpTable.enabled) und stimmt dadurch garantiert mit der Zelle überein, deren
    // Amplitude/Frequenz/etc. tatsächlich die Höhe an diesem Punkt geformt haben.
    vBiomeCell = int(aBiomeCell + 0.5);

    gl_Position = projection * view * wp;
}
