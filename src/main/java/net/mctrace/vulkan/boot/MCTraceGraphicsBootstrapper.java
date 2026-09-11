package net.mctrace.vulkan.boot;

import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * NeoForge GraphicsBootstrapper for MCTrace.
 *
 * Runs BEFORE the FML early window is initialized.
 * If the user has selected the experimental Vulkan renderer (or passed --renderBackend vulkan),
 * this automatically disables FML's early OpenGL window (earlyWindowControl = false)
 * to prevent GLFW error 65540 (OpenGL/Vulkan client API conflict on the same window).
 */
public class MCTraceGraphicsBootstrapper implements GraphicsBootstrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("MCTrace-Bootstrapper");

    @Override
    public String name() {
        return "MCTraceVulkanBootstrapper";
    }

    @Override
    public void bootstrap(String[] args) {
        LOGGER.info("[MCTrace Bootstrapper] Inspecting graphics backend configuration before early display...");

        boolean isVulkanRequested = false;

        // 1. Check command-line arguments (--renderBackend vulkan)
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--renderBackend".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                    if ("vulkan".equalsIgnoreCase(args[i + 1])) {
                        isVulkanRequested = true;
                        LOGGER.info("[MCTrace Bootstrapper] Vulkan backend requested via command-line argument.");
                        break;
                    }
                }
            }
        }

        // 2. Check options.txt (preferredGraphicsBackend:"vulkan")
        if (!isVulkanRequested) {
            try {
                Path gameDir = FMLPaths.GAMEDIR.get();
                if (gameDir != null) {
                    Path optionsFile = gameDir.resolve("options.txt");
                    if (Files.exists(optionsFile)) {
                        List<String> lines = Files.readAllLines(optionsFile);
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("preferredGraphicsBackend:") && trimmed.toLowerCase().contains("vulkan")) {
                                isVulkanRequested = true;
                                LOGGER.info("[MCTrace Bootstrapper] Vulkan backend requested in options.txt.");
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[MCTrace Bootstrapper] Failed to read options.txt: {}", e.getMessage());
            }
        }

        // 3. If Vulkan is requested, automatically disable earlyWindowControl
        if (isVulkanRequested) {
            boolean earlyControlActive = FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL);
            if (earlyControlActive) {
                LOGGER.info("[MCTrace Bootstrapper] Vulkan active: Disabling FML earlyWindowControl to prevent GLFW error 65540...");
                FMLConfig.updateConfig(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
                LOGGER.info("[MCTrace Bootstrapper] earlyWindowControl successfully set to false.");
            } else {
                LOGGER.info("[MCTrace Bootstrapper] FML earlyWindowControl is already disabled.");
            }
        }
    }
}
