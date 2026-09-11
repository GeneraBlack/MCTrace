package net.mctrace.vulkan.shader;

import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.vulkan.rt.CompositePipeline;
import net.mctrace.vulkan.rt.DenoiserPipeline;
import net.mctrace.vulkan.rt.FsrPipeline;
import net.mctrace.vulkan.rt.RayTracingPipeline;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Discovers, loads, and hot-reloads custom SPIR-V and GLSL shader packs
 * (both uncompressed directories and .zip archives) from the game's shaderpacks folder.
 */
public class ShaderPackLoader {

    public static final String INTERNAL_PACK = "internal";
    private static final String SHADERPACK_DIR_NAME = "shaderpacks";
    private static String activeShaderPackName = INTERNAL_PACK;
    private static Path customPacksDirectory = null;
    private static Runnable postChainInvalidator = null;

    /**
     * Sets a custom packs directory (useful for tests or custom installations).
     */
    public static void setCustomPacksDirectory(Path dir) {
        customPacksDirectory = dir;
    }

    /**
     * Returns the path to the shaderpacks folder, creating it if needed.
     */
    public static Path getShaderPacksDirectory() {
        if (customPacksDirectory != null) {
            try {
                if (!Files.exists(customPacksDirectory)) {
                    Files.createDirectories(customPacksDirectory);
                }
            } catch (Exception ignored) {}
            return customPacksDirectory;
        }

        Path dir;
        try {
            Minecraft mc = Minecraft.getInstance();
            File gameDir = (mc != null && mc.gameDirectory != null) ? mc.gameDirectory : new File(".");
            dir = gameDir.toPath().resolve(SHADERPACK_DIR_NAME);
        } catch (Throwable t) {
            dir = Path.of(SHADERPACK_DIR_NAME);
        }

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
        packs.add(INTERNAL_PACK); // Default bundled Vulkan / SPIR-V pipeline

        Path dir = getShaderPacksDirectory();
        File[] files = dir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() || file.getName().toLowerCase().endsWith(".zip")) {
                    packs.add(file.getName());
                }
            }
        }
        return packs;
    }

    /**
     * Normalizes and expands query paths into standard candidate locations inside a shader pack:
     * e.g. "shaders/post/world.fsh", "shaders/world.fsh", "world.fsh", etc.
     */
    private static List<String> getCandidatePaths(String queryPath) {
        List<String> candidates = new ArrayList<>();
        if (queryPath == null || queryPath.isBlank()) return candidates;

        String normalized = queryPath.replace('\\', '/');
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.startsWith("assets/mctrace/")) normalized = normalized.substring("assets/mctrace/".length());

        candidates.add(normalized);

        // Strip "shaders/" prefix if present to check root, or prepend "shaders/" if absent
        if (normalized.startsWith("shaders/")) {
            String sub = normalized.substring("shaders/".length());
            candidates.add(sub);
            if (sub.startsWith("post/")) {
                candidates.add("shaders/" + sub.substring("post/".length()));
                candidates.add(sub.substring("post/".length()));
            }
        } else {
            candidates.add("shaders/" + normalized);
            candidates.add("shaders/post/" + normalized);
        }

        // Basename candidate (e.g. "world.fsh" or "rayquery.comp")
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            String base = normalized.substring(lastSlash + 1);
            if (!candidates.contains(base)) candidates.add(base);
            if (!candidates.contains("shaders/" + base)) candidates.add("shaders/" + base);
        }

        return candidates;
    }

    /**
     * Checks whether the currently active shader pack overrides the specified shader.
     */
    public static boolean hasShaderOverride(String queryPath) {
        if (INTERNAL_PACK.equalsIgnoreCase(activeShaderPackName) || activeShaderPackName == null || activeShaderPackName.isBlank()) {
            return false;
        }

        Path dir = getShaderPacksDirectory();
        Path packFile = dir.resolve(activeShaderPackName);
        if (!Files.exists(packFile)) {
            // Also check with .zip extension if not present
            if (!activeShaderPackName.endsWith(".zip")) {
                packFile = dir.resolve(activeShaderPackName + ".zip");
            }
            if (!Files.exists(packFile)) {
                return false;
            }
        }

        List<String> candidates = getCandidatePaths(queryPath);

        if (Files.isDirectory(packFile)) {
            for (String c : candidates) {
                if (Files.isRegularFile(packFile.resolve(c))) {
                    return true;
                }
            }
            return false;
        } else if (packFile.getFileName().toString().toLowerCase().endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(packFile.toFile())) {
                for (String c : candidates) {
                    ZipEntry entry = zip.getEntry(c);
                    if (entry != null && !entry.isDirectory()) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Retrieves the GLSL shader source from the active shader pack.
     *
     * @param queryPath The shader path or name.
     * @return The source code string, or null if not found.
     */
    public static String getShaderSource(String queryPath) {
        if (INTERNAL_PACK.equalsIgnoreCase(activeShaderPackName) || activeShaderPackName == null || activeShaderPackName.isBlank()) {
            return null;
        }

        Path dir = getShaderPacksDirectory();
        Path packFile = dir.resolve(activeShaderPackName);
        if (!Files.exists(packFile)) {
            if (!activeShaderPackName.endsWith(".zip")) {
                packFile = dir.resolve(activeShaderPackName + ".zip");
            }
            if (!Files.exists(packFile)) {
                return null;
            }
        }

        List<String> candidates = getCandidatePaths(queryPath);

        if (Files.isDirectory(packFile)) {
            for (String c : candidates) {
                Path target = packFile.resolve(c);
                if (Files.isRegularFile(target)) {
                    try {
                        return Files.readString(target, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        MCTrace.LOGGER.error("[MCTrace Loader] Failed to read shader {} from folder: {}", target, e.getMessage());
                        return null;
                    }
                }
            }
        } else if (packFile.getFileName().toString().toLowerCase().endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(packFile.toFile())) {
                for (String c : candidates) {
                    ZipEntry entry = zip.getEntry(c);
                    if (entry != null && !entry.isDirectory()) {
                        try (InputStream is = zip.getInputStream(entry);
                             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append('\n');
                            }
                            return sb.toString();
                        }
                    }
                }
            } catch (Exception e) {
                MCTrace.LOGGER.error("[MCTrace Loader] Failed to read shader {} from zip {}: {}", queryPath, packFile, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Registers a callback invoked during reload to invalidate cached post-processing chains.
     */
    public static void setPostChainInvalidator(Runnable invalidator) {
        postChainInvalidator = invalidator;
    }

    /**
     * Hot-reloads all shaders in real time.
     */
    public static void reloadShaders() {
        MCTrace.LOGGER.info("[MCTrace Loader] Hot-reloading shader pipelines (active pack: {})...", activeShaderPackName);

        // 1. Re-initialize compute pipelines with updated shaders
        RayTracingPipeline.reload();
        DenoiserPipeline.reload();
        FsrPipeline.reload();
        CompositePipeline.reload();

        // 2. Invalidate post-processing chains so game renderer re-creates them
        if (postChainInvalidator != null) {
            try {
                postChainInvalidator.run();
                MCTrace.LOGGER.info("[MCTrace Loader] Post-chain compilation cache invalidated for immediate reload.");
            } catch (Throwable t) {
                MCTrace.LOGGER.warn("[MCTrace Loader] Post-chain invalidation notice: {}", t.getMessage());
            }
        }

        MCTrace.LOGGER.info("[MCTrace Loader] Shader reload completed successfully.");
    }

    public static String getActiveShaderPackName() {
        return activeShaderPackName;
    }

    public static void setActiveShaderPackName(String name) {
        if (name == null || name.isBlank()) {
            activeShaderPackName = INTERNAL_PACK;
        } else {
            activeShaderPackName = name;
        }
        MCTraceConfig.activeShaderPack = activeShaderPackName;
        MCTraceConfig.save();
        reloadShaders();
    }
}
