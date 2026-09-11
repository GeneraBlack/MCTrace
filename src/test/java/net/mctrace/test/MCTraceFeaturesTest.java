package net.mctrace.test;

import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.VelocityPass;
import net.mctrace.vulkan.hdr.DisplayHdrSync;
import net.mctrace.vulkan.pbr.MaterialRegistry;
import net.mctrace.vulkan.pbr.PbrMaterial;
import net.mctrace.vulkan.rt.BlasManager;
import net.mctrace.vulkan.rt.SectionGeometry;
import net.mctrace.vulkan.rt.TlasManager;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MCTraceFeaturesTest {

    @BeforeEach
    void setUp() {
        BlasManager.clearAll();
        TlasManager.destroy();
        CameraHistory.requestReset();
    }

    // =========================================================================
    // Tier 1: Immediate HDR & Display Polish
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Auto-Detect Monitor Peak Luminance & Sync with Windows HDR (456 Nits)")
    void testAutoDetectMonitorPeakLuminance() {
        // Verify default detected peak luminance for GS27U
        float peakNits = DisplayHdrSync.getDetectedPeakLuminance();
        assertEquals(456.0f, peakNits, 0.01f, "Detected peak luminance must match GS27U hardware limit (456 Nits)");

        String monitorName = DisplayHdrSync.getDetectedMonitorName();
        assertNotNull(monitorName);
        assertTrue(monitorName.contains("GS27U"), "Detected monitor name should identify GS27U");

        // Test one-click sync
        MCTraceConfig.hdrPeakLuminance = 1000.0f; // reset to previous default
        float syncedNits = DisplayHdrSync.syncWithMonitor();
        assertEquals(456.0f, syncedNits, 0.01f);
        assertEquals(456.0f, MCTraceConfig.hdrPeakLuminance, 0.01f, "Config peak luminance must sync to 456 Nits");
    }

    @Test
    @DisplayName("Tier 1: DCI-P3 / BT.2020 Wide Color Gamut Expansion Configuration")
    void testWideColorGamutConfiguration() {
        assertTrue(MCTraceConfig.enableWideGamut, "Wide color gamut expansion should be enabled by default");
        assertEquals(1.0f, MCTraceConfig.wideGamutStrength, 0.01f, "Default wide gamut strength should be 1.0x");

        // Verify config data model
        MCTraceConfig.ConfigData data = new MCTraceConfig.ConfigData();
        assertTrue(data.enableWideGamut);
        assertEquals(1.0f, data.wideGamutStrength);
        assertEquals(456.0f, data.hdrPeakLuminance);
    }

    // =========================================================================
    // Tier 2: World Shading & Material Realism
    // =========================================================================

    @Test
    @DisplayName("Tier 2: LabPBR Specular & Roughness Block Classification")
    void testLabPbrMaterialClassification() {
        assertTrue(MCTraceConfig.enablePbrMaterials, "PBR materials should be enabled by default");

        // 1. Metals
        PbrMaterial gold = MaterialRegistry.getMaterialForBlock("minecraft:block/gold_block");
        assertTrue(gold.getDefaultMetallic() >= 0.9f, "Gold must be metallic");
        assertTrue(gold.getDefaultRoughness() <= 0.25f, "Gold must have smooth specular");

        PbrMaterial copper = MaterialRegistry.getMaterialForBlock("minecraft:block/cut_copper");
        assertTrue(copper.getDefaultMetallic() >= 0.9f, "Copper must be metallic");
        assertTrue(copper.getDefaultRoughness() <= 0.30f, "Copper must have low roughness");

        PbrMaterial iron = MaterialRegistry.getMaterialForBlock("minecraft:block/iron_block");
        assertTrue(iron.getDefaultMetallic() >= 0.8f, "Iron must be metallic");

        // 2. Polished Minerals
        PbrMaterial deepslate = MaterialRegistry.getMaterialForBlock("minecraft:block/polished_deepslate");
        assertEquals(0.0f, deepslate.getDefaultMetallic(), 0.01f, "Deepslate is a dielectric");
        assertTrue(deepslate.getDefaultRoughness() <= 0.20f, "Polished deepslate must gleam with smooth roughness");

        // 3. Matte Materials (Wood, Stone, Dirt, Leaves)
        PbrMaterial wood = MaterialRegistry.getMaterialForBlock("minecraft:block/oak_planks");
        assertEquals(0.0f, wood.getDefaultMetallic(), 0.01f);
        assertTrue(wood.getDefaultRoughness() >= 0.80f, "Wood must remain matte without plastic sheen");

        PbrMaterial stone = MaterialRegistry.getMaterialForBlock("minecraft:block/stone");
        assertEquals(0.0f, stone.getDefaultMetallic(), 0.01f);
        assertTrue(stone.getDefaultRoughness() >= 0.85f, "Stone must remain matte");

        PbrMaterial dirt = MaterialRegistry.getMaterialForBlock("minecraft:block/dirt");
        assertEquals(0.0f, dirt.getDefaultMetallic(), 0.01f);
        assertTrue(dirt.getDefaultRoughness() >= 0.90f, "Dirt must have high roughness");
        assertEquals("block_dirt", dirt.getName());

        PbrMaterial leaves = MaterialRegistry.getMaterialForBlock("minecraft:block/oak_leaves");
        assertEquals(0.0f, leaves.getDefaultMetallic(), 0.01f);
        assertTrue(leaves.getDefaultRoughness() >= 0.75f, "Leaves must have matte foliage roughness");
        assertEquals("block_leaves", leaves.getName());

        // 4. Gemstones
        PbrMaterial diamond = MaterialRegistry.getMaterialForBlock("minecraft:block/diamond_block");
        assertTrue(diamond.getDefaultRoughness() <= 0.10f, "Diamond must have sharp gem glint");

        // 5. Emissives & Dynamic Lights
        PbrMaterial torch = MaterialRegistry.getMaterialForBlock("minecraft:block/torch");
        assertTrue(torch.getDefaultEmission() >= 0.9f, "Torch must have high emission");

        PbrMaterial redstone = MaterialRegistry.getMaterialForBlock("minecraft:block/redstone_wire");
        assertTrue(redstone.getDefaultEmission() >= 0.9f, "Redstone must be emissive");

        PbrMaterial sculk = MaterialRegistry.getMaterialForBlock("minecraft:block/sculk_sensor");
        assertTrue(sculk.getDefaultEmission() >= 0.8f, "Sculk must have emissive glow");

        PbrMaterial lava = MaterialRegistry.getMaterialForBlock("minecraft:block/lava");
        assertTrue(lava.getDefaultEmission() >= 0.9f, "Lava must have high emission");
    }

    @Test
    @DisplayName("Tier 2: Screen-Space Water Reflections (SSR) & Dynamic Coloured Light Configuration")
    void testWaterReflectionsAndDynamicLightConfig() {
        assertTrue(MCTraceConfig.enableWaterReflections, "Water SSR reflections & caustics should be enabled");
        assertTrue(MCTraceConfig.enableDynamicColoredLight, "Dynamic coloured block lighting should be enabled");

        PbrMaterial water = MaterialRegistry.getMaterialForBlock("minecraft:block/water_still");
        assertTrue(water.getDefaultRoughness() <= 0.05f, "Water surface must have ultra-low roughness for SSR");
    }

    // =========================================================================
    // Tier 3: Next-Gen Vulkan RT Architecture
    // =========================================================================

    @Test
    @DisplayName("Tier 3: Vulkan Acceleration Structures (BLAS & TLAS Generation)")
    void testVulkanAccelerationStructures() {
        // 1. Create and register chunk sections into BlasManager
        SectionPos pos1 = SectionPos.of(0, 4, 0);
        SectionPos pos2 = SectionPos.of(1, 4, 0);

        SectionGeometry geom1 = new SectionGeometry(pos1);
        SectionGeometry geom2 = new SectionGeometry(pos2);

        BlasManager.registerSection(pos1, geom1);
        BlasManager.registerSection(pos2, geom2);

        assertEquals(2, BlasManager.getBlasCount());
        assertEquals(2, BlasManager.getDirtyCount());

        // 2. Build BLAS device handles and addresses
        BlasManager.buildPendingBlases(null);
        assertEquals(0, BlasManager.getDirtyCount());

        BlasManager.SectionBlas blas1 = BlasManager.getSectionBlas(pos1.asLong());
        BlasManager.SectionBlas blas2 = BlasManager.getSectionBlas(pos2.asLong());
        assertNotNull(blas1);
        assertNotNull(blas2);
        assertNotEquals(0L, blas1.getDeviceAddress());
        assertNotEquals(0L, blas2.getDeviceAddress());
        assertFalse(blas1.isDirty());

        // 3. Build TLAS directly from active BLASes
        TlasManager.buildTlas(null, null);
        assertTrue(TlasManager.isTlasReady(), "TLAS must be ready when active instances are built");
        assertEquals(2, TlasManager.getActiveInstanceCount(), "TLAS should contain 2 section instances");
        assertNotEquals(0L, TlasManager.getTlasDeviceAddress());

        // 4. Test multi-frame dynamic updates (incremental section addition)
        SectionPos pos3 = SectionPos.of(2, 4, 0);
        SectionGeometry geom3 = new SectionGeometry(pos3);
        BlasManager.registerSection(pos3, geom3);
        assertEquals(1, BlasManager.getDirtyCount());

        TlasManager.buildTlas(null, null);
        assertEquals(3, BlasManager.getBlasCount());
        assertEquals(3, TlasManager.getActiveInstanceCount(), "TLAS should dynamically expand to 3 instances");

        // 5. Test SceneInstance 3x4 affine transform matrix
        TlasManager.SceneInstance instance = new TlasManager.SceneInstance(
                0xA50000000001L, 16.0f, 64.0f, 32.0f, 101, 0xFF
        );
        assertEquals(16.0f, instance.getPosX());
        assertEquals(64.0f, instance.getPosY());
        assertEquals(32.0f, instance.getPosZ());
        assertEquals(0xFF, instance.getMask());

        float[] matrix = instance.getTransformMatrix();
        assertEquals(12, matrix.length);
        // Row 0
        assertEquals(1.0f, matrix[0]);
        assertEquals(0.0f, matrix[1]);
        assertEquals(0.0f, matrix[2]);
        assertEquals(16.0f, matrix[3]);
        // Row 1
        assertEquals(0.0f, matrix[4]);
        assertEquals(1.0f, matrix[5]);
        assertEquals(0.0f, matrix[6]);
        assertEquals(64.0f, matrix[7]);
        // Row 2
        assertEquals(0.0f, matrix[8]);
        assertEquals(0.0f, matrix[9]);
        assertEquals(1.0f, matrix[10]);
        assertEquals(32.0f, matrix[11]);
    }

    @Test
    @DisplayName("Tier 3: Temporal FSR Motion Vector Buffering (Velocity Vector Pass)")
    void testVelocityPassComputation() {
        Vector2f velocity = new Vector2f();

        // 1. Uninitialized camera history produces zero velocity
        VelocityPass.computeVelocity(0.5f, 0.5f, 0.5f, velocity);
        assertEquals(0.0f, velocity.x, 0.0001f);
        assertEquals(0.0f, velocity.y, 0.0001f);

        // 2. Stationary camera across frames produces zero velocity
        Matrix4f view = new Matrix4f().lookAt(0, 0, 5, 0, 0, 0, 0, 1, 0);
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0), 16.0f / 9.0f, 0.1f, 1000.0f);
        Vec3 pos = new Vec3(0, 0, 0);

        CameraHistory.update(view, proj, pos); // Frame 1
        CameraHistory.update(view, proj, pos); // Frame 2 (identical)

        assertTrue(CameraHistory.isHistoryValid());
        VelocityPass.computeVelocity(0.5f, 0.5f, 0.5f, velocity);
        assertEquals(0.0f, velocity.x, 0.001f, "Stationary camera must yield zero horizontal velocity");
        assertEquals(0.0f, velocity.y, 0.001f, "Stationary camera must yield zero vertical velocity");

        // 3. Camera rotation produces valid non-zero motion vectors
        Matrix4f viewRotated = new Matrix4f().lookAt(0, 0, 5, 1, 0, 0, 0, 1, 0);
        CameraHistory.update(viewRotated, proj, pos); // Frame 3 (rotated camera)

        VelocityPass.computeVelocity(0.5f, 0.5f, 0.5f, velocity);
        assertNotEquals(0.0f, velocity.x, "Camera rotation must produce non-zero screen-space velocity");

        // 4. History reset request zeroes velocity
        CameraHistory.requestReset();
        assertFalse(CameraHistory.isHistoryValid());
        VelocityPass.computeVelocity(0.5f, 0.5f, 0.5f, velocity);
        assertEquals(0.0f, velocity.x, 0.0001f);
        assertEquals(0.0f, velocity.y, 0.0001f);
    }
}
