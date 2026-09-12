package net.mctrace.vulkan.pbr;

import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import org.joml.Vector3f;

/**
 * High-Resolution (512x / 1024x / 2048x) Texture Pack Optimizer.
 *
 * Prevents VRAM exhaustion, GPU texture cache thrashing, and POM stalls when
 * ultra-high-resolution resource packs (e.g. Realistico, Stratum, Patrix, ModernArch)
 * are loaded with full LabPBR specular and normal maps.
 */
public class HDTextureOptimizer {

    public enum TextureResolutionLimit {
        NATIVE_1024("Native (Max 1024x/2048x)", 1024, 1.0f),
        BALANCED_512("Optimized (Max 512x - 75% VRAM saved)", 512, 0.5f),
        PERFORMANCE_256("Performance (Max 256x - 93% VRAM saved)", 256, 0.25f);

        private final String displayName;
        private final int maxDimension;
        private final float memoryScale;

        TextureResolutionLimit(String displayName, int maxDimension, float memoryScale) {
            this.displayName = displayName;
            this.maxDimension = maxDimension;
            this.memoryScale = memoryScale;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMaxDimension() {
            return maxDimension;
        }

        public float getMemoryScale() {
            return memoryScale;
        }
    }

    private static int detectedResolution = 16;
    private static boolean isHdPackDetected = false;

    /**
     * Inspects active texture dimensions to detect if a 256x, 512x, or 1024x pack is loaded.
     */
    public static void updateDetectedTexturePack(int sampleWidth, int sampleHeight) {
        int maxDim = Math.max(sampleWidth, sampleHeight);
        detectedResolution = maxDim;
        isHdPackDetected = (maxDim >= 256);

        if (isHdPackDetected) {
            MCTrace.LOGGER.info("[MCTrace HD Optimizer] High-resolution texture pack detected ({}x{}). Active profile: {}",
                    maxDim, maxDim, MCTraceConfig.hdTextureMode.getDisplayName());
        }
    }

    public static boolean isHdPackDetected() {
        return isHdPackDetected;
    }

    public static int getDetectedResolution() {
        return detectedResolution;
    }

    /**
     * Calculates the estimated VRAM footprint in megabytes for the 3 LabPBR atlases
     * (Base Albedo, Normal/Height `_n`, Specular/Roughness `_s`).
     */
    public static float estimateTextureVramMb(int resolution, TextureResolutionLimit limit) {
        int effectiveRes = Math.min(resolution, limit.getMaxDimension());
        // Standard Minecraft atlas has ~1024-2048 block states stitched together
        // For a 512x block: stitched atlas is ~16384 x 16384 (268 MPix * 4 bytes = 1.07 GB per atlas)
        // 3 atlases (Albedo, Normal, Specular) = ~3.2 GB
        float baseMegabytes = switch (effectiveRes) {
            case 1024 -> 4096.0f; // 32K virtual atlas / multiple 16K atlases
            case 512 -> 1024.0f * 3.0f; // ~3.0 GB
            case 256 -> 256.0f * 3.0f;  // ~768 MB
            case 128 -> 64.0f * 3.0f;   // ~192 MB
            default -> 16.0f * 3.0f;    // ~48 MB (Vanilla 16x)
        };
        return baseMegabytes;
    }

    /**
     * Calculates distance-adaptive POM raymarching step count.
     * Drops steps from 32 down to 4 based on view distance, and disables beyond 24 blocks.
     */
    public static int calculateAdaptivePomSteps(float distanceMeters, float nDotV) {
        if (!MCTraceConfig.enableDistancePomLod) {
            return 32;
        }

        if (distanceMeters > 24.0f) {
            return 0; // Flat mapping beyond 24 blocks (saves 95% lookups)
        }
        if (distanceMeters > 12.0f) {
            return 4;
        }
        if (distanceMeters > 6.0f) {
            return 8;
        }

        // Close-up: scale steps dynamically with grazing view angle
        float angleFactor = 1.0f - Math.max(0.0f, nDotV);
        return (int) (12.0f + angleFactor * 20.0f);
    }

    /**
     * Toksvig Specular Anti-Aliasing filter calculation.
     * Blends high-frequency normal variance into microfacet roughness to eliminate specular shimmering.
     */
    public static float applyToksvigRoughness(float baseRoughness, Vector3f normalVariation) {
        if (!MCTraceConfig.enableSpecularAntiAliasing) {
            return baseRoughness;
        }
        float variance = normalVariation.lengthSquared();
        float varianceRoughness = Math.min(0.40f, variance * 2.0f);
        return (float) Math.min(1.0, Math.sqrt(baseRoughness * baseRoughness + varianceRoughness));
    }
}
