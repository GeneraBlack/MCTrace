#version 330

uniform sampler2D MainSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MCTraceParams {
    vec4 HdrConfig;       // x: sceneBrightness, y: paperWhite, z: peakLum, w: contrast
    vec4 LightingConfig;  // x: isHdrActive, y: ssaoMultiplier, z: minLum, w: wideGamutStrength
    vec4 MaterialConfig;  // x: enablePbr, y: enableWaterReflections, z: enableDynamicColoredLight, w: time
    vec4 ExtraConfig;
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
    float peakLum = HdrConfig.z > 0.0 ? HdrConfig.z : 456.0;
    float wideGamut = LightingConfig.w;

    // Scale linear luminance by calibrated Paper White exposure
    float exposure = paperWhite / 200.0;
    color *= exposure;

    // DCI-P3 Wide Color Gamut in scRGB linear:
    // In scRGB linear space, values outside the standard sRGB gamut triangle
    // are represented with coordinates extending beyond [0..1].
    // If wide color gamut is enabled and color is saturated, expand the gamut.
    if (wideGamut > 0.0) {
        float cMax = max(color.r, max(color.g, color.b));
        float cMin = min(color.r, min(color.g, color.b));
        float cSat = (cMax - cMin) / max(cMax, 0.001);
        if (cSat > 0.35) {
            float weight = smoothstep(0.35, 0.85, cSat) * wideGamut;
            // Primary expansion matrix towards DCI-P3 in scRGB basis:
            // DCI-P3 Red in scRGB is (1.2249, -0.0420, -0.0197)
            // DCI-P3 Green in scRGB is (-0.2249, 1.0420, -0.0786)
            // DCI-P3 Blue in scRGB is (0.0, 0.0, 1.0983)
            vec3 expanded = vec3(
                color.r * 1.08 - color.g * 0.04 - color.b * 0.02,
               -color.r * 0.03 + color.g * 1.06 - color.b * 0.03,
               -color.r * 0.01 - color.g * 0.02 + color.b * 1.07
            );
            color = mix(color, expanded, weight);
        }
    }

    // Peak white headroom clamp at exact hardware limit of display (in scRGB, 1.0 = 80 nits)
    float maxHeadroom = max(paperWhite, peakLum) / 80.0;
    fragColor = vec4(clamp(color, 0.0, maxHeadroom), rawColor.a);
}
