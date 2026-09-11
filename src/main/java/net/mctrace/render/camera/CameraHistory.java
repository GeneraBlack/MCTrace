package net.mctrace.render.camera;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Tracks previous and current frame camera transforms, view/projection matrices,
 * and computes screen-space reprojection matrices for velocity buffer generation
 * and temporal accumulation.
 */
public class CameraHistory {

    private static final Matrix4f prevViewMatrix = new Matrix4f();
    private static final Matrix4f currViewMatrix = new Matrix4f();

    private static final Matrix4f prevProjMatrix = new Matrix4f(); // Unjittered
    private static final Matrix4f currProjMatrix = new Matrix4f(); // Unjittered

    private static final Matrix4f prevViewProjMatrix = new Matrix4f();
    private static final Matrix4f currViewProjMatrix = new Matrix4f();
    private static final Matrix4f currInvViewProjMatrix = new Matrix4f();

    private static final Matrix4f reprojectionMatrix = new Matrix4f();

    private static Vec3 prevCameraPos = Vec3.ZERO;
    private static Vec3 currCameraPos = Vec3.ZERO;
    private static Vec3 cameraTranslationDelta = Vec3.ZERO;

    private static boolean historyValid = false;
    private static boolean resetRequested = true;

    /**
     * Updates camera matrices for the current frame.
     *
     * @param viewMatrix       The current camera view/rotation matrix.
     * @param unjitteredProj   The current unjittered projection matrix.
     * @param cameraPos        The current camera world position.
     */
    public static void update(Matrix4f viewMatrix, Matrix4f unjitteredProj, Vec3 cameraPos) {
        if (resetRequested || !historyValid) {
            currViewMatrix.set(viewMatrix);
            currProjMatrix.set(unjitteredProj);
            currProjMatrix.mul(currViewMatrix, currViewProjMatrix);
            currViewProjMatrix.invert(currInvViewProjMatrix);

            prevViewMatrix.set(currViewMatrix);
            prevProjMatrix.set(currProjMatrix);
            prevViewProjMatrix.set(currViewProjMatrix);

            currCameraPos = cameraPos;
            prevCameraPos = cameraPos;
            cameraTranslationDelta = Vec3.ZERO;

            reprojectionMatrix.identity();
            historyValid = true;
            resetRequested = false;
            return;
        }

        // Cycle current to previous
        prevViewMatrix.set(currViewMatrix);
        prevProjMatrix.set(currProjMatrix);
        prevViewProjMatrix.set(currViewProjMatrix);
        prevCameraPos = currCameraPos;

        // Set new current
        currViewMatrix.set(viewMatrix);
        currProjMatrix.set(unjitteredProj);
        currProjMatrix.mul(currViewMatrix, currViewProjMatrix);
        currViewProjMatrix.invert(currInvViewProjMatrix);

        currCameraPos = cameraPos;
        cameraTranslationDelta = currCameraPos.subtract(prevCameraPos);

        // Reprojection matrix: M_prev * M_curr^(-1)
        prevViewProjMatrix.mul(currInvViewProjMatrix, reprojectionMatrix);
    }

    /**
     * Signals that camera history has been invalidated (e.g., dimension change, respawn, teleport).
     */
    public static void requestReset() {
        resetRequested = true;
    }

    public static boolean isHistoryValid() {
        return historyValid && !resetRequested;
    }

    public static Matrix4f getPrevViewProjMatrix() {
        return prevViewProjMatrix;
    }

    public static Matrix4f getCurrViewProjMatrix() {
        return currViewProjMatrix;
    }

    public static Matrix4f getCurrInvViewProjMatrix() {
        return currInvViewProjMatrix;
    }

    public static Matrix4f getReprojectionMatrix() {
        return reprojectionMatrix;
    }

    public static Vec3 getCameraTranslationDelta() {
        return cameraTranslationDelta;
    }

    public static Vec3 getCurrCameraPos() {
        return currCameraPos;
    }

    public static Vec3 getPrevCameraPos() {
        return prevCameraPos;
    }
}
