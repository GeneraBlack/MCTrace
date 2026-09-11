package net.mctrace.vulkan.rt;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.mctrace.MCTrace;
import net.mctrace.vulkan.shader.ShaderCompiler;

import java.nio.ByteBuffer;

/**
 * Manages the SVGF (Spatio-Temporal Variance-Guided Filtering) denoising pipeline,
 * including temporal accumulation ping-pong buffers and multi-iteration A-Trous wavelet filtering.
 */
public class DenoiserPipeline {

    // Temporal accumulation ping-pong
    private static TextureTarget[] accumulatedTargets = new TextureTarget[2];
    private static TextureTarget momentsTarget;
    private static TextureTarget historyLengthTarget;

    // Spatial A-Trous filtered output ping-pong
    private static TextureTarget[] atrousTargets = new TextureTarget[2];

    private static int currentTemporalIndex = 0;
    private static ByteBuffer temporalSpirv = null;
    private static ByteBuffer atrousSpirv = null;
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        MCTrace.LOGGER.info("[MCTrace Denoiser] Compiling SVGF compute shaders...");
        temporalSpirv = ShaderCompiler.loadAndCompile("/assets/mctrace/shaders/svgf_temporal.comp");
        atrousSpirv = ShaderCompiler.loadAndCompile("/assets/mctrace/shaders/svgf_atrous.comp");

        if (temporalSpirv != null && atrousSpirv != null) {
            initialized = true;
            MCTrace.LOGGER.info("[MCTrace Denoiser] SVGF denoising pipeline initialized successfully.");
        } else {
            MCTrace.LOGGER.warn("[MCTrace Denoiser] Failed to compile one or more SVGF shaders.");
        }
    }

    public static void resize(int renderWidth, int renderHeight) {
        destroyTargets();

        for (int i = 0; i < 2; i++) {
            accumulatedTargets[i] = new TextureTarget(
                    "mctrace:svgf_accum_" + i,
                    renderWidth,
                    renderHeight,
                    false,
                    GpuFormat.RGBA16_FLOAT
            );

            atrousTargets[i] = new TextureTarget(
                    "mctrace:svgf_atrous_" + i,
                    renderWidth,
                    renderHeight,
                    false,
                    GpuFormat.RGBA16_FLOAT
            );
        }

        momentsTarget = new TextureTarget(
                "mctrace:svgf_moments",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RG16_FLOAT
        );

        historyLengthTarget = new TextureTarget(
                "mctrace:svgf_history_len",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.R16_FLOAT
        );
    }

    /**
     * Cycles the temporal ping-pong index each frame.
     */
    public static void advanceFrame() {
        currentTemporalIndex = 1 - currentTemporalIndex;
    }

    public static TextureTarget getCurrentAccumulatedTarget() {
        return accumulatedTargets[currentTemporalIndex];
    }

    public static TextureTarget getPreviousAccumulatedTarget() {
        return accumulatedTargets[1 - currentTemporalIndex];
    }

    public static TextureTarget getMomentsTarget() {
        return momentsTarget;
    }

    public static TextureTarget getHistoryLengthTarget() {
        return historyLengthTarget;
    }

    public static TextureTarget getFinalDenoisedOutput() {
        return atrousTargets[0] != null ? atrousTargets[0] : getCurrentAccumulatedTarget();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static void destroyTargets() {
        for (int i = 0; i < 2; i++) {
            if (accumulatedTargets[i] != null) {
                accumulatedTargets[i].destroyBuffers();
                accumulatedTargets[i] = null;
            }
            if (atrousTargets[i] != null) {
                atrousTargets[i].destroyBuffers();
                atrousTargets[i] = null;
            }
        }
        if (momentsTarget != null) {
            momentsTarget.destroyBuffers();
            momentsTarget = null;
        }
        if (historyLengthTarget != null) {
            historyLengthTarget.destroyBuffers();
            historyLengthTarget = null;
        }
    }

    public static void reload() {
        initialized = false;
        initialize();
    }

    public static void destroy() {
        destroyTargets();
        initialized = false;
    }
}
