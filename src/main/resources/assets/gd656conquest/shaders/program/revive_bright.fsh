#version 150
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 pixel = 0.5 / InSize;
    vec3 color = (texture(DiffuseSampler, texCoord + vec2(-pixel.x, -pixel.y)).rgb
                + texture(DiffuseSampler, texCoord + vec2( pixel.x, -pixel.y)).rgb
                + texture(DiffuseSampler, texCoord + vec2(-pixel.x,  pixel.y)).rgb
                + texture(DiffuseSampler, texCoord + vec2( pixel.x,  pixel.y)).rgb) * 0.25;
    float light = dot(color, vec3(0.299, 0.587, 0.114));
    float weight = smoothstep(0.42, 0.72, light);
    fragColor = vec4(pow(max(color, vec3(0.0)), vec3(2.2)) * weight, 1.0);
}
