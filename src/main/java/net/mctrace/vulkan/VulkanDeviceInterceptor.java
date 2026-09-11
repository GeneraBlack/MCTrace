package net.mctrace.vulkan;

import net.mctrace.MCTrace;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Intercepts Vulkan physical device queries and device creation in Blaze3D
 * to negotiate and enable ray tracing and modern shader extensions.
 */
public class VulkanDeviceInterceptor {

    // Core Ray Tracing & Acceleration Structure Extensions
    public static final String VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME = "VK_KHR_acceleration_structure";
    public static final String VK_KHR_RAY_QUERY_EXTENSION_NAME              = "VK_KHR_ray_query";
    public static final String VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME   = "VK_KHR_ray_tracing_pipeline";
    public static final String VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME = "VK_KHR_deferred_host_operations";
    public static final String VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME  = "VK_KHR_buffer_device_address";
    public static final String VK_KHR_SPIRV_1_4_EXTENSION_NAME             = "VK_KHR_spirv_1_4";
    public static final String VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME  = "VK_KHR_shader_float_controls";

    // Modern Shading & Bindless Extensions
    public static final String VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME    = "VK_EXT_descriptor_indexing";
    public static final String VK_EXT_MESH_SHADER_EXTENSION_NAME            = "VK_EXT_mesh_shader";

    private static final Set<String> REQUESTED_EXTENSIONS = Set.of(
            VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
            VK_KHR_RAY_QUERY_EXTENSION_NAME,
            VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
            VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME,
            VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME,
            VK_KHR_SPIRV_1_4_EXTENSION_NAME,
            VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME,
            VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME
    );

    /**
     * Filters available device extensions and returns the complete set
     * including vanilla extensions plus MCTrace's RT extensions.
     */
    public static List<String> augmentDeviceExtensions(List<String> vanillaExtensions, Set<String> supportedDeviceExtensions) {
        Set<String> augmented = new HashSet<>(vanillaExtensions);

        MCTrace.LOGGER.info("[MCTrace] Negotiating Vulkan device extensions...");

        boolean hasRayQuery = supportedDeviceExtensions.contains(VK_KHR_RAY_QUERY_EXTENSION_NAME);
        boolean hasAccelStruct = supportedDeviceExtensions.contains(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME);
        boolean hasBufAddr = supportedDeviceExtensions.contains(VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
        boolean hasDeferredHost = supportedDeviceExtensions.contains(VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME);

        if (hasRayQuery && hasAccelStruct && hasBufAddr && hasDeferredHost) {
            MCTrace.LOGGER.info("[MCTrace] GPU supports Hardware Ray Tracing! Activating RT extensions.");
            augmented.add(VK_KHR_RAY_QUERY_EXTENSION_NAME);
            augmented.add(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME);
            augmented.add(VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
            augmented.add(VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME);
            augmented.add(VK_KHR_SPIRV_1_4_EXTENSION_NAME);
            augmented.add(VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME);

            VulkanCapabilities.setRayQuerySupported(true);
            VulkanCapabilities.setAccelerationStructureSupported(true);
            VulkanCapabilities.setBufferDeviceAddressSupported(true);
        } else {
            MCTrace.LOGGER.warn("[MCTrace] Hardware Ray Tracing extensions not fully supported by this GPU/driver.");
        }

        if (supportedDeviceExtensions.contains(VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME)) {
            augmented.add(VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME);
            VulkanCapabilities.setRayTracingPipelineSupported(true);
        }

        if (supportedDeviceExtensions.contains(VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME)) {
            augmented.add(VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME);
            VulkanCapabilities.setDescriptorIndexingSupported(true);
        }

        if (supportedDeviceExtensions.contains(VK_EXT_MESH_SHADER_EXTENSION_NAME)) {
            augmented.add(VK_EXT_MESH_SHADER_EXTENSION_NAME);
            VulkanCapabilities.setMeshShaderSupported(true);
        }

        VulkanCapabilities.setVulkanInitialized(true);
        VulkanCapabilities.logCapabilities();

        return new ArrayList<>(augmented);
    }
}
