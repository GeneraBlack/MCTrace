<div align="center">

<img src="https://raw.githubusercontent.com/GeneraBlack/MCTrace/master/src/main/resources/icon.png" width="128" height="128" alt="MCTrace Logo" />

# ⚡ MCTrace
### Next-Gen Hardware Ray Tracing, AMD FSR & Modern Vulkan Shaders for Minecraft

[![NeoForge](https://img.shields.io/badge/Modloader-NeoForge%2026.2-blue.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green.svg)](https://www.minecraft.net/)
[![GitHub](https://img.shields.io/badge/GitHub-GeneraBlack%2FMCTrace-181717.svg?logo=github)](https://github.com/GeneraBlack/MCTrace)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Vulkan](https://img.shields.io/badge/Graphics%20API-Vulkan%201.3%2B-red.svg)](https://www.vulkan.org/)

**MCTrace** transforms Minecraft Java Edition into a modern, photorealistic rendering playground powered by Minecraft 26.2's native **Vulkan** backend and **NeoForge**. Experience real-time hardware ray tracing, temporal upscaling via AMD FSR, true 10-bit scRGB HDR display support, and full LabPBR material realism.

---

</div>

## 🌟 Highlights at a Glance

* 🚀 **Vulkan Hardware Ray Tracing:** Native acceleration structures (BLAS / TLAS) and inline compute ray queries (`VK_KHR_ray_query`) for sharp penumbra sun/moon shadows and physical ambient occlusion.
* ⚡ **AMD FidelityFX Super Resolution (FSR 2 / 3):** Temporal upscaling with subpixel camera jitter, 2D motion vector extraction, and RCAS edge sharpening for fluid high-framerate gameplay at 1440p and 4K.
* 🌈 **True 10-Bit scRGB HDR & Wide Color Gamut:** Native HDR swapchain presentation with DCI-P3 / BT.2020 gamut expansion and an interactive, AAA-grade in-game display calibration screen.
* 💎 **LabPBR & Next-Gen World Shading:** GGX microfacet specular reflections, screen-space water reflections (SSR), dynamic underwater caustics, and Parallax Occlusion Mapping (POM) for true 3D surface depth.
* 🌿 **Subsurface Scattering (SSS):** Sunlight diffuses through leaves, grass, and foliage with natural backlit translucency.
* 💡 **Dynamic Radiosity & Colored Block Lights:** Emissive blocks cast vibrant colored light, with built-in held dynamic lights.
* 🎨 **Shader Pack Ecosystem & Instant Hot-Reload:** Load custom shader packs from your `.minecraft/shaderpacks/` folder, and press **`F4`** to recompile and hot-reload shaders in milliseconds without restarting the game.
* 🛠️ **In-Game Settings GUI:** Configure all engine features on the fly by pressing **`F6`** or selecting MCTrace in the NeoForge Mods menu.

---

## 📸 Feature Deep-Dive

### 🚀 Hardware-Accelerated Ray Tracing
MCTrace bypasses legacy screen-space approximations by constructing GPU Top-Level (TLAS) and Bottom-Level (BLAS) Acceleration Structures directly from Minecraft's voxel chunk geometry and dynamic entities:
* **True Distance Shadows:** Accurate contact hardening, soft penumbra, and cast shadows from blocks and mobs.
* **Ray-Traced Ambient Occlusion (RTAO):** Rich contact shadows in deep caves, corners, and foliage without screen-edge cutoff.
* **SVGF Denoising Pipeline:** Spatio-Temporal Variance-Guided Filtering reconstructs noise-free images from minimal sample counts.

### ⚡ AMD FSR Temporal Upscaling
Render internally at a lower resolution and temporally reconstruct a pristine image up to 4K:
* **Presets:** *Ultra Quality (0.77x)*, *Quality (0.67x)*, *Balanced (0.58x)*, *Performance (0.50x)*, or *Off (Native 1.0x)*.
* **Subpixel Jitter & Velocity Buffers:** Eliminates temporal shimmering and edge crawling during rapid camera turns.
* **Robust RCAS Sharpening:** Fine-tune image clarity without over-sharpening artifacts.

### 🌈 True HDR & In-Game Calibration Screen
Unlock the full potential of your OLED, Mini-LED, or HDR-capable display:
* Built-in **Display Calibration Screen** with live visual test patches.
* Fine-tune **Peak Luminance** (supports up to 2000 Nits, with presets for popular monitors like 456 Nits), **Paper White UI Luminance**, **Black Level Floor** (0.000 nits for OLED), and **Middle Gray Contrast**.
* DCI-P3 & BT.2020 Wide Color Gamut for deep greens, fiery sunset oranges, and neon reds.

### 💎 LabPBR Texturing & Volumetric Atmosphere
* **LabPBR Standard:** Seamlessly compatible with community LabPBR resource packs featuring `_n` (normals) and `_s` (smoothness / metalness / reflectance) textures.
* **Parallax Occlusion Mapping (POM):** Cobblestone, bricks, and bark pop with real geometrical relief and self-shadowing.
* **Atmospheric Volumetrics:** Dynamic god rays piercing through dense tree canopies and volumetric fog responding to weather.
* **Dynamic Weather:** Realistic wetness accumulation and glossy puddles forming during rainstorms.

---

## 🎮 Controls & Keybinds

| Key | Action | Description |
| :---: | :--- | :--- |
| **`F6`** | **MCTrace Settings GUI** | Open the in-game configuration screen to adjust presets, ray tracing, HDR, and shaders. |
| **`F4`** | **Hot-Reload Shaders** | Instantly recompile SPIR-V shaders in memory without reloading textures or restarting Minecraft. |
| **`K`** | **Toggle Shading Effects** | Quickly toggle all MCTrace post-processing effects on and off for instant comparisons. |

---

## ⚙️ 1-Click Quality Presets

MCTrace features three optimized 1-click presets accessible from the top of the **`F6`** menu:

* ⚡ **Performance:** Fast Ray Query, FSR Balanced, subtle SSAO, optimized volumetric fog and god rays, foliage SSS, dynamic lights, and rain wetness. Perfect for mid-range GPUs.
* ⚖ **Balanced (Default):** Standard Ray Query, FSR Quality, standard SSAO, full volumetrics, water SSR & caustics, LabPBR, POM (0.04x depth), foliage SSS, and hardware RT shadows.
* 💎 **Ultra HDR / Cinematic:** Maximum ray query samples, FSR Ultra Quality, enhanced SSAO, boosted god rays, POM (0.06x depth), and True 10-bit HDR wide-gamut tuning.

---

## 📋 System Requirements

| Component | Minimum | Recommended |
| :--- | :--- | :--- |
| **Minecraft** | Java Edition 26.2 | Java Edition 26.2 |
| **Mod Loader** | NeoForge 26.2.0.86+ | NeoForge 26.2.0.86+ |
| **Java Runtime** | Java 25 or 26 (64-bit) | Java 25 or 26 (64-bit) |
| **Graphics API** | Vulkan 1.3+ with Ray Tracing (`VK_KHR_ray_query`) | Vulkan 1.3+ with Ray Tracing |
| **NVIDIA GPU** | GeForce RTX 2060 (6GB) | GeForce RTX 3070 / 4070 or newer |
| **AMD GPU** | Radeon RX 6600 XT (8GB) | Radeon RX 7700 XT / 7800 XT or newer |
| **Intel GPU** | Intel Arc A580 / A750 | Intel Arc A770 or newer |

---

## 🚀 Installation & First-Time Activation Guide

> [!IMPORTANT]
> ### ⚠️ First-Time Setup: Up to 3 Game Launches Required
> Minecraft 26.2 defaults to an OpenGL graphics engine. Because MCTrace transitions the engine to native **Vulkan** and configures NeoForge's windowing subsystem, your first-time setup may take **up to 3 game launches** to reach full steady state:
>
> 1. **Launch 1 (Mod Installation & OpenGL Baseline):**
>    * Drop `mctrace-1.0.0.jar` into your `.minecraft/mods` folder and launch Minecraft normally.
>    * MCTrace automatically updates `config/fml.toml` (`earlyWindowControl = false`) to prevent GLFW error 65540.
>    * Open **Options $\to$ Video Settings $\to$ Graphics Backend**, switch the backend to **Vulkan**, and exit the game.
> 2. **Launch 2 (Vulkan Handshake & Pipeline Generation):**
>    * Launch Minecraft again. The game now boots natively into Vulkan without OpenGL window conflicts.
>    * MCTrace queries your GPU for ray tracing extensions (`VK_KHR_ray_query`, `VK_KHR_acceleration_structure`) and compiles initial SPIR-V pipelines.
> 3. **Launch 3 (Full Steady-State Activation):**
>    * Launch Minecraft once more. The Vulkan swapchain, TLAS scene graph, SVGF denoiser passes, and AMD FSR temporal reconstruction are fully active and persistent!
>
> *(💡 Pro-Tip: If you launch Minecraft directly with the `--renderBackend vulkan` JVM argument, MCTrace activates immediately on the very first launch).*

---

## 🎨 Custom Shader Packs

MCTrace supports custom Vulkan shader packs:
1. Open the in-game settings screen with **`F6`** and click **`Shader Packs...`**.
2. Click **`Open Shader Pack Folder`** to reveal `.minecraft/shaderpacks/`.
3. Drop any compatible `.zip` or uncompressed shader folder into this directory.
4. Select your pack from the list and hit Done!
5. Press **`F4`** during gameplay to hot-reload shader changes instantly while developing.

---

## ❓ Frequently Asked Questions (FAQ)

<details>
<summary><b>Is MCTrace compatible with OptiFine or Iris?</b></summary>
<p>No. OptiFine and Iris are built entirely on legacy OpenGL pipelines. MCTrace is a completely rewritten, modern Vulkan ray tracing engine. Do not install OptiFine or Iris alongside MCTrace.</p>
</details>

<details>
<summary><b>Why do I get GLFW Error 65540?</b></summary>
<p>This occurs if an early window attempts to create an OpenGL context when Vulkan expects <code>GLFW_NO_API</code>. MCTrace includes an automated bootstrapper that disables NeoForge's early loading window (<code>earlyWindowControl = false</code> in <code>config/fml.toml</code>). Ensure you complete the 3-launch setup described above.</p>
</details>

<details>
<summary><b>Can I use MCTrace on a GTX 10-series or older GPU?</b></summary>
<p>No. MCTrace requires hardware support for the <code>VK_KHR_ray_query</code> and <code>VK_KHR_acceleration_structure</code> Vulkan extensions, which require NVIDIA RTX (Turing+), AMD RDNA2 (RX 6000+), or Intel Arc graphics cards.</p>
</details>

<details>
<summary><b>Does MCTrace work on servers / multiplayer?</b></summary>
<p>Yes! MCTrace is 100% <b>client-side</b>. You can connect to any vanilla or NeoForge 26.2 server without needing the mod installed on the server.</p>
</details>

<details>
<summary><b>How do I enable True HDR?</b></summary>
<p>Ensure HDR is enabled in Windows Display Settings (Win + Alt + B). Launch Minecraft with the Vulkan backend active, press <b><code>F6</code></b>, toggle <b>True HDR Display</b> to ON, and click <b>Display Calibration...</b> to calibrate your monitor's peak and paper white nits.</p>
</details>

---

## 📦 Modpack & Redistribution Policy

* **Modpacks:** You are free to include MCTrace in any public or private modpack on CurseForge, Modrinth, or custom launchers.
* **Source Code & Contributions:** Hosted on [GitHub (GeneraBlack/MCTrace)](https://github.com/GeneraBlack/MCTrace), licensed under the [MIT License](https://opensource.org/licenses/MIT).
* **Bug Reports & Feedback:** Please report any issues or GPU feature requests on our [GitHub Issue Tracker](https://github.com/GeneraBlack/MCTrace/issues).

---

<div align="center">
<i>Crafted with passion for next-gen graphics by <b>BlackLightningStudio</b></i>
</div>
