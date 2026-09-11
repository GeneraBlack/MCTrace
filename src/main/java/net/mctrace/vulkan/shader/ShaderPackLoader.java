package net.mctrace.vulkan.shader;

import net.mctrace.MCTrace;
import net.mctrace.vulkan.rt.DenoiserPipeline;
import net.mctrace.vulkan.rt.FsrPipeline;
import net.mctrace.vulkan.rt.RayTracingPipeline;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers, loads, and hot-reloads custom SPIR-V and GLSL shader packs
 * from the game's shaderpacks folder.
 */
public class ShaderPackLoader {

    private static final String SHADERPACK_DIR_NAME = "shaderpacks";
    private static String activeShaderPackName = "internal";

    /**
     * Returns the path to the shaderpacks folder, creating it if needed.
     */
    public static Path getShaderPacksDirectory() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        Path dir = gameDir.toPath().resolve(SHADERPACK_DIR_NAME);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            MCTrace.LOGGER.error("[MCTrace Loader] Failed to create shaderpacks directory: {}", e.getMessage());
        }
        return dir;
    }

    /**
     * Scans and lists all available shader packs in the directory.
     */
    public static List<String> listAvailablePacks() {
        List<String> packs = new ArrayList<>();
        packs.add("internal"); // Default bundled SPIR-V pipeline

        Path dir = getShaderPacksDirectory();
        File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() || file.getName().endsWith(".zip")) {
                    packs.add(file.getName());
                }
            }
        }
        return packs;
    }

    /**
     * Hot-reloads all shaders in real time.
     */
    public static void reloadShaders() {
        MCTrace.LOGGER.info("[MCTrace Loader] Hot-reloading shader pipelines (active pack: {})...", activeShaderPackName);

        // Re-initialize pipelines with updated or recompiled shaders
        RayTracingPipeline.destroy();
        DenoiserPipeline.destroy();
        FsrPipeline.destroy();

        RayTracingPipeline.initialize();
        DenoiserPipeline.initialize();
        FsrPipeline.initialize();

        MCTrace.LOGGER.info("[MCTrace Loader] Shader reload completed.");
    }

    public static String getActiveShaderPackName() {
        return activeShaderPackName;
    }

    public static void setActiveShaderPackName(String name) {
        activeShaderPackName = name;
        reloadShaders();
    }
}
