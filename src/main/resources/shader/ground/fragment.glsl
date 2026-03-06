#version 410 core

in vec2 fragTexCoords;
in vec3 worldPos;
in vec3 normal;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;

uniform sampler2D diffuseTexOne;
uniform sampler2D diffuseTexTwo;
uniform sampler2D diffuseTexThree;
uniform sampler2D noiseTex;

uniform float mixStrength;
uniform float terrainSize;
uniform float sharpness;

out vec4 resultColor;
///*
void main()
{
    vec3 N = normalize(normal);
    vec3 L = normalize(-lightDir);
    float NdotL = max(dot(N, L), 0.0);
    vec2 uv = worldPos.xz / terrainSize;

    float noise = clamp(texture(noiseTex, uv).r, 0.0, 1.0);
    vec3 colorA = texture(diffuseTexOne, uv).rgb;
    vec3 colorB = texture(diffuseTexTwo, uv).rgb;
    vec3 colorC = texture(diffuseTexThree, uv).rgb;

    vec3 base;

    if (noise < 0.5) {
        float t = clamp((noise - 0.25) * sharpness + 0.5, 0.0, 1.0);
        base = mix(colorA, colorB, t);
    } else {
        float t = clamp((noise - 0.75) * sharpness + 0.5, 0.0, 1.0);
        base = mix(colorB, colorC, t);
    }

    vec3 lighting = base * (NdotL * lightColor + 0.3 * ao);
    lighting = pow(lighting, vec3(1.0 / 2.2));
    resultColor = vec4(lighting, 1.0);
}
//*/

/*
void main()
{
    //Display Noise Debug
    vec2 uv = fragTexCoords;
    float noise = texture(noiseTex, uv).r;
    resultColor = vec4(vec3(noise), 1.0);
}
*/