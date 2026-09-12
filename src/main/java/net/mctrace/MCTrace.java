package net.mctrace;

import com.mojang.logging.LogUtils;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.config.MCTraceKeybinds;
import net.mctrace.gui.MCTraceConfigScreen;
import net.mctrace.vulkan.VulkanCapabilities;
import net.mctrace.vulkan.rt.CompositePipeline;
import net.mctrace.vulkan.rt.DenoiserPipeline;
import net.mctrace.vulkan.rt.FsrPipeline;
import net.mctrace.vulkan.rt.RayTracingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

        // Load saved configuration from disk
        MCTraceConfig.load();

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

        // Ensure FML early window is disabled for Vulkan compatibility
        try {
            if (net.neoforged.fml.loading.FMLConfig.getBoolConfigValue(net.neoforged.fml.loading.FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) {
                net.neoforged.fml.loading.FMLConfig.updateConfig(net.neoforged.fml.loading.FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL, false);
                LOGGER.info("[MCTrace] Disabled FML earlyWindowControl in config/fml.toml for Vulkan compatibility.");
            }
        } catch (Throwable ignored) {}
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("[MCTrace] Client setup completed. Initializing graphics pipelines...");

        // Initialize Vulkan compute pipelines
        RayTracingPipeline.initialize();
        DenoiserPipeline.initialize();
        FsrPipeline.initialize();
        CompositePipeline.initialize();

        if (VulkanCapabilities.isVulkanInitialized()) {
            VulkanCapabilities.logCapabilities();
        } else {
            LOGGER.info("[MCTrace] Vulkan device ready for initial swapchain attachment.");
        }
    }

    private void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        event.register(MCTraceKeybinds.OPEN_CONFIG_KEY);
        event.register(MCTraceKeybinds.TOGGLE_EFFECTS_KEY);
        event.register(MCTraceKeybinds.RELOAD_SHADERS_KEY);
        event.register(MCTraceKeybinds.TOGGLE_PROFILER_KEY);
        event.register(MCTraceKeybinds.PHOTO_MODE_KEY);
    }

    private void onClientTick(final ClientTickEvent.Post event) {
        // Update Dynamic Resolution Scaling based on current FPS
        if (MCTraceConfig.enableDrs) {
            net.mctrace.render.drs.DRSManager.update(net.mctrace.vulkan.profiler.MCTraceGpuProfiler.getCurrentFps());
        }

        while (MCTraceKeybinds.OPEN_CONFIG_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.setScreen(new MCTraceConfigScreen(mc.gui.screen()));
            }
        }

        while (MCTraceKeybinds.TOGGLE_EFFECTS_KEY.consumeClick()) {
            MCTraceConfig.enableRayTracing = !MCTraceConfig.enableRayTracing;
            MCTraceConfig.save();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(
                        Component.literal("§6[MCTrace]§r Shading & Effects: " + (MCTraceConfig.enableRayTracing ? "§aEnabled" : "§cDisabled"))
                );
            }
        }

        while (MCTraceKeybinds.RELOAD_SHADERS_KEY.consumeClick()) {
            long t0 = System.currentTimeMillis();
            net.mctrace.vulkan.shader.ShaderPackLoader.reloadShaders();
            long dt = System.currentTimeMillis() - t0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(
                        Component.literal("§6[MCTrace]§r Reloaded shaders in " + dt + " ms (" + net.mctrace.vulkan.shader.ShaderPackLoader.getActiveShaderPackName() + ")")
                );
            }
        }

        while (MCTraceKeybinds.TOGGLE_PROFILER_KEY.consumeClick()) {
            MCTraceConfig.showGpuProfiler = !MCTraceConfig.showGpuProfiler;
            MCTraceConfig.save();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(
                        Component.literal("§6[MCTrace]§r Vulkan GPU Profiler: " + (MCTraceConfig.showGpuProfiler ? "§aEnabled" : "§cDisabled"))
                );
            }
        }

        while (MCTraceKeybinds.PHOTO_MODE_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui != null) {
                mc.gui.setScreen(new net.mctrace.gui.MCTracePhotoModeScreen(mc.gui.screen()));
            }
        }
    }
}
