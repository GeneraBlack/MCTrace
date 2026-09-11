#version 330

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

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

// Reverse-Z depth linearizer (near = 1.0, far/sky = 0.0)
float linearizeDepth(float d) {
    if (d <= 0.00002) {
        return 10000.0; // Sky / infinite background
    }
    float zNear = 0.1;
    return zNear / max(d, 0.00002);
}

vec3 getPosition(vec2 uv) {
    float d = texture(MainDepthSampler, uv).r;
    float z = linearizeDepth(d);
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc * z * 0.75, z);
}

void main() {
    vec4 rawColor = texture(MainSampler, texCoord);
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    float depth = linearizeDepth(rawDepth);

    // Sky / infinite background pass-through without modification
    if (depth >= 9999.0) {
        fragColor = rawColor;
        return;
    }

    vec2 texel = 1.0 / InSize;

    // Dynamic Display Calibration parameters
    float minLum = max(0.0, HdrConfig.x);
    float paperWhite = HdrConfig.y > 0.0 ? HdrConfig.y : 200.0;
    float peakLum = HdrConfig.z > 0.0 ? HdrConfig.z : 1000.0;
    float contrast = HdrConfig.w > 0.0 ? HdrConfig.w : 1.0;
    float isHdr = LightingConfig.x;
    float ssaoStrength = LightingConfig.y > 0.0 ? LightingConfig.y : 1.0;

    // Surface normal reconstruction from screen-space depth gradients
    vec3 pos = getPosition(texCoord);
    vec3 dx = dFdx(pos);
    vec3 dy = dFdy(pos);
    vec3 normal = normalize(cross(dx, dy));
    if (normal.z < 0.0) {
        normal = -normal;
    }

    // 1. Multi-tap Screen Space Ambient Occlusion (SSAO)
    float ao = 0.0;
    float radius = 4.0 * texel.x;
    vec2 samples[8] = vec2[](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0),
        vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7,  0.7), vec2(-0.7,  0.7),
        vec2( 0.7, -0.7), vec2(-0.7, -0.7)
    );

    for (int i = 0; i < 8; i++) {
        vec2 sampleUv = texCoord + samples[i] * radius * 8.0;
        float sampleDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
        float diff = depth - sampleDepth;
        if (diff > 0.02 && diff < 1.0) {
            ao += 1.0 - smoothstep(0.02, 1.0, diff);
        }
    }

    // SSAO corner and crevice darkening (dims up to 45% in tight crevices)
    float aoOcclusion = (ao / 8.0) * (0.45 * ssaoStrength);
    float aoFactor = clamp(1.0 - aoOcclusion, 0.55, 1.0);

    // 2. Directional Sun lighting & Screen Space Contact Shadows
    vec3 sunDir = normalize(vec3(0.45, 0.82, 0.35));
    float NdotL = max(dot(normal, sunDir), 0.0);

    float shadow = 1.0;
    vec2 shadowStep = sunDir.xy * texel * 3.5;
    for (int s = 1; s <= 5; s++) {
        vec2 sampleUv = texCoord + shadowStep * float(s);
        float stepDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
        float depthDiff = depth - stepDepth;
        if (depthDiff > 0.02 && depthDiff < 0.8) {
            shadow = 0.60; // Soft contact shadow
            break;
        }
    }

    // 3. Specular highlight (PBR surface reflection on sunlit surfaces)
    vec3 viewDir = normalize(-pos);
    vec3 halfDir = normalize(sunDir + viewDir);
    float NdotH = max(dot(normal, halfDir), 0.0);
    float specular = pow(NdotH, 32.0) * 0.35 * shadow * NdotL;

    // 4. Illumination synthesis:
    // rawColor already contains Minecraft's lit world (sunlight, torches in caves, block light).
    // Modulate by SSAO contact darkening, add warm directional sunlight boost in sunlit areas,
    // and add specular glints.
    float directSunMod = NdotL * shadow * 0.20;
    vec3 shaded = rawColor.rgb * (aoFactor + directSunMod) + vec3(specular);

    // 5. Dynamic Display Calibration: Contrast & Black Level
    // If True HDR is active, apply a subtle contrast compensation for Windows DWM
    float effectiveContrast = contrast;
    if (isHdr > 0.5) {
        effectiveContrast *= 1.15;
    }

    vec3 contrasted = shaded;
    if (abs(effectiveContrast - 1.0) > 0.01) {
        contrasted = pow(max(shaded, vec3(0.0)), vec3(effectiveContrast));
    }

    // Black level floor calibration (prevents pitch-black crushing in caves)
    if (minLum > 0.0) {
        contrasted = max(contrasted, vec3(minLum * 0.2));
    }

    // In True HDR, expand specular highlights into HDR headroom
    if (isHdr > 0.5 && specular > 0.01) {
        float hdrBoost = (peakLum / paperWhite) * 0.20;
        contrasted += vec3(specular * hdrBoost);
    }

    fragColor = vec4(clamp(contrasted, 0.0, 1.0), rawColor.a);
}
