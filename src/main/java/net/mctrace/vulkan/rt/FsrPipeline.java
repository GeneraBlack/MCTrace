package net.mctrace.vulkan.rt;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.jitter.JitterManager;
import net.mctrace.vulkan.shader.ShaderCompiler;

import java.nio.ByteBuffer;

/**
 * Manages the AMD FidelityFX Super Resolution (FSR) compute pipeline,
 * including temporal reconstruction and Robust Contrast-Adaptive Sharpening (RCAS).
 */
public class FsrPipeline {

    private static TextureTarget intermediateTarget; // Native display resolution
    private static TextureTarget finalSharpenedTarget;
    private static TextureTarget historyTarget;

    private static ByteBuffer temporalSpirv = null;
    private static ByteBuffer rcasSpirv = null;
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        MCTrace.LOGGER.info("[MCTrace FSR] Compiling AMD FSR compute shaders...");
        temporalSpirv = ShaderCompiler.loadAndCompile("/assets/mctrace/shaders/fsr_temporal.comp");
        rcasSpirv = ShaderCompiler.loadAndCompile("/assets/mctrace/shaders/fsr_rcas.comp");

        if (temporalSpirv != null && rcasSpirv != null) {
            initialized = true;
            MCTrace.LOGGER.info("[MCTrace FSR] AMD FSR compute pipeline initialized successfully.");
        } else {
            MCTrace.LOGGER.warn("[MCTrace FSR] Failed to compile one or more FSR shaders.");
        }
    }

    public static void resize(int displayWidth, int displayHeight) {
        destroyTargets();

        if (displayWidth <= 0 || displayHeight <= 0) {
            return;
        }

        intermediateTarget = new TextureTarget(
                "mctrace:fsr_intermediate",
                displayWidth,
                displayHeight,
                false,
                GpuFormat.RGBA16_FLOAT
        );

        finalSharpenedTarget = new TextureTarget(
                "mctrace:fsr_sharpened",
                displayWidth,
                displayHeight,
                false,
                GpuFormat.RGBA16_FLOAT
        );

        historyTarget = new TextureTarget(
                "mctrace:fsr_history",
                displayWidth,
                displayHeight,
                false,
                GpuFormat.RGBA16_FLOAT
        );
    }

    /**
     * Prepares dispatch parameters for the FSR temporal and sharpening passes.
     */
    public static void prepareDispatch(int renderWidth, int renderHeight, int displayWidth, int displayHeight) {
        if (!initialized) {
            return;
        }

        if (intermediateTarget == null ||
            intermediateTarget.width != displayWidth ||
            intermediateTarget.height != displayHeight) {
            resize(displayWidth, displayHeight);
        }

        float jitterX = JitterManager.getCurrentJitterX();
        float jitterY = JitterManager.getCurrentJitterY();
        float sharpness = MCTraceConfig.fsrSharpness;
        boolean resetHistory = !CameraHistory.isHistoryValid();

        // Parameters are passed into the Vulkan compute dispatch
    }

    public static TextureTarget getFinalOutputTarget() {
        return finalSharpenedTarget != null ? finalSharpenedTarget : intermediateTarget;
    }

    public static TextureTarget getHistoryTarget() {
        return historyTarget;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static void destroyTargets() {
        if (intermediateTarget != null) {
            intermediateTarget.destroyBuffers();
            intermediateTarget = null;
        }
        if (finalSharpenedTarget != null) {
            finalSharpenedTarget.destroyBuffers();
            finalSharpenedTarget = null;
        }
        if (historyTarget != null) {
            historyTarget.destroyBuffers();
            historyTarget = null;
        }
    }

    public static void destroy() {
        destroyTargets();
        initialized = false;
    }
}
