using System;
using System.Runtime.InteropServices;
using System.Collections.Generic;

public class GpuDetector
{
    // Constants for DXGI error codes and D3D12 features
    const int DXGI_ERROR_NOT_FOUND = unchecked((int)0x887A0002);
    const int D3D_FEATURE_LEVEL_11_0 = 0xb000;
    const int D3D12_FEATURE_ARCHITECTURE1 = 16;
    // Structs for adapter description and architecture info
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct DXGI_ADAPTER_DESC1
    {
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string Description;
        public uint VendorId;
        public uint DeviceId;
        public uint SubSysId;
        public uint Revision;
        public ulong DedicatedVideoMemory;
        public ulong DedicatedSystemMemory;
        public ulong SharedSystemMemory;
        public LUID AdapterLuid;
        public uint Flags;
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct LUID
    {
        public uint LowPart;
        public int HighPart;
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct D3D12_FEATURE_DATA_ARCHITECTURE1
    {
        public uint NodeIndex;
        [MarshalAs(UnmanagedType.Bool)]
        public bool TileBasedRenderer;
        [MarshalAs(UnmanagedType.Bool)]
        public bool UMA;
        [MarshalAs(UnmanagedType.Bool)]
        public bool CacheCoherentUMA;
        [MarshalAs(UnmanagedType.Bool)]
        public bool IsolatedMMU;
    }
    // GUIDs for DXGI factory and D3D12 device
    static Guid IID_IDXGIFactory1 = new Guid("770aae78-f26f-4dba-a829-253c83d1b387");
    static Guid IID_ID3D12Device = new Guid("189819f1-1db6-4b57-be54-1821339b85f7");
    // P/Invoke for DXGI and D3D12 functions
    [DllImport("dxgi.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int CreateDXGIFactory1(ref Guid riid, out IntPtr ppFactory);
    [DllImport("d3d12.dll", CallingConvention = CallingConvention.StdCall)]
    static extern int D3D12CreateDevice(IntPtr pAdapter, int MinimumFeatureLevel, ref Guid riid, out IntPtr ppDevice);
    // Main method to detect GPUs
    public static List<string> DetectGpus()
    {
        // Use dictionary to track unique GPUs by description and handle type conflicts
        Dictionary<string, string> uniqueGpus = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        Guid factoryGuid = IID_IDXGIFactory1;
        IntPtr factory = IntPtr.Zero;
        int hr = CreateDXGIFactory1(ref factoryGuid, out factory);
        if (hr < 0)
        {
            Console.WriteLine("Warning: Failed to create DXGI Factory - HR: 0x" + hr.ToString("x") + ", no adapters will be enumerated");
            return new List<string> { "Warning: No GPUs detected due to DXGI factory creation failure (HR: 0x" + hr.ToString("x") + ")" };
        }
        // Get vtable pointers for factory methods
        IntPtr factoryVtable = IntPtr.Zero;
        try
        {
            factoryVtable = Marshal.ReadIntPtr(factory);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Warning: Failed to read factory vtable - Exception: " + ex.Message + ", cannot release factory");
            return new List<string> { "Warning: No GPUs detected due to factory vtable read failure (Exception: " + ex.Message + ")" };
        }
        if (factoryVtable == IntPtr.Zero)
        {
            Console.WriteLine("Warning: factoryVtable is zero, cannot release factory");
            return new List<string> { "Warning: No GPUs detected due to zero factory vtable" };
        }
        IntPtr releasePtr = IntPtr.Zero;
        try
        {
            releasePtr = Marshal.ReadIntPtr(factoryVtable, 2 * IntPtr.Size);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Warning: Failed to read factory release pointer - Exception: " + ex.Message + ", cannot release factory");
            return new List<string> { "Warning: No GPUs detected due to factory release pointer read failure (Exception: " + ex.Message + ")" };
        }
        if (releasePtr == IntPtr.Zero)
        {
            Console.WriteLine("Warning: factory releasePtr is zero, cannot release factory");
            return new List<string> { "Warning: No GPUs detected due to zero factory release pointer" };
        }
        ReleaseDelegate release = Marshal.GetDelegateForFunctionPointer<ReleaseDelegate>(releasePtr);
        IntPtr enumAdapters1Ptr = IntPtr.Zero;
        try
        {
            enumAdapters1Ptr = Marshal.ReadIntPtr(factoryVtable, 12 * IntPtr.Size);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Warning: Failed to read EnumAdapters1 pointer from factory vtable - Exception: " + ex.Message + ", releasing factory");
            release(factory);
            return new List<string> { "Warning: No GPUs detected due to EnumAdapters1 pointer read failure (Exception: " + ex.Message + ")" };
        }
        if (enumAdapters1Ptr == IntPtr.Zero)
        {
            Console.WriteLine("Warning: EnumAdapters1Ptr is zero, releasing factory");
            release(factory);
            return new List<string> { "Warning: No GPUs detected due to zero EnumAdapters1 pointer" };
        }
        EnumAdapters1Delegate enumAdapters1 = Marshal.GetDelegateForFunctionPointer<EnumAdapters1Delegate>(enumAdapters1Ptr);
        // Enumerate adapters in a loop
        uint index = 0;
        while (true)
        {
            IntPtr adapter = IntPtr.Zero;
            hr = enumAdapters1(factory, index, out adapter);
            if (hr == DXGI_ERROR_NOT_FOUND) break;
            if (hr < 0)
            {
                Console.WriteLine("Warning: EnumAdapters1 failed for index " + index + " - HR: 0x" + hr.ToString("x") + ", skipping adapter");
                index++;
                continue;
            }
            if (adapter == IntPtr.Zero)
            {
                Console.WriteLine("Warning: EnumAdapters1 returned null adapter for index " + index + ", skipping");
                index++;
                continue;
            }
            index++;
            // Get vtable pointers for adapter methods
            IntPtr adapterVtable = IntPtr.Zero;
            try
            {
                adapterVtable = Marshal.ReadIntPtr(adapter);
            }
            catch (Exception ex)
            {
                Console.WriteLine("Warning: Failed to read adapter vtable for index " + (index-1) + " - Exception: " + ex.Message + ", cannot release adapter");
                continue;
            }
            if (adapterVtable == IntPtr.Zero)
            {
                Console.WriteLine("Warning: adapterVtable is zero for index " + (index-1) + ", cannot release adapter or proceed");
                continue;
            }
            IntPtr adapterReleasePtr = IntPtr.Zero;
            try
            {
                adapterReleasePtr = Marshal.ReadIntPtr(adapterVtable, 2 * IntPtr.Size);
            }
            catch (Exception ex)
            {
                Console.WriteLine("Warning: Failed to read adapter release pointer for index " + (index-1) + " - Exception: " + ex.Message + ", cannot release adapter safely");
                continue;
            }
            if (adapterReleasePtr == IntPtr.Zero)
            {
                Console.WriteLine("Warning: adapterReleasePtr is zero for index " + (index-1) + ", cannot release adapter");
                continue;
            }
            ReleaseDelegate adapterRelease = Marshal.GetDelegateForFunctionPointer<ReleaseDelegate>(adapterReleasePtr);
            IntPtr getDesc1Ptr= IntPtr.Zero;
            try
            {
                getDesc1Ptr = Marshal.ReadIntPtr(adapterVtable, 10 * IntPtr.Size);
            }
            catch (Exception ex)
            {
                Console.WriteLine("Warning: Failed to read GetDesc1 pointer for index " + (index-1) + " - Exception: " + ex.Message + ", releasing adapter");
                adapterRelease(adapter);
                continue;
            }
            if (getDesc1Ptr == IntPtr.Zero)
            {
                Console.WriteLine("Warning: getDesc1Ptr is zero for index " + (index-1) + ", releasing adapter");
                adapterRelease(adapter);
                continue;
            }
            GetDesc1Delegate getDesc1 = Marshal.GetDelegateForFunctionPointer<GetDesc1Delegate>(getDesc1Ptr);
            DXGI_ADAPTER_DESC1 desc = new DXGI_ADAPTER_DESC1();
            hr = getDesc1(adapter, out desc);
            string description;
            string debugInfo;
            string type = null;
            if (hr < 0)
            {
                Console.WriteLine("Warning: Failed to get adapter description for index " + (index-1) + " - HR: 0x" + hr.ToString("x") + " - assuming INTEGRATED with placeholder desc, releasing adapter");
                description = "Unknown Adapter [index " + (index-1) + "]";
                debugInfo = "index " + (index-1) + ", description '" + description + "', VendorId unknown, DeviceId unknown";
                type = "INTEGRATED";
                // Add even on desc failure
                string entry = type + " : " + description;
                string existingType;
                if (uniqueGpus.TryGetValue(description, out existingType))
                {
                    if (existingType != type)
                    {
                        Console.WriteLine("Warning: Conflicting type for '" + description + "' (" + debugInfo + "): " + existingType + " vs " + type + " - preferring INTEGRATED assuming hybrid");
                        uniqueGpus[description] = "INTEGRATED";
                    }
                }
                else
                {
                    uniqueGpus[description] = type;
                }
                adapterRelease(adapter);
                continue;
            }
            else
            {
                description = desc.Description.Trim();
                debugInfo = "index " + (index-1) + ", description '" + description + "', VendorId 0x" + desc.VendorId.ToString("X") + ", DeviceId 0x" + desc.DeviceId.ToString("X");
            }
            // Try to create D3D12 device for accurate type detection
            Guid deviceGuid = IID_ID3D12Device;
            IntPtr device = IntPtr.Zero;
            hr = D3D12CreateDevice(adapter, D3D_FEATURE_LEVEL_11_0, ref deviceGuid, out device);
            bool deviceCreated = (hr >= 0 && device != IntPtr.Zero);
            ReleaseDelegate deviceRelease = null;
            try
            {
                if (!deviceCreated)
                {
                    if (hr < 0)
                    {
                        Console.WriteLine("Warning: Failed to create D3D12 device - " + debugInfo + ", HR: 0x" + hr.ToString("x") + " - assuming INTEGRATED");
                    }
                    else
                    {
                        Console.WriteLine("Warning: D3D12CreateDevice returned null device - " + debugInfo + ", HR: 0x" + hr.ToString("x") + " - assuming INTEGRATED");
                    }
                    type = "INTEGRATED";
                }
                else
                {
                    // Get vtable pointers for device methods
                    IntPtr deviceVtable = IntPtr.Zero;
                    try
                    {
                        deviceVtable = Marshal.ReadIntPtr(device);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine("Warning: Failed to read device vtable - " + debugInfo + ", Exception: " + ex.Message + " - assuming INTEGRATED, attempting safe release");
                        type = "INTEGRATED";
                        if (device != IntPtr.Zero) Marshal.Release(device); // Fallback release
                    }
                    if (deviceVtable == IntPtr.Zero)
                    {
                        Console.WriteLine("Warning: deviceVtable is zero - " + debugInfo + " - assuming INTEGRATED, attempting safe release");
                        type = "INTEGRATED";
                        if (device != IntPtr.Zero) Marshal.Release(device); // Fallback release
                    }
                    if (type == null) // Proceed only if not already assumed
                    {
                        IntPtr deviceReleasePtr = IntPtr.Zero;
                        try
                        {
                            deviceReleasePtr = Marshal.ReadIntPtr(deviceVtable, 2 * IntPtr.Size);
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine("Warning: Failed to read device release pointer - " + debugInfo + ", Exception: " + ex.Message + " - assuming INTEGRATED, attempting safe release");
                            type = "INTEGRATED";
                            if (device != IntPtr.Zero) Marshal.Release(device); // Fallback release
                        }
                        if (deviceReleasePtr == IntPtr.Zero)
                        {
                            Console.WriteLine("Warning: deviceReleasePtr is zero - " + debugInfo + " - assuming INTEGRATED, attempting safe release");
                            type = "INTEGRATED";
                            if (device != IntPtr.Zero) Marshal.Release(device); // Fallback release
                        }
                        if (type == null) // Proceed only if not already assumed
                        {
                            deviceRelease = Marshal.GetDelegateForFunctionPointer<ReleaseDelegate>(deviceReleasePtr);
                            IntPtr checkFeaturePtr = IntPtr.Zero;
                            try
                            {
                                checkFeaturePtr = Marshal.ReadIntPtr(deviceVtable, 13 * IntPtr.Size);
                            }
                            catch (Exception ex)
                            {
                                Console.WriteLine("Warning: Failed to read CheckFeatureSupport pointer - " + debugInfo + ", Exception: " + ex.Message + " - assuming INTEGRATED");
                                type = "INTEGRATED";
                            }
                            if (checkFeaturePtr == IntPtr.Zero)
                            {
                                Console.WriteLine("Warning: checkFeaturePtr is zero - " + debugInfo + " - assuming INTEGRATED");
                                type = "INTEGRATED";
                            }
                            if (type == null) // Proceed to query only if not assumed
                            {
                                CheckFeatureSupportDelegate checkFeature = Marshal.GetDelegateForFunctionPointer<CheckFeatureSupportDelegate>(checkFeaturePtr);
                                // Query architecture to determine if UMA (integrated)
                                D3D12_FEATURE_DATA_ARCHITECTURE1 architecture = new D3D12_FEATURE_DATA_ARCHITECTURE1();
                                IntPtr archPtr = IntPtr.Zero;
                                try
                                {
                                    archPtr = Marshal.AllocHGlobal(Marshal.SizeOf(architecture));
                                    Marshal.StructureToPtr(architecture, archPtr, true);
                                    hr = checkFeature(device, D3D12_FEATURE_ARCHITECTURE1, archPtr, (uint)Marshal.SizeOf(architecture));
                                    if (hr < 0)
                                    {
                                        Console.WriteLine("Warning: CheckFeatureSupport failed - " + debugInfo + ", HR: 0x" + hr.ToString("x") + " - assuming INTEGRATED");
                                        type = "INTEGRATED";
                                    }
                                    else
                                    {
                                        architecture = Marshal.PtrToStructure<D3D12_FEATURE_DATA_ARCHITECTURE1>(archPtr);
                                        type = architecture.UMA ? "INTEGRATED" : "DEDICATED";
                                    }
                                }
                                catch (Exception ex)
                                {
                                    Console.WriteLine("Warning: Exception during architecture query - " + debugInfo + ", Exception: " + ex.Message + " - assuming INTEGRATED");
                                    type = "INTEGRATED";
                                }
                                finally
                                {
                                    if (archPtr != IntPtr.Zero) Marshal.FreeHGlobal(archPtr);
                                }
                            }
                        }
                    }
                }
            }
            finally
            {
                // Always attempt release if device was created
                if (deviceCreated)
                {
                    if (deviceRelease != null)
                    {
                        deviceRelease(device);
                    }
                    else if (device != IntPtr.Zero)
                    {
                        Marshal.Release(device); // Fallback if delegate unavailable
                    }
                }
            }
            // Add type if determined
            if (type != null)
            {
                string entry = type + " : " + description;
                // Deduplicate by description; prefer INTEGRATED if conflict and warn
                string existingType;
                if (uniqueGpus.TryGetValue(description, out existingType))
                {
                    if (existingType != type)
                    {
                        Console.WriteLine("Warning: Conflicting type for '" + description + "' (" + debugInfo + "): " + existingType + " vs " + type + " - preferring INTEGRATED assuming hybrid");
                        uniqueGpus[description] = "INTEGRATED";
                    }
                }
                else
                {
                    uniqueGpus[description] = type;
                }
            }
            else
            {
                Console.WriteLine("Warning: Type determination failed entirely - " + debugInfo + " - skipping adapter");
            }
            adapterRelease(adapter);
        }
        release(factory);
        // Convert dictionary to list of entries
        List<string> results = new List<string>();
        foreach (var kvp in uniqueGpus)
        {
            results.Add(kvp.Value + " : " + kvp.Key);
        }
        if (results.Count == 0)
        {
            results.Add("Warning: No valid GPUs detected after enumeration");
        }
        return results;
    }
    // Delegate definitions for COM methods
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int EnumAdapters1Delegate(IntPtr thisPtr, uint adapterIndex, out IntPtr ppAdapter);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int GetDesc1Delegate(IntPtr thisPtr, out DXGI_ADAPTER_DESC1 pDesc);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int CheckFeatureSupportDelegate(IntPtr thisPtr, int Feature, IntPtr pFeatureSupportData, uint FeatureSupportDataSize);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate uint ReleaseDelegate(IntPtr thisPtr);
}