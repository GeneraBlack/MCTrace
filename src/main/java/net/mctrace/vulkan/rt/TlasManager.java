package net.mctrace.vulkan.rt;

import net.mctrace.MCTrace;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the Top-Level Acceleration Structure (TLAS) scene graph.
 *
 * Each frame, gathers all active chunk BLASes and dynamic entities,
 * sets their 3x4 affine world transforms, and constructs the TLAS
 * used by ray query compute shaders and path tracing pipelines.
 */
public class TlasManager {

    public static class SceneInstance {
        private final long blasAddress;
        private final float posX;
        private final float posY;
        private final float posZ;
        private final int customIndex;
        private final int mask;

        public SceneInstance(long blasAddress, float posX, float posY, float posZ, int customIndex, int mask) {
            this.blasAddress = blasAddress;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.customIndex = customIndex;
            this.mask = mask;
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
    }

    private static long vkTopLevelAS = 0L;
    private static long tlasDeviceAddress = 0L;
    private static int activeInstanceCount = 0;

    private static final List<SceneInstance> frameInstances = new ArrayList<>();

    /**
     * Gathers all active chunk sections and entities to prepare the per-frame TLAS scene instances.
     *
     * @param cameraState The current frame camera state.
     */
    public static void prepareFrame(CameraRenderState cameraState) {
        frameInstances.clear();

        for (BlasManager.SectionBlas blas : BlasManager.getActiveBlases()) {
            // Only include built BLASes with valid GPU device addresses
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
                        0xFF // Visibile to all ray masks
                ));
            }
        }

        activeInstanceCount = frameInstances.size();
    }

    public static List<SceneInstance> getFrameInstances() {
        return frameInstances;
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
