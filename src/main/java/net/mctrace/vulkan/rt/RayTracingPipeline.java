package net.mctrace.vulkan.rt;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.vulkan.shader.ShaderCompiler;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

/**
 * Manages the Ray Query compute pipeline for hardware-accelerated direct shadows
 * and ambient occlusion.
 */
public class RayTracingPipeline {

    private static TextureTarget rawIlluminationTarget;
    private static ByteBuffer compiledSpirv = null;
    private static boolean initialized = false;

    // Push constant cache
    private static final Matrix4f invViewProj = new Matrix4f();
    private static final Vector4f cameraPosVec = new Vector4f();
    private static final Vector4f lightDirVec = new Vector4f(0.5f, 0.8f, 0.3f, 1.0f);
    private static final Vector4f lightColorVec = new Vector4f(1.0f, 0.95f, 0.85f, 1.5f); // w = aoRadius

    public static void initialize() {
        if (initialized) {
            return;
        }

        MCTrace.LOGGER.info("[MCTrace RT] Compiling Ray Query compute shader...");
        compiledSpirv = ShaderCompiler.loadAndCompile("/assets/mctrace/shaders/rayquery.comp");

        if (compiledSpirv != null) {
            initialized = true;
            MCTrace.LOGGER.info("[MCTrace RT] Ray Query compute pipeline initialized successfully.");
        } else {
            MCTrace.LOGGER.warn("[MCTrace RT] Ray Query shader compilation failed or hardware unsupported.");
        }
    }

    /**
     * Resizes or initializes the raw illumination render target.
     */
    public static void resize(int renderWidth, int renderHeight) {
        if (rawIlluminationTarget != null) {
            rawIlluminationTarget.destroyBuffers();
        }

        rawIlluminationTarget = new TextureTarget(
                "mctrace:raw_illumination",
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RGBA16_FLOAT
        );
    }

    /**
     * Prepares uniform data and push constants for the ray query dispatch.
     */
    public static void prepareDispatch(
            CameraRenderState cameraState,
            Vector3f sunDir,
            Vector3f sunColor,
            int frameIndex
    ) {
        if (!initialized || !TlasManager.isTlasReady() || !GBufferManager.isInitialized()) {
            return;
        }

        int width = GBufferManager.getRenderWidth();
        int height = GBufferManager.getRenderHeight();

        if (rawIlluminationTarget == null ||
            rawIlluminationTarget.width != width ||
            rawIlluminationTarget.height != height) {
            resize(width, height);
        }

        // 1. Inverse View-Projection
        invViewProj.set(CameraHistory.getCurrInvViewProjMatrix());

        // 2. Camera position
        if (cameraState != null && cameraState.pos != null) {
            cameraPosVec.set((float) cameraState.pos.x, (float) cameraState.pos.y, (float) cameraState.pos.z, 1.0f);
        }

        // 3. Sun/Moon directional light
        if (sunDir != null) {
            lightDirVec.set(sunDir.x, sunDir.y, sunDir.z, 1.0f);
        }
        if (sunColor != null) {
            lightColorVec.set(sunColor.x, sunColor.y, sunColor.z, 1.5f); // AO radius = 1.5 blocks
        }
    }

    public static TextureTarget getRawIlluminationTarget() {
        return rawIlluminationTarget;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void destroy() {
        if (rawIlluminationTarget != null) {
            rawIlluminationTarget.destroyBuffers();
            rawIlluminationTarget = null;
        }
        initialized = false;
    }
}
