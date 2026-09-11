#version 330

uniform sampler2D MainSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MCTraceParams {
    vec4 HdrConfig;       // x: minLum, y: paperWhite, z: peakLum, w: contrast
    vec4 LightingConfig;  // x: isHdrActive, y: ssaoMultiplier, z: timeOfDay, w: unused
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 rawColor = texture(MainSampler, texCoord);
    vec3 color = rawColor.rgb;

    // Display calibration parameters
    float minLum = max(0.0, HdrConfig.x);
    float paperWhite = HdrConfig.y > 0.0 ? HdrConfig.y : 200.0;
    float peakLum = HdrConfig.z > 0.0 ? HdrConfig.z : 1000.0;
    float contrast = HdrConfig.w > 0.0 ? HdrConfig.w : 1.0;
    float isHdr = LightingConfig.x;

    // 1. True HDR (scRGB linear swapchain) Gamma Decoding:
    // Minecraft renders in sRGB gamma space. In an scRGB linear swapchain,
    // Windows DWM expects linear color values. Without decoding sRGB gamma,
    // all darks and midtones are lifted up by up to 300% (causing menus & darks
    // to look washed-out, pale gray, and milky).
    // Converting sRGB to linear restores deep, inky blacks, authentic dark backgrounds,
    // and punchy contrast.
    if (isHdr > 0.5) {
        // Standard IEC 61966-2-1 sRGB to Linear conversion
        color = mix(color / 12.92, pow((color + 0.055) / 1.055, vec3(2.4)), step(0.04045, color));
    }

    // 2. Dynamic Display Calibration: Filmic S-curve Contrast
    float effectiveContrast = contrast;
    if (isHdr > 0.5) {
        effectiveContrast *= 1.10; // Extra midtone punch for HDR dynamic range
    }

    if (abs(effectiveContrast - 1.0) > 0.01) {
        float pivot = (isHdr > 0.5) ? 0.18 : 0.45; // 18% linear gray for HDR, 45% gamma gray for SDR
        color = pow(max(color / pivot, vec3(0.0)), vec3(effectiveContrast)) * pivot;
    }

    // 3. Black level floor calibration
    if (minLum > 0.0) {
        float floorVal = (isHdr > 0.5) ? minLum * 0.05 : minLum * 0.2;
        color = max(color, vec3(floorVal));
    }

    // 4. Color Vibrancy & Atmospheric De-Hazing (removes gray veil and enhances colors)
    color = max(color - vec3(0.015), vec3(0.0)) / 0.985;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float maxC = max(color.r, max(color.g, color.b));
    float minC = min(color.r, min(color.g, color.b));
    float sat = (maxC - minC) / max(maxC, 0.001);
    float vibrance = 1.25; // 25% boost to color richness
    vec3 vibrant = mix(vec3(luma), color, vibrance + (1.0 - sat) * 0.15);

    // 5. Dynamic Exposure scaling derived from Paper White
    float exposure = paperWhite / 200.0;
    vec3 finalOut = vibrant * exposure;

    fragColor = vec4(clamp(finalOut, 0.0, 1.0), rawColor.a);
}
