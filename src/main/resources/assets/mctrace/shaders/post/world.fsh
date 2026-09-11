#version 330

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
};

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.141592653589793;

// Henyey-Greenstein forward scattering phase function for volumetric god rays
float henyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (4.0 * PI * pow(max(1.0 + g2 - 2.0 * g * cosTheta, 0.001), 1.5));
}

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
    // 1. Redstone dust / torch (vivid crimson: very low G and B)
    if (col.r > 0.55 && col.g < 0.16 && col.b < 0.16 && col.g < col.r * 0.25) {
        return vec3(1.0, 0.12, 0.06) * 2.2;
    }
    // 2. Soul fire / soul lantern (eerie teal cyan)
    if (col.b > 0.60 && col.g > 0.60 && col.r < 0.45) {
        return vec3(0.12, 0.88, 0.95) * 2.2;
    }
    // 3. Torch / lantern / campfire / lava (warm amber glow)
    // Covers bright molten magma, fire, torches, and darker lava crust
    // Excludes metallic copper (R/B < 3.0, B >= 0.19) and gold (R/B < 7.5, R/G < 1.30, B >= 0.13)
    if ((col.r > 0.50 && col.g >= 0.18 && col.b < 0.13 && col.r > col.g * 1.15) ||
        (col.r > 0.70 && col.g > 0.30 && col.b < 0.35 && col.r > col.b * 3.2 && (col.r > col.g * 1.25 || (col.r > 0.88 && col.g > 0.75 && col.b < 0.18)))) {
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
    // 6. Glowstone / shroomlight (warm luminescent gold, distinct from metallic gold block)
    if (col.r > 0.75 && col.g > 0.60 && col.b < 0.45 && col.r > col.b * 2.2 && (col.r - col.g) > 0.18) {
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

    float rainLevel = WeatherConfig.x;
    float rainWetness = WeatherConfig.y;

    float enableFog = AtmosphereConfig.x;
    float fogDensity = AtmosphereConfig.y;
    float enableGodRays = AtmosphereConfig.z;
    float godRaysIntensity = AtmosphereConfig.w;

    float heldIntensity = DynamicLightConfig.w;

    float enableMotionBlur = CinematicConfig.x;
    float motionBlurStrength = CinematicConfig.y;
    float enableDof = CinematicConfig.z;
    float pomDepth = CinematicConfig.w;

    bool isSky = (depth >= 9999.0);
    float aoFactor = 1.0;
    float directSunMod = 0.0;
    vec3 pbrSpecular = vec3(0.0);
    vec3 dynamicRadiosity = vec3(0.0);
    float puddleMask = 0.0;

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

        // Parallax Occlusion Mapping (POM) 3D surface relief
        if (pomDepth > 0.005) {
            vec3 dPdx = dFdx(pos);
            vec3 dPdy = dFdy(pos);
            vec2 dUdx = dFdx(texCoord);
            vec2 dUdy = dFdy(texCoord);
            vec3 tangent = normalize(dPdx * dUdy.y - dPdy * dUdx.y);
            vec3 bitangent = cross(normal, tangent);
            mat3 TBN = mat3(tangent, bitangent, normal);
            vec3 tangentView = normalize(transpose(TBN) * viewDir);

            float numLayers = 8.0;
            float layerDepth = 1.0 / numLayers;
            float currentLayerDepth = 0.0;
            vec2 deltaTexCoords = tangentView.xy * pomDepth / (abs(tangentView.z) * numLayers + 0.001);
            vec2 pomCoord = texCoord;
            float currentDepthMapValue = 1.0 - dot(texture(MainSampler, pomCoord).rgb, vec3(0.299, 0.587, 0.114));

            for (int step = 0; step < 8; step++) {
                if (currentLayerDepth >= currentDepthMapValue) break;
                pomCoord -= deltaTexCoords;
                currentDepthMapValue = 1.0 - dot(texture(MainSampler, pomCoord).rgb, vec3(0.299, 0.587, 0.114));
                currentLayerDepth += layerDepth;
            }
            float selfShadow = clamp(1.0 - (currentLayerDepth - currentDepthMapValue) * 2.0, 0.70, 1.0);
            shadow *= selfShadow;
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

            // Gold: vibrant warm yellow-orange (exclude self-emissive lava/fire/torches)
            if (length(getEmissiveRadiance(albedo)) < 0.1 && albedo.r > 0.65 && albedo.g > 0.50 && albedo.b < 0.35 && albedo.r >= albedo.g) {
                metallic = 0.95;
                roughness = 0.20;
                F0 = albedo;
                metallicScale = 0.95;
            }
            // Copper: rich reddish orange (exclude self-emissive lava crust/fire)
            else if (length(getEmissiveRadiance(albedo)) < 0.1 && albedo.r > 0.60 && albedo.g > 0.30 && albedo.g < 0.55 && albedo.b < 0.35 && albedo.r > albedo.g * 1.25) {
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

            // Rain Wetness & Puddle Accumulation on upward facing blocks
            if (rainWetness > 0.01 && normal.y > 0.65) {
                float pNoise = sin(pos.x * 0.8 + 1.2) * cos(pos.z * 0.8 + 0.7) * 0.5 + 0.5;
                puddleMask = smoothstep(0.35, 0.70, pNoise) * rainWetness;
                // Porous darkening
                albedo *= mix(1.0, 0.72, puddleMask * (1.0 - metallicScale));
                // Mirror sheen
                roughness = mix(roughness, 0.02, puddleMask);
                // Rain ripple normals
                float ripple1 = sin(length(fract(pos.xz * 2.5) - 0.5) * 25.0 - time * 6.0);
                float ripple2 = cos(length(fract(pos.xz * 1.8 + 0.3) - 0.5) * 20.0 - time * 5.0);
                vec3 rippleNorm = vec3(ripple1 * 0.04, 0.0, ripple2 * 0.04) * puddleMask;
                normal = normalize(normal + rippleNorm);
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
        vec3 selfEmissive = getEmissiveRadiance(rawColor.rgb);
        bool isEmissive = (length(selfEmissive) > 0.1);

        if (isEmissive) {
            // Fragment itself is an emissive light source (e.g. lava, fire, torch):
            // Do not add sun specular or direct sun boost onto emissive surfaces.
            // Preserves the authentic flowing magma texture and natural contrast of lava blocks.
            directSunMod = 0.0;
            pbrSpecular = vec3(0.0);
            aoFactor = 1.0;
        } else if (enableDynamicLight) {
            // Gather dynamic radiosity from nearby emissive blocks across multiple scales
            // Scale screen-space radius inversely with depth so radiosity has consistent world-space reach (~2 blocks)
            float baseRadius = clamp(2.0 / max(depth, 1.0), 0.006, 0.05);

            // Isotropic sample distribution: correct for screen aspect ratio
            vec2 aspect = vec2(InSize.y / InSize.x, 1.0);

            // Per-pixel rotation using Interleaved Gradient Noise (IGN) to eliminate directional starbursts/petals
            float ign = fract(52.9829189 * fract(0.06711056 * gl_FragCoord.x + 0.00583715 * gl_FragCoord.y));
            float rotAngle = ign * (2.0 * PI);

            // Golden-ratio spiral / Poisson disk sampling with smooth distance falloff
            const float GOLDEN_ANGLE = 2.39996323; // PI * (3.0 - sqrt(5.0))
            const int SAMPLE_COUNT = 16;
            const float SAMPLE_WEIGHT = 1.6 / float(SAMPLE_COUNT);

            for (int i = 0; i < SAMPLE_COUNT; i++) {
                float frac = (float(i) + 0.5) / float(SAMPLE_COUNT);
                float rFrac = sqrt(frac); // Uniform Poisson disc distribution
                float angle = float(i) * GOLDEN_ANGLE + rotAngle;

                vec2 offset = vec2(cos(angle), sin(angle)) * (baseRadius * rFrac) * aspect;
                vec2 sampleUv = texCoord + offset;

                if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
                    continue;
                }

                vec3 colSample = texture(MainSampler, sampleUv).rgb;
                vec3 emSample = getEmissiveRadiance(colSample);
                if (length(emSample) > 0.1) {
                    float dSample = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
                    float depthDiff = abs(depth - dSample);
                    if (depthDiff < 2.0) {
                        // Smooth depth occlusion and smooth distance falloff
                        float depthWeight = smoothstep(2.0, 0.0, depthDiff);
                        float distWeight = smoothstep(1.0, 0.0, rFrac);
                        dynamicRadiosity += emSample * (depthWeight * distWeight) * SAMPLE_WEIGHT;
                    }
                }
            }
        }

        // Hand-Held Dynamic Lighting
        if (heldIntensity > 0.01 && !isEmissive) {
            vec3 heldLightColor = DynamicLightConfig.xyz;
            vec3 handPos = vec3(0.25, -0.35, 0.5);
            vec3 lightVec = pos - handPos;
            float dist = length(lightVec);
            vec3 lightDirHeld = -normalize(lightVec);
            float atten = 1.0 / (1.0 + 0.18 * dist + 0.04 * dist * dist);
            atten *= smoothstep(16.0, 4.0, dist);
            float NdotL_held = max(dot(normal, lightDirHeld), 0.0);
            dynamicRadiosity += heldLightColor * (NdotL_held * atten * heldIntensity * 2.2);
        }
    }

    // 5. Illumination synthesis on 3D geometry
    // Multiply diffuse dynamic light by surface albedo so warm amber light realistically warms terrain
    vec3 shaded = rawColor.rgb * (aoFactor + directSunMod + dynamicRadiosity) + pbrSpecular;

    // 6. Screen-Space Water & Glass Reflections (SSR) + Caustics + Rain Puddles
    if (enableWaterReflections && !isSky) {
        bool isWater = (rawColor.b > rawColor.r + 0.14 && rawColor.b > 0.22 && normal.y > 0.55);
        bool isGlass = (rawColor.a > 0.10 && rawColor.a < 0.95);
        bool isPuddle = (rainWetness > 0.05 && normal.y > 0.65 && puddleMask > 0.25);

        if (isWater || isGlass || isPuddle) {
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
            float F0_val = isWater ? 0.02 : (isGlass ? 0.04 : 0.03);
            float fresnel = F0_val + (1.0 - F0_val) * pow(clamp(1.0 - max(dot(waveNorm, viewDir), 0.0), 0.0, 1.0), 5.0);
            float finalReflWeight = hitWeight;
            if (hitWeight <= 0.0 && reflDir.y > 0.15 && normal.y > 0.4) {
                finalReflWeight = 0.20; // Subtle sky reflection only for upward facing open-air surfaces
            }
            if (isPuddle) {
                finalReflWeight *= puddleMask;
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

    // 6b. Atmospheric Crepuscular God Rays & Volumetric Fog
    if (enableGodRays > 0.5 || enableFog > 0.5) {
        // Sun position projected into screen space
        vec2 sunScreen = vec2(0.5, 0.5) + (sunDir.xy / max(sunDir.z + 1.0, 0.2)) * 0.5;
        vec2 rayStep = (sunScreen - texCoord) / 12.0;
        float godRayAccum = 0.0;
        vec2 curCoord = texCoord;
        for (int i = 0; i < 12; i++) {
            curCoord += rayStep;
            if (curCoord.x >= 0.0 && curCoord.x <= 1.0 && curCoord.y >= 0.0 && curCoord.y <= 1.0) {
                float dSample = texture(MainDepthSampler, curCoord).r;
                if (dSample >= 0.9999 || dSample <= 0.0) {
                    godRayAccum += 1.0;
                }
            }
        }
        godRayAccum /= 12.0;
        float cosTheta = dot(viewDir, sunDir);
        float phase = henyeyGreenstein(cosTheta, 0.72);
        vec3 godRayColor = vec3(1.0, 0.92, 0.78) * (godRayAccum * phase * 16.0 * godRaysIntensity);

        // Valley / ground atmospheric height fog
        float fogDist = length(pos);
        float heightFactor = clamp((80.0 - pos.y) / 60.0, 0.0, 1.0);
        float fogAmount = (1.0 - exp(-fogDist * 0.015 * fogDensity)) * (0.30 + heightFactor * 0.70);
        vec3 fogColor = mix(vec3(0.65, 0.78, 0.95), vec3(1.0, 0.88, 0.70), max(dot(viewDir, sunDir), 0.0) * 0.5);

        if (enableFog > 0.5 && !isSky) {
            shaded = mix(shaded, fogColor, fogAmount);
        }
        if (enableGodRays > 0.5) {
            shaded += godRayColor;
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

    // 10. Cinematic Bokeh Depth of Field & Velocity Motion Blur
    if (enableDof > 0.5 && !isSky) {
        float centerDepth = linearizeDepth(texture(MainDepthSampler, vec2(0.5, 0.5)).r);
        float coc = clamp(abs(depth - centerDepth) / max(depth, 1.0) * 1.8, 0.0, 1.0);
        if (coc > 0.05) {
            vec3 dofAccum = shaded;
            float dofWeight = 1.0;
            vec2 bokehOffsets[12] = vec2[](
                vec2( 1.00,  0.00), vec2( 0.50,  0.86), vec2(-0.50,  0.86),
                vec2(-1.00,  0.00), vec2(-0.50, -0.86), vec2( 0.50, -0.86),
                vec2( 0.50,  0.28), vec2( 0.00,  0.57), vec2(-0.50,  0.28),
                vec2(-0.50, -0.28), vec2( 0.00, -0.57), vec2( 0.50, -0.28)
            );
            vec2 blurRadius = texel * (coc * 6.0);
            for (int b = 0; b < 12; b++) {
                vec2 sampleCoord = texCoord + bokehOffsets[b] * blurRadius;
                vec3 sCol = texture(MainSampler, sampleCoord).rgb;
                float bWeight = 1.0 + dot(sCol, vec3(0.333)) * 0.8;
                dofAccum += sCol * bWeight;
                dofWeight += bWeight;
            }
            shaded = dofAccum / dofWeight;
        }
    }

    if (enableMotionBlur > 0.5) {
        vec2 velDir = (texCoord - vec2(0.5)) * (0.008 * motionBlurStrength);
        vec3 mbAccum = shaded;
        for (int m = 1; m <= 4; m++) {
            vec2 mbUv = texCoord + velDir * float(m);
            mbAccum += texture(MainSampler, mbUv).rgb;
        }
        shaded = mix(shaded, mbAccum / 5.0, 0.45 * motionBlurStrength);
    }

    // 11. SDR Color Vibrancy / Tone Polish
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
