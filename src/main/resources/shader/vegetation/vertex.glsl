#version 410 core

layout(location = 0) in vec3 aLocalPos;

layout(location = 1) in vec3 aNodePos;
layout(location = 2) in float aBiomeId;

uniform mat4 view;
uniform mat4 projection;

uniform float widthMin;
uniform float widthMax;
uniform float heightMin;
uniform float heightMax;

out float fragHeightT;
out vec3 fragNormal;
flat out int fragBiomeId;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

const int BLADES_PER_NODE = 8;

void main() {
    int bladeIndex = gl_InstanceID % BLADES_PER_NODE;
    vec2 seed = aNodePos.xz + vec2(float(bladeIndex) * 91.7, float(bladeIndex) * 13.3);

    float angle       = hash(seed) * 6.28318530718;
    float heightScale = mix(heightMin, heightMax, hash(seed + vec2(17.0, 0.0)));
    float widthScale  = mix(widthMin, widthMax, hash(seed + vec2(31.0, 0.0)));
    float leanScale   = (hash(seed + vec2(53.0, 0.0)) - 0.5) * 0.6;
    vec2 jitter =
            (vec2(hash(seed + vec2(7.0, 0.0)), hash(seed + vec2(0.0, 7.0))) - 0.5) * 0.35;

    vec3 scaled = aLocalPos * vec3(widthScale, heightScale, leanScale);

    float c = cos(angle);
    float s = sin(angle);
    vec3 rotated = vec3(scaled.x * c - scaled.z * s, scaled.y, scaled.x * s + scaled.z * c);
    vec3 normalLocal = vec3(-s, 0.0, c);

    vec3 worldPos = aNodePos + vec3(jitter.x, 0.0, jitter.y) + rotated;

    fragHeightT = aLocalPos.y;
    fragNormal = normalLocal;
    fragBiomeId = int(aBiomeId + 0.5);

    gl_Position = projection * view * vec4(worldPos, 1.0);
}
