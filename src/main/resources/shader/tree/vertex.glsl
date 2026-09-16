#version 410 core

layout(location = 0) in vec3 aLocalPos;
layout(location = 1) in vec3 aLocalNormal;
layout(location = 2) in float aHeightT;

layout(location = 3) in vec3 aNodePos;
layout(location = 4) in float aBiomeId;

uniform mat4 view;
uniform mat4 projection;

uniform float heightMin;
uniform float heightMax;
uniform float radiusMin;
uniform float radiusMax;

out float fragHeightT;
out vec3 fragNormal;
flat out int fragBiomeId;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 seed = aNodePos.xz;

    float angle = hash(seed) * 6.28318530718;
    float heightScale = mix(heightMin, heightMax, hash(seed + vec2(17.0, 0.0)));
    float radiusScale = mix(radiusMin, radiusMax, hash(seed + vec2(31.0, 0.0)));

    vec3 scaled = aLocalPos * vec3(radiusScale, heightScale, radiusScale);

    float c = cos(angle);
    float s = sin(angle);
    vec3 rotatedPos = vec3(scaled.x * c - scaled.z * s, scaled.y, scaled.x * s + scaled.z * c);
    vec3 rotatedNormal =
            vec3(aLocalNormal.x * c - aLocalNormal.z * s,
                 aLocalNormal.y,
                 aLocalNormal.x * s + aLocalNormal.z * c);

    vec3 worldPos = aNodePos + rotatedPos;

    fragHeightT = aHeightT;
    fragNormal = rotatedNormal;
    fragBiomeId = int(aBiomeId + 0.5);

    gl_Position = projection * view * vec4(worldPos, 1.0);
}
