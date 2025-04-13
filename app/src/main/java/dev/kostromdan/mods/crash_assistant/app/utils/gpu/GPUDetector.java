package dev.kostromdan.mods.crash_assistant.app.utils.gpu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class GPUDetector {
    /**
     * Detects GPUs using Vulkan and returns a list of GPU records.
     * Each record contains the GPU's type and name.
     *
     * @return a List of GPU objects representing the detected GPUs
     * @throws RuntimeException if Vulkan instance creation or device enumeration fails
     */
    public static List<GPU> detectGPUs() {
        // Create Vulkan instance
        VkInstance instance = createVulkanInstance();

        // List to store detected GPUs
        List<GPU> gpus = new ArrayList<>();

        // Use MemoryStack for native memory allocation
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get the number of physical devices
            IntBuffer deviceCount = stack.mallocInt(1);
            int err = VK10.vkEnumeratePhysicalDevices(instance, deviceCount, null);
            if (err != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to enumerate physical devices: " + err);
            }

            // If no devices are found, return an empty list
            if (deviceCount.get(0) == 0) {
                VK10.vkDestroyInstance(instance, null);
                return gpus;
            }

            // Allocate buffer for physical devices
            PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            VK10.vkEnumeratePhysicalDevices(instance, deviceCount, devices);

            // Iterate over devices and collect their properties
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), instance);
                VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceProperties(device, properties);

                String deviceName = properties.deviceNameString();
                int deviceType = properties.deviceType();

                // Map Vulkan device type to RendererType
                RendererType type;
                if (deviceType == VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
                    type = RendererType.INTEGRATED;
                } else if (deviceType == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                    type = RendererType.DEDICATED;
                } else {
                    type = RendererType.UNKNOWN;
                }

                // Add GPU record to the list
                gpus.add(new GPU(type, deviceName));
            }
        }

        // Clean up Vulkan instance
        VK10.vkDestroyInstance(instance, null);

        return gpus;
    }

    /**
     * Creates a Vulkan instance for GPU detection.
     *
     * @return the created VkInstance
     * @throws RuntimeException if instance creation fails
     */
    private static VkInstance createVulkanInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Application info
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("GPU Detector"))
                    .applicationVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8("No Engine"))
                    .engineVersion(VK10.VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK10.VK_API_VERSION_1_0);

            // Instance create info
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo);

            // Pointer to store the instance
            PointerBuffer instancePtr = stack.mallocPointer(1);
            if (VK10.vkCreateInstance(createInfo, null, instancePtr) != VK10.VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance");
            }

            return new VkInstance(instancePtr.get(0), createInfo);
        }
    }
}