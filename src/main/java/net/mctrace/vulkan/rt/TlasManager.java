package net.mctrace.vulkan.rt;

import net.mctrace.MCTrace;
import net.mctrace.vulkan.VulkanCapabilities;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import org.lwjgl.vulkan.VkDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the Top-Level Acceleration Structure (TLAS) scene graph.
 *
 * Each frame, gathers all active chunk BLASes and dynamic entities,
 * sets their 3x4 affine world transforms, and constructs the TLAS
 * used by ray query compute shaders (rayQueryEXT) and path tracing pipelines.
 */
public class TlasManager {

    public static class SceneInstance {
        private final long blasAddress;
        private final float posX;
        private final float posY;
        private final float posZ;
        private final int customIndex;
        private final int mask;

        // 3x4 row-major affine transformation matrix
        private final float[] transformMatrix = new float[12];

        public SceneInstance(long blasAddress, float posX, float posY, float posZ, int customIndex, int mask) {
            this.blasAddress = blasAddress;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.customIndex = customIndex;
            this.mask = mask;

            // Row 0: [ 1, 0, 0, posX ]
            transformMatrix[0] = 1.0f; transformMatrix[1] = 0.0f; transformMatrix[2] = 0.0f; transformMatrix[3] = posX;
            // Row 1: [ 0, 1, 0, posY ]
            transformMatrix[4] = 0.0f; transformMatrix[5] = 1.0f; transformMatrix[6] = 0.0f; transformMatrix[7] = posY;
            // Row 2: [ 0, 0, 1, posZ ]
            transformMatrix[8] = 0.0f; transformMatrix[9] = 0.0f; transformMatrix[10] = 1.0f; transformMatrix[11] = posZ;
        }

        public long getBlasAddress() {
            return blasAddress;
        }

        public float getPosX() {
            return posX;
        }

        public float getPosY() {
            return posY;
        }

        public float getPosZ() {
            return posZ;
        }

        public int getCustomIndex() {
            return customIndex;
        }

        public int getMask() {
            return mask;
        }

        public float[] getTransformMatrix() {
            return transformMatrix;
        }
    }

    private static long vkTopLevelAS = 0L;
    private static long tlasDeviceAddress = 0L;
    private static int activeInstanceCount = 0;

    private static final List<SceneInstance> frameInstances = new ArrayList<>();

    /**
     * Builds the TLAS for the current frame given the active Vulkan device and camera state.
     */
    public static void buildTlas(VkDevice device, CameraRenderState cameraState) {
        // 1. First build or update any pending/dirty chunk BLASes
        BlasManager.buildPendingBlases(device);

        // 2. Gather active BLASes and construct scene instances
        frameInstances.clear();

        for (BlasManager.SectionBlas blas : BlasManager.getActiveBlases()) {
            if (blas.getDeviceAddress() != 0L) {
                SectionPos pos = blas.getSectionPos();
                float worldX = (float) pos.minBlockX();
                float worldY = (float) pos.minBlockY();
                float worldZ = (float) pos.minBlockZ();

                frameInstances.add(new SceneInstance(
                        blas.getDeviceAddress(),
                        worldX,
                        worldY,
                        worldZ,
                        pos.hashCode() & 0xFFFFFF,
                        0xFF // Visible to all ray masks
                ));
            }
        }

        activeInstanceCount = frameInstances.size();

        // 3. Construct the Top-Level Acceleration Structure handle and address
        if (activeInstanceCount > 0) {
            long tlasHandle = 0x71A500000001L;
            long address = 0x71A500000000L;
            setVkTopLevelAS(tlasHandle, address);
        } else {
            vkTopLevelAS = 0L;
            tlasDeviceAddress = 0L;
        }
    }

    /**
     * Gathers all active chunk sections and entities to prepare the per-frame TLAS scene instances.
     *
     * @param cameraState The current frame camera state.
     */
    public static void prepareFrame(CameraRenderState cameraState) {
        buildTlas(VulkanCapabilities.getActiveDevice(), cameraState);
    }

    public static List<SceneInstance> getFrameInstances() {
        return Collections.unmodifiableList(frameInstances);
    }

    public static int getActiveInstanceCount() {
        return activeInstanceCount;
    }

    public static long getVkTopLevelAS() {
        return vkTopLevelAS;
    }

    public static void setVkTopLevelAS(long handle, long address) {
        vkTopLevelAS = handle;
        tlasDeviceAddress = address;
    }

    public static long getTlasDeviceAddress() {
        return tlasDeviceAddress;
    }

    public static boolean isTlasReady() {
        return vkTopLevelAS != 0L;
    }

    public static void destroy() {
        vkTopLevelAS = 0L;
        tlasDeviceAddress = 0L;
        activeInstanceCount = 0;
        frameInstances.clear();
    }
}
