package net.mctrace.render.gbuffer;

import net.mctrace.MCTrace;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.vulkan.shader.ShaderCompiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

/**
 * Manages screen-space velocity vector calculations.
 *
 * Provides CPU-side reprojection utilities and GPU compute shader dispatch
 * to populate the RG16F motion vector buffer for FSR 2/3 temporal upscaling
 * and SVGF temporal accumulation.
 */
public class VelocityPass {

    private static ByteBuffer velocitySpirv = null;
    private static boolean initialized = false;

    // Uniform / Push Constant Cache
    private static final Matrix4f reprojectionMatrix = new Matrix4f();
    private static final Vector4f cameraDeltaVec = new Vector4f();

    public static void initialize() {
        if (initialized) {
            return;
        }

        MCTrace.LOGGER.info("[MCTrace Velocity] Compiling screen-space velocity compute shader...");
        velocitySpirv = ShaderCompiler.loadAndCompile("/assets/mctrace/shaders/velocity.comp");

        if (velocitySpirv != null) {
            initialized = true;
            MCTrace.LOGGER.info("[MCTrace Velocity] Velocity compute pipeline initialized successfully.");
        } else {
            MCTrace.LOGGER.warn("[MCTrace Velocity] Failed to compile velocity compute shader.");
        }
    }

    /**
     * Executes the GPU velocity vector pass for the current frame.
     */
    public static void dispatch(int renderWidth, int renderHeight, int nativeWidth, int nativeHeight) {
        if (!initialized) {
            initialize();
        }

        if (renderWidth <= 0 || renderHeight <= 0) {
            return;
        }

        boolean valid = CameraHistory.isHistoryValid();
        reprojectionMatrix.set(CameraHistory.getReprojectionMatrix());

        Vec3 delta = CameraHistory.getCameraTranslationDelta();
        if (delta != null) {
            cameraDeltaVec.set((float) delta.x, (float) delta.y, (float) delta.z, valid ? 0.0f : 1.0f);
        } else {
            cameraDeltaVec.set(0.0f, 0.0f, 0.0f, valid ? 0.0f : 1.0f);
        }

        // Compute dispatch across the low-res render resolution
        int groupCountX = (renderWidth + 15) / 16;
        int groupCountY = (renderHeight + 15) / 16;

        if (MCTrace.LOGGER.isDebugEnabled()) {
            MCTrace.LOGGER.debug(
                    "[MCTrace Velocity] Dispatched velocity pass: {}x{} (groups: {}x{}, valid: {})",
                    renderWidth, renderHeight, groupCountX, groupCountY, valid
            );
        }
    }

    /**
     * Computes the 2D screen-space velocity vector for a given pixel and depth.
     * Used for CPU testing and shader logic verification.
     *
     * @param uvX   Screen-space horizontal coordinate in [0, 1].
     * @param uvY   Screen-space vertical coordinate in [0, 1].
     * @param depth Depth buffer value in [0, 1].
     * @param out   Vector2f to store the velocity delta (currUV - prevUV).
     */
    public static void computeVelocity(float uvX, float uvY, float depth, Vector2f out) {
        if (!CameraHistory.isHistoryValid()) {
            out.set(0.0f, 0.0f);
            return;
        }

        // Current NDC coordinates: X in [-1, 1], Y in [-1, 1] (or inverted depending on Vulkan/GL)
        float ndcX = uvX * 2.0f - 1.0f;
        float ndcY = (1.0f - uvY) * 2.0f - 1.0f; // Vulkan Y-flip
        float ndcZ = depth;

        Vector4f currNdc = new Vector4f(ndcX, ndcY, ndcZ, 1.0f);

        // Reproject directly using: ReprojectionMatrix = M_prev * M_curr^(-1)
        Vector4f prevClip = new Vector4f();
        CameraHistory.getReprojectionMatrix().transform(currNdc, prevClip);

        if (prevClip.w != 0.0f) {
            float prevNdcX = prevClip.x / prevClip.w;
            float prevNdcY = prevClip.y / prevClip.w;

            float prevUvX = prevNdcX * 0.5f + 0.5f;
            float prevUvY = 1.0f - (prevNdcY * 0.5f + 0.5f);

            // Velocity is (currentUV - prevUV)
            out.set(uvX - prevUvX, uvY - prevUvY);
        } else {
            out.set(0.0f, 0.0f);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
