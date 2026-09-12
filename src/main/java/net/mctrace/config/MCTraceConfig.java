package net.mctrace.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mctrace.MCTrace;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

public class MCTraceConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum RayTracingMode {
        DISABLED,
        RAY_QUERY_HYBRID,   // Fast inline compute ray queries (AO + Shadows + Reflections)
        FULL_PATH_TRACING   // Full recursive path tracer
    }

    public enum FsrQualityMode {
        OFF(1.0f),
        ULTRA_QUALITY(0.77f),
        QUALITY(0.67f),
        BALANCED(0.58f),
        PERFORMANCE(0.50f);

        private final float scale;

        FsrQualityMode(float scale) {
            this.scale = scale;
        }

        public float getScale() {
            return scale;
        }
    }

    public enum SsaoIntensity {
        OFF(0.0f, "Off"),
        SUBTLE(0.5f, "Subtle (50%)"),
        STANDARD(1.0f, "Standard (100%)"),
        ENHANCED(1.5f, "Enhanced (150%)");

        private final float multiplier;
        private final String displayName;

        SsaoIntensity(float multiplier, String displayName) {
            this.multiplier = multiplier;
            this.displayName = displayName;
        }

        public float getMultiplier() {
            return multiplier;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum QualityPreset {
        CUSTOM("Custom"),
        PERFORMANCE("Performance"),
        BALANCED("Balanced"),
        ULTRA_HDR("Ultra HDR / Cinematic");

        private final String displayName;

        QualityPreset(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RayQueryQuality {
        PERFORMANCE(1, 1.0f, "Performance"),
        BALANCED(2, 1.5f, "Balanced"),
        QUALITY(4, 2.5f, "Quality");

        private final int sampleCount;
        private final float radius;
        private final String displayName;

        RayQueryQuality(int sampleCount, float radius, String displayName) {
            this.sampleCount = sampleCount;
            this.radius = radius;
            this.displayName = displayName;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        public float getRadius() {
            return radius;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum AtmosphereMode {
        GOD_RAYS_AND_FOG("God Rays + Fog", true, true),
        GOD_RAYS_ONLY("God Rays Only", true, false),
        FOG_ONLY("Fog Only", false, true),
        OFF("Off", false, false);

        private final String displayName;
        private final boolean godRays;
        private final boolean fog;

        AtmosphereMode(String displayName, boolean godRays, boolean fog) {
            this.displayName = displayName;
            this.godRays = godRays;
            this.fog = fog;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean hasGodRays() {
            return godRays;
        }

        public boolean hasFog() {
            return fog;
        }

        public static AtmosphereMode fromConfig(boolean godRays, boolean fog) {
            if (godRays && fog) return GOD_RAYS_AND_FOG;
            if (godRays) return GOD_RAYS_ONLY;
            if (fog) return FOG_ONLY;
            return OFF;
        }
    }

    // Default configuration values
    public static boolean enableRayTracing = true;
    public static RayTracingMode rayTracingMode = RayTracingMode.RAY_QUERY_HYBRID;
    public static boolean enableFSR = true;
    public static FsrQualityMode fsrQualityMode = FsrQualityMode.QUALITY;
    public static boolean enableDenoiser = true;
    public static float fsrSharpness = 0.8f;
    public static int raysPerPixel = 1;
    public static int aoSampleCount = 2;
    public static float aoRadius = 1.5f;
    public static int maxBounces = 3;

    // True HDR & Display Configuration
    public static boolean enableHDR = true;
    public static float sceneBrightness = 1.15f;    // General scene brightness offset (0.70x - 1.60x)
    public static float hdrPeakLuminance = 456.0f;  // Peak brightness in nits (auto-calibrated for GS27U)
    public static float hdrPaperWhite = 200.0f;    // Standard UI / paper white brightness in nits
    public static float hdrMinLuminance = 0.000f;   // Black level floor in nits (0.000 for OLED, 0.02-0.08 for LCD)
    public static float hdrMiddleGrayContrast = 1.0f; // Midtone contrast slope (0.8 - 1.5)
    public static boolean hdrCalibrated = false;    // Has the user completed display calibration
    public static boolean isHdrActive = false;     // True when swapchain is running in HDR mode
    public static String activeFormatName = "SDR (8-bit)"; // Detected swapchain surface format name
    public static boolean enableWideGamut = true;   // DCI-P3 / BT.2020 Wide Color Gamut Expansion
    public static float wideGamutStrength = 1.0f;   // Wide Color Gamut intensity (0.0x - 1.5x)

    // Tier 2: World Shading & Material Realism
    public static boolean enablePbrMaterials = true;     // LabPBR Specular & Roughness
    public static boolean enableWaterReflections = true; // Screen-Space Reflections (SSR) & Caustics
    public static boolean enableDynamicColoredLight = true; // Dynamic Coloured Block Radiosity & Emissive Glow

    // Atmosphere & Volumetric Lighting
    public static boolean enableVolumetricFog = true;
    public static float volumetricFogDensity = 1.0f;
    public static boolean enableGodRays = true;
    public static float godRaysIntensity = 1.0f;

    // Weather & Dynamic PBR
    public static boolean enableRainWetness = true;

    // Dynamic Lighting
    public static boolean enableHeldDynamicLights = true;

    // Material Depth & POM
    public static boolean enableParallaxOcclusion = true;
    public static float pomDepth = 0.04f;

    // Cinematic Post-Processing
    public static boolean enableMotionBlur = false;
    public static float motionBlurStrength = 0.5f;
    public static boolean enableBokehDof = false;
    public static float dofFocalDistance = 0.0f;

    // Advanced Lighting & Foliage Realism
    public static boolean enableFoliageSss = true;          // Backlit Foliage Subsurface Scattering
    public static float foliageSssStrength = 1.0f;           // Foliage SSS Intensity multiplier
    public static boolean enableLabPbrTextures = true;      // Auto-hook community resource pack LabPBR textures (_n & _s)
    public static boolean enableRtShadows = true;           // Hardware TLAS Ray-Traced Direct Shadows & Penumbra
    public static String activeShaderPack = "internal";     // Active shader pack name ("internal" or custom pack in shaderpacks/)

    // =========================================================================
    // Next-Gen Expansion: 5 Major Pillars
    // =========================================================================
    // Pillar 1: Next-Gen Ray Tracing & Lighting
    public static boolean enableColoredShadows = true;      // Stained glass / tinted colored light transmission
    public static boolean enableRestirGi = true;            // ReSTIR Spatio-Temporal Resampled Multi-Bounce GI
    public static int restirSpatialSamples = 4;             // Spatial neighbor reservoir samples (1 to 8)
    public static boolean enableRefraction = true;          // Ray-traced refraction with chromatic dispersion

    // Pillar 2: Performance, Upscaling & Geometry
    public static boolean enableFrameGeneration = false;    // AMD FSR 3 Fluid Motion Frame Generation
    public static boolean enableDrs = false;                // Dynamic Resolution Scaling (Target FPS mode)
    public static int drsTargetFps = 120;                   // Target FPS for DRS adjustments
    public static boolean enableMeshShaders = true;         // GPU-driven meshlet LOD for distant terrain (VK_EXT_mesh_shader)

    // Pillar 3: Atmosphere, Weather & World Dynamics
    public static boolean enableVolumetricClouds = true;    // Ray-marched 3D Perlin-Worley volumetric clouds
    public static float cloudDensity = 1.0f;                // Cloud volume density multiplier
    public static boolean enablePhysicalSky = true;         // Bruneton / Nishita atmospheric scattering & twilight
    public static boolean enableDynamicSnow = true;         // Dynamic procedural snow accumulation on upward faces

    // Pillar 4: Ocean & Materials Realism
    public static boolean enableFftOcean = true;            // Compute Phillips spectrum FFT ocean waves & foam
    public static boolean enableMobSss = true;              // Subsurface scattering on players, mobs, candles, slime

    // Pillar 5: Creator & Player Tools
    public static boolean showGpuProfiler = false;          // Real-time Vulkan timestamp query profiler HUD overlay
    public static boolean photoModeActive = false;          // Cinematic Photo Mode active flag
    public static float photoAperture = 2.8f;               // Lens aperture (f-number: 1.2 to 16.0)
    public static float photoFocalDistance = 5.0f;          // Focal plane distance in meters
    public static float photoExposure = 1.0f;               // Exposure EV multiplier (0.5x to 2.0x)
    public static float photoFov = 70.0f;                   // Camera FOV in degrees (15 to 110)
    public static int photoBokehBlades = 7;                 // Number of aperture iris blades (0 = circle, 5, 7, 9)

    // Advanced Quality & Shading Parameters
    public static QualityPreset currentPreset = QualityPreset.BALANCED;
    public static SsaoIntensity ssaoIntensity = SsaoIntensity.STANDARD;
    public static RayQueryQuality rayQueryQuality = RayQueryQuality.BALANCED;

    public static void applyPreset(QualityPreset preset) {
        currentPreset = preset;
        switch (preset) {
            case PERFORMANCE -> {
                enableRayTracing = true;
                rayTracingMode = RayTracingMode.RAY_QUERY_HYBRID;
                rayQueryQuality = RayQueryQuality.PERFORMANCE;
                enableFSR = true;
                fsrQualityMode = FsrQualityMode.BALANCED;
                enableDenoiser = true;
                ssaoIntensity = SsaoIntensity.SUBTLE;
                enableVolumetricFog = true;
                volumetricFogDensity = 0.6f;
                enableGodRays = true;
                godRaysIntensity = 0.7f;
                enableRainWetness = true;
                enableHeldDynamicLights = true;
                enablePbrMaterials = true;
                enableWaterReflections = true;
                enableDynamicColoredLight = true;
                enableParallaxOcclusion = false;
                enableMotionBlur = false;
                enableBokehDof = false;
                enableFoliageSss = true;
                foliageSssStrength = 0.7f;
                enableLabPbrTextures = true;
                enableRtShadows = false;
                enableColoredShadows = true;
                enableRestirGi = false;
                enableRefraction = false;
                enableFrameGeneration = true;
                enableDrs = true;
                drsTargetFps = 120;
                enableMeshShaders = true;
                enableVolumetricClouds = false;
                enablePhysicalSky = true;
                enableDynamicSnow = true;
                enableFftOcean = false;
                enableMobSss = false;
            }
            case BALANCED -> {
                enableRayTracing = true;
                rayTracingMode = RayTracingMode.RAY_QUERY_HYBRID;
                rayQueryQuality = RayQueryQuality.BALANCED;
                enableFSR = true;
                fsrQualityMode = FsrQualityMode.QUALITY;
                enableDenoiser = true;
                ssaoIntensity = SsaoIntensity.STANDARD;
                enableVolumetricFog = true;
                volumetricFogDensity = 1.0f;
                enableGodRays = true;
                godRaysIntensity = 1.0f;
                enableRainWetness = true;
                enableHeldDynamicLights = true;
                enablePbrMaterials = true;
                enableWaterReflections = true;
                enableDynamicColoredLight = true;
                enableParallaxOcclusion = true;
                pomDepth = 0.04f;
                enableMotionBlur = false;
                enableBokehDof = false;
                enableFoliageSss = true;
                foliageSssStrength = 1.0f;
                enableLabPbrTextures = true;
                enableRtShadows = true;
                enableColoredShadows = true;
                enableRestirGi = true;
                restirSpatialSamples = 4;
                enableRefraction = true;
                enableFrameGeneration = false;
                enableDrs = false;
                enableMeshShaders = true;
                enableVolumetricClouds = true;
                cloudDensity = 1.0f;
                enablePhysicalSky = true;
                enableDynamicSnow = true;
                enableFftOcean = true;
                enableMobSss = true;
            }
            case ULTRA_HDR -> {
                enableRayTracing = true;
                rayTracingMode = RayTracingMode.RAY_QUERY_HYBRID;
                rayQueryQuality = RayQueryQuality.QUALITY;
                enableFSR = true;
                fsrQualityMode = FsrQualityMode.ULTRA_QUALITY;
                enableDenoiser = true;
                ssaoIntensity = SsaoIntensity.ENHANCED;
                enableVolumetricFog = true;
                volumetricFogDensity = 1.0f;
                enableGodRays = true;
                godRaysIntensity = 1.2f;
                enableRainWetness = true;
                enableHeldDynamicLights = true;
                enablePbrMaterials = true;
                enableWaterReflections = true;
                enableDynamicColoredLight = true;
                enableParallaxOcclusion = true;
                pomDepth = 0.06f;
                enableMotionBlur = false;
                motionBlurStrength = 0.5f;
                enableBokehDof = false;
                enableFoliageSss = true;
                foliageSssStrength = 1.25f;
                enableLabPbrTextures = true;
                enableRtShadows = true;
                enableColoredShadows = true;
                enableRestirGi = true;
                restirSpatialSamples = 6;
                enableRefraction = true;
                enableFrameGeneration = false;
                enableDrs = false;
                enableMeshShaders = true;
                enableVolumetricClouds = true;
                cloudDensity = 1.25f;
                enablePhysicalSky = true;
                enableDynamicSnow = true;
                enableFftOcean = true;
                enableMobSss = true;
            }
            case CUSTOM -> {}
        }
    }

    public static class ConfigData {
        public QualityPreset currentPreset = QualityPreset.BALANCED;
        public boolean enableRayTracing = true;
        public RayTracingMode rayTracingMode = RayTracingMode.RAY_QUERY_HYBRID;
        public boolean enableFSR = true;
        public FsrQualityMode fsrQualityMode = FsrQualityMode.QUALITY;
        public boolean enableDenoiser = true;
        public float fsrSharpness = 0.8f;
        public int raysPerPixel = 1;
        public int aoSampleCount = 2;
        public float aoRadius = 1.5f;
        public int maxBounces = 3;
        public boolean enableHDR = true;
        public float sceneBrightness = 1.15f;
        public float hdrPeakLuminance = 456.0f;
        public float hdrPaperWhite = 200.0f;
        public float hdrMinLuminance = 0.000f;
        public float hdrMiddleGrayContrast = 1.0f;
        public boolean hdrCalibrated = false;
        public boolean enableWideGamut = true;
        public float wideGamutStrength = 1.0f;
        public boolean enablePbrMaterials = true;
        public boolean enableWaterReflections = true;
        public boolean enableDynamicColoredLight = true;
        public boolean enableVolumetricFog = true;
        public float volumetricFogDensity = 1.0f;
        public boolean enableGodRays = true;
        public float godRaysIntensity = 1.0f;
        public boolean enableRainWetness = true;
        public boolean enableHeldDynamicLights = true;
        public boolean enableParallaxOcclusion = true;
        public float pomDepth = 0.04f;
        public boolean enableMotionBlur = false;
        public float motionBlurStrength = 0.5f;
        public boolean enableBokehDof = false;
        public float dofFocalDistance = 0.0f;
        public SsaoIntensity ssaoIntensity = SsaoIntensity.STANDARD;
        public RayQueryQuality rayQueryQuality = RayQueryQuality.BALANCED;
        public boolean enableFoliageSss = true;
        public float foliageSssStrength = 1.0f;
        public boolean enableLabPbrTextures = true;
        public boolean enableRtShadows = true;
        public String activeShaderPack = "internal";

        // Next-Gen Expansion Fields
        public boolean enableColoredShadows = true;
        public boolean enableRestirGi = true;
        public int restirSpatialSamples = 4;
        public boolean enableRefraction = true;
        public boolean enableFrameGeneration = false;
        public boolean enableDrs = false;
        public int drsTargetFps = 120;
        public boolean enableMeshShaders = true;
        public boolean enableVolumetricClouds = true;
        public float cloudDensity = 1.0f;
        public boolean enablePhysicalSky = true;
        public boolean enableDynamicSnow = true;
        public boolean enableFftOcean = true;
        public boolean enableMobSss = true;
        public boolean showGpuProfiler = false;
        public float photoAperture = 2.8f;
        public float photoFocalDistance = 5.0f;
        public float photoExposure = 1.0f;
        public float photoFov = 70.0f;
        public int photoBokehBlades = 7;
    }

    public static synchronized void save() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("mctrace.json");
            Files.createDirectories(path.getParent());
            ConfigData data = new ConfigData();
            data.enableRayTracing = enableRayTracing;
            data.rayTracingMode = rayTracingMode;
            data.enableFSR = enableFSR;
            data.fsrQualityMode = fsrQualityMode;
            data.enableDenoiser = enableDenoiser;
            data.fsrSharpness = fsrSharpness;
            data.raysPerPixel = raysPerPixel;
            data.aoSampleCount = aoSampleCount;
            data.aoRadius = aoRadius;
            data.maxBounces = maxBounces;
            data.enableHDR = enableHDR;
            data.sceneBrightness = sceneBrightness;
            data.hdrPeakLuminance = hdrPeakLuminance;
            data.hdrPaperWhite = hdrPaperWhite;
            data.hdrMinLuminance = hdrMinLuminance;
            data.hdrMiddleGrayContrast = hdrMiddleGrayContrast;
            data.hdrCalibrated = hdrCalibrated;
            data.enableWideGamut = enableWideGamut;
            data.wideGamutStrength = wideGamutStrength;
            data.enablePbrMaterials = enablePbrMaterials;
            data.enableWaterReflections = enableWaterReflections;
            data.enableDynamicColoredLight = enableDynamicColoredLight;
            data.currentPreset = currentPreset;
            data.enableVolumetricFog = enableVolumetricFog;
            data.volumetricFogDensity = volumetricFogDensity;
            data.enableGodRays = enableGodRays;
            data.godRaysIntensity = godRaysIntensity;
            data.enableRainWetness = enableRainWetness;
            data.enableHeldDynamicLights = enableHeldDynamicLights;
            data.enableParallaxOcclusion = enableParallaxOcclusion;
            data.pomDepth = pomDepth;
            data.enableMotionBlur = enableMotionBlur;
            data.motionBlurStrength = motionBlurStrength;
            data.enableBokehDof = enableBokehDof;
            data.dofFocalDistance = dofFocalDistance;
            data.ssaoIntensity = ssaoIntensity;
            data.rayQueryQuality = rayQueryQuality;
            data.enableFoliageSss = enableFoliageSss;
            data.foliageSssStrength = foliageSssStrength;
            data.enableLabPbrTextures = enableLabPbrTextures;
            data.enableRtShadows = enableRtShadows;
            data.activeShaderPack = activeShaderPack;

            data.enableColoredShadows = enableColoredShadows;
            data.enableRestirGi = enableRestirGi;
            data.restirSpatialSamples = restirSpatialSamples;
            data.enableRefraction = enableRefraction;
            data.enableFrameGeneration = enableFrameGeneration;
            data.enableDrs = enableDrs;
            data.drsTargetFps = drsTargetFps;
            data.enableMeshShaders = enableMeshShaders;
            data.enableVolumetricClouds = enableVolumetricClouds;
            data.cloudDensity = cloudDensity;
            data.enablePhysicalSky = enablePhysicalSky;
            data.enableDynamicSnow = enableDynamicSnow;
            data.enableFftOcean = enableFftOcean;
            data.enableMobSss = enableMobSss;
            data.showGpuProfiler = showGpuProfiler;
            data.photoAperture = photoAperture;
            data.photoFocalDistance = photoFocalDistance;
            data.photoExposure = photoExposure;
            data.photoFov = photoFov;
            data.photoBokehBlades = photoBokehBlades;

            Files.writeString(path, GSON.toJson(data));
            MCTrace.LOGGER.info("[MCTrace] Saved configuration to {}", path);
        } catch (Exception e) {
            MCTrace.LOGGER.error("[MCTrace] Failed to save configuration: {}", e.getMessage());
        }
    }

    public static synchronized void load() {
        try {
            net.mctrace.vulkan.hdr.DisplayHdrSync.queryMonitorCapabilitiesAsync();
            Path path = FMLPaths.CONFIGDIR.get().resolve("mctrace.json");
            if (Files.exists(path)) {
                String json = Files.readString(path);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    if (data.currentPreset != null) currentPreset = data.currentPreset;
                    enableRayTracing = data.enableRayTracing;
                    if (data.rayTracingMode != null) rayTracingMode = data.rayTracingMode;
                    enableFSR = data.enableFSR;
                    if (data.fsrQualityMode != null) fsrQualityMode = data.fsrQualityMode;
                    enableDenoiser = data.enableDenoiser;
                    fsrSharpness = data.fsrSharpness;
                    raysPerPixel = data.raysPerPixel;
                    aoSampleCount = data.aoSampleCount;
                    aoRadius = data.aoRadius;
                    maxBounces = data.maxBounces;
                    enableHDR = data.enableHDR;
                    if (data.sceneBrightness > 0.0f) sceneBrightness = data.sceneBrightness;
                    if (data.hdrPeakLuminance > 0.0f) hdrPeakLuminance = data.hdrPeakLuminance;
                    if (data.hdrPaperWhite > 0.0f) hdrPaperWhite = data.hdrPaperWhite;
                    hdrMinLuminance = Math.max(0.0f, data.hdrMinLuminance);
                    if (data.hdrMiddleGrayContrast > 0.0f) hdrMiddleGrayContrast = data.hdrMiddleGrayContrast;
                    hdrCalibrated = data.hdrCalibrated;
                    enableWideGamut = data.enableWideGamut;
                    if (data.wideGamutStrength > 0.0f) wideGamutStrength = data.wideGamutStrength;
                    enablePbrMaterials = data.enablePbrMaterials;
                    enableWaterReflections = data.enableWaterReflections;
                    enableDynamicColoredLight = data.enableDynamicColoredLight;
                    enableVolumetricFog = data.enableVolumetricFog;
                    if (data.volumetricFogDensity > 0.0f) volumetricFogDensity = data.volumetricFogDensity;
                    enableGodRays = data.enableGodRays;
                    if (data.godRaysIntensity > 0.0f) godRaysIntensity = data.godRaysIntensity;
                    enableRainWetness = data.enableRainWetness;
                    enableHeldDynamicLights = data.enableHeldDynamicLights;
                    enableParallaxOcclusion = data.enableParallaxOcclusion;
                    if (data.pomDepth > 0.0f) pomDepth = data.pomDepth;
                    enableMotionBlur = data.enableMotionBlur;
                    if (data.motionBlurStrength > 0.0f) motionBlurStrength = data.motionBlurStrength;
                    enableBokehDof = data.enableBokehDof;
                    dofFocalDistance = data.dofFocalDistance;
                    if (data.ssaoIntensity != null) ssaoIntensity = data.ssaoIntensity;
                    if (data.rayQueryQuality != null) rayQueryQuality = data.rayQueryQuality;
                    enableFoliageSss = data.enableFoliageSss;
                    if (data.foliageSssStrength > 0.0f) foliageSssStrength = data.foliageSssStrength;
                    enableLabPbrTextures = data.enableLabPbrTextures;
                    enableRtShadows = data.enableRtShadows;
                    if (data.activeShaderPack != null) {
                        activeShaderPack = data.activeShaderPack;
                        net.mctrace.vulkan.shader.ShaderPackLoader.setActiveShaderPackName(activeShaderPack);
                    }

                    enableColoredShadows = data.enableColoredShadows;
                    enableRestirGi = data.enableRestirGi;
                    if (data.restirSpatialSamples > 0) restirSpatialSamples = data.restirSpatialSamples;
                    enableRefraction = data.enableRefraction;
                    enableFrameGeneration = data.enableFrameGeneration;
                    enableDrs = data.enableDrs;
                    if (data.drsTargetFps > 0) drsTargetFps = data.drsTargetFps;
                    enableMeshShaders = data.enableMeshShaders;
                    enableVolumetricClouds = data.enableVolumetricClouds;
                    if (data.cloudDensity > 0.0f) cloudDensity = data.cloudDensity;
                    enablePhysicalSky = data.enablePhysicalSky;
                    enableDynamicSnow = data.enableDynamicSnow;
                    enableFftOcean = data.enableFftOcean;
                    enableMobSss = data.enableMobSss;
                    showGpuProfiler = data.showGpuProfiler;
                    if (data.photoAperture > 0.0f) photoAperture = data.photoAperture;
                    if (data.photoFocalDistance > 0.0f) photoFocalDistance = data.photoFocalDistance;
                    if (data.photoExposure > 0.0f) photoExposure = data.photoExposure;
                    if (data.photoFov > 0.0f) photoFov = data.photoFov;
                    if (data.photoBokehBlades >= 0) photoBokehBlades = data.photoBokehBlades;

                    MCTrace.LOGGER.info("[MCTrace] Loaded configuration from {}", path);
                }
            } else {
                save(); // Write default configuration
            }
        } catch (Exception e) {
            MCTrace.LOGGER.error("[MCTrace] Failed to load configuration: {}", e.getMessage());
        }
    }
}
