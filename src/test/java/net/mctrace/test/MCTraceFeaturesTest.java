package net.mctrace.test;

import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.VelocityPass;
import net.mctrace.vulkan.hdr.DisplayHdrSync;
import net.mctrace.vulkan.pbr.LabPbrTextureHook;
import net.mctrace.vulkan.pbr.MaterialRegistry;
import net.mctrace.vulkan.pbr.PbrMaterial;
import net.mctrace.vulkan.rt.BlasManager;
import net.mctrace.vulkan.rt.EntityBlasManager;
import net.mctrace.vulkan.rt.SectionGeometry;
import net.mctrace.vulkan.rt.TlasManager;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import net.mctrace.vulkan.shader.ShaderPackLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        assertEquals(0.0f, lava.getDefaultMetallic(), 0.01f, "Molten lava is non-metallic");

        PbrMaterial lavaFlowing = MaterialRegistry.getMaterialForBlock("minecraft:block/lava_flowing");
        assertTrue(lavaFlowing.getDefaultEmission() >= 0.9f, "Flowing lava must have high emission");
        assertEquals(0.0f, lavaFlowing.getDefaultMetallic(), 0.01f, "Flowing lava is non-metallic");
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

    // =========================================================================
    // Category 1 & Category 4: Quality Presets & Atmosphere
    // =========================================================================

    @Test
    @DisplayName("Next-Gen: 1-Click Graphics & RT Quality Presets")
    void testQualityPresets() {
        // 1. Performance preset
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.PERFORMANCE);
        assertEquals(MCTraceConfig.QualityPreset.PERFORMANCE, MCTraceConfig.currentPreset);
        assertEquals(MCTraceConfig.RayQueryQuality.PERFORMANCE, MCTraceConfig.rayQueryQuality);
        assertEquals(MCTraceConfig.SsaoIntensity.SUBTLE, MCTraceConfig.ssaoIntensity);
        assertEquals(MCTraceConfig.FsrQualityMode.BALANCED, MCTraceConfig.fsrQualityMode);
        assertFalse(MCTraceConfig.enableParallaxOcclusion);
        assertFalse(MCTraceConfig.enableBokehDof);

        // 2. Balanced preset
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.BALANCED);
        assertEquals(MCTraceConfig.QualityPreset.BALANCED, MCTraceConfig.currentPreset);
        assertEquals(MCTraceConfig.RayQueryQuality.BALANCED, MCTraceConfig.rayQueryQuality);
        assertEquals(MCTraceConfig.SsaoIntensity.STANDARD, MCTraceConfig.ssaoIntensity);
        assertEquals(MCTraceConfig.FsrQualityMode.QUALITY, MCTraceConfig.fsrQualityMode);
        assertTrue(MCTraceConfig.enableVolumetricFog);
        assertTrue(MCTraceConfig.enableGodRays);
        assertTrue(MCTraceConfig.enableRainWetness);
        assertTrue(MCTraceConfig.enableHeldDynamicLights);
        assertTrue(MCTraceConfig.enableParallaxOcclusion);

        // 3. Ultra HDR / Cinematic preset
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.ULTRA_HDR);
        assertEquals(MCTraceConfig.QualityPreset.ULTRA_HDR, MCTraceConfig.currentPreset);
        assertEquals(MCTraceConfig.RayQueryQuality.QUALITY, MCTraceConfig.rayQueryQuality);
        assertEquals(MCTraceConfig.SsaoIntensity.ENHANCED, MCTraceConfig.ssaoIntensity);
        assertEquals(MCTraceConfig.FsrQualityMode.ULTRA_QUALITY, MCTraceConfig.fsrQualityMode);
        assertFalse(MCTraceConfig.enableMotionBlur, "Motion blur is kept off in presets to prevent ghosting");
        assertFalse(MCTraceConfig.enableBokehDof, "Bokeh DoF is kept off in presets to prevent background starburst duplicates");
        assertTrue(MCTraceConfig.enableParallaxOcclusion);
        assertTrue(MCTraceConfig.pomDepth >= 0.05f);

        // Reset to Balanced
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.BALANCED);
    }

    @Test
    @DisplayName("Category 1: Volumetric Fog, God Rays & Rain Wetness Weather PBR")
    void testWeatherAndAtmosphereFeatures() {
        assertTrue(MCTraceConfig.enableVolumetricFog, "Volumetric fog should be enabled by default");
        assertTrue(MCTraceConfig.enableGodRays, "God rays should be enabled by default");
        assertTrue(MCTraceConfig.enableRainWetness, "Rain wetness and puddle accumulation should be enabled by default");
        assertEquals(1.0f, MCTraceConfig.volumetricFogDensity, 0.01f);
        assertEquals(1.0f, MCTraceConfig.godRaysIntensity, 0.01f);

        // Verify config data serialization
        MCTraceConfig.ConfigData data = new MCTraceConfig.ConfigData();
        assertTrue(data.enableVolumetricFog);
        assertTrue(data.enableGodRays);
        assertTrue(data.enableRainWetness);

        // Verify AtmosphereMode enum mappings
        assertEquals(MCTraceConfig.AtmosphereMode.GOD_RAYS_AND_FOG, MCTraceConfig.AtmosphereMode.fromConfig(true, true));
        assertEquals(MCTraceConfig.AtmosphereMode.GOD_RAYS_ONLY, MCTraceConfig.AtmosphereMode.fromConfig(true, false));
        assertEquals(MCTraceConfig.AtmosphereMode.FOG_ONLY, MCTraceConfig.AtmosphereMode.fromConfig(false, true));
        assertEquals(MCTraceConfig.AtmosphereMode.OFF, MCTraceConfig.AtmosphereMode.fromConfig(false, false));
        assertTrue(MCTraceConfig.AtmosphereMode.GOD_RAYS_ONLY.hasGodRays());
        assertFalse(MCTraceConfig.AtmosphereMode.GOD_RAYS_ONLY.hasFog());
    }

    @Test
    @DisplayName("Category 1: Hand-Held Dynamic Lights Configuration")
    void testHeldDynamicLightsConfig() {
        assertTrue(MCTraceConfig.enableHeldDynamicLights, "Hand-held dynamic lights should be enabled by default");
    }

    // =========================================================================
    // Category 2: LabPBR 1.3 & Parallax Occlusion Mapping
    // =========================================================================

    @Test
    @DisplayName("Category 2: LabPBR 1.3 Specular Channel Decoding & POM Depth")
    void testLabPbr13DecoderAndPomRelief() {
        // Test LabPBR decoder:
        // R = 200 (Smoothness ~0.784 => Roughness ~0.216)
        // G = 240 (Metallic => (240 - 230) / 25 = 0.40)
        // B = 50  (Porosity ~0.196)
        // A = 255 (Emission 1.0)
        MaterialRegistry.LabPbrDecoded decoded = new MaterialRegistry.LabPbrDecoded(200, 240, 50, 255);
        assertEquals(0.216f, decoded.roughness, 0.01f);
        assertEquals(0.40f, decoded.metallic, 0.01f);
        assertEquals(1.0f, decoded.f0, 0.01f);
        assertEquals(1.0f, decoded.emission, 0.01f);

        // Dielectric test: G = 128
        MaterialRegistry.LabPbrDecoded dielectric = new MaterialRegistry.LabPbrDecoded(100, 128, 0, 0);
        assertEquals(0.0f, dielectric.metallic, 0.01f);
        assertEquals(128.0f / 255.0f, dielectric.f0, 0.01f);
        assertEquals(0.0f, dielectric.emission, 0.01f);

        // POM Depth profiling for relief surfaces
        assertTrue(MaterialRegistry.getPomDepthForBlock("minecraft:block/stone_bricks") > 0.05f, "Stone bricks must have high POM relief");
        assertTrue(MaterialRegistry.getPomDepthForBlock("minecraft:block/cobblestone") > 0.05f, "Cobblestone must have high POM relief");
        assertTrue(MaterialRegistry.getPomDepthForBlock("minecraft:block/oak_planks") > 0.03f, "Planks must have POM relief");
        assertEquals(0.0f, MaterialRegistry.getPomDepthForBlock("minecraft:block/glass"), 0.001f, "Glass has zero POM relief");
        assertEquals(0.0f, MaterialRegistry.getPomDepthForBlock("minecraft:block/smooth_stone"), 0.001f, "Smooth stone has zero POM relief");
    }

    // =========================================================================
    // Category 3: Dynamic Entity BLAS & TLAS
    // =========================================================================

    @Test
    @DisplayName("Category 3: Dynamic Entity BLAS Tracking & TLAS Scene Instances")
    void testEntityAccelerationStructures() {
        EntityBlasManager.clear();
        assertEquals(0, EntityBlasManager.getActiveEntityCount());

        // 1. Register player entity
        EntityBlasManager.updateEntity(1001, "minecraft:player", 10.5, 64.0, -20.5, 90.0f, 0.6f, 1.8f);
        // 2. Register zombie entity
        EntityBlasManager.updateEntity(1002, "minecraft:zombie", 14.0, 64.0, -18.0, 45.0f, 0.6f, 1.9f);

        assertEquals(2, EntityBlasManager.getActiveEntityCount());

        List<TlasManager.SceneInstance> instances = EntityBlasManager.buildEntityInstances();
        assertEquals(2, instances.size());

        TlasManager.SceneInstance playerInst = instances.get(0);
        assertEquals(10.5f, playerInst.getPosX(), 0.01f);
        assertEquals(64.0f, playerInst.getPosY(), 0.01f);
        assertEquals(-20.5f, playerInst.getPosZ(), 0.01f);
        assertTrue((playerInst.getCustomIndex() & 0x800000) != 0, "Entity custom index must have high bit set");

        // 3. Entity removal
        EntityBlasManager.removeEntity(1002);
        assertEquals(1, EntityBlasManager.getActiveEntityCount());

        EntityBlasManager.clear();
        assertEquals(0, EntityBlasManager.getActiveEntityCount());
    }

    // =========================================================================
    // Category 4: Cinematic Motion Blur & Bokeh DoF
    // =========================================================================

    @Test
    @DisplayName("Category 4: Cinematic Polish (Motion Blur & Optical Bokeh DoF)")
    void testCinematicPolishConfig() {
        assertFalse(MCTraceConfig.enableMotionBlur, "Motion blur is disabled by default for competitive clarity");
        assertFalse(MCTraceConfig.enableBokehDof, "Bokeh DoF is disabled by default until aimed or enabled");
        assertEquals(0.5f, MCTraceConfig.motionBlurStrength, 0.01f);
        assertEquals(0.0f, MCTraceConfig.dofFocalDistance, 0.01f, "Default focal distance is auto-focus (0.0)");

        MCTraceConfig.enableMotionBlur = true;
        MCTraceConfig.enableBokehDof = true;
        MCTraceConfig.ConfigData data = new MCTraceConfig.ConfigData();
        data.enableMotionBlur = true;
        data.enableBokehDof = true;
        assertTrue(data.enableMotionBlur);
        assertTrue(data.enableBokehDof);

        // Reset
        MCTraceConfig.enableMotionBlur = false;
        MCTraceConfig.enableBokehDof = false;
    }

    // =========================================================================
    // Category 5: LabPBR Resource Pack Hooking, Foliage SSS & RT Shadows
    // =========================================================================

    @Test
    @DisplayName("Category 5: Resource Pack LabPBR Texture Hooking (_n.png & _s.png)")
    void testResourcePackLabPbrTextureHooking() {
        LabPbrTextureHook.reset();
        assertFalse(LabPbrTextureHook.isLabPbrActive());

        // 1. Create mock normal map with height variation in alpha channel
        BufferedImage normImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // Height relief: alpha varies from 50 (crevice) to 250 (surface)
                int a = (x + y < 16) ? 50 : 250;
                int r = 128; // tangent X
                int g = 128; // tangent Y
                int b = 255; // tangent Z
                normImg.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        // 2. Create mock specular map: Smoothness = 200, Metallic = 240, Porosity = 180, Emission = 60
        BufferedImage specImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int specArgb = (60 << 24) | (200 << 16) | (240 << 8) | 180;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                specImg.setRGB(x, y, specArgb);
            }
        }

        // 3. Register custom texture pair into MaterialRegistry
        String blockId = "minecraft:test_chiseled_obsidian";
        PbrMaterial customMat = LabPbrTextureHook.registerFromImages(blockId, normImg, specImg);
        assertNotNull(customMat);
        assertTrue(LabPbrTextureHook.isLabPbrActive());
        assertTrue(LabPbrTextureHook.getDiscoveredTextureCount() >= 1);

        // Verify decoded physical properties:
        // Smoothness 200 -> Roughness = 1.0 - 200/255 = 0.215
        assertEquals(0.215f, customMat.getDefaultRoughness(), 0.05f);
        // G 240 >= 230 -> Metallic = (240 - 230) / 25 = 0.40
        assertEquals(0.40f, customMat.getDefaultMetallic(), 0.05f);
        // B 180 -> Porosity = 180 / 255 = 0.705
        assertEquals(0.705f, customMat.getPorosity(), 0.05f);
        // A 60 -> Emission = 60 / 255 = 0.235
        assertEquals(0.235f, customMat.getDefaultEmission(), 0.05f);
        // Normal height variance (250 - 50 = 200) -> POM depth > 0.04
        assertTrue(customMat.getPomDepth() > 0.04f, "Normal height map should produce POM depth");

        // 4. Verify MaterialRegistry lookup prioritizes custom hooked material
        PbrMaterial resolved = MaterialRegistry.getMaterialForBlock(blockId);
        assertSame(customMat, resolved, "MaterialRegistry must prioritize custom LabPBR pack materials");

        float pomDepth = MaterialRegistry.getPomDepthForBlock(blockId);
        assertEquals(customMat.getPomDepth(), pomDepth, 0.001f);
    }

    @Test
    @DisplayName("Category 5: Foliage Translucency & Subsurface Scattering (SSS) Configuration")
    void testFoliageSubsurfaceScatteringConfig() {
        assertTrue(MCTraceConfig.enableFoliageSss, "Foliage SSS should be enabled by default");
        assertEquals(1.0f, MCTraceConfig.foliageSssStrength, 0.01f);

        // Presets
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.PERFORMANCE);
        assertTrue(MCTraceConfig.enableFoliageSss);
        assertEquals(0.7f, MCTraceConfig.foliageSssStrength, 0.01f);

        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.BALANCED);
        assertTrue(MCTraceConfig.enableFoliageSss);
        assertEquals(1.0f, MCTraceConfig.foliageSssStrength, 0.01f);

        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.ULTRA_HDR);
        assertTrue(MCTraceConfig.enableFoliageSss);
        assertEquals(1.25f, MCTraceConfig.foliageSssStrength, 0.01f);

        // Serialization
        MCTraceConfig.ConfigData data = new MCTraceConfig.ConfigData();
        assertTrue(data.enableFoliageSss);
        assertEquals(1.0f, data.foliageSssStrength, 0.01f);
    }

    @Test
    @DisplayName("Category 5: Hardware Ray-Traced Direct Shadows via TLAS Configuration")
    void testHardwareRayTracedShadowsConfig() {
        assertTrue(MCTraceConfig.enableRtShadows, "Hardware RT shadows should be enabled by default");

        // Presets: Performance disables heavy RT shadows, Balanced & Ultra enable it
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.PERFORMANCE);
        assertFalse(MCTraceConfig.enableRtShadows, "Performance preset uses screen-space contact shadows only");

        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.BALANCED);
        assertTrue(MCTraceConfig.enableRtShadows, "Balanced preset enables full RT shadows");

        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.ULTRA_HDR);
        assertTrue(MCTraceConfig.enableRtShadows, "Ultra HDR preset enables full RT shadows");

        // Reset to Balanced
        MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.BALANCED);
    }

    // =========================================================================
    // Custom Shader Pack Loader & Hot-Reloading
    // =========================================================================

    @Test
    @DisplayName("Custom Shader Pack: Discovery of Folders and .zip Archives")
    void testShaderPackDiscovery() throws Exception {
        Path tempPacksDir = Files.createTempDirectory("mctrace_test_packs_disc");
        try {
            ShaderPackLoader.setCustomPacksDirectory(tempPacksDir);

            // Verify internal pack is always present
            List<String> packs = ShaderPackLoader.listAvailablePacks();
            assertTrue(packs.contains(ShaderPackLoader.INTERNAL_PACK));

            // Create a folder pack
            Path folderPack = tempPacksDir.resolve("MyVulkanPack");
            Files.createDirectories(folderPack.resolve("shaders"));

            // Create a .zip pack
            Path zipPack = tempPacksDir.resolve("CrispShaders.zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPack.toFile()))) {
                zos.putNextEntry(new ZipEntry("shaders/world.fsh"));
                zos.write("// Zip shader".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // Also create a non-pack file (should be ignored)
            Files.writeString(tempPacksDir.resolve("readme.txt"), "Ignore me");

            List<String> updatedPacks = ShaderPackLoader.listAvailablePacks();
            assertEquals(3, updatedPacks.size(), "Should contain internal + 1 folder + 1 zip");
            assertTrue(updatedPacks.contains(ShaderPackLoader.INTERNAL_PACK));
            assertTrue(updatedPacks.contains("MyVulkanPack"));
            assertTrue(updatedPacks.contains("CrispShaders.zip"));
            assertFalse(updatedPacks.contains("readme.txt"));
        } finally {
            ShaderPackLoader.setCustomPacksDirectory(null);
            ShaderPackLoader.setActiveShaderPackName(ShaderPackLoader.INTERNAL_PACK);
        }
    }

    @Test
    @DisplayName("Custom Shader Pack: Selective Overriding & Fallback Precedence")
    void testShaderPackOverridePrecedence() throws Exception {
        Path tempPacksDir = Files.createTempDirectory("mctrace_test_packs_prec");
        try {
            ShaderPackLoader.setCustomPacksDirectory(tempPacksDir);

            Path packFolder = tempPacksDir.resolve("WarmLuminance");
            Path shadersDir = packFolder.resolve("shaders");
            Files.createDirectories(shadersDir);

            String customWorldFsh = "#version 330\n// Custom World Shading\nvoid main() {}";
            Files.writeString(shadersDir.resolve("world.fsh"), customWorldFsh);

            // Initially internal pack: no overrides
            ShaderPackLoader.setActiveShaderPackName(ShaderPackLoader.INTERNAL_PACK);
            assertFalse(ShaderPackLoader.hasShaderOverride("world.fsh"));
            assertFalse(ShaderPackLoader.hasShaderOverride("shaders/post/world.fsh"));
            assertNull(ShaderPackLoader.getShaderSource("world.fsh"));

            // Activate WarmLuminance pack
            ShaderPackLoader.setActiveShaderPackName("WarmLuminance");
            assertEquals("WarmLuminance", ShaderPackLoader.getActiveShaderPackName());
            assertEquals("WarmLuminance", MCTraceConfig.activeShaderPack);

            // Should override world.fsh under all path variations
            assertTrue(ShaderPackLoader.hasShaderOverride("world.fsh"));
            assertTrue(ShaderPackLoader.hasShaderOverride("shaders/world.fsh"));
            assertTrue(ShaderPackLoader.hasShaderOverride("shaders/post/world.fsh"));
            assertTrue(ShaderPackLoader.hasShaderOverride("/assets/mctrace/shaders/post/world.fsh"));

            String loadedSource = ShaderPackLoader.getShaderSource("shaders/post/world.fsh");
            assertNotNull(loadedSource);
            assertTrue(loadedSource.contains("Custom World Shading"));

            // Selective fallback: composite.fsh was not provided, so returns null / false
            assertFalse(ShaderPackLoader.hasShaderOverride("composite.fsh"));
            assertFalse(ShaderPackLoader.hasShaderOverride("shaders/post/composite.fsh"));
            assertNull(ShaderPackLoader.getShaderSource("composite.fsh"));
        } finally {
            ShaderPackLoader.setCustomPacksDirectory(null);
            ShaderPackLoader.setActiveShaderPackName(ShaderPackLoader.INTERNAL_PACK);
        }
    }

    @Test
    @DisplayName("Custom Shader Pack: In-Memory .zip Archive Stream Reading")
    void testShaderPackZipReading() throws Exception {
        Path tempPacksDir = Files.createTempDirectory("mctrace_test_packs_zip");
        try {
            ShaderPackLoader.setCustomPacksDirectory(tempPacksDir);

            Path zipPack = tempPacksDir.resolve("VulkanUltra.zip");
            String compShader = "#version 460\n// Custom Ray Query Compute\nvoid main() {}";
            String fragShader = "#version 330\n// Custom Composite Presentation\nvoid main() {}";

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPack.toFile()))) {
                zos.putNextEntry(new ZipEntry("shaders/rayquery.comp"));
                zos.write(compShader.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("shaders/post/composite.fsh"));
                zos.write(fragShader.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            ShaderPackLoader.setActiveShaderPackName("VulkanUltra.zip");

            assertTrue(ShaderPackLoader.hasShaderOverride("rayquery.comp"));
            String loadedComp = ShaderPackLoader.getShaderSource("rayquery.comp");
            assertNotNull(loadedComp);
            assertTrue(loadedComp.contains("Custom Ray Query Compute"));

            assertTrue(ShaderPackLoader.hasShaderOverride("composite.fsh"));
            String loadedFrag = ShaderPackLoader.getShaderSource("composite.fsh");
            assertNotNull(loadedFrag);
            assertTrue(loadedFrag.contains("Custom Composite Presentation"));

            // world.fsh not in zip
            assertFalse(ShaderPackLoader.hasShaderOverride("world.fsh"));
            assertNull(ShaderPackLoader.getShaderSource("world.fsh"));
        } finally {
            ShaderPackLoader.setCustomPacksDirectory(null);
            ShaderPackLoader.setActiveShaderPackName(ShaderPackLoader.INTERNAL_PACK);
        }
    }

    @Test
    @DisplayName("Custom Shader Pack: Config Persistence & Post-Chain Invalidation Hook")
    void testShaderPackConfigAndInvalidator() {
        boolean[] invalidatorCalled = new boolean[]{false};
        ShaderPackLoader.setPostChainInvalidator(() -> invalidatorCalled[0] = true);

        ShaderPackLoader.setActiveShaderPackName("NeonGlowPack");
        assertEquals("NeonGlowPack", MCTraceConfig.activeShaderPack);
        assertTrue(invalidatorCalled[0], "Post-chain invalidator must be triggered when switching shader packs");

        // Test ConfigData default and serialization
        MCTraceConfig.ConfigData data = new MCTraceConfig.ConfigData();
        assertEquals("internal", data.activeShaderPack);
        data.activeShaderPack = MCTraceConfig.activeShaderPack;
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(data);
        assertTrue(json.contains("NeonGlowPack"));
        MCTraceConfig.ConfigData deserialized = gson.fromJson(json, MCTraceConfig.ConfigData.class);
        assertEquals("NeonGlowPack", deserialized.activeShaderPack);

        // Reset
        ShaderPackLoader.setActiveShaderPackName(ShaderPackLoader.INTERNAL_PACK);
        assertEquals(ShaderPackLoader.INTERNAL_PACK, MCTraceConfig.activeShaderPack);
        ShaderPackLoader.setPostChainInvalidator(null);
    }
}
