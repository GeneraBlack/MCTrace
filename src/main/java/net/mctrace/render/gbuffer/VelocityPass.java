package net.mctrace.render.gbuffer;

import net.mctrace.render.camera.CameraHistory;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Manages screen-space velocity vector calculations.
 *
 * Provides CPU-side reprojection utilities and matrix uniform uploads
 * for the GPU velocity reconstruction pass.
 */
public class VelocityPass {

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
}
