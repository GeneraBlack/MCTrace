package net.mctrace.render.drs;

import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.minecraft.client.Minecraft;

/**
 * Dynamic Resolution Scaling (DRS) Manager.
 *
 * Dynamically scales internal render resolution in real time based on moving average
 * frame timings to guarantee locked target framerates (e.g. 60, 120, 144, 240 FPS)
 * without stuttering or sudden visual hitches.
 */
public class DRSManager {

    private static float currentScale = 1.0f;
    private static final float MIN_SCALE = 0.50f;
    private static final float MAX_SCALE = 1.00f;
    private static final float SCALE_STEP_DOWN = 0.04f;
    private static final float SCALE_STEP_UP = 0.015f;

    private static final int FRAME_HISTORY_SIZE = 30;
    private static final long[] frameTimesNanos = new long[FRAME_HISTORY_SIZE];
    private static int frameIndex = 0;
    private static int headroomFrameCount = 0;
    private static long lastFrameTimestamp = System.nanoTime();

    /**
     * Called at the start of each client render frame to measure Delta-T and adjust resolution scale.
     */
    public static void update() {
        if (!MCTraceConfig.enableDrs) {
            currentScale = MCTraceConfig.fsrQualityMode.getScale();
            return;
        }

        long now = System.nanoTime();
        long deltaNanos = now - lastFrameTimestamp;
        lastFrameTimestamp = now;
        updateWithDelta(deltaNanos);
    }

    /**
     * Overload to update DRS based on measured instantaneous framerate.
     */
    public static void update(float fps) {
        if (!MCTraceConfig.enableDrs) {
            currentScale = MCTraceConfig.fsrQualityMode.getScale();
            return;
        }
        if (fps <= 1.0f) return;
        long deltaNanos = (long) ((1000.0f / fps) * 1_000_000.0f);
        updateWithDelta(deltaNanos);
    }

    private static void updateWithDelta(long deltaNanos) {
        frameTimesNanos[frameIndex % FRAME_HISTORY_SIZE] = deltaNanos;
        frameIndex++;

        if (frameIndex < FRAME_HISTORY_SIZE) {
            return;
        }

        // Calculate moving average frame time in milliseconds
        long sum = 0;
        for (long dt : frameTimesNanos) {
            sum += dt;
        }
        float avgMs = (sum / (float) FRAME_HISTORY_SIZE) / 1_000_000.0f;

        int targetFps = Math.max(30, MCTraceConfig.drsTargetFps);
        float targetBudgetMs = 1000.0f / targetFps;

        float oldScale = currentScale;

        // If frame time exceeds target budget by > 8%, downscale aggressively
        if (avgMs > targetBudgetMs * 1.08f) {
            currentScale = Math.max(MIN_SCALE, currentScale - SCALE_STEP_DOWN);
            headroomFrameCount = 0;
        }
        // If system has > 15% headroom consistently for 60+ frames, upscale gently
        else if (avgMs < targetBudgetMs * 0.85f) {
            headroomFrameCount++;
            if (headroomFrameCount > 60) {
                currentScale = Math.min(MAX_SCALE, currentScale + SCALE_STEP_UP);
                headroomFrameCount = 0;
            }
        } else {
            headroomFrameCount = 0;
        }

        // Reallocate G-Buffer render targets if resolution scale shifted significantly (> 1.5%)
        if (Math.abs(oldScale - currentScale) > 0.015f) {
            applyCurrentScale();
        }
    }

    private static void applyCurrentScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            int displayWidth = mc.getWindow().getWidth();
            int displayHeight = mc.getWindow().getHeight();
            int renderWidth = Math.max(16, (int) (displayWidth * currentScale));
            int renderHeight = Math.max(16, (int) (displayHeight * currentScale));

            GBufferManager.initOrResize(renderWidth, renderHeight);
            CameraHistory.requestReset();
        }
    }

    public static float getCurrentScale() {
        return currentScale;
    }

    public static void reset() {
        currentScale = MCTraceConfig.fsrQualityMode.getScale();
        frameIndex = 0;
        headroomFrameCount = 0;
        lastFrameTimestamp = System.nanoTime();
    }
}
