package net.mctrace.render.gbuffer;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;

/**
 * Manages allocation, lifecycle, resizing, and access to MCTrace's G-Buffers.
 *
 * Provides dedicated render targets for:
 * - HDR Color (RGBA16_FLOAT)
 * - World/View Normals + Material Flags (RGBA16_FLOAT)
 * - 2D Screen-Space Velocity / Motion Vectors (RG16_FLOAT)
 * - PBR Material Properties: Roughness, Metallic, Emissive, AO (RGBA8_UNORM)
 * - Linear Depth (R32_FLOAT)
 */
public class GBufferManager {

    private static TextureTarget hdrColorTarget;
    private static TextureTarget normalTarget;
    private static TextureTarget velocityTarget;
    private static TextureTarget pbrMaterialTarget;
    private static TextureTarget linearDepthTarget;

    private static int nativeWidth = 0;
    private static int nativeHeight = 0;
    private static int renderWidth = 0;
    private static int renderHeight = 0;

    private static boolean initialized = false;

    /**
     * Initializes or resizes the G-Buffers to match the display and internal render resolutions.
     *
     * @param displayWidth  The native window/viewport width.
     * @param displayHeight The native window/viewport height.
     */
    public static void initOrResize(int displayWidth, int displayHeight) {
        if (displayWidth <= 0 || displayHeight <= 0) {
            return;
        }

        float scale = MCTraceConfig.enableFSR ? MCTraceConfig.fsrQualityMode.getScale() : 1.0f;
        int targetRenderWidth = Math.max(1, Math.round(displayWidth * scale));
        int targetRenderHeight = Math.max(1, Math.round(displayHeight * scale));

        if (initialized && targetRenderWidth == renderWidth && targetRenderHeight == renderHeight) {
            return; // No resize needed
        }

        nativeWidth = displayWidth;
        nativeHeight = displayHeight;
        renderWidth = targetRenderWidth;
        renderHeight = targetRenderHeight;

        MCTrace.LOGGER.info("[MCTrace GBuffer] Allocating G-Buffers: Native {}x{}, Render {}x{} (scale: {}x)",
                nativeWidth, nativeHeight, renderWidth, renderHeight, String.format("%.2f", scale));

        destroy();

        // 1. HDR Color Target (with hardware depth buffer)
        hdrColorTarget = new TextureTarget(
                "mctrace:hdr_color",
                renderWidth,
                renderHeight,
                true, // useDepth
                GpuFormat.RGBA16_FLOAT
        );

        // 2. Surface Normals & Material Flags
        normalTarget = new TextureTarget(
                "mctrace:normals",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RGBA16_FLOAT
        );

        // 3. Motion Vectors / Screen-Space Velocity (2-channel RG16_FLOAT)
        velocityTarget = new TextureTarget(
                "mctrace:velocity",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RG16_FLOAT
        );

        // 4. PBR Materials: (R: Roughness, G: Metallic, B: Emissive, A: MaterialID)
        pbrMaterialTarget = new TextureTarget(
                "mctrace:pbr_materials",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RGBA8_UNORM
        );

        // 5. Linear Depth Target (R32_FLOAT)
        linearDepthTarget = new TextureTarget(
                "mctrace:linear_depth",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.R32_FLOAT
        );

        initialized = true;
    }

    public static void destroy() {
        if (hdrColorTarget != null) {
            hdrColorTarget.destroyBuffers();
            hdrColorTarget = null;
        }
        if (normalTarget != null) {
            normalTarget.destroyBuffers();
            normalTarget = null;
        }
        if (velocityTarget != null) {
            velocityTarget.destroyBuffers();
            velocityTarget = null;
        }
        if (pbrMaterialTarget != null) {
            pbrMaterialTarget.destroyBuffers();
            pbrMaterialTarget = null;
        }
        if (linearDepthTarget != null) {
            linearDepthTarget.destroyBuffers();
            linearDepthTarget = null;
        }
        initialized = false;
    }

    public static TextureTarget getHdrColorTarget() {
        return hdrColorTarget;
    }

    public static TextureTarget getNormalTarget() {
        return normalTarget;
    }

    public static TextureTarget getVelocityTarget() {
        return velocityTarget;
    }

    public static TextureTarget getPbrMaterialTarget() {
        return pbrMaterialTarget;
    }

    public static TextureTarget getLinearDepthTarget() {
        return linearDepthTarget;
    }

    public static int getNativeWidth() {
        return nativeWidth;
    }

    public static int getNativeHeight() {
        return nativeHeight;
    }

    public static int getRenderWidth() {
        return renderWidth;
    }

    public static int getRenderHeight() {
        return renderHeight;
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
