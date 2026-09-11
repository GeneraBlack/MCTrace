# MCTrace Custom Shader Pack Developer Guide

Welcome to the **MCTrace** Custom Shader Developer Guide! MCTrace brings hardware-accelerated Vulkan ray tracing, True 10-bit scRGB HDR, LabPBR material modeling, and AMD FSR upscaling to Minecraft Java Edition.

This guide provides everything shader creators need to create, package, and hot-reload custom shaders in MCTrace.

---

## 1. Quick Start: Shaderpack Location & Format

Shader packs reside in your game's **`shaderpacks/`** folder:
```
.minecraft/
└── shaderpacks/
    ├── ExamplePack/               <-- Uncompressed folder pack
    │   └── shaders/
    │       ├── world.fsh          <-- 3D World pass fragment shader
    │       ├── composite.fsh      <-- Final composite & presentation shader
    │       └── rayquery.comp      <-- Vulkan hardware TLAS ray query compute shader
    └── BeautifulVulkan.zip        <-- Standard .zip archive pack (same structure inside)
```

> [!TIP]
> **Selective Overriding**: Custom shader packs do **not** need to provide every shader. If you only want to customize world lighting, you only need to include `shaders/world.fsh`. MCTrace will seamlessly use internal bundled defaults for all other passes.

---

## 2. In-Game Controls & Hot-Reloading

- **`F6`**: Opens the **MCTrace Engine Settings** screen.
- Click **`Shader Packs...`** at the bottom of the `F6` screen to browse and select installed packs.
- Click **`Open Shader Pack Folder`** to immediately open `.minecraft/shaderpacks/` in Windows Explorer.
- **`F4`**: **Real-time Hot-Reload**. Press `F4` at any time during gameplay to recompile all shaders in milliseconds via Shaderc without restarting Minecraft or reloading textures.

---

## 3. Shader Architecture

MCTrace uses a multi-stage hybrid Vulkan pipeline:

1. **Hardware Ray Query Compute Pass (`rayquery.comp`)**:
   - Executes `rayQueryEXT` against Minecraft's Top-Level Acceleration Structure (`topLevelAS`).
   - Casts direct sun/moon shadow rays and cosine-weighted ambient occlusion rays.
2. **3D World Shading Pass (`world.fsh`)**:
   - Executes before depth clear and first-person hand rendering.
   - Computes normal reconstruction, contact shadows, PBR microfacet specular (GGX), foliage subsurface scattering (SSS), screen-space reflections (SSR), dynamic block radiosity, and volumetric atmosphere.
3. **Composite & Presentation Pass (`composite.fsh`)**:
   - Final presentation pass. In SDR, passes through authentic colors. In True HDR, performs scRGB 16-bit float linear decoding, paper white scaling, wide-gamut DCI-P3 expansion, and monitor peak luminance tone mapping.

---

## 4. Uniform Buffer Layout: `MCTraceParams` (std140, 128 Bytes)

All post shaders (`world.fsh`, `composite.fsh`) receive uniform buffer `MCTraceParams`:

```glsl
layout(std140) uniform MCTraceParams {
    vec4 HdrConfig;          // x: sceneBrightness, y: paperWhite, z: peakLum, w: contrast
    vec4 LightingConfig;     // x: isHdrActive, y: ssaoMultiplier, z: minLum, w: wideGamutStrength
    vec4 MaterialConfig;     // x: enablePbr, y: enableWaterReflections, z: enableDynamicColoredLight, w: time
    vec4 WeatherConfig;      // x: rainLevel, y: wetness, z: thunderLevel, w: skyAngle
    vec4 AtmosphereConfig;   // x: enableFog, y: fogDensity, z: enableGodRays, w: godRaysIntensity
    vec4 DynamicLightConfig; // x: heldLightR, y: heldLightG, z: heldLightB, w: heldLightIntensity
    vec4 CinematicConfig;    // x: enableMotionBlur, y: motionBlurStrength, z: enableDof, w: pomDepth
    vec4 AdvancedConfig;     // x: enableFoliageSss, y: foliageSssStrength, z: enableRtShadows, w: enableLabPbrTextures
};
```

