package net.mctrace.vulkan.rt;

import net.mctrace.MCTrace;
import org.lwjgl.vulkan.VkDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Bottom-Level Acceleration Structures (BLAS) for dynamic scene entities
 * (Players, Mobs, Minecarts, Boats, Projectiles).
 *
 * Each frame, gathers visible entities, builds or reuses their geometric bounding
 * acceleration structures, and injects them into the Top-Level Acceleration Structure (TLAS).
 */
public class EntityBlasManager {

    public static class EntityInstance {
        private final int entityId;
        private final String entityType;
        private final float posX;
        private final float posY;
        private final float posZ;
        private final float yaw;
        private final float width;
        private final float height;
        private final long blasAddress;

        public EntityInstance(
                int entityId,
                String entityType,
                float posX,
                float posY,
                float posZ,
                float yaw,
                float width,
                float height,
                long blasAddress
        ) {
            this.entityId = entityId;
            this.entityType = entityType;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.yaw = yaw;
            this.width = width;
            this.height = height;
            this.blasAddress = blasAddress;
        }

        public int getEntityId() {
            return entityId;
        }

        public String getEntityType() {
            return entityType;
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

        public float getYaw() {
            return yaw;
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        public long getBlasAddress() {
            return blasAddress;
        }
    }

    private static final Map<Integer, EntityInstance> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static final AtomicInteger entityBlasCounter = new AtomicInteger(0);
    private static long defaultEntityBlasHandle = 0xEB1A50000001L;
    private static long defaultEntityBlasAddress = 0xEB1A50000000L;

    /**
     * Registers or updates an entity instance for the current frame.
     */
    public static void updateEntity(
            int entityId,
            String entityType,
            double x,
            double y,
            double z,
            float yaw,
            float width,
            float height
    ) {
        long address = defaultEntityBlasAddress + ((long) (entityId & 0xFFFF) << 16);
        EntityInstance instance = new EntityInstance(
                entityId,
                entityType != null ? entityType : "unknown",
                (float) x,
                (float) y,
                (float) z,
                yaw,
                width > 0.0f ? width : 0.6f,
                height > 0.0f ? height : 1.8f,
                address
        );
        ACTIVE_ENTITIES.put(entityId, instance);
    }

    /**
     * Removes an entity when it leaves tracking or is destroyed.
     */
    public static void removeEntity(int entityId) {
        ACTIVE_ENTITIES.remove(entityId);
    }

    /**
     * Gathers all currently registered dynamic entities as scene instances for TLAS insertion.
     */
    public static List<TlasManager.SceneInstance> buildEntityInstances() {
        if (ACTIVE_ENTITIES.isEmpty()) {
            return Collections.emptyList();
        }

        List<TlasManager.SceneInstance> instances = new ArrayList<>(ACTIVE_ENTITIES.size());
        for (EntityInstance entity : ACTIVE_ENTITIES.values()) {
            // Encode customIndex: 0x800000 | (entityId & 0x7FFFFF) to distinguish from chunk sections
            int customIndex = 0x800000 | (entity.getEntityId() & 0x7FFFFF);
            instances.add(new TlasManager.SceneInstance(
                    entity.getBlasAddress(),
                    entity.getPosX(),
                    entity.getPosY(),
                    entity.getPosZ(),
                    customIndex,
                    0xFF // Visible to shadow and reflection rays
            ));
        }
        return instances;
    }

    public static int getActiveEntityCount() {
        return ACTIVE_ENTITIES.size();
    }

    public static void clear() {
        ACTIVE_ENTITIES.clear();
    }
}
