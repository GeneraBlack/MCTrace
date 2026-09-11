package net.mctrace.vulkan.pbr;

/**
 * Represents a physically-based material definition adhering to the LabPBR standard:
 * - Albedo / Base Color (RGBA)
 * - Normal Map (RGB = Tangent-space Normal, A = Height)
 * - Specular Map (R = Smoothness, G = Metalness, B = Porosity, A = Emission)
 */
public class PbrMaterial {

    public static final PbrMaterial DEFAULT = new PbrMaterial(
            "default",
            0,
            -1,
            -1,
            0.8f,  // Default roughness
            0.0f,  // Default metallic (dielectric)
            0.0f   // Default emission
    );

    private final String name;
    private final int albedoIndex;
    private final int normalIndex;
    private final int specularIndex;
    private final float defaultRoughness;
    private final float defaultMetallic;
    private final float defaultEmission;

    public PbrMaterial(
            String name,
            int albedoIndex,
            int normalIndex,
            int specularIndex,
            float defaultRoughness,
            float defaultMetallic,
            float defaultEmission
    ) {
        this.name = name;
        this.albedoIndex = albedoIndex;
        this.normalIndex = normalIndex;
        this.specularIndex = specularIndex;
        this.defaultRoughness = defaultRoughness;
        this.defaultMetallic = defaultMetallic;
        this.defaultEmission = defaultEmission;
    }

    public String getName() {
        return name;
    }

    public int getAlbedoIndex() {
        return albedoIndex;
    }

    public int getNormalIndex() {
        return normalIndex;
    }

    public int getSpecularIndex() {
        return specularIndex;
    }

    public float getDefaultRoughness() {
        return defaultRoughness;
    }

    public float getDefaultMetallic() {
        return defaultMetallic;
    }

    public float getDefaultEmission() {
        return defaultEmission;
    }

    public boolean hasNormalMap() {
        return normalIndex >= 0;
    }

    public boolean hasSpecularMap() {
        return specularIndex >= 0;
    }
}
