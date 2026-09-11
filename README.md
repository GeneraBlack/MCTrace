# MCTrace

**MCTrace** is an experimental next-generation ray tracing, upscaling, and modern shader engine for **Minecraft Java Edition 26.2**, powered by the native Vulkan rendering backend and **NeoForge**.

## Highlights

* 🚀 **Hardware Ray Tracing:** Direct integration with modern GPU RT hardware using `VK_KHR_ray_query` and acceleration structures (BLAS / TLAS).
* ⚡ **AMD FSR Support:** Temporal upscaling (FSR 2 / 3) with motion vector generation and subpixel jitter for high frame rates at 1440p and 4K.
* 🎨 **Modern Vulkan Shaders:** Transition away from legacy OpenGL GLSL to pre-compiled SPIR-V bytecode (Slang / HLSL), bindless textures (`VK_EXT_descriptor_indexing`), and GPU-driven compute passes.

## Documentation

* [Project Goals & Technical Architecture](PROJECT_GOALS.md) - In-depth breakdown of pillars, technical requirements, and the 6-phase implementation roadmap.
