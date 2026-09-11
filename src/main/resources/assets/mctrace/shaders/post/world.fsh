#version 330

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

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

// Standard OpenGL/Vulkan depth linearizer (near = 0.0, far/sky = 1.0)
float linearizeDepth(float d) {
    if (d >= 0.9999 || d <= 0.0) {
        return 10000.0; // Sky / infinite background / cleared
    }
    float zNear = 0.1;
    float zFar = 1000.0;
    return (zNear * zFar) / (zFar - d * (zFar - zNear));
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

    vec2 texel = 1.0 / InSize;
    float sceneBrightness = HdrConfig.x > 0.0 ? HdrConfig.x : 1.15;
    float contrast = HdrConfig.w > 0.0 ? HdrConfig.w : 1.0;
    float isHdr = LightingConfig.x;
    float ssaoStrength = LightingConfig.y > 0.0 ? LightingConfig.y : 1.0;
    float minLum = LightingConfig.z;

    bool isSky = (depth >= 9999.0);
    float aoFactor = 1.0;
    float directSunMod = 0.0;
    float specular = 0.0;

    if (!isSky) {
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

        // Gentle SSAO: max 25% occlusion instead of 45% to preserve shadow detail
        float aoOcclusion = (ao / 8.0) * (0.25 * ssaoStrength);
        aoFactor = clamp(1.0 - aoOcclusion, 0.75, 1.0);

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
                shadow = 0.75; // Soft gentle contact shadow
                break;
            }
        }

        // 3. Specular highlight (PBR surface reflection on sunlit surfaces)
        vec3 viewDir = normalize(-pos);
        vec3 halfDir = normalize(sunDir + viewDir);
        float NdotH = max(dot(normal, halfDir), 0.0);
        specular = pow(NdotH, 32.0) * 0.35 * shadow * NdotL;

        directSunMod = NdotL * shadow * 0.20;
    }

    // 4. Illumination synthesis on 3D geometry
    vec3 shaded = rawColor.rgb * (aoFactor + directSunMod) + vec3(specular);

    // 5. Apply General Scene Brightness Offset (keeps overworld vibrant & well-lit)
    shaded *= sceneBrightness;

    // 6. Filmic Contrast with Shadow Toe Flare / Preservation:
    // Below 0.35 luma, smoothly taper the contrast curve towards linear so deep
    // caves and dark blocks are never crushed into pitch black!
    float luma = dot(shaded, vec3(0.2126, 0.7152, 0.0722));
    float toeWeight = smoothstep(0.01, 0.35, luma);
    if (abs(contrast - 1.0) > 0.01) {
        float pivot = (isHdr > 0.5) ? 0.18 : 0.45;
        vec3 curved = pow(max(shaded / pivot, vec3(0.0)), vec3(contrast)) * pivot;
        shaded = mix(shaded, curved, toeWeight);
    }

    // 7. Black level floor calibration
    if (minLum > 0.0) {
        float floorVal = minLum * 0.1;
        shaded = max(shaded, vec3(floorVal));
    }

    // 8. Color Vibrancy (enhances foliage, sky, and terrain without a gray veil)
    float cLuma = dot(shaded, vec3(0.2126, 0.7152, 0.0722));
    float cMax = max(shaded.r, max(shaded.g, shaded.b));
    float cMin = min(shaded.r, min(shaded.g, shaded.b));
    float cSat = (cMax - cMin) / max(cMax, 0.001);
    float vibrance = 1.15; // +15% boost to rich colors
    shaded = mix(vec3(cLuma), shaded, vibrance + (1.0 - cSat) * 0.10);

    fragColor = vec4(clamp(shaded, 0.0, 1.0), rawColor.a);
}
