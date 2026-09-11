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
 * and SVGF denoising parameters.
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
        int startY = this.height / 6;
        int buttonWidth = 220;
        int buttonHeight = 20;
        int spacing = 24;

        // 1. Ray Tracing Mode
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.RayTracingMode>builder(
                                mode -> Component.literal("Ray Tracing: " + mode.name()),
                                MCTraceConfig.rayTracingMode
                        )
                        .withValues(MCTraceConfig.RayTracingMode.values())
                        .create(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight,
                                Component.literal("Ray Tracing Mode"),
                                (btn, val) -> MCTraceConfig.rayTracingMode = val)
        );

        // 2. AMD FSR Quality Mode
        this.addRenderableWidget(
                CycleButton.<MCTraceConfig.FsrQualityMode>builder(
                                mode -> Component.literal("AMD FSR: " + mode.name()),
                                MCTraceConfig.fsrQualityMode
                        )
                        .withValues(MCTraceConfig.FsrQualityMode.values())
                        .create(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight,
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

        // 3. FSR Sharpness Presets
        List<Float> sharpnessLevels = List.of(0.0f, 0.4f, 0.6f, 0.8f, 1.0f);
        this.addRenderableWidget(
                CycleButton.<Float>builder(
                                s -> Component.literal("FSR Sharpness: " + (int) (s * 100) + "%"),
                                MCTraceConfig.fsrSharpness
                        )
                        .withValues(sharpnessLevels)
                        .create(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight,
                                Component.literal("FSR Sharpness"),
                                (btn, val) -> MCTraceConfig.fsrSharpness = val)
        );

        // 4. SVGF Denoiser Toggle
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableDenoiser)
                        .create(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight,
                                Component.literal("SVGF Denoiser"),
                                (btn, val) -> MCTraceConfig.enableDenoiser = val)
        );

        // 5. True HDR Display Toggle
        this.addRenderableWidget(
                CycleButton.onOffBuilder(MCTraceConfig.enableHDR)
                        .create(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight,
                                Component.literal("True HDR Display"),
                                (btn, val) -> MCTraceConfig.enableHDR = val)
        );

        // 6. HDR Peak Luminance
        List<Float> peakNitsOptions = List.of(400.0f, 600.0f, 800.0f, 1000.0f, 1200.0f, 1500.0f, 2000.0f);
        this.addRenderableWidget(
                CycleButton.<Float>builder(
                                nits -> Component.literal("HDR Peak: " + nits.intValue() + " Nits"),
                                MCTraceConfig.hdrPeakLuminance
                        )
                        .withValues(peakNitsOptions)
                        .create(centerX - buttonWidth / 2, startY + spacing * 5, buttonWidth, buttonHeight,
                                Component.literal("HDR Peak Luminance"),
                                (btn, val) -> MCTraceConfig.hdrPeakLuminance = val)
        );

        // 7. Done Button
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.lastScreen);
                    }
                }).bounds(centerX - 100, this.height - 40, 200, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        extractor.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}
