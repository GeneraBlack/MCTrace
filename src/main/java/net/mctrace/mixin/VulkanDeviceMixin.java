package net.mctrace.mixin;

/**
 * Mixin hook template for Blaze3D's Vulkan Device creation.
 *
 * In Minecraft 26.2, Blaze3D initializes the Vulkan physical device, queries extension
 * properties via vkEnumerateDeviceExtensionProperties, and builds VkDeviceCreateInfo.
 *
 * This hook delegates extension negotiation to VulkanDeviceInterceptor.augmentDeviceExtensions()
 * and configures the pNext feature chain for Ray Query and Acceleration Structures.
 */
public class VulkanDeviceMixin {
    // Target: com.mojang.blaze3d.vulkan.VulkanDevice or equivalent Blaze3D Vulkan backend entrypoint
}
