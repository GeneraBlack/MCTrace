package net.mctrace.render.jitter;

import net.mctrace.config.MCTraceConfig;
import org.joml.Matrix4f;

/**
 * Generates low-discrepancy subpixel camera jitter sequences (Halton 2,3)
 * required by temporal anti-aliasing (TAA) and AMD FidelityFX Super Resolution (FSR 2/3).
 */
public class JitterManager {

    private static final int DEFAULT_PHASE_COUNT = 16;

    private static int frameIndex = 0;
    private static float currentJitterX = 0.0f; // in pixels [-0.5, 0.5]
    private static float currentJitterY = 0.0f; // in pixels [-0.5, 0.5]

    private static float currentNdcJitterX = 0.0f; // in NDC coordinates [-1, 1]
    private static float currentNdcJitterY = 0.0f;

    /**
     * Advances the jitter sequence for the current frame.
     *
     * @param renderWidth  The current internal render width.
     * @param renderHeight The current internal render height.
     */
    public static void advanceFrame(int renderWidth, int renderHeight) {
        if (!MCTraceConfig.enableFSR || renderWidth <= 0 || renderHeight <= 0) {
            currentJitterX = 0.0f;
            currentJitterY = 0.0f;
            currentNdcJitterX = 0.0f;
            currentNdcJitterY = 0.0f;
            return;
        }

        frameIndex++;

        // Halton sequence index is 1-based
        int index = (frameIndex % DEFAULT_PHASE_COUNT) + 1;

        // Halton(index, 2) and Halton(index, 3) mapped from [0, 1) to [-0.5, 0.5)
        currentJitterX = halton(index, 2) - 0.5f;
        currentJitterY = halton(index, 3) - 0.5f;

        // In NDC: [-1, 1], so full width is 2.0
        currentNdcJitterX = (2.0f * currentJitterX) / (float) renderWidth;
        currentNdcJitterY = (2.0f * currentJitterY) / (float) renderHeight;
    }

    /**
     * Applies the current subpixel jitter offset to a projection matrix.
     *
     * @param projectionMatrix The JOML Matrix4f projection matrix to modify.
     * @return The modified projection matrix.
     */
    public static Matrix4f applyJitter(Matrix4f projectionMatrix) {
        if (currentNdcJitterX != 0.0f || currentNdcJitterY != 0.0f) {
            projectionMatrix.m20(projectionMatrix.m20() + currentNdcJitterX);
            projectionMatrix.m21(projectionMatrix.m21() + currentNdcJitterY);
        }
        return projectionMatrix;
    }

    /**
     * Computes the n-th value of the Halton sequence for a given base.
     */
    public static float halton(int index, int base) {
        float result = 0.0f;
        float f = 1.0f / (float) base;
        int i = index;
        while (i > 0) {
            result += f * (float) (i % base);
            i = i / base;
            f = f / (float) base;
        }
        return result;
    }

    public static float getCurrentJitterX() {
        return currentJitterX;
    }

    public static float getCurrentJitterY() {
        return currentJitterY;
    }

    public static float getCurrentNdcJitterX() {
        return currentNdcJitterX;
    }

    public static float getCurrentNdcJitterY() {
        return currentNdcJitterY;
    }

    public static int getFrameIndex() {
        return frameIndex;
    }

    public static void reset() {
        frameIndex = 0;
        currentJitterX = 0.0f;
        currentJitterY = 0.0f;
        currentNdcJitterX = 0.0f;
        currentNdcJitterY = 0.0f;
    }
}
