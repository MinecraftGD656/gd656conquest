#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDirection;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 stepSize = BlurDirection * max(1.5, InSize.y * 0.003) / InSize;
    vec3 color = texture(DiffuseSampler, texCoord).rgb * 0.227027;
    color += (texture(DiffuseSampler, texCoord - stepSize).rgb + texture(DiffuseSampler, texCoord + stepSize).rgb) * 0.194595;
    color += (texture(DiffuseSampler, texCoord - stepSize * 2.0).rgb + texture(DiffuseSampler, texCoord + stepSize * 2.0).rgb) * 0.121622;
    color += (texture(DiffuseSampler, texCoord - stepSize * 3.0).rgb + texture(DiffuseSampler, texCoord + stepSize * 3.0).rgb) * 0.054054;
    color += (texture(DiffuseSampler, texCoord - stepSize * 4.0).rgb + texture(DiffuseSampler, texCoord + stepSize * 4.0).rgb) * 0.016216;
    fragColor = vec4(color, 1.0);
}
