package net.mctrace.config;

public class MCTraceConfig {

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
}
