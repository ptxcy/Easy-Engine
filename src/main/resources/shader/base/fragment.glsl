#version 410 core

in vec3 worldPos;
in vec3 normal;

uniform vec3 lightDir;
uniform vec3 lightColor;
uniform float ao;

out vec4 fragColor;

void main() {
    vec3 N = normalize(normal);
    float NdotL = max(dot(N, normalize(-lightDir)), 0.0);

    // height-based color: grün (niedrig) → grau (hoch)
    float h = clamp(worldPos.y / 20.0, 0.0, 1.0);
    vec3 albedo = mix(vec3(0.28, 0.42, 0.18), vec3(0.55, 0.50, 0.45), smoothstep(0.4, 0.8, h));

    vec3 color = albedo * (NdotL * lightColor + 0.15 * ao);
    color = pow(color, vec3(1.0 / 2.2));
    fragColor = vec4(color, 1.0);
}
