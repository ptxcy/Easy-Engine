#version 410 core

layout(location = 0) in vec3 aLocalPos;
layout(location = 3) in vec3 aLocalNormal;
layout(location = 4) in vec2 aLocalUV;

layout(location = 1) in vec3 aNodePos;
layout(location = 2) in float aBiomeId;

uniform mat4 view;
uniform mat4 projection;
uniform vec3 camPos;

uniform float widthMin;
uniform float widthMax;
uniform float heightMin;
uniform float heightMax;
uniform float time;

// Blade count/scatter/LOD tuning — read from vegetation.* in SceneConfig.json (set once per
// frame from ChunkManager, not baked in here) so Java and GLSL can never drift out of sync.
uniform int bladesPerNode;
uniform float scatterRadius;
// Distance LOD: thin out how many of a node's bladesPerNode blades actually render as distance
// grows, instead of drawing full density all the way to the grass cutoff distance. Blades beyond
// the allowed count are collapsed to one degenerate point (zero-area triangle, discarded by the
// rasterizer before it ever reaches the fragment shader) — cheap, and needs no CPU/draw-call
// changes since it only touches per-instance logic already in this shader.
uniform float lodNearDistance;
uniform float lodFarDistance;
uniform float lodMinBlades;

out vec2 fragUV;
out vec3 fragNormal;
out float fragColorJitter;
flat out int fragBiomeId;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

const vec2 WIND_DIRECTION = vec2(0.83, 0.55);
const float WIND_STRENGTH = 0.35;
const float WIND_SPEED = 1.8;
const vec2 WIND_WAVE_SCALE = vec2(0.05, 0.03);

void main() {
    int bladeIndex = gl_InstanceID % bladesPerNode;
    vec2 seed = aNodePos.xz + vec2(float(bladeIndex) * 91.7, float(bladeIndex) * 13.3);

    float camDist = distance(aNodePos, camPos);
    float lodT = clamp((camDist - lodNearDistance) / (lodFarDistance - lodNearDistance), 0.0, 1.0);
    float maxBladeIndex = mix(float(bladesPerNode), lodMinBlades, lodT);
    if (float(bladeIndex) >= maxBladeIndex) {
        gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
        return;
    }

    float angle       = hash(seed) * 6.28318530718;
    float heightScale = mix(heightMin, heightMax, hash(seed + vec2(17.0, 0.0)));
    float widthScale  = mix(widthMin, widthMax, hash(seed + vec2(31.0, 0.0)));
    // Random bend per blade — can curve either way, some barely at all, some quite far.
    float leanScale   = (hash(seed + vec2(53.0, 0.0)) - 0.5) * 1.2;
    vec2 jitter =
            (vec2(hash(seed + vec2(7.0, 0.0)), hash(seed + vec2(0.0, 7.0))) - 0.5)
            * (2.0 * scatterRadius);
    float windPhase =
            dot(aNodePos.xz, WIND_WAVE_SCALE) + hash(seed + vec2(41.0, 0.0)) * 1.5;

    vec3 scaled = aLocalPos * vec3(widthScale, heightScale, leanScale);

    float c = cos(angle);
    float s = sin(angle);
    vec3 rotated = vec3(scaled.x * c - scaled.z * s, scaled.y, scaled.x * s + scaled.z * c);
    vec3 rotatedNormal =
            vec3(aLocalNormal.x * c - aLocalNormal.z * s,
                 aLocalNormal.y,
                 aLocalNormal.x * s + aLocalNormal.z * c);

    vec3 worldPos = aNodePos + vec3(jitter.x, 0.0, jitter.y) + rotated;

    float sway = sin(time * WIND_SPEED + windPhase) * WIND_STRENGTH * aLocalPos.y;
    worldPos.xz += WIND_DIRECTION * sway;

    fragUV = aLocalUV;
    fragNormal = rotatedNormal;
    fragColorJitter = hash(seed + vec2(71.0, 0.0));
    fragBiomeId = int(aBiomeId + 0.5);

    gl_Position = projection * view * vec4(worldPos, 1.0);
}
