package net.mctrace.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.render.jitter.JitterManager;
import net.mctrace.vulkan.rt.TlasManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void mctrace$onLevelRenderHead(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            CameraRenderState cameraRenderState,
            Matrix4fc matrix4fc,
            GpuBufferSlice gpuBufferSlice,
            Vector4f vector4f,
            boolean bl,
            CallbackInfo ci
    ) {
        if (cameraRenderState != null && cameraRenderState.projectionMatrix != null) {
            // 1. Advance temporal jitter sequence for the current frame
            JitterManager.advanceFrame(GBufferManager.getRenderWidth(), GBufferManager.getRenderHeight());

            // 2. Record unjittered camera matrices and world position into history
            if (cameraRenderState.viewRotationMatrix != null && cameraRenderState.pos != null) {
                CameraHistory.update(
                        cameraRenderState.viewRotationMatrix,
                        cameraRenderState.projectionMatrix,
                        cameraRenderState.pos
                );
            }

            // 3. Prepare per-frame TLAS scene instances
            TlasManager.prepareFrame(cameraRenderState);

            // 4. Execute dynamic Vulkan Render Graph
            net.mctrace.vulkan.graph.RenderGraph.executeFrame(cameraRenderState);
        }
    }
}
