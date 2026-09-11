package net.mctrace.vulkan.boot;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Early graphics bootstrapper for NeoForge that automatically disables
 * NeoForge's early loading screen (earlyWindowControl) when the experimental
 * Vulkan backend is detected, preventing GLFW error 65540.
 *
 * It safely unclaims its own code location from FMLLoader's locatedPaths
 * so that MCTrace is discovered and loaded normally as a mod by NeoForge.
 */
public class MCTraceGraphicsBootstrapper implements GraphicsBootstrapper {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String name() {
        return "MCTraceVulkanBootstrapper";
    }

    @Override
    public void bootstrap(String[] arguments) {
        LOGGER.info("[MCTrace Bootstrapper] Checking graphics backend and FML early window config...");

        try {
            boolean vulkanRequested = isVulkanRequested(arguments);

            if (vulkanRequested) {
                if (FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
                    LOGGER.info("[MCTrace Bootstrapper] Vulkan backend active: Disabling FML earlyWindowControl to prevent GLFW error 65540...");
                    FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
                    LOGGER.info("[MCTrace Bootstrapper] earlyWindowControl successfully set to false in fml.toml.");
                } else {
                    LOGGER.info("[MCTrace Bootstrapper] FML earlyWindowControl is already disabled.");
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[MCTrace Bootstrapper] Failed to check or update earlyWindowControl config: {}", t.getMessage());
        } finally {
            // Unclaim our code location so InDevFolderLocator / ModsFolderLocator can discover MCTrace as a mod
            unclaimCodeLocation();
        }
    }

    private void unclaimCodeLocation() {
        try {
            FMLLoader loader = FMLLoader.getCurrentOrNull();
            if (loader == null) {
                return;
            }

            Field locatedField = FMLLoader.class.getDeclaredField("locatedPaths");
            locatedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Path> locatedPaths = (Set<Path>) locatedField.get(loader);

            if (locatedPaths != null) {
                URI codeUri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
                Path myPath = Path.of(codeUri);
                if (locatedPaths.remove(myPath)) {
                    LOGGER.info("[MCTrace Bootstrapper] Successfully unclaimed code location {} for mod discovery.", myPath);
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[MCTrace Bootstrapper] Note: Could not unclaim code location: {}", t.getMessage());
        }
    }

    private boolean isVulkanRequested(String[] arguments) {
        // 1. Check command line arguments: --renderBackend vulkan
        if (arguments != null) {
            for (int i = 0; i < arguments.length; i++) {
                if ("--renderBackend".equalsIgnoreCase(arguments[i]) && (i + 1) < arguments.length) {
                    if ("vulkan".equalsIgnoreCase(arguments[i + 1])) {
                        LOGGER.info("[MCTrace Bootstrapper] Vulkan backend requested via command-line argument.");
                        return true;
                    }
                }
            }
        }

        // 2. Check options.txt for preferredGraphicsBackend:"vulkan"
        try {
            Path optionsFile = FMLPaths.GAMEDIR.get().resolve("options.txt");
            if (Files.exists(optionsFile)) {
                List<String> lines = Files.readAllLines(optionsFile);
                for (String line : lines) {
                    if (line.trim().startsWith("preferredGraphicsBackend:")) {
                        String backend = line.substring("preferredGraphicsBackend:".length()).trim().replace("\"", "");
                        if ("vulkan".equalsIgnoreCase(backend)) {
                            LOGGER.info("[MCTrace Bootstrapper] Vulkan backend requested via options.txt.");
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[MCTrace Bootstrapper] Unable to read options.txt: {}", t.getMessage());
        }

        return false;
    }
}
