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
        int startY = Math.max(30, this.height / 8);
        int buttonWidth = 160;
        int buttonHeight = 20;
        int spacing = 22;

        int col1X = centerX - buttonWidth - 10;
        int col2X = centerX + 10;

        // --- Row 0: Ray Tracing Mode & True HDR Display ---
        // 1. Ray Tracing Mode
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.RayTracingMode>builder(
                                mode -> Component.literal("Ray Tracing: " + mode.name()),
                                MCTraceConfig.rayTracingMode
                        )
                        .withValues(MCTraceConfig.RayTracingMode.values())
                        .create(col1X, startY, buttonWidth, buttonHeight,
                                Component.literal("Ray Tracing Mode"),
                                (btn, val) -> MCTraceConfig.rayTracingMode = val)
        );

        // 2. True HDR Display Toggle
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableHDR)
                        .create(col2X, startY, buttonWidth, buttonHeight,
                                Component.literal("True HDR Display"),
                                (btn, val) -> MCTraceConfig.enableHDR = val)
        );

        // --- Row 1: AMD FSR Mode & HDR Peak Luminance ---
        // 3. AMD FSR Quality Mode
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
                                    if (this.minecraft != null && this.minecraft.getWindow() != null) {
                                        GBufferManager.initOrResize(
                                                this.minecraft.getWindow().getWidth(),
                                                this.minecraft.getWindow().getHeight()
                                        );
                                        CameraHistory.requestReset();
                                    }
                                })
        );

        // 4. HDR Peak Luminance (includes 456 Nits for GS27U)
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

        // --- Row 2: FSR Sharpness & HDR Paper White ---
        // 5. FSR Sharpness Presets
        List<Float> sharpnessLevels = List.of(0.0f, 0.4f, 0.6f, 0.8f, 1.0f);
        this.addRenderableWidget(
                CycleButton.<Float>builder(
                                s -> Component.literal("FSR Sharpness: " + (int) (s * 100) + "%"),
                                MCTraceConfig.fsrSharpness
                        )
                        .withValues(sharpnessLevels)
                        .create(col1X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("FSR Sharpness"),
                                (btn, val) -> MCTraceConfig.fsrSharpness = val)
        );

        // 6. HDR Paper White (UI Brightness)
        List<Float> paperWhiteOptions = List.of(100.0f, 150.0f, 200.0f, 250.0f, 300.0f);
        this.addRenderableWidget(
                CycleButton.<Float>builder(
                                nits -> Component.literal("Paper White: " + nits.intValue() + " Nits"),
                                MCTraceConfig.hdrPaperWhite
                        )
                        .withValues(paperWhiteOptions)
                        .create(col2X, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("HDR Paper White"),
                                (btn, val) -> MCTraceConfig.hdrPaperWhite = val)
        );

        // --- Row 3: SVGF Denoiser & Ray Query Quality ---
        // 7. SVGF Denoiser Toggle
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDenoiser)
                        .create(col1X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("SVGF Denoiser"),
                                (btn, val) -> MCTraceConfig.enableDenoiser = val)
        );

        // 8. Ray Query Quality
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.RayQueryQuality>builder(
                                quality -> Component.literal("RT Quality: " + quality.getDisplayName()),
                                MCTraceConfig.rayQueryQuality
                        )
                        .withValues(MCTraceConfig.RayQueryQuality.values())
                        .create(col2X, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("Ray Query Quality"),
                                (btn, val) -> MCTraceConfig.rayQueryQuality = val)
        );

        // --- Row 4: PBR Materials & Screen-Space Water Reflections (SSR) ---
        // 9. PBR Materials (LabPBR Specular & Roughness)
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enablePbrMaterials)
                        .create(col1X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("PBR Materials"),
                                (btn, val) -> MCTraceConfig.enablePbrMaterials = val)
        );

        // 10. Screen-Space Water & Glass Reflections (SSR) + Caustics
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableWaterReflections)
                        .create(col2X, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("Water SSR & Caustics"),
                                (btn, val) -> MCTraceConfig.enableWaterReflections = val)
        );

        // --- Row 5: Dynamic Coloured Lighting & Wide Gamut DCI-P3 ---
        // 11. Coloured Dynamic Block Lighting
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDynamicColoredLight)
                        .create(col1X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("Coloured Block Light"),
                                (btn, val) -> MCTraceConfig.enableDynamicColoredLight = val)
        );

        // 12. DCI-P3 / BT.2020 Wide Color Gamut
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableWideGamut)
                        .create(col2X, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("DCI-P3 Wide Gamut"),
                                (btn, val) -> MCTraceConfig.enableWideGamut = val)
        );

        // --- Row 6: SSAO Intensity & Display Calibration Button ---
        // 13. SSAO & Shadow Intensity
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.SsaoIntensity>builder(
                                intensity -> Component.literal("SSAO: " + intensity.getDisplayName()),
                                MCTraceConfig.ssaoIntensity
                        )
                        .withValues(MCTraceConfig.SsaoIntensity.values())
                        .create(col1X, startY + spacing * 6, buttonWidth, buttonHeight,
                                Component.literal("SSAO Intensity"),
                                (btn, val) -> MCTraceConfig.ssaoIntensity = val)
        );

        // 14. Display & HDR Calibration Button
        this.addRenderableWidget(
                Button.builder(Component.literal("§6Display Calibration...§r"), btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new MCTraceCalibrationScreen(this));
                    }
                }).bounds(col2X, startY + spacing * 6, buttonWidth, buttonHeight).build()
        );

        // --- Row 7: Graphics Backend Selector ---
        if (this.minecraft != null && this.minecraft.options != null) {
            net.minecraft.client.PreferredGraphicsApi currentApi = this.minecraft.options.preferredGraphicsBackend().get();
            this.addRenderableWidget(
                    CycleButton.<net.minecraft.client.PreferredGraphicsApi>builder(
                                    api -> Component.literal("Graphics API: " + (api == net.minecraft.client.PreferredGraphicsApi.VULKAN ? "§aVulkan (True HDR)§r" : api.caption().getString())),
                                    currentApi
                            )
                            .withValues(net.minecraft.client.PreferredGraphicsApi.values())
                            .create(centerX - 110, startY + spacing * 7, 220, 20,
                                     Component.literal("Graphics API"),
                                    (btn, val) -> {
                                        this.minecraft.options.preferredGraphicsBackend().set(val);
                                        this.minecraft.options.save();
                                    })
            );
        }

        // --- Bottom: Done Button ---
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> {
                    MCTraceConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.lastScreen);
                    }
                }).bounds(centerX - 100, Math.min(this.height - 24, startY + spacing * 7 + 34), 200, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int startY = Math.max(30, this.height / 8);
        int spacing = 22;

        extractor.centeredText(this.font, this.title, centerX, 12, 0xFFFFFF);
        extractor.centeredText(this.font, Component.literal("§7* Set Graphics API to Vulkan for Hardware RT & True 10-bit HDR (requires restart)§r"), centerX, startY + spacing * 7 + 22, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        MCTraceConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}