### Unpacking Semantics

| Uniform Vector | Channels | Description |
|---|---|---|
| `HdrConfig` | `x, y, z, w` | `x`: Scene exposure factor (0.7-1.6)<br>`y`: Paper white in Nits (default 200)<br>`z`: Peak display luminance in Nits (e.g. 456 for GS27U)<br>`w`: Midtone contrast curve |
| `LightingConfig` | `x, y, z, w` | `x`: `1.0` if HDR swapchain is active, `0.0` for SDR<br>`y`: SSAO intensity multiplier<br>`z`: Monitor black floor luminance in Nits<br>`w`: DCI-P3 wide gamut expansion strength |
| `MaterialConfig` | `x, y, z, w` | `x`: Enable PBR materials (`1.0`/`0.0`)<br>`y`: Enable Water SSR (`1.0`/`0.0`)<br>`z`: Enable Dynamic Colored Block Light (`1.0`/`0.0`)<br>`w`: Running elapsed game time in seconds |
| `WeatherConfig` | `x, y, z, w` | `x`: Rain level (0.0 to 1.0)<br>`y`: Wetness accumulation factor<br>`z`: Thunder level (0.0 to 1.0)<br>`w`: Normalized sky angle (0.0 to 1.0) |
| `AtmosphereConfig` | `x, y, z, w` | `x`: Enable atmospheric fog (`1.0`/`0.0`)<br>`y`: Fog density factor<br>`z`: Enable god rays (`1.0`/`0.0`)<br>`w`: God rays intensity factor |
| `DynamicLightConfig` | `x, y, z, w` | `x, y, z`: Held item light color (RGB)<br>`w`: Held item light intensity (0.0 to 1.0) |
| `CinematicConfig` | `x, y, z, w` | `x`: Enable velocity motion blur<br>`y`: Motion blur strength<br>`z`: Enable bokeh depth of field<br>`w`: Parallax occlusion mapping (POM) depth scale |
| `AdvancedConfig` | `x, y, z, w` | `x`: Enable Foliage SSS (`1.0`/`0.0`)<br>`y`: Foliage SSS strength multiplier<br>`z`: Enable Hardware RT Shadows (`1.0`/`0.0`)<br>`w`: Enable LabPBR resource pack textures |

---

## 5. Hardware Ray Query Compute Interface (`rayquery.comp`)

```glsl
#version 460
#extension GL_EXT_ray_query : require
#extension GL_EXT_scalar_block_layout : enable

layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

// Top-Level Acceleration Structure (Chunks + Dynamic Entities)
layout(binding = 0, set = 0) uniform accelerationStructureEXT topLevelAS;

// G-Buffer Inputs
layout(binding = 1, set = 0) uniform sampler2D depthTexture;
layout(binding = 2, set = 0) uniform sampler2D normalTexture;

// Output Illumination Target (RGBA16F)
layout(binding = 3, set = 0, rgba16f) writeonly uniform image2D outIllumination;

layout(push_constant) uniform PushConstants {
    mat4 invViewProj;
    vec4 cameraPos;     // xyz = world pos
    vec4 lightDir;      // xyz = normalized sun direction
    vec4 lightColor;    // xyz = sun color, w = aoRadius
    int frameIndex;
    int aoSamples;
    int renderWidth;
    int renderHeight;
} pc;
```

---

## 6. Packaging & Distribution

To distribute your shader pack:
1. Ensure your files are in the `shaders/` directory (e.g. `shaders/world.fsh`, `shaders/composite.fsh`).
2. Select the `shaders` folder and compress it into a `.zip` archive (e.g. `MyShaderPack_v1.0.zip`).
3. Users simply drop `MyShaderPack_v1.0.zip` into `.minecraft/shaderpacks/` and select it from the `F6` menu!
