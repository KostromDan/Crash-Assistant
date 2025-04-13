package dev.kostromdan.mods.crash_assistant.app.utils;

import dev.kostromdan.mods.crash_assistant.app.CrashAssistantApp;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class GPUDetector {
    public static void main(String[] args) {
        // Step 1: Create Vulkan instance
        VkInstance instance = createVulkanInstance();

        // Step 2 & 3: Enumerate physical devices and get their properties
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get the number of physical devices
            IntBuffer deviceCount = stack.mallocInt(1);
            VK10.vkEnumeratePhysicalDevices(instance, deviceCount, null);
            if (deviceCount.get(0) == 0) {
                CrashAssistantApp.LOGGER.info("No Vulkan-compatible GPUs found.");
                return;
            }

            // Allocate buffer for physical devices
            PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            VK10.vkEnumeratePhysicalDevices(instance, deviceCount, devices);

            // Step 4: Iterate over devices and check their type
            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), instance);
                VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
                VK10.vkGetPhysicalDeviceProperties(device, properties);

                String deviceName = properties.deviceNameString();
                int deviceType = properties.deviceType();

                CrashAssistantApp.LOGGER.info("GPU: " + deviceName);
                if (deviceType == VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) {
                    CrashAssistantApp.LOGGER.info("Type: Integrated");
                } else if (deviceType == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                    CrashAssistantApp.LOGGER.info("Type: Dedicated");
                } else {
                    CrashAssistantApp.LOGGER.info("Type: Other");
                }
            }
        }

        // Step 6: Clean up
        VK10.vkDestroyInstance(instance, null);
    }

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