package net.mctrace;

import com.mojang.logging.LogUtils;
import net.mctrace.config.MCTraceKeybinds;
import net.mctrace.gui.MCTraceConfigScreen;
import net.mctrace.vulkan.VulkanCapabilities;
import net.mctrace.vulkan.rt.DenoiserPipeline;
import net.mctrace.vulkan.rt.FsrPipeline;
import net.mctrace.vulkan.rt.RayTracingPipeline;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod(value = MCTrace.MODID, dist = Dist.CLIENT)
public class MCTrace {
    public static final String MODID = "mctrace";
    public static final String NAME = "MCTrace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCTrace(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[MCTrace] Initializing next-gen Vulkan RT & FSR engine...");

        // Register client lifecycle and keybind events
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterKeyMappings);

        // Register game loop tick event
        NeoForge.EVENT_BUS.addListener(this::onClientTick);

        // Register Config screen factory for the NeoForge "Mods" tab
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, lastScreen) -> new MCTraceConfigScreen(lastScreen)
        );
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[MCTrace] Client setup completed. Initializing graphics pipelines...");

        // Initialize Vulkan compute pipelines
        RayTracingPipeline.initialize();
        DenoiserPipeline.initialize();
        FsrPipeline.initialize();

        if (VulkanCapabilities.isVulkanInitialized()) {
            VulkanCapabilities.logCapabilities();
        } else {
            LOGGER.info("[MCTrace] Vulkan device ready for initial swapchain attachment.");
        }
    }

    private void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(MCTraceKeybinds.OPEN_CONFIG_KEY);
    }

    private void onClientTick(final ClientTickEvent.Post event) {
        while (MCTraceKeybinds.OPEN_CONFIG_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.setScreen(new MCTraceConfigScreen(mc.gui.screen()));
            }
        }
    }
}
