#version 330

uniform sampler2D MainSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MCTraceParams {
    vec4 HdrConfig;       // x: sceneBrightness, y: paperWhite, z: peakLum, w: contrast
    vec4 LightingConfig;  // x: isHdrActive, y: ssaoMultiplier, z: minLum, w: unused
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 rawColor = texture(MainSampler, texCoord);
    float isHdr = LightingConfig.x;

    // 1. SDR Mode Pass-through:
    // In SDR mode (8-bit sRGB swapchain), the 3D world was already fully shaded,
    // brightened, and contrast-calibrated in world.fsh.
    // Menus, item icons, and UI are drawn in authentic sRGB gamma.
    // Passing through rawColor ensures menus and item icons are NEVER crushed to black!
    if (isHdr <= 0.5) {
        fragColor = rawColor;
        return;
    }

    // 2. True HDR Mode (scRGB Linear swapchain):
    // In scRGB linear, Windows DWM expects linear optical values.
    // Converting sRGB to linear restores deep blacks, rich contrast, and prevents washed-out gray menus.
    vec3 color = rawColor.rgb;
    // Standard IEC 61966-2-1 sRGB to Linear conversion
    color = mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(0.04045, color));

    float paperWhite = HdrConfig.y > 0.0 ? HdrConfig.y : 200.0;
    float peakLum = HdrConfig.z > 0.0 ? HdrConfig.z : 1000.0;

    // Scale linear luminance by calibrated Paper White exposure
    float exposure = paperWhite / 200.0;
    color *= exposure;

    // Peak white headroom clamp (in scRGB, 1.0 = 80 nits)
    float maxHeadroom = max(paperWhite, peakLum) / 80.0;
    fragColor = vec4(clamp(color, 0.0, maxHeadroom), rawColor.a);
}
