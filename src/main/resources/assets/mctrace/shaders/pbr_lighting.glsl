#ifndef MCT_PBR_LIGHTING_GLSL
#define MCT_PBR_LIGHTING_GLSL

const float PI = 3.141592653589793;

// Normal Distribution Function (Trowbridge-Reitz GGX)
float D_GGX(float NdotH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH2 = NdotH * NdotH;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    return a2 / (PI * denom * denom + 1e-7);
}

// Geometric Shadowing (Smith GGX)
float G_SchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r * r) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k + 1e-7);
}

float G_Smith(float NdotV, float NdotL, float roughness) {
    float ggx2 = G_SchlickGGX(NdotV, roughness);
    float ggx1 = G_SchlickGGX(NdotL, roughness);
    return ggx1 * ggx2;
}

// Fresnel Schlick
vec3 F_Schlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

/**
 * Evaluates the full Cook-Torrance specular + Lambertian diffuse PBR reflection.
 *
 * @param N Surface normal (normalized)
 * @param V View direction pointing towards camera (normalized)
 * @param L Light direction pointing towards light source (normalized)
 * @param albedo Base surface color
 * @param roughness Surface roughness [0.04, 1.0]
 * @param metallic Surface metalness [0.0, 1.0]
 * @param lightRadiance Incident light color and intensity
 */
vec3 evaluatePBR(
    vec3 N,
    vec3 V,
    vec3 L,
    vec3 albedo,
    float roughness,
    float metallic,
    vec3 lightRadiance
) {
    vec3 H = normalize(V + L);

    float NdotV = max(dot(N, V), 0.001);
    float NdotL = max(dot(N, L), 0.0);
    float NdotH = max(dot(N, H), 0.0);
    float VdotH = max(dot(V, H), 0.0);

    // Dielectrics have base reflectance ~0.04, conductors use albedo
    vec3 F0 = mix(vec3(0.04), albedo, metallic);

    // Specular D, G, F terms
    float NDF = D_GGX(NdotH, roughness);
    float G   = G_Smith(NdotV, NdotL, roughness);
    vec3  F   = F_Schlick(VdotH, F0);

    vec3 numerator = NDF * G * F;
    float denominator = 4.0 * NdotV * NdotL + 0.0001;
    vec3 specular = numerator / denominator;

    // Energy conservation: diffuse light is light that wasn't reflected as specular
    vec3 kS = F;
    vec3 kD = (vec3(1.0) - kS) * (1.0 - metallic);

    vec3 diffuse = kD * albedo / PI;

    return (diffuse + specular) * lightRadiance * NdotL;
}

#endif // MCT_PBR_LIGHTING_GLSL
