package net.mctrace.mixin;

import com.mojang.blaze3d.vulkan.VulkanInstance;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import org.lwjgl.vulkan.EXTSwapchainColorspace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(VulkanInstance.class)
public abstract class VulkanInstanceMixin {

    @Shadow @Final
    private Set<String> enabledExtensions;

    @Shadow
    private native Set<String> getSupportedInstanceExtensions();

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Set;size()I"))
    private void mctrace$enableHdrInstanceExtension(int flags, boolean debug, boolean validation, CallbackInfo ci) {
        if (MCTraceConfig.enableHDR) {
            try {
                Set<String> supported = this.getSupportedInstanceExtensions();
                if (supported != null && supported.contains(EXTSwapchainColorspace.VK_EXT_SWAPCHAIN_COLOR_SPACE_EXTENSION_NAME)) {
                    this.enabledExtensions.add(EXTSwapchainColorspace.VK_EXT_SWAPCHAIN_COLOR_SPACE_EXTENSION_NAME);
                    MCTrace.LOGGER.info("[MCTrace HDR] Successfully enabled Vulkan instance extension: {}", EXTSwapchainColorspace.VK_EXT_SWAPCHAIN_COLOR_SPACE_EXTENSION_NAME);
                }
            } catch (Throwable t) {
                MCTrace.LOGGER.warn("[MCTrace HDR] Could not query instance extensions: {}", t.getMessage());
            }
        }
    }
}
