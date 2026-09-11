package net.mctrace.vulkan.pbr;

import net.mctrace.MCTrace;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scans active Minecraft resource packs for LabPBR 1.3 textures (_n.png and _s.png).
 * Automatically decodes:
 * - Specular maps (_s): Smoothness -> Roughness, F0 / Metallic, Porosity (SSS), and Emissive.
 * - Normal maps (_n): Tangent-space Normals and Alpha Height Map (for POM depth).
 *
 * Populates MaterialRegistry dynamically, seamlessly enhancing community PBR packs.
 */
public class LabPbrTextureHook {

    private static final Map<String, MaterialRegistry.LabPbrDecoded> DECODED_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger discoveredTextureCount = new AtomicInteger(0);
    private static boolean labPbrActive = false;

    /**
     * Decodes RGBA specular values according to LabPBR 1.3 standard.
     */
    public static MaterialRegistry.LabPbrDecoded decodeSpecular(int r, int g, int b, int a) {
        return new MaterialRegistry.LabPbrDecoded(r, g, b, a);
    }

    /**
     * Extracts POM depth from normal map alpha channel (height map).
     * Standard height maps range from 0 (lowest relief) to 255 (surface).
     * We calculate the dynamic range of height to determine realistic POM relief depth.
     */
    public static float computePomDepthFromNormalMap(BufferedImage normalImg) {
        if (normalImg == null) return 0.0f;
        int w = normalImg.getWidth();
        int h = normalImg.getHeight();
        int minA = 255;
        int maxA = 0;

        int step = Math.max(1, Math.min(w, h) / 16);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int argb = normalImg.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a < minA) minA = a;
                if (a > maxA) maxA = a;
            }
        }

        int heightVariance = maxA - minA;
        if (heightVariance > 20) {
            // Scale relief depth from 0.02f up to 0.08f based on height range
            return 0.02f + (heightVariance / 255.0f) * 0.06f;
        }
        return 0.0f;
    }

    /**
     * Registers a custom LabPBR texture pair into MaterialRegistry.
     */
    public static PbrMaterial registerFromImages(
            String blockName,
            BufferedImage normalImg,
            BufferedImage specularImg
    ) {
        float roughness = 0.85f;
        float metallic = 0.0f;
        float emission = 0.0f;
        float porosity = 0.0f;
        float pomDepth = 0.0f;

        if (specularImg != null) {
            int w = specularImg.getWidth();
            int h = specularImg.getHeight();
            // Sample center 4x4 region for representative material values
            long totalR = 0, totalG = 0, totalB = 0, totalA = 0;
            int count = 0;
            int startX = Math.max(0, w / 2 - 2);
            int startY = Math.max(0, h / 2 - 2);
            for (int y = startY; y < Math.min(h, startY + 4); y++) {
                for (int x = startX; x < Math.min(w, startX + 4); x++) {
                    int argb = specularImg.getRGB(x, y);
                    totalR += (argb >> 16) & 0xFF;
                    totalG += (argb >> 8) & 0xFF;
                    totalB += argb & 0xFF;
                    totalA += (argb >> 24) & 0xFF;
                    count++;
                }
            }

            if (count > 0) {
                int avgR = (int) (totalR / count);
                int avgG = (int) (totalG / count);
                int avgB = (int) (totalB / count);
                int avgA = (int) (totalA / count);

                MaterialRegistry.LabPbrDecoded decoded = decodeSpecular(avgR, avgG, avgB, avgA);
                roughness = decoded.roughness;
                metallic = decoded.metallic;
                porosity = decoded.porosity;
                emission = decoded.emission;

                DECODED_CACHE.put(blockName, decoded);
            }
        }

        if (normalImg != null) {
            pomDepth = computePomDepthFromNormalMap(normalImg);
        }

        int normalIdx = normalImg != null ? MaterialRegistry.getOrAllocateTexture(blockName + "_n") : -1;
        int specIdx = specularImg != null ? MaterialRegistry.getOrAllocateTexture(blockName + "_s") : -1;

        PbrMaterial material = new PbrMaterial(
                blockName,
                0,
                normalIdx,
                specIdx,
                roughness,
                metallic,
                emission,
                porosity,
                pomDepth
        );

        MaterialRegistry.registerMaterial(material);
        if (blockName.contains(":")) {
            String pathOnly = blockName.substring(blockName.indexOf(':') + 1);
            MaterialRegistry.registerMaterial(new PbrMaterial(pathOnly, 0, normalIdx, specIdx, roughness, metallic, emission, porosity, pomDepth));
            MaterialRegistry.registerMaterial(new PbrMaterial("minecraft:block/" + pathOnly, 0, normalIdx, specIdx, roughness, metallic, emission, porosity, pomDepth));
        }
        discoveredTextureCount.incrementAndGet();
        labPbrActive = true;

        MCTrace.LOGGER.info("[MCTrace LabPBR] Registered custom pack material: {} (roughness={}, metallic={}, porosity={}, pom={})",
                blockName, roughness, metallic, porosity, pomDepth);

        return material;
    }

    /**
     * Scans active Minecraft resource packs for _s.png and _n.png files.
     */
    public static void scanResourceManager(ResourceManager resourceManager) {
        if (resourceManager == null) return;

        MCTrace.LOGGER.info("[MCTrace LabPBR] Scanning active resource packs for LabPBR textures...");
        int countBefore = discoveredTextureCount.get();

        try {
            // Find all specular map textures matching */textures/block/*_s.png
            Map<Identifier, Resource> specularResources = resourceManager.listResources(
                    "textures/block",
                    loc -> loc.getPath().endsWith("_s.png")
            );

            for (Map.Entry<Identifier, Resource> entry : specularResources.entrySet()) {
                Identifier specId = entry.getKey();
                String specPath = specId.getPath(); // e.g. "textures/block/iron_block_s.png"
                String basePath = specPath.substring(0, specPath.length() - 6); // "textures/block/iron_block"
                String blockName = specId.getNamespace() + ":" + basePath.substring("textures/block/".length());

                Identifier normalId = Identifier.fromNamespaceAndPath(specId.getNamespace(), basePath + "_n.png");

                BufferedImage specImg = null;
                BufferedImage normImg = null;

                try (InputStream specStream = entry.getValue().open()) {
                    specImg = ImageIO.read(specStream);
                } catch (Throwable ignored) {}

                Optional<Resource> normRes = resourceManager.getResource(normalId);
                if (normRes.isPresent()) {
                    try (InputStream normStream = normRes.get().open()) {
                        normImg = ImageIO.read(normStream);
                    } catch (Throwable ignored) {}
                }

                if (specImg != null || normImg != null) {
                    registerFromImages(blockName, normImg, specImg);
                }
            }

            int found = discoveredTextureCount.get() - countBefore;
            if (found > 0) {
                MCTrace.LOGGER.info("[MCTrace LabPBR] Scan complete: successfully hooked {} LabPBR materials from active packs.", found);
            } else {
                MCTrace.LOGGER.info("[MCTrace LabPBR] No third-party LabPBR textures detected in active packs; using procedural material engine.");
            }
        } catch (Throwable t) {
            MCTrace.LOGGER.warn("[MCTrace LabPBR] Resource pack scan encountered an issue: {}", t.getMessage());
        }
    }

    public static int getDiscoveredTextureCount() {
        return discoveredTextureCount.get();
    }

    public static boolean isLabPbrActive() {
        return labPbrActive && discoveredTextureCount.get() > 0;
    }

    public static void reset() {
        DECODED_CACHE.clear();
        discoveredTextureCount.set(0);
        labPbrActive = false;
    }
}
