#version 410 core

in vec2 fragTexCoords;
in vec3 worldPos;
in vec3 normal;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform vec3 albedo;
uniform float ao;

uniform sampler2D diffuseTexOne;
uniform sampler2D diffuseTexTwo;
uniform sampler2D diffuseTexThree;

uniform sampler2D noiseTex;

uniform float noiseScale;
uniform float mixThreshold;
uniform float mixStrength;

out vec4 resultColor;

void main()
{
    // --- Normal ---
    vec3 N = normalize(normal);

    // --- Licht ---
    vec3 L = normalize(-lightDir);
    float NdotL = max(dot(N, L), 0.0);

    // --- Noise Sampling (World Space XZ) ---
    vec2 noiseUV = worldPos.xz * noiseScale;
    float noiseValue = texture(noiseTex, noiseUV).r;
    float t = smoothstep(mixThreshold - 0.1,
    mixThreshold + 0.1,
    noiseValue);

    // --- Basis Texturen ---
    vec3 colorA = texture(diffuseTexOne,   fragTexCoords).rgb;
    vec3 colorB = texture(diffuseTexTwo,   fragTexCoords).rgb;
    vec3 colorC = texture(diffuseTexThree, fragTexCoords).rgb;

    // --- Mix 1 (A + B) ---
    vec3 mixAB = mix(colorA, colorB, t * mixStrength);

    // --- Mix 2 ((A+B) + C) ---
    vec3 finalBase = mix(mixAB, colorC, t);

    // --- Lighting ---
    vec3 diffuse = finalBase * NdotL * lightColor;
    vec3 ambient = finalBase * 0.3 * ao;
    vec3 color = ambient + diffuse;

    // Gamma
    color = pow(color, vec3(1.0/2.2));

    resultColor = vec4(color, 1.0);
}
