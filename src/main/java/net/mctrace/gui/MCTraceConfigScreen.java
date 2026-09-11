package net.mctrace.gui;

import net.mctrace.config.MCTraceConfig;
import net.mctrace.render.camera.CameraHistory;
import net.mctrace.render.gbuffer.GBufferManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Interactive settings GUI for configuring MCTrace's Ray Tracing, AMD FSR,
 * PBR Shading, SSR Water Reflections, and HDR / Wide Color Gamut parameters.
 */
public class MCTraceConfigScreen extends Screen {

    private final Screen lastScreen;

    public MCTraceConfigScreen(Screen lastScreen) {
        super(Component.literal("MCTrace Engine Settings"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = Math.max(48, this.height / 7);
        int buttonWidth = 160;
        int buttonHeight = 19;
        int spacing = 21;

        int col1X = centerX - buttonWidth - 8;
        int col2X = centerX + 8;

        // --- Top: 1-Click Quality Presets ---
        int presetY = startY - 23;
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

        // --- Row 0: Ray Tracing Mode & True HDR Display ---
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

        // --- Row 1: AMD FSR Mode & HDR Peak Luminance ---
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.FsrQualityMode>builder(
                                mode -> Component.literal("AMD FSR: " + mode.name()),
                                MCTraceConfig.fsrQualityMode
                        )
                        .withValues(MCTraceConfig.FsrQualityMode.values())
                        .create(col1X, startY + spacing, buttonWidth, buttonHeight,
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

        List<Float> peakNitsOptions = List.of(400.0f, 456.0f, 600.0f, 800.0f, 1000.0f, 1200.0f, 1500.0f, 2000.0f);
        this.addRenderableWidget(
                CycleButton.<Float>builder(
                                nits -> Component.literal("HDR Peak: " + (nits == 456.0f ? "456 Nits (GS27U)" : nits.intValue() + " Nits")),
                                MCTraceConfig.hdrPeakLuminance
                        )
                        .withValues(peakNitsOptions)
                        .create(col2X, startY + spacing, buttonWidth, buttonHeight,
                                Component.literal("HDR Peak Luminance"),
                                (btn, val) -> MCTraceConfig.hdrPeakLuminance = val)
        );

        // --- Row 2: Volumetric Fog / God Rays & Rain Wetness ---
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.AtmosphereMode>builder(
                                mode -> Component.literal("Atmosphere: " + mode.getDisplayName()),
                                MCTraceConfig.AtmosphereMode.fromConfig(MCTraceConfig.enableGodRays, MCTraceConfig.enableVolumetricFog)
                        )
                        .withValues(MCTraceConfig.AtmosphereMode.values())
                        .create(col1X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("Atmosphere Mode"),
                                (btn, val) -> {
                                    MCTraceConfig.enableGodRays = val.hasGodRays();
                                    MCTraceConfig.enableVolumetricFog = val.hasFog();
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableRainWetness)
                        .create(col2X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("Rain Wetness & Puddles"),
                                (btn, val) -> {
                                    MCTraceConfig.enableRainWetness = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // --- Row 3: PBR Materials & Parallax Occlusion (POM) ---
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enablePbrMaterials)
                        .create(col1X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("PBR Materials"),
                                (btn, val) -> {
                                    MCTraceConfig.enablePbrMaterials = val;
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

        // --- Row 4: Water SSR & Dynamic Coloured Light ---
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableWaterReflections)
                        .create(col1X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("Water SSR & Caustics"),
                                (btn, val) -> {
                                    MCTraceConfig.enableWaterReflections = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDynamicColoredLight)
                        .create(col2X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("Coloured Block Light"),
                                (btn, val) -> {
                                    MCTraceConfig.enableDynamicColoredLight = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // --- Row 5: Hand-Held Dynamic Light & DCI-P3 Wide Gamut ---
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableHeldDynamicLights)
                        .create(col1X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("Held Dynamic Light"),
                                (btn, val) -> {
                                    MCTraceConfig.enableHeldDynamicLights = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableWideGamut)
                        .create(col2X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("DCI-P3 Wide Gamut"),
                                (btn, val) -> {
                                    MCTraceConfig.enableWideGamut = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // --- Row 6: Cinematic Motion Blur & Bokeh Depth of Field ---
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableMotionBlur)
                        .create(col1X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("Velocity Motion Blur"),
                                (btn, val) -> {
                                    MCTraceConfig.enableMotionBlur = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableBokehDof)
                        .create(col2X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("Cinematic Bokeh DoF"),
                                (btn, val) -> {
                                    MCTraceConfig.enableBokehDof = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        // --- Row 7: SSAO Intensity & Display Calibration Button ---
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.SsaoIntensity>builder(
                                intensity -> Component.literal("SSAO: " + intensity.getDisplayName()),
                                MCTraceConfig.ssaoIntensity
                        )
                        .withValues(MCTraceConfig.SsaoIntensity.values())
                        .create(col1X, startY + spacing * 7, buttonWidth, buttonHeight,
                                Component.literal("SSAO Intensity"),
                                (btn, val) -> {
                                    MCTraceConfig.ssaoIntensity = val;
                                    MCTraceConfig.currentPreset = MCTraceConfig.QualityPreset.CUSTOM;
                                })
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("§6Display Calibration...§r"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new MCTraceCalibrationScreen(this));
                    }
                }).bounds(col2X, startY + spacing * 7, buttonWidth, buttonHeight).build()
        );

        // --- Bottom: Done Button ---
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> {
                    MCTraceConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.lastScreen);
                    }
                }).bounds(centerX - 100, Math.min(this.height - 24, startY + spacing * 7 + 24), 200, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int startY = Math.max(30, this.height / 8);
        int spacing = 22;

        extractor.centeredText(this.font, this.title, centerX, 8, 0xFFFFFF);
        extractor.centeredText(this.font, Component.literal("§7* Set Graphics API to Vulkan for Hardware RT & True 10-bit HDR (requires restart)§r"), centerX, Math.min(this.height - 10, startY + spacing * 7 + 44), 0xAAAAAA);
    }

    @Override
    public void onClose() {
        MCTraceConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}
