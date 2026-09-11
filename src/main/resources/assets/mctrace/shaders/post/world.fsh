#version 330

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

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

const float PI = 3.141592653589793;

// Standard OpenGL/Vulkan depth linearizer (near = 0.1, far/sky = 1000.0)
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

// GGX Microfacet Distribution
float D_GGX(float NdotH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH2 = NdotH * NdotH;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    return a2 / (PI * denom * denom + 1e-7);
}

// Smith Geometry Shadowing
float G_Smith(float NdotV, float NdotL, float roughness) {
    float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
    float gV = NdotV / (NdotV * (1.0 - k) + k + 1e-7);
    float gL = NdotL / (NdotL * (1.0 - k) + k + 1e-7);
    return gV * gL;
}

// Schlick Fresnel
vec3 F_Schlick(float cosTheta, vec3 F0) {
    return F0 + (vec3(1.0) - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

// Emissive light source color classifier
vec3 getEmissiveRadiance(vec3 col) {
    // 1. Redstone dust / torch (vivid crimson)
    if (col.r > 0.65 && col.g < 0.32 && col.b < 0.32) {
        return vec3(1.0, 0.12, 0.06) * 2.2;
    }
    // 2. Soul fire / soul lantern (eerie teal cyan)
    if (col.b > 0.60 && col.g > 0.60 && col.r < 0.45) {
        return vec3(0.12, 0.88, 0.95) * 2.2;
    }
    // 3. Torch / lantern / campfire / lava (warm amber glow)
    if (col.r > 0.70 && col.g > 0.40 && col.g < 0.90 && col.b < 0.40) {
        return vec3(1.0, 0.65, 0.22) * 2.0;
    }
    // 4. Sculk / catalyst / sensor (luminescent sculk cyan)
    if (col.b > 0.50 && col.g > 0.50 && col.r < 0.35 && (col.g + col.b) > 1.1) {
        return vec3(0.08, 0.94, 0.88) * 2.4;
    }
    // 5. Amethyst cluster (violet glow)
    if (col.r > 0.45 && col.b > 0.60 && col.g < 0.55) {
        return vec3(0.75, 0.35, 1.0) * 1.9;
    }
    // 6. Glowstone / shroomlight (warm luminescent gold)
    if (col.r > 0.72 && col.g > 0.60 && col.b < 0.45 && col.r > col.b * 1.8) {
        return vec3(1.0, 0.80, 0.30) * 1.8;
    }
    return vec3(0.0);
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
    float wideGamutStrength = LightingConfig.w;

    bool enablePbr = MaterialConfig.x > 0.5;
    bool enableWaterReflections = MaterialConfig.y > 0.5;
    bool enableDynamicLight = MaterialConfig.z > 0.5;
    float time = MaterialConfig.w;

    bool isSky = (depth >= 9999.0);
    float aoFactor = 1.0;
    float directSunMod = 0.0;
    vec3 pbrSpecular = vec3(0.0);
    vec3 dynamicRadiosity = vec3(0.0);

    vec3 pos = vec3(0.0);
    vec3 normal = vec3(0.0, 1.0, 0.0);
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 sunDir = normalize(vec3(0.45, 0.82, 0.35));
    float shadow = 1.0;

    if (!isSky) {
        // Surface normal reconstruction from screen-space depth gradients
        pos = getPosition(texCoord);
        vec3 dx = dFdx(pos);
        vec3 dy = dFdy(pos);
        normal = normalize(cross(dx, dy));
        if (normal.z < 0.0) {
            normal = -normal;
        }
        viewDir = normalize(-pos);

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
        float aoOcclusion = (ao / 8.0) * (0.25 * ssaoStrength);
        aoFactor = clamp(1.0 - aoOcclusion, 0.75, 1.0);

        // 2. Directional Sun lighting & Screen Space Contact Shadows
        float NdotL = max(dot(normal, sunDir), 0.0);
        vec2 shadowStep = sunDir.xy * texel * 3.5;
        for (int s = 1; s <= 5; s++) {
            vec2 sampleUv = texCoord + shadowStep * float(s);
            float stepDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
            float depthDiff = depth - stepDepth;
            if (depthDiff > 0.02 && depthDiff < 0.8) {
                shadow = 0.75;
                break;
            }
        }
        directSunMod = NdotL * shadow * 0.20;

        // 3. PBR Materials & LabPBR Specular / Roughness Support
        if (enablePbr) {
            vec3 halfDir = normalize(sunDir + viewDir);
            float NdotH = max(dot(normal, halfDir), 0.0);
            float NdotV = max(dot(normal, viewDir), 0.001);
            float VdotH = max(dot(viewDir, halfDir), 0.0);

            // Procedural Material Classification
            float roughness = 0.85; // Default matte (wood, stone, dirt)
            float metallic = 0.0;
            vec3 F0 = vec3(0.04);
            vec3 albedo = rawColor.rgb;
            float albedoMax = max(albedo.r, max(albedo.g, albedo.b));
            float albedoMin = min(albedo.r, min(albedo.g, albedo.b));
            float albedoSat = (albedoMax - albedoMin) / max(albedoMax, 0.001);
            float albedoLuma = dot(albedo, vec3(0.2126, 0.7152, 0.0722));

            float metallicScale = 0.0;

            // Gold: vibrant warm yellow-orange
            if (albedo.r > 0.65 && albedo.g > 0.50 && albedo.b < 0.35 && albedo.r >= albedo.g) {
                metallic = 0.95;
                roughness = 0.20;
                F0 = albedo;
                metallicScale = 0.95;
            }
            // Copper: rich reddish orange
            else if (albedo.r > 0.60 && albedo.g > 0.30 && albedo.g < 0.55 && albedo.b < 0.35 && albedo.r > albedo.g * 1.25) {
                metallic = 0.90;
                roughness = 0.25;
                F0 = vec3(0.95, 0.64, 0.54);
                metallicScale = 0.90;
            }
            // Iron: bright neutral grey with low saturation
            else if (albedoLuma > 0.55 && albedoSat < 0.08 && abs(albedo.r - albedo.b) < 0.06) {
                metallic = 0.85;
                roughness = 0.28;
                F0 = vec3(0.78, 0.78, 0.82);
                metallicScale = 0.85;
            }
            // Polished Deepslate & Blackstone: dark sleek neutral minerals
            else if (albedoLuma >= 0.08 && albedoLuma <= 0.24 && albedoSat < 0.08) {
                metallic = 0.0;
                roughness = 0.16; // Polished gleam
                F0 = vec3(0.06);
            }
            // Smooth Stone & Quartz: bright sleek minerals
            else if (albedoLuma > 0.75 && albedoSat < 0.06) {
                metallic = 0.0;
                roughness = 0.20;
                F0 = vec3(0.05);
            }
            // Diamond / Emerald / Amethyst Gems
            else if (albedoSat > 0.40 && (albedo.b > 0.60 || albedo.g > 0.60)) {
                metallic = 0.05;
                roughness = 0.10;
                F0 = vec3(0.08);
            }

            // Cook-Torrance Specular Model
            float NDF = D_GGX(NdotH, roughness);
            float G = G_Smith(NdotV, NdotL, roughness);
            vec3 F = F_Schlick(VdotH, F0);

            vec3 specNumerator = NDF * G * F;
            float specDenominator = 4.0 * NdotV * NdotL + 0.001;
            pbrSpecular = (specNumerator / specDenominator) * NdotL * shadow * 0.45;

            // Diffuse energy conservation for authentic metallic luster
            directSunMod *= (1.0 - metallicScale * 0.75);
        } else {
            // Fallback uniform specular
            vec3 halfDir = normalize(sunDir + viewDir);
            float NdotH = max(dot(normal, halfDir), 0.0);
            pbrSpecular = vec3(pow(NdotH, 32.0) * 0.35 * shadow * NdotL);
        }

        // 4. Coloured Dynamic Block Lighting & Emissive Radiosity
        if (enableDynamicLight) {
            vec3 selfEmissive = getEmissiveRadiance(rawColor.rgb);
            if (length(selfEmissive) > 0.1) {
                // Fragment itself is an emissive light source: enhance radiance
                directSunMod += 0.35;
                pbrSpecular += selfEmissive * 0.45;
            } else {
                // Gather dynamic radiosity from nearby emissive blocks across multiple scales
                // Scale screen-space radius inversely with depth so radiosity has consistent world-space reach (~2 blocks)
                float baseRadius = clamp(2.0 / max(depth, 1.0), 0.006, 0.05);

                vec2 radDirs[6] = vec2[](
                    vec2( 1.000,  0.000),
                    vec2( 0.500,  0.866),
                    vec2(-0.500,  0.866),
                    vec2(-1.000,  0.000),
                    vec2(-0.500, -0.866),
                    vec2( 0.500, -0.866)
                );

                for (int r = 0; r < 6; r++) {
                    // Inner ring: close glow
                    vec2 uvInner = texCoord + radDirs[r] * (baseRadius * 0.45);
                    vec3 colInner = texture(MainSampler, uvInner).rgb;
                    vec3 emInner = getEmissiveRadiance(colInner);
                    if (length(emInner) > 0.1) {
                        float dInner = linearizeDepth(texture(MainDepthSampler, uvInner).r);
                        float dist = abs(depth - dInner);
                        if (dist < 1.5) {
                            dynamicRadiosity += emInner * (1.0 / (1.0 + dist * 2.0)) * 0.16;
                        }
                    }

                    // Outer ring: soft diffuse radiosity
                    vec2 uvOuter = texCoord + radDirs[(r + 1) % 6] * baseRadius;
                    vec3 colOuter = texture(MainSampler, uvOuter).rgb;
                    vec3 emOuter = getEmissiveRadiance(colOuter);
                    if (length(emOuter) > 0.1) {
                        float dOuter = linearizeDepth(texture(MainDepthSampler, uvOuter).r);
                        float dist = abs(depth - dOuter);
                        if (dist < 2.0) {
                            dynamicRadiosity += emOuter * (1.0 / (1.0 + dist * 1.8)) * 0.09;
                        }
                    }
                }
            }
        }
    }

    // 5. Illumination synthesis on 3D geometry
    vec3 shaded = rawColor.rgb * (aoFactor + directSunMod) + pbrSpecular + dynamicRadiosity;

    // 6. Screen-Space Water & Glass Reflections (SSR) + Caustics
    if (enableWaterReflections && !isSky) {
        bool isWater = (rawColor.b > rawColor.r + 0.14 && rawColor.b > 0.22 && normal.y > 0.55);
        bool isGlass = (rawColor.a > 0.10 && rawColor.a < 0.95);

        if (isWater || isGlass) {
            // Animated wave normals for water surface
            vec3 waveNorm = normal;
            if (isWater) {
                float wave1 = sin(pos.x * 3.0 + time * 2.4) * cos(pos.z * 3.0 + time * 1.8);
                float wave2 = cos(pos.x * 4.5 - time * 2.0) * sin(pos.z * 4.5 + time * 2.2);
                waveNorm = normalize(normal + vec3(wave1, 0.0, wave2) * 0.05);
            }

            // Screen-space ray marching along reflection vector
            vec3 reflDir = reflect(-viewDir, waveNorm);
            vec3 reflColor = vec3(0.45, 0.65, 0.95) * 0.6; // Sky fallback
            float hitWeight = 0.0;

            vec3 marchPos = pos + reflDir * 0.2;
            vec3 marchStep = reflDir * 0.35;
            for (int step = 0; step < 16; step++) {
                marchPos += marchStep;
                vec2 sampleUv = (marchPos.xy / (marchPos.z * 0.75)) * 0.5 + 0.5;
                if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
                    break;
                }
                float sampledDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
                float depthDiff = marchPos.z - sampledDepth;
                if (depthDiff > 0.02 && depthDiff < 1.0) {
                    float edgeFade = smoothstep(0.0, 0.08, min(sampleUv.x, 1.0 - sampleUv.x)) *
                                     smoothstep(0.0, 0.08, min(sampleUv.y, 1.0 - sampleUv.y));
                    reflColor = texture(MainSampler, sampleUv).rgb;
                    hitWeight = edgeFade * 0.75;
                    break;
                }
            }

            // Fresnel view-angle reflections: only fall back to sky if looking upwards under open sky
            float F0_val = isWater ? 0.02 : 0.04;
            float fresnel = F0_val + (1.0 - F0_val) * pow(clamp(1.0 - max(dot(waveNorm, viewDir), 0.0), 0.0, 1.0), 5.0);
            float finalReflWeight = hitWeight;
            if (hitWeight <= 0.0 && reflDir.y > 0.15 && normal.y > 0.4) {
                finalReflWeight = 0.20; // Subtle sky reflection only for upward facing open-air surfaces
            }
            shaded = mix(shaded, reflColor, fresnel * finalReflWeight);

            // Animated underwater light caustics
            if (isWater) {
                vec2 cUv1 = pos.xz * 1.5 + vec2(time * 0.5, time * 0.3);
                vec2 cUv2 = pos.xz * 2.2 - vec2(time * 0.3, time * 0.6);
                float c1 = sin(cUv1.x + cos(cUv1.y)) * 0.5 + 0.5;
                float c2 = sin(cUv2.y + cos(cUv2.x)) * 0.5 + 0.5;
                float caustics = pow(c1 * c2, 2.2) * 1.8;
                vec3 causticLight = vec3(0.18, 0.85, 1.0) * caustics * shadow * max(dot(normal, sunDir), 0.0);
                shaded += causticLight * 0.35;
            }
        }
    }

    // 7. General Scene Brightness Offset
    shaded *= sceneBrightness;

    // 8. Filmic Contrast with Shadow Toe Flare / Preservation:
    // Below 0.35 luma, smoothly taper the contrast curve towards linear so deep
    // caves and dark blocks are never crushed into pitch black!
    float luma = dot(shaded, vec3(0.2126, 0.7152, 0.0722));
    float toeWeight = smoothstep(0.01, 0.35, luma);
    if (abs(contrast - 1.0) > 0.01) {
        float pivot = (isHdr > 0.5) ? 0.18 : 0.45;
        vec3 curved = pow(max(shaded / pivot, vec3(0.0)), vec3(contrast)) * pivot;
        shaded = mix(shaded, curved, toeWeight);
    }

    // 9. Black level floor calibration
    if (minLum > 0.0) {
        float floorVal = minLum * 0.1;
        shaded = max(shaded, vec3(floorVal));
    }

    // 10. SDR Color Vibrancy / Tone Polish
    // In HDR mode, wide gamut expansion is performed in the 16-bit float composite pass.
    // In SDR mode, apply subtle saturation polish without clipping highlights.
    float cLuma = dot(shaded, vec3(0.2126, 0.7152, 0.0722));
    float cMax = max(shaded.r, max(shaded.g, shaded.b));
    float cMin = min(shaded.r, min(shaded.g, shaded.b));
    float cSat = (cMax - cMin) / max(cMax, 0.001);

    float vibrance = (isHdr > 0.5) ? 1.05 : 1.15;
    shaded = mix(vec3(cLuma), shaded, vibrance + (1.0 - cSat) * 0.05);

    fragColor = vec4(clamp(shaded, 0.0, 1.0), rawColor.a);
}
