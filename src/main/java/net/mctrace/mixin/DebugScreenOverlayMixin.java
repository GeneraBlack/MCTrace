package net.mctrace.mixin;

import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.gbuffer.GBufferManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Inject(method = "extractLines", at = @At("HEAD"))
    private void mctrace$addDebugLines(GuiGraphicsExtractor extractor, List<String> lines, boolean isLeft, CallbackInfo ci) {
        if (!isLeft) {
            lines.add("");
            lines.add("§6[MCTrace Engine]§r " + (MCTraceConfig.enableRayTracing ? "§aActive§r" : "§cDisabled§r"));

            String backendName = "OpenGL";
            try {
                backendName = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName();
            } catch (Throwable ignored) {}

            boolean isVulkan = "Vulkan".equalsIgnoreCase(backendName);
            lines.add(" Backend: " + (isVulkan ? "§aVulkan 1.4§r" : "§e" + backendName + " (SDR only)§r"));

            if (MCTraceConfig.isHdrActive) {
                lines.add(" Display: §bTrue HDR (" + MCTraceConfig.activeFormatName + " @ " + (int) MCTraceConfig.hdrPeakLuminance + " nits)§r");
            } else {
                lines.add(" Display: §e" + MCTraceConfig.activeFormatName + (!isVulkan ? " §7[Switch to Vulkan for HDR]§r" : "") + "§r");
            }
            if (MCTraceConfig.enableFSR && GBufferManager.isInitialized()) {
                lines.add(" AMD FSR: §a" + MCTraceConfig.fsrQualityMode.name() + "§r (" +
                        GBufferManager.getRenderWidth() + "x" + GBufferManager.getRenderHeight() + " -> " +
                        GBufferManager.getNativeWidth() + "x" + GBufferManager.getNativeHeight() + ", " +
                        (int) (MCTraceConfig.fsrSharpness * 100) + "% Sharpness)");
            } else {
                lines.add(" AMD FSR: §7Off (Native Resolution)§r");
            }
            lines.add(" Lighting: §f" + MCTraceConfig.rayTracingMode.name() + "§r | SSAO: §e" + MCTraceConfig.ssaoIntensity.name() + "§r");
            if (MCTraceConfig.showGpuProfiler) {
                lines.add(String.format(" GPU Frame: §a%.2f ms§r (%.1f FPS) | VRAM: §e%.1f MB§r",
                        net.mctrace.vulkan.profiler.MCTraceGpuProfiler.getTotalGpuTimeMs(),
                        net.mctrace.vulkan.profiler.MCTraceGpuProfiler.getCurrentFps(),
                        net.mctrace.vulkan.profiler.MCTraceGpuProfiler.getEstimatedVramUsageMb()));
                for (var pass : net.mctrace.vulkan.profiler.MCTraceGpuProfiler.PassType.values()) {
                    float t = net.mctrace.vulkan.profiler.MCTraceGpuProfiler.getPassTimeMs(pass);
                    if (t > 0.05f) {
                        lines.add(String.format("   • %s: §6%.2f ms§r", pass.getDisplayName(), t));
                    }
                }
            }
        }
    }
}