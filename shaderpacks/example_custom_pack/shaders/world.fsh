#version 330

// =============================================================================
// MCTrace Example Custom Shader Pack - Starter world.fsh
// Place in .minecraft/shaderpacks/example_custom_pack/shaders/world.fsh
// Press F4 in-game to hot-reload edits instantly!
// =============================================================================

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform MCTraceParams {
    vec4 HdrConfig;          // x: sceneBrightness, y: paperWhite, z: peakLum, w: contrast
    vec4 LightingConfig;     // x: isHdrActive, y: ssaoMultiplier, z: minLum, w: wideGamutStrength
    vec4 MaterialConfig;     // x: enablePbr, y: enableWaterReflections, z: enableDynamicColoredLight, w: time
    vec4 WeatherConfig;      // x: rainLevel, y: wetness, z: thunderLevel, w: skyAngle
    vec4 AtmosphereConfig;   // x: enableFog, y: fogDensity, z: enableGodRays, w: godRaysIntensity
    vec4 DynamicLightConfig; // x: heldLightR, y: heldLightG, z: heldLightB, w: heldLightIntensity
    vec4 CinematicConfig;    // x: enableMotionBlur, y: motionBlurStrength, z: enableDof, w: pomDepth
    vec4 AdvancedConfig;     // x: enableFoliageSss, y: foliageSssStrength, z: enableRtShadows, w: enableLabPbrTextures
};

in vec2 texCoord;
out vec4 fragColor;

float linearizeDepth(float d) {
    if (d >= 0.9999 || d <= 0.0) return 10000.0;
    float zNear = 0.1;
    float zFar = 1000.0;
    return (zNear * zFar) / (zFar - d * (zFar - zNear));
}

void main() {
    vec4 rawColor = texture(MainSampler, texCoord);
    float depth = texture(MainDepthSampler, texCoord).r;
    float linearZ = linearizeDepth(depth);

    // Apply custom cinematic warmth and atmospheric depth tinting
    vec3 color = rawColor.rgb;

    if (linearZ < 9000.0) {
        // Subtle filmic contrast curve
        color = pow(color, vec3(1.05));

        // Warm sunlight highlight tint
        color *= vec3(1.02, 1.00, 0.97);

        // Distance atmospheric depth haze
        float depthHaze = clamp(linearZ / 128.0, 0.0, 0.4) * AtmosphereConfig.y;
        vec3 hazeColor = vec3(0.55, 0.68, 0.85);
        color = mix(color, hazeColor, depthHaze);
    }

    fragColor = vec4(color, rawColor.a);
}
