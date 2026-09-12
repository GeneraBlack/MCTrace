package net.mctrace.mixin;

import net.mctrace.config.MCTraceConfig;
import net.mctrace.vulkan.profiler.MCTraceGpuProfiler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void mctrace$renderGpuProfilerHud(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (MCTraceConfig.showGpuProfiler) {
            MCTraceGpuProfiler.renderHud(extractor, this.getFont());
        }
    }
}
