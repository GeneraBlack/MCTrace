package net.mctrace.mixin;

import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "resize", at = @At("HEAD"))
    private void mctrace$onResize(int width, int height, CallbackInfo ci) {
        GBufferManager.initOrResize(width, height);
    }

    @Inject(method = "resetData", at = @At("HEAD"))
    private void mctrace$onResetData(CallbackInfo ci) {
        CameraHistory.requestReset();
    }
}
