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

    // True HDR Display Configuration
    public static boolean enableHDR = true;
    public static float hdrPeakLuminance = 1000.0f; // Peak brightness in nits
    public static float hdrPaperWhite = 200.0f;    // Standard UI / paper white brightness in nits
    public static float hdrMinLuminance = 0.000f;   // Black level floor in nits (0.000 for OLED, 0.02-0.08 for LCD)
    public static float hdrMiddleGrayContrast = 1.0f; // Midtone contrast slope (0.8 - 1.5)
    public static boolean hdrCalibrated = false;    // Has the user completed display calibration
    public static boolean isHdrActive = false;     // True when swapchain is running in HDR mode

    // Advanced Quality & Shading Parameters
    public static SsaoIntensity ssaoIntensity = SsaoIntensity.STANDARD;
    public static RayQueryQuality rayQueryQuality = RayQueryQuality.BALANCED;

    public static class ConfigData {
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
        public float hdrPeakLuminance = 1000.0f;
        public float hdrPaperWhite = 200.0f;
        public float hdrMinLuminance = 0.000f;
        public float hdrMiddleGrayContrast = 1.0f;
        public boolean hdrCalibrated = false;
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
            data.hdrPeakLuminance = hdrPeakLuminance;
            data.hdrPaperWhite = hdrPaperWhite;
            data.hdrMinLuminance = hdrMinLuminance;
            data.hdrMiddleGrayContrast = hdrMiddleGrayContrast;
            data.hdrCalibrated = hdrCalibrated;
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
            Path path = FMLPaths.CONFIGDIR.get().resolve("mctrace.json");
            if (Files.exists(path)) {
                String json = Files.readString(path);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
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
                    if (data.hdrPeakLuminance > 0.0f) hdrPeakLuminance = data.hdrPeakLuminance;
                    if (data.hdrPaperWhite > 0.0f) hdrPaperWhite = data.hdrPaperWhite;
                    hdrMinLuminance = Math.max(0.0f, data.hdrMinLuminance);
                    if (data.hdrMiddleGrayContrast > 0.0f) hdrMiddleGrayContrast = data.hdrMiddleGrayContrast;
                    hdrCalibrated = data.hdrCalibrated;
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
