package net.mctrace.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.vulkan.rt.CompositePipeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    private RenderTarget mainRenderTarget;

    @Inject(method = "resize", at = @At("HEAD"))
    private void mctrace$onResize(int width, int height, CallbackInfo ci) {
        GBufferManager.initOrResize(width, height);
    }

    @Inject(method = "resetData", at = @At("HEAD"))
    private void mctrace$onResetData(CallbackInfo ci) {
        CameraHistory.requestReset();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void mctrace$onRenderLevelTail(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.mainRenderTarget != null) {
            int w = this.mainRenderTarget.width;
            int h = this.mainRenderTarget.height;
            if (w > 0 && h > 0) {
                GBufferManager.initOrResize(w, h);
                Vector3f sunDir = new Vector3f(0.5f, 0.8f, 0.3f).normalize();
                CompositePipeline.dispatch(w, h, sunDir, 0.5f);
            }
        }
    }
}

