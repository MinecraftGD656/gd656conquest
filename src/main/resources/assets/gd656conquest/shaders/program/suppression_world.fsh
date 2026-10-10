#version 150
uniform sampler2D DiffuseSampler;
uniform float SuppressionOpacity;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    vec2 centered = (texCoord - 0.5) * 2.0;
    float vignette = smoothstep(0.49, 1.0, max(abs(centered.x), abs(centered.y)));
    fragColor = vec4(source.rgb * (1.0 - SuppressionOpacity * vignette), source.a);
}
