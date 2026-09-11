package net.mctrace;

import com.mojang.logging.LogUtils;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.vulkan.VulkanCapabilities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(value = MCTrace.MODID, dist = Dist.CLIENT)
public class MCTrace {
    public static final String MODID = "mctrace";
    public static final String NAME = "MCTrace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCTrace(IEventBus modEventBus) {
        LOGGER.info("[MCTrace] Initializing next-gen Vulkan RT & FSR engine...");

        // Register client setup
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[MCTrace] Client setup completed. Checking Vulkan runtime capabilities...");
        if (VulkanCapabilities.isVulkanInitialized()) {
            VulkanCapabilities.logCapabilities();
        } else {
            LOGGER.info("[MCTrace] Vulkan device not yet created or OpenGL backend currently active.");
        }
    }
}
