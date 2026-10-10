#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;
uniform float ExposureEV;
uniform float BloomGain;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    // The final pass sets both gains to zero for an exact RGBA copy.
    // Replacement blending preserves sky RGB even when its alpha is zero.
    if (ExposureEV == 0.0 && BloomGain == 0.0) {
        fragColor = source;
        return;
    }

    vec3 linearColor = pow(clamp(source.rgb, 0.0, 1.0), vec3(2.2));
    vec3 halo = max(texture(BloomSampler, texCoord).rgb, vec3(0.0)) * BloomGain;
    float exposure = exp2(ExposureEV);
    vec3 exposed = (linearColor + halo) * exposure;
    // The soft shoulder retains dark silhouettes while highlights approach white.
    vec3 shoulder = vec3(1.0) + linearColor * (exposure - 1.0) + halo * exposure;
    vec3 color = pow(clamp(exposed / shoulder, 0.0, 1.0), vec3(1.0 / 2.2));
    fragColor = vec4(color, source.a);
}
