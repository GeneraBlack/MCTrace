package net.mctrace.gui;

import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.mctrace.vulkan.pbr.HDTextureOptimizer;
import net.mctrace.vulkan.profiler.MCTraceGpuProfiler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Interactive settings GUI for configuring MCTrace's Ray Tracing, AMD FSR,
 * PBR Shading, SSR Water Reflections, HDR, and Next-Gen Features (ReSTIR GI,
 * Frame Generation, Volumetric Clouds, Ocean FFT, Profiler & Photo Mode).
 */
public class MCTraceConfigScreen extends Screen {

    private final Screen lastScreen;
    private int currentTab = 0; // 0 = Core & Display, 1 = Next-Gen Features

    public MCTraceConfigScreen(Screen lastScreen) {
        super(Component.literal("MCTrace Engine Settings"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = Math.max(50, this.height / 10 + 16);
        int buttonWidth = 160;
        int buttonHeight = 18;
        int spacing = 20;

        int col1X = centerX - buttonWidth - 8;
        int col2X = centerX + 8;

        // --- Top: 1-Click Quality Presets ---
        int presetY = startY - 36;
        int pW = 104;
        this.addRenderableWidget(
                Button.builder(Component.literal(MCTraceConfig.currentPreset == MCTraceConfig.QualityPreset.PERFORMANCE ? "§e[⚡ Performance]§r" : "§7⚡ Performance§r"), btn -> {
                    MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.PERFORMANCE);
                    this.clearWidgets();
                    this.init();
                }).bounds(centerX - 162, presetY, pW, 18).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal(MCTraceConfig.currentPreset == MCTraceConfig.QualityPreset.BALANCED ? "§a[⚖ Balanced]§r" : "§7⚖ Balanced§r"), btn -> {
                    MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.BALANCED);
                    this.clearWidgets();
                    this.init();
                }).bounds(centerX - 53, presetY, 106, 18).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal(MCTraceConfig.currentPreset == MCTraceConfig.QualityPreset.ULTRA_HDR ? "§b[💎 Ultra HDR]§r" : "§7💎 Ultra HDR§r"), btn -> {
                    MCTraceConfig.applyPreset(MCTraceConfig.QualityPreset.ULTRA_HDR);
                    this.clearWidgets();
                    this.init();
                }).bounds(centerX + 58, presetY, pW, 18).build()
        );

        // --- Tabs Navigation ---
        int tabY = startY - 16;
        int tabW = 160;
        this.addRenderableWidget(
                Button.builder(Component.literal(currentTab == 0 ? "§e§l[⚡ Core & Display]§r" : "§7⚡ Core & Display§r"), btn -> {
                    currentTab = 0;
                    this.clearWidgets();
                    this.init();
                }).bounds(centerX - tabW - 4, tabY, tabW, 16).build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal(currentTab == 1 ? "§d§l[🌟 Next-Gen Features]§r" : "§7🌟 Next-Gen Features§r"), btn -> {
                    currentTab = 1;
                    this.clearWidgets();
                    this.init();
                }).bounds(centerX + 4, tabY, tabW, 16).build()
        );

        if (currentTab == 0) {
            initCoreTab(col1X, col2X, startY, buttonWidth, buttonHeight, spacing);
        } else {
            initNextGenTab(col1X, col2X, startY, buttonWidth, buttonHeight, spacing);
        }

        // --- Bottom: Shader Packs & Done Buttons ---
        int bottomY = Math.min(this.height - 24, startY + spacing * 8 + 16);
        this.addRenderableWidget(
                Button.builder(Component.literal("§eShader Packs...§r"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new MCTraceShaderPackScreen(this));
                    }
                }).bounds(centerX - 125, bottomY, 120, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> {
                    MCTraceConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.lastScreen);
                    }
                }).bounds(centerX + 5, bottomY, 120, 20).build()
        );
    }

    private void initCoreTab(int col1X, int col2X, int startY, int buttonWidth, int buttonHeight, int spacing) {
        // Row 0: Ray Tracing Mode & True HDR Display
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.RayTracingMode>builder(
                                mode -> Component.literal("Ray Tracing: " + mode.name()),
                                MCTraceConfig.rayTracingMode
                        )
                        .withValues(MCTraceConfig.RayTracingMode.values())
                        .create(col1X, startY, buttonWidth, buttonHeight,
                                Component.literal("Ray Tracing Mode"),
                                (btn, val) -> {
                                    MCTraceConfig.rayTracingMode = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableHDR)
                        .create(col2X, startY, buttonWidth, buttonHeight,
                                Component.literal("True HDR Display"),
                                (btn, val) -> {
                                    MCTraceConfig.enableHDR = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 1: Hardware RT Shadows & AMD FSR Mode
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableRtShadows)
                        .create(col1X, startY + spacing, buttonWidth, buttonHeight,
                                Component.literal("Hardware RT Shadows"),
                                (btn, val) -> {
                                    MCTraceConfig.enableRtShadows = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.FsrQualityMode>builder(
                                mode -> Component.literal("AMD FSR: " + mode.name()),
                                MCTraceConfig.fsrQualityMode
                        )
                        .withValues(MCTraceConfig.FsrQualityMode.values())
                        .create(col2X, startY + spacing, buttonWidth, buttonHeight,
                                Component.literal("AMD FSR Mode"),
                                (btn, val) -> {
                                    MCTraceConfig.fsrQualityMode = val;
                                    MCTraceConfig.enableFSR = (val != MCTraceConfig.FsrQualityMode.OFF);
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                    if (this.minecraft != null && this.minecraft.getWindow() != null) {
                                        GBufferManager.initOrResize(
                                                this.minecraft.getWindow().getWidth(),
                                                this.minecraft.getWindow().getHeight()
                                        );
                                        CameraHistory.requestReset();
                                    }
                                })
        );

        // Row 2: Foliage SSS Glow & HDR Peak Luminance
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableFoliageSss)
                        .create(col1X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("Foliage SSS Glow"),
                                (btn, val) -> {
                                    MCTraceConfig.enableFoliageSss = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        List<Float> peakNitsOptions = List.of(400.0f, 456.0f, 600.0f, 800.0f, 1000.0f, 1200.0f, 1500.0f, 2000.0f);
        this.addRenderableWidget(
                CycleButton.<Float>builder(
                                nits -> Component.literal("HDR Peak: " + (nits == 456.0f ? "456 Nits (GS27U)" : nits.intValue() + " Nits")),
                                MCTraceConfig.hdrPeakLuminance
                        )
                        .withValues(peakNitsOptions)
                        .create(col2X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("HDR Peak Luminance"),
                                (btn, val) -> MCTraceConfig.hdrPeakLuminance = val)
        );

        // Row 3: LabPBR Textures (_n/_s) & Parallax 3D Relief (POM)
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableLabPbrTextures)
                        .create(col1X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("LabPBR Textures"),
                                (btn, val) -> {
                                    MCTraceConfig.enableLabPbrTextures = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableParallaxOcclusion)
                        .create(col2X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("Parallax 3D Relief (POM)"),
                                (btn, val) -> {
                                    MCTraceConfig.enableParallaxOcclusion = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 4: PBR Materials & Atmosphere Mode
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enablePbrMaterials)
                        .create(col1X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("PBR Materials"),
                                (btn, val) -> {
                                    MCTraceConfig.enablePbrMaterials = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.AtmosphereMode>builder(
                                mode -> Component.literal("Atmosphere: " + mode.getDisplayName()),
                                MCTraceConfig.AtmosphereMode.fromConfig(MCTraceConfig.enableGodRays, MCTraceConfig.enableVolumetricFog)
                        )
                        .withValues(MCTraceConfig.AtmosphereMode.values())
                        .create(col2X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("Atmosphere Mode"),
                                (btn, val) -> {
                                    MCTraceConfig.enableGodRays = val.hasGodRays();
                                    MCTraceConfig.enableVolumetricFog = val.hasFog();
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 5: Rain Wetness & Water SSR
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableRainWetness)
                        .create(col1X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("Rain Wetness & Puddles"),
                                (btn, val) -> {
                                    MCTraceConfig.enableRainWetness = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableWaterReflections)
                        .create(col2X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("Water SSR & Caustics"),
                                (btn, val) -> {
                                    MCTraceConfig.enableWaterReflections = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 6: Coloured Block Light & Held Dynamic Light
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDynamicColoredLight)
                        .create(col1X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("Coloured Block Light"),
                                (btn, val) -> {
                                    MCTraceConfig.enableDynamicColoredLight = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableHeldDynamicLights)
                        .create(col2X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("Held Dynamic Light"),
                                (btn, val) -> {
                                    MCTraceConfig.enableHeldDynamicLights = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 7: DCI-P3 Wide Gamut & SSAO Intensity
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableWideGamut)
                        .create(col1X, startY + spacing * 7, buttonWidth, buttonHeight,
                                Component.literal("DCI-P3 Wide Gamut"),
                                (btn, val) -> {
                                    MCTraceConfig.enableWideGamut = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.SsaoIntensity>builder(
                                intensity -> Component.literal("SSAO: " + intensity.getDisplayName()),
                                MCTraceConfig.ssaoIntensity
                        )
                        .withValues(MCTraceConfig.SsaoIntensity.values())
                        .create(col2X, startY + spacing * 7, buttonWidth, buttonHeight,
                                Component.literal("SSAO Intensity"),
                                (btn, val) -> {
                                    MCTraceConfig.ssaoIntensity = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 8: Velocity Motion Blur & Display Calibration Button
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableMotionBlur)
                        .create(col1X, startY + spacing * 8, buttonWidth, buttonHeight,
                                Component.literal("Velocity Motion Blur"),
                                (btn, val) -> {
                                    MCTraceConfig.enableMotionBlur = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("§6Display Calibration...§r"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new MCTraceCalibrationScreen(this));
                    }
                }).bounds(col2X, startY + spacing * 8, buttonWidth, buttonHeight).build()
        );
    }

    private void initNextGenTab(int col1X, int col2X, int startY, int buttonWidth, int buttonHeight, int spacing) {
        // Row 0: ReSTIR GI & Stained Glass Colored Shadows
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableRestirGi)
                        .create(col1X, startY, buttonWidth, buttonHeight,
                                Component.literal("ReSTIR Multi-Bounce GI"),
                                (btn, val) -> {
                                    MCTraceConfig.enableRestirGi = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableColoredShadows)
                        .create(col2X, startY, buttonWidth, buttonHeight,
                                Component.literal("Colored Stained Glass Shadows"),
                                (btn, val) -> {
                                    MCTraceConfig.enableColoredShadows = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 1: RT Refraction & Dynamic Snow
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableRefraction)
                        .create(col1X, startY + spacing, buttonWidth, buttonHeight,
                                Component.literal("Ray-Traced Refraction & Dispersion"),
                                (btn, val) -> {
                                    MCTraceConfig.enableRefraction = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDynamicSnow)
                        .create(col2X, startY + spacing, buttonWidth, buttonHeight,
                                Component.literal("Dynamic Snow Accumulation"),
                                (btn, val) -> {
                                    MCTraceConfig.enableDynamicSnow = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 2: AMD FSR 3 Frame Generation & Dynamic Resolution Scaling (DRS)
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableFrameGeneration)
                        .create(col1X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("FSR 3 Frame Generation"),
                                (btn, val) -> {
                                    MCTraceConfig.enableFrameGeneration = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDrs)
                        .create(col2X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("Dynamic Resolution Scaling (DRS)"),
                                (btn, val) -> {
                                    MCTraceConfig.enableDrs = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 3: 3D Volumetric Clouds & Bruneton Physical Sky
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableVolumetricClouds)
                        .create(col1X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("3D Volumetric Clouds"),
                                (btn, val) -> {
                                    MCTraceConfig.enableVolumetricClouds = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enablePhysicalSky)
                        .create(col2X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("Bruneton Physical Sky"),
                                (btn, val) -> {
                                    MCTraceConfig.enablePhysicalSky = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 4: Phillips FFT Ocean & Mob Subsurface Scattering
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableFftOcean)
                        .create(col1X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("Phillips FFT Ocean Waves"),
                                (btn, val) -> {
                                    MCTraceConfig.enableFftOcean = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableMobSss)
                        .create(col2X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("Mob & Wax Subsurface Scattering"),
                                (btn, val) -> {
                                    MCTraceConfig.enableMobSss = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 5: GPU Meshlet LOD (Mesh Shaders) & ReSTIR Spatial Samples
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableMeshShaders)
                        .create(col1X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("GPU Meshlet LOD (Mesh Shaders)"),
                                (btn, val) -> {
                                    MCTraceConfig.enableMeshShaders = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        List<Integer> samplesList = List.of(1, 2, 4, 8);
        this.addRenderableWidget(
                CycleButton.<Integer>builder(
                                s -> Component.literal("ReSTIR Spatial: " + s + " Samples"),
                                MCTraceConfig.restirSpatialSamples
                        )
                        .withValues(samplesList)
                        .create(col2X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("ReSTIR Spatial Samples"),
                                (btn, val) -> {
                                    MCTraceConfig.restirSpatialSamples = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // Row 6: Vulkan GPU Profiler Mode & HD Texture Pack Optimizer
        this.addRenderableWidget(
                CycleButton.<MCTraceGpuProfiler.ProfilerDisplayMode>builder(
                                mode -> Component.literal("Profiler: " + mode.getDisplayName()),
                                MCTraceConfig.profilerMode
                        )
                        .withValues(MCTraceGpuProfiler.ProfilerDisplayMode.values())
                        .create(col1X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("GPU Profiler Mode"),
                                (btn, val) -> {
                                    MCTraceConfig.profilerMode = val;
                                    MCTraceConfig.showGpuProfiler = (val != MCTraceGpuProfiler.ProfilerDisplayMode.OFF);
                                    MCTraceConfig.save();
                                })
        );

        this.addRenderableWidget(
                CycleButton.<HDTextureOptimizer.TextureResolutionLimit>builder(
                                limit -> Component.literal("HD Tex: " + limit.getDisplayName()),
                                MCTraceConfig.hdTextureMode
                        )
                        .withValues(HDTextureOptimizer.TextureResolutionLimit.values())
                        .create(col2X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("HD Texture Optimizer"),
                                (btn, val) -> {
                                    MCTraceConfig.hdTextureMode = val;
                                    MCTraceConfig.save();
                                })
        );

        // Row 7: Distance POM LoD & Toksvig Specular AA
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDistancePomLod)
                        .create(col1X, startY + spacing * 7, buttonWidth, buttonHeight,
                                Component.literal("Distance POM LoD"),
                                (btn, val) -> {
                                    MCTraceConfig.enableDistancePomLod = val;
                                    MCTraceConfig.save();
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableSpecularAntiAliasing)
                        .create(col2X, startY + spacing * 7, buttonWidth, buttonHeight,
                                Component.literal("Toksvig Specular AA"),
                                (btn, val) -> {
                                    MCTraceConfig.enableSpecularAntiAliasing = val;
                                    MCTraceConfig.save();
                                })
        );

        // Row 8: Launch Photo Mode
        this.addRenderableWidget(
                Button.builder(Component.literal("§d📷 Launch Photo Mode [F8]...§r"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new MCTracePhotoModeScreen(this));
                    }
                }).bounds(col1X, startY + spacing * 8, buttonWidth * 2 + 16, buttonHeight).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int startY = Math.max(50, this.height / 10 + 16);
        int spacing = 20;

        extractor.centeredText(this.font, this.title, centerX, 8, 0xFFFFFF);
        extractor.centeredText(this.font, Component.literal("§7* Set Graphics API to Vulkan for Hardware RT & True 10-bit HDR (requires restart)§r"), centerX, Math.min(this.height - 10, startY + spacing * 8 + 38), 0xAAAAAA);
    }

    @Override
    public void onClose() {
        MCTraceConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}
