#version 410 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in float aTemp;
layout(location = 2) in float aHumidity;
layout(location = 3) in float aBiomeCell;
layout(location = 4) in float aWeightTundra;
layout(location = 5) in float aWeightSavanna;
layout(location = 6) in float aWeightDeciduous;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec3 worldPos;
out float temp;
out float humidity;

flat out int vBiomeCell;

out float vWeightTundra;
out float vWeightSavanna;
out float vWeightDeciduous;

void main() {
    vec4 wp = model * vec4(aPos, 1.0);
    worldPos = wp.xyz;
    temp = aTemp;
    humidity = aHumidity;

    vBiomeCell = int(aBiomeCell + 0.5);
    vWeightTundra = aWeightTundra;
    vWeightSavanna = aWeightSavanna;
    vWeightDeciduous = aWeightDeciduous;

    gl_Position = projection * view * wp;
}
