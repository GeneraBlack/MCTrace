package net.mctrace.vulkan.pbr;

import net.mctrace.MCTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages bindless PBR materials and assigns texture indices
 * for unbounded descriptor arrays (VK_EXT_descriptor_indexing).
 */
public class MaterialRegistry {

    private static final Map<String, PbrMaterial> MATERIALS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> TEXTURE_DESCRIPTORS = new ConcurrentHashMap<>();
    private static final AtomicInteger nextDescriptorIndex = new AtomicInteger(1); // 0 is reserved for missing texture

    static {
        // Register default fallback material
        registerMaterial(PbrMaterial.DEFAULT);

        // Pre-register common PBR presets
        registerMaterial(new PbrMaterial("metal_iron", 0, -1, -1, 0.35f, 1.0f, 0.0f));
        registerMaterial(new PbrMaterial("metal_gold", 0, -1, -1, 0.25f, 1.0f, 0.0f));
        registerMaterial(new PbrMaterial("gem_diamond", 0, -1, -1, 0.10f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("light_glowstone", 0, -1, -1, 0.80f, 0.0f, 1.0f));
        registerMaterial(new PbrMaterial("light_shroomlight", 0, -1, -1, 0.70f, 0.0f, 1.0f));
        registerMaterial(new PbrMaterial("fluid_water", 0, -1, -1, 0.02f, 0.0f, 0.0f));
    }

    public static void registerMaterial(PbrMaterial material) {
        MATERIALS.put(material.getName(), material);
    }

    public static PbrMaterial getMaterial(String name) {
        return MATERIALS.getOrDefault(name, PbrMaterial.DEFAULT);
    }

    /**
     * Allocates or retrieves an existing descriptor index for a texture resource path.
     */
    public static int getOrAllocateTexture(String textureIdentifier) {
        return TEXTURE_DESCRIPTORS.computeIfAbsent(textureIdentifier, k -> {
            int id = nextDescriptorIndex.getAndIncrement();
            MCTrace.LOGGER.debug("[MCTrace PBR] Allocated descriptor index {} for texture: {}", id, textureIdentifier);
            return id;
        });
    }

    public static int getRegisteredMaterialCount() {
        return MATERIALS.size();
    }

    public static int getAllocatedTextureCount() {
        return TEXTURE_DESCRIPTORS.size();
    }

    public static void clear() {
        MATERIALS.clear();
        TEXTURE_DESCRIPTORS.clear();
        nextDescriptorIndex.set(1);
        registerMaterial(PbrMaterial.DEFAULT);
    }
}
