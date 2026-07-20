#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoords;
layout(location = 2) in float aTemp;
layout(location = 3) in float aHumidity;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform float heightTempLapse;

out vec3 worldPos;
out float temp;
out float humidity;
flat out int vBiomeCell;

void main() {
    vec4 wp = model * vec4(aPos, 1.0);
    worldPos = wp.xyz;
    temp = aTemp;
    humidity = aHumidity;

    // Biom-Zelle nutzt die Roh-Temperatur (vor Höhenabzug), sonst hängt die Klassifikation
    // zusätzlich vom lokalen Terrain-Rauschen ab und Klimazonen wirken zerrissen/überlappend,
    // obwohl jeder Punkt klimatisch eindeutig einer Zelle zugeordnet ist.
    float rawTemp = aTemp + worldPos.y * heightTempLapse;
    int row = int(clamp((rawTemp   + 1.0) / 2.0 * 2.9999, 0.0, 2.0));
    int col = int(clamp((aHumidity + 1.0) / 2.0 * 2.9999, 0.0, 2.0));
    vBiomeCell = row * 3 + col;

    gl_Position = projection * view * wp;
}
