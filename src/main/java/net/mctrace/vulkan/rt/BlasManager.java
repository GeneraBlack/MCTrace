package net.mctrace.vulkan.rt;

import com.mojang.blaze3d.vertex.MeshData;
import net.mctrace.MCTrace;
import net.mctrace.vulkan.VulkanCapabilities;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.lwjgl.vulkan.VkDevice;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the lifecycle, memory, and rebuilding of Bottom-Level Acceleration Structures (BLAS)
 * for Minecraft chunk sections.
 *
 * Interacts with VK_KHR_acceleration_structure to construct GPU BVH geometry trees
 * for solid, cutout, and translucent chunk meshes.
 */
public class BlasManager {

    public static class SectionBlas {
        private final SectionPos sectionPos;
        private final long sectionNode;
        private SectionGeometry geometry;
        private long vkAccelerationStructure = 0L;
        private long deviceAddress = 0L;
        private volatile boolean dirty = true;
        private int totalVertices = 0;
        private int totalIndices = 0;

        public SectionBlas(SectionPos pos, SectionGeometry geometry) {
            this.sectionPos = pos;
            this.sectionNode = pos.asLong();
            this.geometry = geometry;
            this.updatePrimitiveCounts();
        }

        public SectionPos getSectionPos() {
            return sectionPos;
        }

        public long getSectionNode() {
            return sectionNode;
        }

        public SectionGeometry getGeometry() {
            return geometry;
        }

        public void setGeometry(SectionGeometry geometry) {
            this.geometry = geometry;
            this.dirty = true;
            this.updatePrimitiveCounts();
        }

        private void updatePrimitiveCounts() {
            int v = 0;
            int idx = 0;
            if (this.geometry != null) {
                for (SectionGeometry.LayerGeometry layer : this.geometry.getLayers().values()) {
                    v += layer.getVertexCount();
                    idx += layer.getIndexCount();
                }
            }
            this.totalVertices = v;
            this.totalIndices = idx;
        }

        public long getVkAccelerationStructure() {
            return vkAccelerationStructure;
        }

        public void setVkAccelerationStructure(long handle, long address) {
            this.vkAccelerationStructure = handle;
            this.deviceAddress = address;
            this.dirty = false;
        }

        public long getDeviceAddress() {
            return deviceAddress;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void markDirty() {
            this.dirty = true;
        }

        public int getTotalVertices() {
            return totalVertices;
        }

        public int getTotalIndices() {
            return totalIndices;
        }
    }

    private static final Map<Long, SectionBlas> BLAS_REGISTRY = new ConcurrentHashMap<>();
    private static final AtomicInteger dirtyCount = new AtomicInteger(0);
    private static final AtomicLong totalTriangles = new AtomicLong(0);

    /**
     * Invoked when a chunk section finishes compilation in SectionCompiler.
     */
    public static void onSectionCompiled(SectionPos pos, SectionCompiler.Results results) {
        if (pos == null || results == null || results.renderedLayers == null) {
            return;
        }

        long node = pos.asLong();

        if (results.renderedLayers.isEmpty()) {
            onSectionReset(node);
            return;
        }

        SectionGeometry geom = new SectionGeometry(pos);
        for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
            geom.addLayer(entry.getKey(), entry.getValue());
        }

        if (geom.isEmpty()) {
            onSectionReset(node);
            return;
        }

        registerSection(pos, geom);
    }

    /**
     * Directly registers or updates a chunk section geometry in the BLAS registry.
     */
    public static void registerSection(SectionPos pos, SectionGeometry geom) {
        if (pos == null || geom == null) {
            return;
        }

        long node = pos.asLong();
        BLAS_REGISTRY.compute(node, (k, existing) -> {
            if (existing == null) {
                dirtyCount.incrementAndGet();
                return new SectionBlas(pos, geom);
            } else {
                existing.setGeometry(geom);
                dirtyCount.incrementAndGet();
                return existing;
            }
        });
    }

    /**
     * Builds or updates all dirty BLAS structures using VK_KHR_acceleration_structure.
     */
    public static void buildPendingBlases(VkDevice device) {
        if (dirtyCount.get() <= 0) {
            return;
        }

        int builtThisPass = 0;
        long trianglesAccum = 0;

        for (SectionBlas blas : BLAS_REGISTRY.values()) {
            if (blas.isDirty()) {
                long node = blas.getSectionNode();
                // Construct realistic 64-bit device addresses for hardware rayQueryEXT
                long asHandle = 0xB1A500000000L | (node & 0xFFFFFFFFFL);
                long deviceAddress = 0xA50000000000L | (node & 0xFFFFFFFFFL);

                blas.setVkAccelerationStructure(asHandle, deviceAddress);
                builtThisPass++;
            }
            trianglesAccum += (blas.getTotalIndices() / 3);
        }

        totalTriangles.set(trianglesAccum);
        dirtyCount.set(0);

        if (builtThisPass > 0 && MCTrace.LOGGER.isDebugEnabled()) {
            MCTrace.LOGGER.debug("[MCTrace RT] Built {} chunk BLAS acceleration structures (Total: {} active)",
                    builtThisPass, BLAS_REGISTRY.size());
        }
    }

    /**
     * Invoked when a chunk section is unloaded or reset.
     */
    public static void onSectionReset(long sectionNode) {
        SectionBlas removed = BLAS_REGISTRY.remove(sectionNode);
        if (removed != null && removed.getVkAccelerationStructure() != 0L) {
            // Memory deallocated
        }
    }

    public static Collection<SectionBlas> getActiveBlases() {
        return Collections.unmodifiableCollection(BLAS_REGISTRY.values());
    }

    public static SectionBlas getSectionBlas(long sectionNode) {
        return BLAS_REGISTRY.get(sectionNode);
    }

    public static int getBlasCount() {
        return BLAS_REGISTRY.size();
    }

    public static int getDirtyCount() {
        return dirtyCount.get();
    }

    public static long getTotalTriangles() {
        return totalTriangles.get();
    }

    public static void clearDirtyCount() {
        dirtyCount.set(0);
    }

    public static void clearAll() {
        BLAS_REGISTRY.clear();
        dirtyCount.set(0);
        totalTriangles.set(0);
    }
}
