#version 150

uniform sampler2D DiffuseSampler;
uniform float DeathFilterStrength;
uniform float DeathContrast;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    float luminance = dot(source.rgb, vec3(0.299, 0.587, 0.114));
    vec3 color = mix(source.rgb, vec3(luminance), DeathFilterStrength);
    // Neutral, restrained S-curve: preserve black/white endpoints and shadow detail.
    float contrast = (DeathContrast - 1.0) * (luminance - 0.5) * 4.0 * luminance * (1.0 - luminance);
    color += vec3(contrast * DeathFilterStrength);
    fragColor = vec4(clamp(color, 0.0, 1.0), source.a);
}
