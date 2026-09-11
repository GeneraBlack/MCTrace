package net.mctrace.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import java.nio.LongBuffer;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import org.lwjgl.vulkan.EXTSwapchainColorspace;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanGpuSurface.class)
public abstract class VulkanGpuSurfaceMixin {

    private static int currentHdrFormat = 0;
    private static int currentHdrColorSpace = 0;
    private static int fallbackSdrFormat = VK10.VK_FORMAT_B8G8R8A8_UNORM;

    @Inject(method = "pickSwapchainSurfaceFormat", at = @At("HEAD"), cancellable = true)
    private void mctrace$pickHdrSwapchainFormat(VkSurfaceFormatKHR.Buffer formats, CallbackInfoReturnable<VkSurfaceFormatKHR> cir) {
        // Cache available SDR fallback format first
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
                    && (format.format() == VK10.VK_FORMAT_B8G8R8A8_UNORM || format.format() == VK10.VK_FORMAT_R8G8B8A8_UNORM)) {
                fallbackSdrFormat = format.format();
                break;
            }
        }

        if (!MCTraceConfig.enableHDR) {
            currentHdrFormat = 0;
            currentHdrColorSpace = 0;
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
            currentHdrFormat = chosenFormat.format();
            currentHdrColorSpace = chosenFormat.colorSpace();
            MCTraceConfig.isHdrActive = true;
            cir.setReturnValue(chosenFormat);
        } else {
            currentHdrFormat = 0;
            currentHdrColorSpace = 0;
            MCTraceConfig.isHdrActive = false;
            MCTrace.LOGGER.info("[MCTrace HDR] Monitor/OS does not report HDR swapchain surface format, falling back to SDR.");
        }
    }

    @Redirect(
        method = "configure",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;imageFormat(I)Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;"
        )
    )
    private VkSwapchainCreateInfoKHR mctrace$redirectImageFormat(VkSwapchainCreateInfoKHR instance, int originalFormat) {
        if (MCTraceConfig.isHdrActive && currentHdrFormat != 0) {
            return instance.imageFormat(currentHdrFormat);
        }
        if (!MCTraceConfig.isHdrActive && fallbackSdrFormat != 0) {
            return instance.imageFormat(fallbackSdrFormat);
        }
        return instance.imageFormat(originalFormat);
    }

    @Redirect(
        method = "configure",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;imageColorSpace(I)Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;"
        )
    )
    private VkSwapchainCreateInfoKHR mctrace$redirectImageColorSpace(VkSwapchainCreateInfoKHR instance, int originalColorSpace) {
        if (MCTraceConfig.isHdrActive && currentHdrColorSpace != 0) {
            return instance.imageColorSpace(currentHdrColorSpace);
        }
        return instance.imageColorSpace(KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
    }

    @Redirect(
        method = "configure",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRSwapchain;vkCreateSwapchainKHR(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSwapchainCreateInfoKHR;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"
        )
    )
    private int mctrace$createSwapchainWithFallback(
        VkDevice device,
        VkSwapchainCreateInfoKHR pCreateInfo,
        VkAllocationCallbacks pAllocator,
        LongBuffer pSwapchain
    ) {
        int res = KHRSwapchain.vkCreateSwapchainKHR(device, pCreateInfo, pAllocator, pSwapchain);
        if (res != VK10.VK_SUCCESS && MCTraceConfig.isHdrActive) {
            MCTrace.LOGGER.warn("[MCTrace HDR] vkCreateSwapchainKHR failed (result: {}). Falling back to SDR format.", res);
            MCTraceConfig.isHdrActive = false;
            currentHdrFormat = 0;
            currentHdrColorSpace = 0;
            pCreateInfo.imageFormat(fallbackSdrFormat != 0 ? fallbackSdrFormat : VK10.VK_FORMAT_B8G8R8A8_UNORM);
            pCreateInfo.imageColorSpace(KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
            res = KHRSwapchain.vkCreateSwapchainKHR(device, pCreateInfo, pAllocator, pSwapchain);
            if (res == VK10.VK_SUCCESS) {
                MCTrace.LOGGER.info("[MCTrace HDR] Successfully recovered and created SDR swapchain.");
            }
        }
        return res;
    }
}
