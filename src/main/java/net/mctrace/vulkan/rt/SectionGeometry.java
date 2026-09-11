package net.mctrace.vulkan.rt;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates extracted vertex and index geometry for a 16x16x16 chunk section,
 * formatted for Vulkan Bottom-Level Acceleration Structure (BLAS) generation.
 */
public class SectionGeometry {

    public static class LayerGeometry {
        private final ChunkSectionLayer layer;
        private final int vertexCount;
        private final int indexCount;
        private final ByteBuffer vertexBuffer;
        private final ByteBuffer indexBuffer;
        private final boolean opaque;

        public LayerGeometry(ChunkSectionLayer layer, MeshData meshData) {
            this.layer = layer;
            this.vertexCount = meshData.drawState().vertexCount();
            this.indexCount = meshData.drawState().indexCount();

            // Slices of vertex and index data
            this.vertexBuffer = meshData.vertexBuffer();
            this.indexBuffer = meshData.indexBuffer();

            // Solid layer is opaque; cutout and translucent require any-hit shaders
            this.opaque = (layer == ChunkSectionLayer.SOLID);
        }

        public ChunkSectionLayer getLayer() {
            return layer;
        }

        public int getVertexCount() {
            return vertexCount;
        }

        public int getIndexCount() {
            return indexCount;
        }

        public ByteBuffer getVertexBuffer() {
            return vertexBuffer;
        }

        public ByteBuffer getIndexBuffer() {
            return indexBuffer;
        }

        public boolean isOpaque() {
            return opaque;
        }
    }

    private final SectionPos sectionPos;
    private final long sectionNode;
    private final Map<ChunkSectionLayer, LayerGeometry> layers = new HashMap<>();

    public SectionGeometry(SectionPos sectionPos) {
        this.sectionPos = sectionPos;
        this.sectionNode = sectionPos.asLong();
    }

    public void addLayer(ChunkSectionLayer layer, MeshData meshData) {
        if (meshData != null && meshData.drawState().vertexCount() > 0) {
            layers.put(layer, new LayerGeometry(layer, meshData));
        }
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public SectionPos getSectionPos() {
        return sectionPos;
    }

    public long getSectionNode() {
        return sectionNode;
    }

    public Map<ChunkSectionLayer, LayerGeometry> getLayers() {
        return Collections.unmodifiableMap(layers);
    }
}
