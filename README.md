# MCTrace

[![NeoForge](https://img.shields.io/badge/Modloader-NeoForge%2026.2-blue.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green.svg)](https://www.minecraft.net/)
[![GitHub](https://img.shields.io/badge/GitHub-GeneraBlack%2FMCTrace-181717.svg?logo=github)](https://github.com/GeneraBlack/MCTrace)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**MCTrace** is an experimental next-generation ray tracing, upscaling, and modern shader engine for **Minecraft Java Edition 26.2**, powered by the native Vulkan rendering backend and **NeoForge**.

Developed by **BlackLightningStudio** ([@GeneraBlack](https://github.com/GeneraBlack)).

---

## Highlights

* 🚀 **Hardware Ray Tracing:** Direct integration with modern GPU RT hardware using `VK_KHR_ray_query` and acceleration structures (BLAS / TLAS).
* ⚡ **AMD FSR Support:** Temporal upscaling (FSR 2 / 3) with motion vector generation and subpixel jitter for high frame rates at 1440p and 4K.
* 🎨 **Modern Vulkan Shaders:** Transition away from legacy OpenGL GLSL to pre-compiled SPIR-V bytecode, bindless textures (`VK_EXT_descriptor_indexing`), and GPU-driven compute passes.
* 🛠️ **In-Game Configuration:** Built-in settings screen accessible directly from the NeoForge **Mods** tab or via the `F6` hotkey.

---

## System Requirements

* **Minecraft:** Java Edition 26.2
* **Mod Loader:** NeoForge 26.2.0.86 or newer
* **Java Version:** Java 25 or 26
* **GPU Hardware:** Vulkan 1.3+ compatible GPU with Ray Tracing support:
  * NVIDIA GeForce RTX 20 series or newer
  * AMD Radeon RX 6000 / 7000 series or newer
  * Intel Arc A-Series or newer

---

## First-Time Setup & Activation

> [!NOTE]
> ### ⚠️ Worst-Case Scenario: Up to 3 Game Starts Required
> Because Minecraft 26.2 defaults to an OpenGL backend, transitioning to the native Vulkan engine alongside NeoForge's early loading system may require **up to 3 game launches** before MCTrace is actively rendering:
>
> 1. **Launch 1 (Mod Installation & OpenGL Baseline):**
>    * The game starts up using Minecraft's default OpenGL backend.
>    * MCTrace automatically updates `config/fml.toml` (`earlyWindowControl = false`) to eliminate potential GLFW error 65540 conflicts.
>    * Go to **Options $\to$ Video Settings $\to$ Graphics Backend** and select **Vulkan**. Minecraft will prompt you to restart.
> 2. **Launch 2 (Vulkan Engine Transition):**
>    * Minecraft launches using the native Vulkan backend for the first time without early window OpenGL lockups.
>    * Shader caches, Vulkan device extensions (`VK_KHR_ray_query`, `VK_KHR_acceleration_structure`), and initial SPIR-V pipelines are generated.
> 3. **Launch 3 (Full Pipeline Activation & Steady State):**
>    * The Vulkan swapchain, TLAS scene graph, SVGF denoiser passes, and AMD FSR temporal reconstruction are fully active, synchronized, and persistent.
>
> *(Note: If you launch Minecraft directly with the `--renderBackend vulkan` command-line argument, MCTrace automatically detects it and activates on the first launch).*

---

## Configuration & Keybinds

* **In-Game Menu:** Press **`F6`** at any time to open the MCTrace Settings GUI.
* **Mods Menu:** Go to the main menu **Mods** tab $\to$ select **MCTrace** $\to$ click **Config**.
* Configurable options:
  * Ray Tracing Mode (Disabled, Fast Ray Query Shadows & AO, Full Path-Traced GI)
  * AMD FSR Quality Preset (Native, Quality, Balanced, Performance, Ultra Performance)
  * RCAS Sharpness slider (0.0 to 1.0)
  * Denoiser Temporal / Spatial passes

---

## Documentation & Architecture

* [Project Goals & Technical Architecture](PROJECT_GOALS.md) — Comprehensive breakdown of architectural pillars, Vulkan pipeline details, and implementation phases.
* [Custom Shader Developer Guide](SHADERS.md) — Documentation for shader creators writing SPIR-V shaders and shader packs.
* [CurseForge Project Page](CURSEFORGE.md) — Formatted description and release documentation for CurseForge.
