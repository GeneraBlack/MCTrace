package net.mctrace.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.vulkan.rt.CompositePipeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    @Shadow @Final
    private CrossFrameResourcePool resourcePool;

    @Shadow
    private RenderTarget mainRenderTarget;

    private static boolean loggedActive = false;

    @Inject(method = "resize", at = @At("HEAD"))
    private void mctrace$onResize(int width, int height, CallbackInfo ci) {
        GBufferManager.initOrResize(width, height);
    }

    @Inject(method = "resetData", at = @At("HEAD"))
    private void mctrace$onResetData(CallbackInfo ci) {
        CameraHistory.requestReset();
    }

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"
            )
    )
    private void mctrace$onRenderLevelBeforeHand(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.mainRenderTarget != null && MCTraceConfig.enableRayTracing && MCTraceConfig.rayTracingMode != MCTraceConfig.RayTracingMode.DISABLED) {
            int w = this.mainRenderTarget.width;
            int h = this.mainRenderTarget.height;
            if (w > 0 && h > 0) {
                GBufferManager.initOrResize(w, h);
                Vector3f sunDir = new Vector3f(0.5f, 0.8f, 0.3f).normalize();
                CompositePipeline.dispatch(w, h, sunDir, 0.5f);

                if (this.minecraft != null && this.minecraft.isGameLoadFinished() && this.minecraft.getShaderManager() != null) {
                    try {
                        PostChain worldChain = this.minecraft.getShaderManager().getPostChain(
                                Identifier.fromNamespaceAndPath("mctrace", "world"),
                                LevelTargetBundle.MAIN_TARGETS
                        );
                        if (worldChain != null) {
                            mctrace$updateCompositeUniforms(worldChain);
                            worldChain.process(this.mainRenderTarget, this.resourcePool);
                        }
                    } catch (Throwable t) {
                        if (!loggedActive) {
                            MCTrace.LOGGER.warn("[MCTrace] World shading pass waiting: {}", t.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mctrace$onRenderTail(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (this.mainRenderTarget != null && (MCTraceConfig.isHdrActive || MCTraceConfig.enableHDR || MCTraceConfig.enableRayTracing)) {
            int w = this.mainRenderTarget.width;
            int h = this.mainRenderTarget.height;
            if (w > 0 && h > 0) {
                if (this.minecraft != null && this.minecraft.isGameLoadFinished() && this.minecraft.getShaderManager() != null) {
                    try {
                        PostChain compositeChain = this.minecraft.getShaderManager().getPostChain(
                                Identifier.fromNamespaceAndPath("mctrace", "composite"),
                                LevelTargetBundle.MAIN_TARGETS
                        );
                        if (compositeChain != null) {
                            mctrace$updateCompositeUniforms(compositeChain);
                            compositeChain.process(this.mainRenderTarget, this.resourcePool);
                            if (!loggedActive) {
                                loggedActive = true;
                                MCTrace.LOGGER.info("[MCTrace] Final frame display calibration & HDR tone mapping active ({}x{}).", w, h);
                            }
                        }
                    } catch (Throwable t) {
                        if (!loggedActive) {
                            MCTrace.LOGGER.warn("[MCTrace] Final composite pass waiting: {}", t.getMessage());
                        }
                    }
                }
            }
        }
    }

    private static final java.nio.ByteBuffer uboBuffer = java.nio.ByteBuffer.allocateDirect(32).order(java.nio.ByteOrder.nativeOrder());
    private static boolean loggedUniformsOk = false;

    private void mctrace$updateCompositeUniforms(PostChain chain) {
        try {
            java.util.List<net.minecraft.client.renderer.PostPass> passes = ((PostChainAccessor) chain).mctrace$getPasses();
            if (passes != null && !passes.isEmpty()) {
                net.minecraft.client.renderer.PostPass pass = passes.get(0);
                java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer> uniforms = ((PostPassAccessor) pass).mctrace$getCustomUniforms();
                if (uniforms != null) {
                    com.mojang.blaze3d.buffers.GpuBuffer buffer = uniforms.get("MCTraceParams");
                    if (buffer != null) {
                        uboBuffer.clear();
                        // vec4 HdrConfig: minLum, paperWhite, peakLum, contrast
                        uboBuffer.putFloat(MCTraceConfig.hdrMinLuminance);
                        uboBuffer.putFloat(MCTraceConfig.hdrPaperWhite);
                        uboBuffer.putFloat(MCTraceConfig.hdrPeakLuminance);
                        uboBuffer.putFloat(MCTraceConfig.hdrMiddleGrayContrast);
                        // vec4 LightingConfig: isHdrActive, ssaoMultiplier, timeOfDay, unused
                        uboBuffer.putFloat(MCTraceConfig.isHdrActive ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.ssaoIntensity.getMultiplier());
                        uboBuffer.putFloat(0.5f);
                        uboBuffer.putFloat(0.0f);
                        uboBuffer.flip();

                        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), uboBuffer);
                        if (!loggedUniformsOk) {
                            loggedUniformsOk = true;
                            MCTrace.LOGGER.info("[MCTrace] Real-time display calibration uniform updates active on GPU.");
                        }
                    } else if (!loggedUniformsOk) {
                        loggedUniformsOk = true;
                        MCTrace.LOGGER.warn("[MCTrace] MCTraceParams uniform buffer not found in composite pass uniforms: {}", uniforms.keySet());
                    }
                }
            }
        } catch (Throwable t) {
            if (!loggedUniformsOk) {
                loggedUniformsOk = true;
                MCTrace.LOGGER.warn("[MCTrace] Failed to update composite uniforms: {}", t.getMessage());
            }
        }
    }
}

