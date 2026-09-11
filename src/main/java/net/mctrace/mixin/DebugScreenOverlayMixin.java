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
            lines.add(" Display: " + (MCTraceConfig.isHdrActive 
                    ? "§bTrue HDR (scRGB 16-bit Float @ " + (int) MCTraceConfig.hdrPeakLuminance + " nits)§r" 
                    : "§eSDR (8-bit)§r"));
            if (MCTraceConfig.enableFSR && GBufferManager.isInitialized()) {
                lines.add(" AMD FSR: §a" + MCTraceConfig.fsrQualityMode.name() + "§r (" +
                        GBufferManager.getRenderWidth() + "x" + GBufferManager.getRenderHeight() + " -> " +
                        GBufferManager.getNativeWidth() + "x" + GBufferManager.getNativeHeight() + ", " +
                        (int) (MCTraceConfig.fsrSharpness * 100) + "% Sharpness)");
            } else {
                lines.add(" AMD FSR: §7Off (Native Resolution)§r");
            }
            lines.add(" Lighting: §f" + MCTraceConfig.rayTracingMode.name() + "§r | SSAO: §e" + MCTraceConfig.ssaoIntensity.name() + "§r");
        }
    }
}