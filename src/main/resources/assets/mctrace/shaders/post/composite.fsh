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

// ACES Filmic Tone Mapping Curve (Narkowicz 2015)
vec3 ACESFilm(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec4 rawAlbedo = texture(MainSampler, texCoord);
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    float depth = linearizeDepth(rawDepth);

    // Dynamic Display Calibration parameters
    float minLum = max(0.0, HdrConfig.x);
    float paperWhite = HdrConfig.y > 0.0 ? HdrConfig.y : 200.0;
    float peakLum = HdrConfig.z > 0.0 ? HdrConfig.z : 1000.0;
    float contrast = HdrConfig.w > 0.0 ? HdrConfig.w : 1.0;
    float isHdr = LightingConfig.x;
    float ssaoMultiplier = LightingConfig.y > 0.0 ? LightingConfig.y : 1.0;

    // Sky / infinite background pass-through
    if (depth >= 9999.0) {
        if (isHdr > 0.5) {
            // In True HDR, linearize sky albedo to physical space
            fragColor = vec4(pow(max(rawAlbedo.rgb, vec3(0.0)), vec3(2.2)), rawAlbedo.a);
        } else {
            fragColor = rawAlbedo;
        }
        return;
    }

    vec2 texel = 1.0 / InSize;

    // Surface normal reconstruction from screen-space depth gradients
    vec3 pos = getPosition(texCoord);
    vec3 dx = dFdx(pos);
    vec3 dy = dFdy(pos);
    vec3 normal = normalize(cross(dx, dy));
    if (normal.z < 0.0) {
        normal = -normal;
    }

    // 1. Convert incoming rasterized sRGB textures to true physical linear space
    vec3 linearAlbedo = pow(max(rawAlbedo.rgb, vec3(0.0)), vec3(2.2));

    // 2. Directional Sun lighting vector
    vec3 sunDir = normalize(vec3(0.45, 0.82, 0.35));
    float NdotL = max(dot(normal, sunDir), 0.0);

    // 3. Multi-tap Screen Space Ambient Occlusion (SSAO)
    float ao = 0.0;
    float radius = 4.5 * texel.x;
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
        if (diff > 0.02 && diff < 1.2) {
            ao += 1.0 - smoothstep(0.02, 1.2, diff);
        }
    }

    float aoFactor = clamp(1.0 - (ao / 8.0) * (1.8 * ssaoMultiplier), 0.02, 1.0);

    // 4. Screen Space Contact Shadows
    float shadow = 1.0;
    vec2 shadowStep = sunDir.xy * texel * 3.5;
    for (int s = 1; s <= 5; s++) {
        vec2 sampleUv = texCoord + shadowStep * float(s);
        float stepDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
        float depthDiff = depth - stepDepth;
        if (depthDiff > 0.02 && depthDiff < 0.8) {
            shadow = 0.12; // Deep, crisp contact shadow!
            break;
        }
    }

    // 5. Specular highlight (PBR surface reflection)
    vec3 viewDir = normalize(-pos);
    vec3 halfDir = normalize(sunDir + viewDir);
    float NdotH = max(dot(normal, halfDir), 0.0);
    float specular = pow(NdotH, 32.0) * 0.45 * shadow * NdotL;

    // 6. Ambient skylight vs direct sunlight
    vec3 ambientLight = vec3(0.05, 0.07, 0.10) * aoFactor;
    vec3 sunLightColor = vec3(1.45, 1.35, 1.15);
    vec3 directLight = sunLightColor * (NdotL * shadow);

    vec3 diffuse = linearAlbedo * (ambientLight + directLight);
    vec3 combined = diffuse + vec3(specular);

    // 7. Middle gray contrast curve (centered on 0.18 linear reflectance)
    vec3 contrasted = combined;
    if (abs(contrast - 1.0) > 0.01) {
        contrasted = pow(max(combined / 0.18, vec3(0.0)), vec3(contrast)) * 0.18;
    }

    // 8. Filmic tone mapping
    vec3 tonemapped = ACESFilm(contrasted);

    // 9. Black level floor calibration
    if (minLum > 0.0) {
        tonemapped = max(tonemapped, vec3(minLum / 80.0));
    }

    // 10. Output:
    // If True HDR scRGB is active, output linear light for Windows DWM.
    // If SDR is active, encode with sRGB gamma 1/2.2.
    vec3 finalOut;
    if (isHdr > 0.5) {
        finalOut = tonemapped + vec3(specular * 0.35);
    } else {
        finalOut = pow(max(tonemapped, vec3(0.0)), vec3(1.0 / 2.2));
    }

    fragColor = vec4(clamp(finalOut, 0.0, 1.0), rawAlbedo.a);
}
