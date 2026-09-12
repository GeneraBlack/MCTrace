package net.mctrace.vulkan.pbr;

import net.mctrace.MCTrace;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages bindless PBR materials and assigns texture indices
 * for unbounded descriptor arrays (VK_EXT_descriptor_indexing).
 *
 * Implements full LabPBR specular, roughness, metallic, and emissive properties.
 */
public class MaterialRegistry {

    private static final Map<String, PbrMaterial> MATERIALS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> TEXTURE_DESCRIPTORS = new ConcurrentHashMap<>();
    private static final AtomicInteger nextDescriptorIndex = new AtomicInteger(1); // 0 is reserved for missing texture

    static {
        // Register default fallback material (matte dielectric)
        registerMaterial(PbrMaterial.DEFAULT);

        // Pre-register LabPBR materials with authentic physical properties
        // Metals (high metallic, low-medium roughness)
        registerMaterial(new PbrMaterial("metal_iron", 0, -1, -1, 0.30f, 1.0f, 0.0f));
        registerMaterial(new PbrMaterial("metal_gold", 0, -1, -1, 0.22f, 1.0f, 0.0f));
        registerMaterial(new PbrMaterial("metal_copper", 0, -1, -1, 0.28f, 1.0f, 0.0f));
        registerMaterial(new PbrMaterial("metal_netherite", 0, -1, -1, 0.35f, 0.9f, 0.0f));

        // Polished Minerals & Smooth Stones (low roughness, dielectric)
        registerMaterial(new PbrMaterial("polished_deepslate", 0, -1, -1, 0.16f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("polished_blackstone", 0, -1, -1, 0.16f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("smooth_stone", 0, -1, -1, 0.18f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("quartz_block", 0, -1, -1, 0.18f, 0.0f, 0.0f));

        // Gemstones (sharp glossy glints)
        registerMaterial(new PbrMaterial("gem_diamond", 0, -1, -1, 0.08f, 0.05f, 0.0f));
        registerMaterial(new PbrMaterial("gem_emerald", 0, -1, -1, 0.10f, 0.05f, 0.0f));
        registerMaterial(new PbrMaterial("gem_amethyst", 0, -1, -1, 0.12f, 0.05f, 0.4f));

        // Natural Matte Materials (high roughness, zero specular sheen)
        registerMaterial(new PbrMaterial("block_wood", 0, -1, -1, 0.85f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("block_stone", 0, -1, -1, 0.88f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("block_dirt", 0, -1, -1, 0.95f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("block_leaves", 0, -1, -1, 0.80f, 0.0f, 0.0f));

        // Fluids & Translucent
        registerMaterial(new PbrMaterial("fluid_water", 0, -1, -1, 0.03f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("block_glass", 0, -1, -1, 0.05f, 0.0f, 0.0f));
        registerMaterial(new PbrMaterial("block_ice", 0, -1, -1, 0.08f, 0.0f, 0.0f));

        // Emissive Blocks (coloured emission)
        registerMaterial(new PbrMaterial("light_torch", 0, -1, -1, 0.70f, 0.0f, 1.0f));
        registerMaterial(new PbrMaterial("light_redstone", 0, -1, -1, 0.65f, 0.0f, 1.0f));
        registerMaterial(new PbrMaterial("light_soul_fire", 0, -1, -1, 0.70f, 0.0f, 1.0f));
        registerMaterial(new PbrMaterial("light_sculk", 0, -1, -1, 0.25f, 0.0f, 0.9f));
        registerMaterial(new PbrMaterial("light_glowstone", 0, -1, -1, 0.75f, 0.0f, 1.0f));
        registerMaterial(new PbrMaterial("light_shroomlight", 0, -1, -1, 0.70f, 0.0f, 1.0f));

        // Subsurface Scattering & Translucent Materials (Mobs, Skin, Wax, Slime)
        registerMaterial(new PbrMaterial("entity_skin", 0, -1, -1, 0.45f, 0.0f, 0.0f, 0.0f, 0.0f, true, 0.85f));
        registerMaterial(new PbrMaterial("entity_wax", 0, -1, -1, 0.35f, 0.0f, 0.0f, 0.0f, 0.0f, true, 0.85f));
        registerMaterial(new PbrMaterial("block_slime", 0, -1, -1, 0.12f, 0.0f, 0.0f, 0.0f, 0.0f, true, 0.95f));
        registerMaterial(new PbrMaterial("block_honey", 0, -1, -1, 0.15f, 0.0f, 0.0f, 0.0f, 0.0f, true, 0.80f));
    }

    public static void registerMaterial(PbrMaterial material) {
        MATERIALS.put(material.getName(), material);
    }

    public static PbrMaterial getMaterial(String name) {
        return MATERIALS.getOrDefault(name, PbrMaterial.DEFAULT);
    }

    /**
     * Resolves material properties for Minecraft entities (players, mobs, animals).
     */
    public static PbrMaterial getMaterialForEntity(String identifier) {
        if (identifier == null) return PbrMaterial.DEFAULT;
        String lower = identifier.toLowerCase();
        if (lower.contains("player") || lower.contains("steve") || lower.contains("alex") ||
            lower.contains("zombie") || lower.contains("villager") || lower.contains("pig") ||
            lower.contains("cow") || lower.contains("sheep") || lower.contains("human")) {
            return getMaterial("entity_skin");
        }
        return getMaterialForBlock(identifier);
    }

    /**
     * Resolves material properties for a Minecraft block ID or texture identifier.
     */
    public static PbrMaterial getMaterialForBlock(String identifier) {
        if (identifier == null) return PbrMaterial.DEFAULT;
        String lower = identifier.toLowerCase();

        // 1. Check for exact or path-matched custom LabPBR resource pack materials first
        PbrMaterial direct = MATERIALS.get(lower);
        if (direct != null) return direct;
        if (lower.contains(":")) {
            String pathOnly = lower.substring(lower.indexOf(':') + 1);
            PbrMaterial pathMat = MATERIALS.get(pathOnly);
            if (pathMat != null) return pathMat;
            if (pathOnly.startsWith("block/")) {
                PbrMaterial shortMat = MATERIALS.get(pathOnly.substring("block/".length()));
                if (shortMat != null) return shortMat;
            }
        } else {
            PbrMaterial namespaced = MATERIALS.get("minecraft:" + lower);
            if (namespaced != null) return namespaced;
            PbrMaterial blockNamespaced = MATERIALS.get("minecraft:block/" + lower);
            if (blockNamespaced != null) return blockNamespaced;
        }

        // 2. Procedural heuristic matching for vanilla / unhooked textures
        if (lower.contains("gold")) return getMaterial("metal_gold");
        if (lower.contains("copper")) return getMaterial("metal_copper");
        if (lower.contains("iron")) return getMaterial("metal_iron");
        if (lower.contains("netherite")) return getMaterial("metal_netherite");
        if (lower.contains("polished_deepslate") || lower.contains("deepslate_tiles")) return getMaterial("polished_deepslate");
        if (lower.contains("polished_blackstone")) return getMaterial("polished_blackstone");
        if (lower.contains("smooth_stone")) return getMaterial("smooth_stone");
        if (lower.contains("quartz")) return getMaterial("quartz_block");
        if (lower.contains("diamond")) return getMaterial("gem_diamond");
        if (lower.contains("emerald")) return getMaterial("gem_emerald");
        if (lower.contains("amethyst")) return getMaterial("gem_amethyst");
        if (lower.contains("water")) return getMaterial("fluid_water");
        if (lower.contains("glass")) return getMaterial("block_glass");
        if (lower.contains("ice")) return getMaterial("block_ice");
        if (lower.contains("soul")) return getMaterial("light_soul_fire");
        if (lower.contains("sculk")) return getMaterial("light_sculk");
        if (lower.contains("redstone")) return getMaterial("light_redstone");
        if (lower.contains("torch") || lower.contains("lantern") || lower.contains("campfire") || lower.contains("lava")) return getMaterial("light_torch");
        if (lower.contains("leaves") || lower.contains("foliage") || lower.contains("vine")) return getMaterial("block_leaves");
        if (lower.contains("skin") || lower.contains("steve") || lower.contains("alex") || lower.contains("zombie") || lower.contains("villager")) return getMaterial("entity_skin");
        if (lower.contains("candle") || lower.contains("wax")) return getMaterial("entity_wax");
        if (lower.contains("slime")) return getMaterial("block_slime");
        if (lower.contains("honey")) return getMaterial("block_honey");
        if (lower.contains("dirt") || lower.contains("mud") || lower.contains("clay") || lower.contains("grass_block")) return getMaterial("block_dirt");
        if (lower.contains("wood") || lower.contains("planks") || lower.contains("log") || lower.contains("stem")) return getMaterial("block_wood");
        if (lower.contains("stone") || lower.contains("cobblestone") || lower.contains("gravel") || lower.contains("sand")) return getMaterial("block_stone");

        return MATERIALS.getOrDefault(lower, PbrMaterial.DEFAULT);
    }

    /**
     * LabPBR 1.3 Specular Channel Decoder.
     * Maps RGBA channels from a LabPBR _s texture to physical material properties:
     * - R: Smoothness (Roughness = 1.0 - Smoothness)
     * - G: F0 / Metallic (values >= 230 represent metals)
     * - B: Porosity / SSS
     * - A: Emissive radiance
     */
    public static class LabPbrDecoded {
        public final float roughness;
        public final float metallic;
        public final float f0;
        public final float porosity;
        public final float emission;

        public LabPbrDecoded(int r, int g, int b, int a) {
            float smoothness = (r & 0xFF) / 255.0f;
            this.roughness = Math.max(0.04f, 1.0f - smoothness);
            int gVal = g & 0xFF;
            if (gVal >= 230) {
                this.metallic = ((gVal - 230) / 25.0f);
                this.f0 = 1.0f;
            } else {
                this.metallic = 0.0f;
                this.f0 = (gVal / 255.0f);
            }
            this.porosity = (b & 0xFF) / 255.0f;
            this.emission = (a & 0xFF) / 255.0f;
        }
    }

    /**
     * Determines whether a block supports Parallax Occlusion Mapping (POM) 3D relief.
     * High relief blocks include bricks, cobblestone, planks, deepslate, and ore veins.
     */
    public static float getPomDepthForBlock(String identifier) {
        if (identifier == null) return 0.0f;
        String lower = identifier.toLowerCase();
        PbrMaterial mat = MATERIALS.get(lower);
        if (mat != null && mat.getPomDepth() > 0.0f) {
            return mat.getPomDepth();
        }
        if (lower.contains("brick") || lower.contains("cobblestone") || lower.contains("deepslate_tiles")) {
            return 0.065f;
        }
        if (lower.contains("planks") || lower.contains("log") || lower.contains("carved") || lower.contains("chiseled")) {
            return 0.045f;
        }
        if (lower.contains("ore") || lower.contains("gravel")) {
            return 0.035f;
        }
        return 0.0f;
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

