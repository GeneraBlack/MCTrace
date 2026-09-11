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
    // Maps vibrant world elements (deep sunset oranges, torch embers, neon sculk cyan,
    // lush green foliage, crimson redstone) into the wider DCI-P3 color gamut while
    // keeping stone, wood, dirt, and UI completely natural!
    if (wideGamut > 0.0) {
        float cMax = max(color.r, max(color.g, color.b));
        float cMin = min(color.r, min(color.g, color.b));
        float cSat = (cMax - cMin) / max(cMax, 0.001);

        // Stone, wood, and UI containers have low saturation (cSat < 0.28) and stay 100% natural
        if (cSat > 0.28) {
            float gamutWeight = smoothstep(0.28, 0.85, cSat) * wideGamut;
            vec3 p3Color = color;

            // 1. Sunset orange, fire & torch embers (high R, medium G, low B)
            if (color.r > color.g && color.g > color.b && color.r > 0.15) {
                // Expand towards DCI-P3 red primary (1.2249, -0.2249, 0.0)
                p3Color.r = color.r * 1.20 - color.g * 0.05;
                p3Color.g = color.g * 0.94;
                p3Color.b = color.b * 0.80;
            }
            // 2. Neon Sculk & Soul fire cyan (high B and G, low R)
            else if (color.b > color.r && color.g > color.r && (color.b + color.g) > 0.25) {
                // Expand into spectral DCI-P3 cyan
                p3Color.b = color.b * 1.22;
                p3Color.g = color.g * 1.16;
                p3Color.r = color.r * 0.80;
            }
            // 3. Lush green foliage & grass (dominant G, lower R and B)
            else if (color.g > color.r && color.g > color.b && color.g > 0.10) {
                // Expand towards DCI-P3 green primary (-0.0420, 1.0420, -0.0786)
                p3Color.g = color.g * 1.24 - color.r * 0.04;
                p3Color.r = color.r * 0.86;
                p3Color.b = color.b * 0.82;
            }
            // 4. Crimson redstone (dominant R, very low G and B)
            else if (color.r > 0.20 && color.g < color.r * 0.40 && color.b < color.r * 0.40) {
                // Pure spectral DCI-P3 red
                p3Color.r = color.r * 1.26;
                p3Color.g = color.g * 0.85;
                p3Color.b = color.b * 0.85;
            }

            color = mix(color, p3Color, gamutWeight);
        }
    }

    // Peak white headroom clamp at exact hardware limit of display (in scRGB, 1.0 = 80 nits)
    float maxHeadroom = max(paperWhite, peakLum) / 80.0;
    fragColor = vec4(clamp(color, 0.0, maxHeadroom), rawColor.a);
}
