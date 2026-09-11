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
