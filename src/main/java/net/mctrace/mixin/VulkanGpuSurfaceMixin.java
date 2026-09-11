package net.mctrace.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import org.lwjgl.vulkan.EXTSwapchainColorspace;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanGpuSurface.class)
public abstract class VulkanGpuSurfaceMixin {

    @Inject(method = "pickSwapchainSurfaceFormat", at = @At("HEAD"), cancellable = true)
    private void mctrace$pickHdrSwapchainFormat(VkSurfaceFormatKHR.Buffer formats, CallbackInfoReturnable<VkSurfaceFormatKHR> cir) {
        if (!MCTraceConfig.enableHDR) {
            MCTraceConfig.isHdrActive = false;
            return;
        }

        VkSurfaceFormatKHR chosenFormat = null;

        // Choice 1: scRGB 16-bit float linear (VK_FORMAT_R16G16B16A16_SFLOAT + VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT)
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.colorSpace() == EXTSwapchainColorspace.VK_COLOR_SPACE_EXTENDED_SRGB_LINEAR_EXT
                    && format.format() == VK10.VK_FORMAT_R16G16B16A16_SFLOAT) {
                chosenFormat = format;
                MCTrace.LOGGER.info("[MCTrace HDR] Selected scRGB 16-bit Float True HDR format (R16G16B16A16_SFLOAT, EXT_SRGB_LINEAR).");
                break;
            }
        }

        // Choice 2: HDR10 10-bit ST.2084 PQ (VK_FORMAT_A2B10G10R10_UNORM_PACK32 + VK_COLOR_SPACE_HDR10_ST2084_EXT)
        if (chosenFormat == null) {
            for (int i = 0; i < formats.capacity(); i++) {
                VkSurfaceFormatKHR format = formats.get(i);
                if (format.colorSpace() == EXTSwapchainColorspace.VK_COLOR_SPACE_HDR10_ST2084_EXT
                        && format.format() == VK10.VK_FORMAT_A2B10G10R10_UNORM_PACK32) {
                    chosenFormat = format;
                    MCTrace.LOGGER.info("[MCTrace HDR] Selected HDR10 10-bit ST.2084 True HDR format (A2B10G10R10_UNORM_PACK32, HDR10_ST2084).");
                    break;
                }
            }
        }

        if (chosenFormat != null) {
            MCTraceConfig.isHdrActive = true;
            cir.setReturnValue(chosenFormat);
        } else {
            MCTraceConfig.isHdrActive = false;
            MCTrace.LOGGER.info("[MCTrace HDR] Monitor/OS does not report HDR swapchain surface format, falling back to SDR.");
        }
    }
}
