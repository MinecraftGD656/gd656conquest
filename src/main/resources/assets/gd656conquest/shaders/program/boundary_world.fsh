#version 150
uniform sampler2D DiffuseSampler;
uniform float BoundaryStrength;
uniform float SuppressionOpacity;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    float luma = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 muted = mix(source.rgb, vec3(luma), 0.72);
    vec3 warm = muted * vec3(1.04, 0.98, 0.90);
    vec2 centered = (texCoord - 0.5) * 2.0;
    float vignette = smoothstep(0.25, 1.30, dot(centered, centered));
    vec3 graded = clamp((warm - 0.5) * 1.04 + 0.5, 0.0, 1.0) * 0.96;
    vec3 color = mix(source.rgb, graded, clamp(BoundaryStrength, 0.0, 1.0));
    float suppressionEdge = smoothstep(0.49, 1.0, max(abs(centered.x), abs(centered.y)));
    float darkness = max(0.28 * vignette * BoundaryStrength, SuppressionOpacity * suppressionEdge);
    fragColor = vec4(color * (1.0 - darkness), source.a);
}
