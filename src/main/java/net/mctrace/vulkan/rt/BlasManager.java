package net.mctrace.vulkan.rt;

import com.mojang.blaze3d.vertex.MeshData;
import net.mctrace.MCTrace;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the lifecycle, memory, and rebuilding of Bottom-Level Acceleration Structures (BLAS)
 * for Minecraft chunk sections.
 */
public class BlasManager {

    public static class SectionBlas {
        private final SectionPos sectionPos;
        private final long sectionNode;
        private SectionGeometry geometry;
        private long vkAccelerationStructure = 0L;
        private long deviceAddress = 0L;
        private volatile boolean dirty = true;

        public SectionBlas(SectionPos pos, SectionGeometry geometry) {
            this.sectionPos = pos;
            this.sectionNode = pos.asLong();
            this.geometry = geometry;
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
    }

    private static final Map<Long, SectionBlas> BLAS_REGISTRY = new ConcurrentHashMap<>();
    private static final AtomicInteger dirtyCount = new AtomicInteger(0);

    /**
     * Invoked when a chunk section finishes compilation in SectionCompiler.
     */
    public static void onSectionCompiled(SectionPos pos, SectionCompiler.Results results) {
        if (pos == null || results == null || results.renderedLayers == null) {
            return;
        }

        long node = pos.asLong();

        if (results.renderedLayers.isEmpty()) {
            // Section has no renderable geometry (e.g. all air blocks or underground solid)
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
     * Invoked when a chunk section is unloaded or reset.
     */
    public static void onSectionReset(long sectionNode) {
        SectionBlas removed = BLAS_REGISTRY.remove(sectionNode);
        if (removed != null && removed.getVkAccelerationStructure() != 0L) {
            // Memory deallocation hooks will free the VkAccelerationStructureKHR
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

    public static void clearDirtyCount() {
        dirtyCount.set(0);
    }

    public static void clearAll() {
        BLAS_REGISTRY.clear();
        dirtyCount.set(0);
    }
}
