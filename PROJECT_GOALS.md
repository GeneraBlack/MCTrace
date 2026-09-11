# MCTrace: Project Goals & Technical Architecture

**MCTrace** is a next-generation graphics and ray tracing engine built on top of **Minecraft Java Edition 26.2**'s native Vulkan backend and the **NeoForge** modding framework.

---

## 1. Executive Summary

Minecraft 26.2 introduces an experimental, opt-in Vulkan rendering backend. This officially transitions the game away from legacy single-threaded OpenGL and unlocks modern graphics hardware capabilities.

The goal of **MCTrace** is to leverage this architectural shift to build a high-performance, hardware-accelerated ray tracing and modern shading pipeline with native AMD FSR support directly in Java Edition.

---

## 2. Core Pillars & Goals

### Pillar 1: Native Hardware Ray Tracing (Vulkan RT)
* **API Extensions:** Hook into the engine's Vulkan device initialization to enable:
  * `VK_KHR_acceleration_structure`
  * `VK_KHR_ray_query` (initial focus for inline compute ray tracing)
  * `VK_KHR_ray_tracing_pipeline` (optional future path for full SBT pipelines)
  * `VK_KHR_buffer_device_address`
  * `VK_KHR_deferred_host_operations`
* **Acceleration Structures (BVH):**
  * **BLAS (Bottom-Level Acceleration Structures):** Construct and cache BVHs for sub-chunk (16x16x16) meshes, block models, foliage, and animated entities. Only rebuild dirty chunks when blocks are placed or broken.
  * **TLAS (Top-Level Acceleration Structure):** Fast per-frame scene graph instance buffer containing transforms for all visible chunks and active entities.
* **Ray Tracing Features:**
  * Hardware-accelerated direct shadows (sun, moon, local block lights).
  * Ray-traced glossy and specular reflections on water, metals, and polished blocks.
  * Diffuse Global Illumination (GI) and ambient occlusion without screen-space cutoff.
* **Denoising Pipeline:**
  * Spatio-Temporal Variance-Guided Filtering (SVGF / A-SVGF) to reconstruct clean images from low sample counts (1–2 samples per pixel).

---

### Pillar 2: Modern Temporal Upscaling (AMD FSR 2 / 3)
* **Temporal Reconstruction:** Enable rendering at lower internal resolutions (e.g., 1080p internal $\to$ 4K display) with sharp temporal reconstruction.
* **Pipeline Requirements:**
  * **Subpixel Camera Jitter:** Implement a Halton sequence jitter pattern applied to the projection matrix.
  * **Velocity / Motion Vector Buffer:** Compute screen-space 2D velocity vectors for moving camera, terrain shifts, and dynamic entities.
  * **Linear Depth & Color Buffers:** Feed pre-tonemapped HDR color and linear depth into the upscaler.
* **Integration Strategy:**
  * Integrate the AMD FidelityFX SDK compute passes (SPIR-V) into the Vulkan render graph.

---

### Pillar 3: Next-Generation Vulkan Shader Pipeline
* **SPIR-V & Modern Shader Languages:**
  * Move away from archaic runtime GLSL string compilation.
  * Support modern pre-compiled SPIR-V shaders authored in **Slang**, **HLSL**, or modern **GLSL 4.60**.
* **Bindless Materials & High-Res PBR (`VK_EXT_descriptor_indexing`):**
  * Eliminate legacy texture atlas bleeding and draw call constraints.
  * Direct array/descriptor indexing for normal maps, roughness, metallic, emissive, and height maps.
* **GPU-Driven Compute & Render Graph:**
  * Asynchronous compute passes for atmospheric scattering, volumetric fog, and water simulation.
  * Dynamic render graph allowing arbitrary multi-pass ping-ponging without fixed OptiFine framebuffer constraints.
* **Future Tech: Mesh Shaders (`VK_EXT_mesh_shader`):**
  * GPU-driven geometry generation, culling, and Level of Detail (LOD) for distant terrain.

---

## 3. High-Level Architecture

```
                      +---------------------------------------+
                      |       Minecraft 26.2 (Blaze3D)        |
                      +---------------------------------------+
                                          |
                      +---------------------------------------+
                      |         NeoForge / Mixin Hooks        |
                      |  - VkInstance / VkDevice Interceptor  |
                      |  - Chunk Mesh Extraction              |
                      |  - Camera & Render Target Hooks       |
                      +---------------------------------------+
                                          |
            +-----------------------------+-----------------------------+
            |                                                           |
+-----------------------+                                   +-----------------------+
|  Vulkan RT Pipeline   |                                   |  G-Buffer & Upscale   |
|  - BLAS / TLAS Builder|                                   |  - Depth / Normals    |
|  - Ray Query Compute  |                                   |  - Motion Vectors     |
|  - SVGF Denoiser      |                                   |  - Subpixel Jitter    |
+-----------------------+                                   |  - AMD FSR 2/3 Pass   |
            \                                               +-----------------------+
             \                                                         /
              +---------------------------+---------------------------+
                                          |
                              +-----------------------+
                              | Post-Processing & HDR |
                              |  - Tonemapping        |
                              |  - Presentation       |
                              +-----------------------+
```

---

## 4. Phased Implementation Roadmap

| Phase | Milestone | Deliverables |
| :--- | :--- | :--- |
| **Phase 1** | **Foundation & Vulkan Hooks** | NeoForge 26.2 Gradle workspace; Vulkan device creation interceptor; query and verify RT extensions on host GPU. |
| **Phase 2** | **Render Targets & G-Buffers** | Custom HDR render targets; linear depth buffer; camera subpixel jittering; motion vector pass. |
| **Phase 3** | **Acceleration Structures (BVH)** | Chunk mesh data extraction; BLAS allocator and builder; per-frame TLAS manager. |
| **Phase 4** | **Ray Traced Lighting & Denoising** | First ray query compute shader (hard shadows / AO); SVGF temporal/spatial denoising filter. |
| **Phase 5** | **Upscaling Integration (FSR)** | FidelityFX FSR 2/3 compute pass integration; resolution scaling UI controls. |
| **Phase 6** | **Shader Pipeline Expansion** | Bindless PBR material support; dynamic render graph; Slang/SPIR-V shader pack loading. |

---

## 5. Engine Boot Lifecycle & First-Time Launch Sequence

### NeoForge Early Window & Vulkan Architecture
NeoForge FML defaults to an early OpenGL loading window (`earlyWindowControl = true`). Minecraft 26.2's native Vulkan backend requires a GLFW window initialized with `GLFW_NO_API` (otherwise causing GLFW error 65540). 

MCTrace features a self-unclaiming `MCTraceGraphicsBootstrapper` SPI service to automatically synchronize `config/fml.toml` without hiding the mod from NeoForge's `InDevFolderLocator` / `ModsFolderLocator`.

### Worst-Case Startup Lifecycle (Up to 3 Starts)
In the worst-case scenario when a user transitions from a default OpenGL Minecraft installation to Vulkan:
1. **Launch 1 (OpenGL Baseline):** The game boots in OpenGL. MCTrace automatically writes `earlyWindowControl = false` into `config/fml.toml`. The user switches graphics backend to Vulkan in Video Settings.
2. **Launch 2 (Vulkan Handshake):** Minecraft switches to Vulkan with no early OpenGL window interference. Initial SPIR-V shaders compile and device physical features (`VK_KHR_ray_query`) are negotiated.
3. **Launch 3 (Full Steady-State Operation):** G-buffers, BVH acceleration structures, SVGF denoising, and FSR temporal pipelines are fully linked and persistent across sessions.

