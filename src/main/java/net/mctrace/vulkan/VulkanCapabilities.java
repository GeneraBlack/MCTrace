package net.mctrace.vulkan;

import net.mctrace.MCTrace;
import org.lwjgl.vulkan.VkDevice;

public class VulkanCapabilities {

    private static boolean vulkanInitialized = false;
    private static VkDevice activeDevice = null;
    private static boolean rayQuerySupported = false;
    private static boolean rayTracingPipelineSupported = false;
    private static boolean accelerationStructureSupported = false;
    private static boolean bufferDeviceAddressSupported = false;
    private static boolean descriptorIndexingSupported = false;
    private static boolean meshShaderSupported = false;

    public static VkDevice getActiveDevice() {
        return activeDevice;
    }

    public static void setActiveDevice(VkDevice device) {
        activeDevice = device;
    }

    private static String gpuDeviceName = "Unknown GPU";
    private static String driverVersion = "Unknown Driver";

    public static boolean isVulkanInitialized() {
        return vulkanInitialized;
    }

    public static void setVulkanInitialized(boolean initialized) {
        vulkanInitialized = initialized;
    }

    public static boolean hasRayQuerySupport() {
        return rayQuerySupported;
    }

    public static void setRayQuerySupported(boolean supported) {
        rayQuerySupported = supported;
    }

    public static boolean hasRayTracingPipelineSupport() {
        return rayTracingPipelineSupported;
    }

    public static void setRayTracingPipelineSupported(boolean supported) {
        rayTracingPipelineSupported = supported;
    }

    public static boolean hasAccelerationStructureSupport() {
        return accelerationStructureSupported;
    }

    public static void setAccelerationStructureSupported(boolean supported) {
        accelerationStructureSupported = supported;
    }

    public static boolean hasBufferDeviceAddressSupport() {
        return bufferDeviceAddressSupported;
    }

    public static void setBufferDeviceAddressSupported(boolean supported) {
        bufferDeviceAddressSupported = supported;
    }

    public static boolean hasDescriptorIndexingSupport() {
        return descriptorIndexingSupported;
    }

    public static void setDescriptorIndexingSupported(boolean supported) {
        descriptorIndexingSupported = supported;
    }

    public static boolean hasMeshShaderSupport() {
        return meshShaderSupported;
    }

    public static void setMeshShaderSupported(boolean supported) {
        meshShaderSupported = supported;
    }

    public static String getGpuDeviceName() {
        return gpuDeviceName;
    }

    public static void setGpuDeviceName(String name) {
        gpuDeviceName = name;
    }

    public static String getDriverVersion() {
        return driverVersion;
    }

    public static void setDriverVersion(String version) {
        driverVersion = version;
    }

    public static void logCapabilities() {
        MCTrace.LOGGER.info("================== MCTrace Vulkan Device Diagnostics ==================");
        MCTrace.LOGGER.info(" GPU Device:                 {}", gpuDeviceName);
        MCTrace.LOGGER.info(" Driver Version:             {}", driverVersion);
        MCTrace.LOGGER.info(" Hardware Ray Query (Compute): {}", rayQuerySupported ? "ENABLED [YES]" : "UNSUPPORTED [NO]");
        MCTrace.LOGGER.info(" Acceleration Structures:    {}", accelerationStructureSupported ? "ENABLED [YES]" : "UNSUPPORTED [NO]");
        MCTrace.LOGGER.info(" Ray Tracing Pipeline:       {}", rayTracingPipelineSupported ? "AVAILABLE [YES]" : "UNSUPPORTED [NO]");
        MCTrace.LOGGER.info(" Buffer Device Address:      {}", bufferDeviceAddressSupported ? "ENABLED [YES]" : "UNSUPPORTED [NO]");
        MCTrace.LOGGER.info(" Descriptor Indexing (PBR):  {}", descriptorIndexingSupported ? "ENABLED [YES]" : "UNSUPPORTED [NO]");
        MCTrace.LOGGER.info(" Mesh Shaders:               {}", meshShaderSupported ? "AVAILABLE [YES]" : "UNSUPPORTED [NO]");
        MCTrace.LOGGER.info("=======================================================================");
    }
}
