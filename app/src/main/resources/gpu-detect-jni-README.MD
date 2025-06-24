# GPU Detect

A lightweight utility for detecting and categorizing GPUs on Windows systems as either integrated or dedicated.

## Overview

This project was created to replace the Vulkan-based GPU detection logic used
in the [Crash Assistant](https://github.com/KostromDan/Crash-Assistant) project for showing a warning if Minecraft is
running on an integrated GPU while a dedicated GPU is available. This is a very common problem for Windows notebooks.
The original implementation was increasing the mod size by ~5 MB, and some modpack creators were not happy
about this. Therefore, this lightweight alternative was developed using DirectX APIs instead
(~20 KB in jar zip).

Source code and full README.MD are available in https://github.com/KostromDan/gpu-detect