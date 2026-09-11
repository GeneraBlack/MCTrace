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

    private static final java.nio.ByteBuffer uboBuffer = java.nio.ByteBuffer.allocateDirect(112).order(java.nio.ByteOrder.nativeOrder());
    private static boolean loggedUniformsOk = false;

    private static float[] mctrace$resolveItemLight(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String desc = stack.getItem().toString().toLowerCase();
        if (desc.contains("torch") && desc.contains("soul")) {
            return new float[]{ 0.12f, 0.88f, 0.95f, 1.0f }; // Soul Torch / Lantern (Teal)
        }
        if (desc.contains("torch") || desc.contains("lantern") || desc.contains("campfire") || desc.contains("lava")) {
            return new float[]{ 1.00f, 0.65f, 0.22f, 1.0f }; // Torch / Lantern / Lava (Amber)
        }
        if (desc.contains("redstone")) {
            return new float[]{ 1.00f, 0.15f, 0.05f, 0.9f }; // Redstone (Crimson)
        }
        if (desc.contains("glowstone") || desc.contains("shroomlight")) {
            return new float[]{ 1.00f, 0.80f, 0.30f, 1.0f }; // Glowstone (Gold)
        }
        if (desc.contains("amethyst")) {
            return new float[]{ 0.75f, 0.35f, 1.00f, 0.8f }; // Amethyst (Violet)
        }
        if (desc.contains("sculk")) {
            return new float[]{ 0.08f, 0.94f, 0.88f, 1.0f }; // Sculk (Cyan)
        }
        return null;
    }

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

                        // 1. vec4 HdrConfig: sceneBrightness, paperWhite, peakLum, contrast
                        uboBuffer.putFloat(MCTraceConfig.sceneBrightness);
                        uboBuffer.putFloat(MCTraceConfig.hdrPaperWhite);
                        uboBuffer.putFloat(MCTraceConfig.hdrPeakLuminance);
                        uboBuffer.putFloat(MCTraceConfig.hdrMiddleGrayContrast);

                        // 2. vec4 LightingConfig: isHdrActive, ssaoMultiplier, minLum, wideGamutStrength
                        uboBuffer.putFloat(MCTraceConfig.isHdrActive ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.ssaoIntensity.getMultiplier());
                        uboBuffer.putFloat(MCTraceConfig.hdrMinLuminance);
                        uboBuffer.putFloat(MCTraceConfig.enableWideGamut ? MCTraceConfig.wideGamutStrength : 0.0f);

                        // 3. vec4 MaterialConfig: enablePbr, enableWaterReflections, enableDynamicColoredLight, time
                        uboBuffer.putFloat(MCTraceConfig.enablePbrMaterials ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.enableWaterReflections ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.enableDynamicColoredLight ? 1.0f : 0.0f);
                        float time = (float) ((System.currentTimeMillis() % 1000000L) / 1000.0);
                        uboBuffer.putFloat(time);

                        // 4. vec4 WeatherConfig: rainLevel, wetness, thunderLevel, skyAngle
                        float rainLevel = 0.0f;
                        float thunderLevel = 0.0f;
                        float skyAngle = 0.0f;
                        if (this.minecraft != null && this.minecraft.level != null) {
                            rainLevel = this.minecraft.level.getRainLevel(1.0f);
                            thunderLevel = this.minecraft.level.getThunderLevel(1.0f);
                            skyAngle = (float) ((this.minecraft.level.getGameTime() % 24000L) / 24000.0);
                        }
                        float wetness = MCTraceConfig.enableRainWetness ? rainLevel : 0.0f;
                        uboBuffer.putFloat(rainLevel);
                        uboBuffer.putFloat(wetness);
                        uboBuffer.putFloat(thunderLevel);
                        uboBuffer.putFloat(skyAngle);

                        // 5. vec4 AtmosphereConfig: enableFog, fogDensity, enableGodRays, godRaysIntensity
                        uboBuffer.putFloat(MCTraceConfig.enableVolumetricFog ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.volumetricFogDensity);
                        uboBuffer.putFloat(MCTraceConfig.enableGodRays ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.godRaysIntensity);

                        // 6. vec4 DynamicLightConfig: heldLightR, heldLightG, heldLightB, heldLightIntensity
                        float heldR = 0.0f, heldG = 0.0f, heldB = 0.0f, heldIntensity = 0.0f;
                        if (MCTraceConfig.enableHeldDynamicLights && this.minecraft != null && this.minecraft.player != null) {
                            float[] light = mctrace$resolveItemLight(this.minecraft.player.getMainHandItem());
                            if (light == null) {
                                light = mctrace$resolveItemLight(this.minecraft.player.getOffhandItem());
                            }
                            if (light != null) {
                                heldR = light[0];
                                heldG = light[1];
                                heldB = light[2];
                                heldIntensity = light[3];
                            }
                        }
                        uboBuffer.putFloat(heldR);
                        uboBuffer.putFloat(heldG);
                        uboBuffer.putFloat(heldB);
                        uboBuffer.putFloat(heldIntensity);

                        // 7. vec4 CinematicConfig: enableMotionBlur, motionBlurStrength, enableDof, pomDepth
                        float enableDof = MCTraceConfig.enableBokehDof ? 1.0f : 0.0f;
                        if (this.minecraft != null && this.minecraft.player != null) {
                            if (this.minecraft.player.isScoping() || (this.minecraft.player.isUsingItem() && this.minecraft.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem)) {
                                enableDof = 1.0f;
                            }
                        }
                        float pomDepth = MCTraceConfig.enableParallaxOcclusion ? MCTraceConfig.pomDepth : 0.0f;
                        uboBuffer.putFloat(MCTraceConfig.enableMotionBlur ? 1.0f : 0.0f);
                        uboBuffer.putFloat(MCTraceConfig.motionBlurStrength);
                        uboBuffer.putFloat(enableDof);
                        uboBuffer.putFloat(pomDepth);

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

