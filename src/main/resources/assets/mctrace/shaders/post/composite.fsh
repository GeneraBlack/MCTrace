#version 330

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

// Linearize reverse-Z depth buffer (near = 1.0, far/sky = 0.0)
float linearizeDepth(float d) {
    if (d <= 0.00002) {
        return 10000.0; // Sky / background clear
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
    vec4 albedo = texture(MainSampler, texCoord);
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    float depth = linearizeDepth(rawDepth);

    // If sampling sky / infinite background, pass through with slight atmospheric tone
    if (depth >= 9999.0) {
        fragColor = albedo;
        return;
    }

    vec2 InSize = vec2(textureSize(MainSampler, 0));
    vec2 texel = 1.0 / InSize;

    // Surface normal reconstruction from screen-space depth gradients
    vec3 pos = getPosition(texCoord);
    vec3 dx = dFdx(pos);
    vec3 dy = dFdy(pos);
    vec3 normal = normalize(cross(dx, dy));
    if (normal.z < 0.0) {
        normal = -normal;
    }

    // Directional Sun lighting & Ray Query shadow simulation
    vec3 sunDir = normalize(vec3(0.4, 0.85, 0.35));
    float NdotL = max(dot(normal, sunDir), 0.0);

    // Multi-tap Screen Space Ambient Occlusion (SSAO) with pronounced corner darkening
    float ao = 0.0;
    float radius = 4.5 * texel.x;
    
    vec2 samples[8] = vec2[](
        vec2( 1.0,  0.0), vec2(-1.0,  0.0),
        vec2( 0.0,  1.0), vec2( 0.0, -1.0),
        vec2( 0.7,  0.7), vec2(-0.7,  0.7),
        vec2( 0.7, -0.7), vec2(-0.7, -0.7)
    );

    for (int i = 0; i < 8; i++) {
        vec2 sampleUv = texCoord + samples[i] * radius * 10.0;
        float sampleDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
        float diff = depth - sampleDepth;
        if (diff > 0.02 && diff < 1.4) {
            ao += 1.0 - smoothstep(0.02, 1.4, diff);
        }
    }
    float aoFactor = clamp(1.0 - (ao / 8.0) * 2.2, 0.15, 1.0);

    // Screen Space Contact Shadow
    float shadow = 1.0;
    vec2 shadowStep = sunDir.xy * texel * 4.0;
    for (int s = 1; s <= 5; s++) {
        vec2 sampleUv = texCoord + shadowStep * float(s);
        float stepDepth = linearizeDepth(texture(MainDepthSampler, sampleUv).r);
        float depthDiff = depth - stepDepth;
        if (depthDiff > 0.03 && depthDiff < 0.9) {
            shadow = 0.35;
            break;
        }
    }

    // Specular highlight (PBR roughness approximation)
    vec3 viewDir = normalize(-pos);
    vec3 halfDir = normalize(sunDir + viewDir);
    float NdotH = max(dot(normal, halfDir), 0.0);
    float specular = pow(NdotH, 32.0) * 0.45 * shadow;

    // Ambient + Direct Light synthesis
    vec3 ambientColor = vec3(0.35, 0.42, 0.58) * 1.2;
    vec3 sunLightColor = vec3(1.50, 1.38, 1.12);

    vec3 diffuse = albedo.rgb * (ambientColor * aoFactor + sunLightColor * (NdotL * 0.9 + 0.1) * shadow);
    vec3 combined = diffuse + vec3(specular);

    // Filmic tone mapping (Reinhard-Jodie)
    float luma = dot(combined, vec3(0.2126, 0.7152, 0.0722));
    vec3 tonemapped = combined / (1.0 + combined);
    vec3 finalRgb = mix(combined / (1.0 + luma), tonemapped, tonemapped);

    // Linear to sRGB gamma correction
    finalRgb = pow(clamp(finalRgb, 0.0, 1.0), vec3(1.0 / 2.2));

    fragColor = vec4(finalRgb, albedo.a);
}
