package net.mctrace.gui;

import net.mctrace.config.MCTraceConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * AAA-grade HDR & Display Calibration Screen.
 *
 * Provides live visual test patches and continuous sliders to precisely tune
 * Black Level (Lowest light floor), Paper White (Middle Gray / UI exposure),
 * Peak Luminance (Highest light clipping point), and Contrast.
 */
public class MCTraceCalibrationScreen extends Screen {

    private final Screen lastScreen;

    public MCTraceCalibrationScreen(Screen lastScreen) {
        super(Component.literal("MCTrace Display Calibration"));
        this.lastScreen = lastScreen;
    }

    private static class CalibrationSlider extends AbstractSliderButton {
        private final Consumer<Double> onValueChange;
        private final Function<Double, Component> messageProvider;

        public CalibrationSlider(int x, int y, int width, int height, double initialNormalized,
                                 Function<Double, Component> messageProvider,
                                 Consumer<Double> onValueChange) {
            super(x, y, width, height, messageProvider.apply(initialNormalized), initialNormalized);
            this.messageProvider = messageProvider;
            this.onValueChange = onValueChange;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.messageProvider.apply(this.value));
        }

        @Override
        protected void applyValue() {
            this.onValueChange.accept(this.value);
        }

        public void updateNormalizedValue(double newVal) {
            this.value = Math.max(0.0, Math.min(1.0, newVal));
            this.updateMessage();
        }
    }

    private CalibrationSlider minLumSlider;
    private CalibrationSlider paperWhiteSlider;
    private CalibrationSlider peakLumSlider;
    private CalibrationSlider contrastSlider;

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int sliderStartY = Math.max(132, this.height / 2 + 10);
        int sliderWidth = 160;
        int sliderHeight = 20;
        int spacing = 24;

        int col1X = centerX - sliderWidth - 10;
        int col2X = centerX + 10;

        // 1. Min Luminance / Black Level (0.000 to 0.100 Nits)
        double initialMin = Math.max(0.0, Math.min(1.0, MCTraceConfig.hdrMinLuminance / 0.100f));
        minLumSlider = new CalibrationSlider(
                col1X, sliderStartY, sliderWidth, sliderHeight, initialMin,
                val -> {
                    float nits = (float) (val * 0.100);
                    return Component.literal(String.format("Black: %.3f Nits", nits));
                },
                val -> MCTraceConfig.hdrMinLuminance = (float) (val * 0.100)
        );
        this.addRenderableWidget(minLumSlider);

        // 2. Paper White / Middle Gray (80 to 400 Nits)
        double initialPaper = Math.max(0.0, Math.min(1.0, (MCTraceConfig.hdrPaperWhite - 80.0f) / 320.0f));
        paperWhiteSlider = new CalibrationSlider(
                col1X, sliderStartY + spacing, sliderWidth, sliderHeight, initialPaper,
                val -> {
                    int nits = (int) (80.0 + val * 320.0);
                    return Component.literal(String.format("Paper White: %d Nits", nits));
                },
                val -> MCTraceConfig.hdrPaperWhite = (float) (80.0 + val * 320.0)
        );
        this.addRenderableWidget(paperWhiteSlider);

        // 3. Peak Luminance / Max White (400 to 2500 Nits)
        double initialPeak = Math.max(0.0, Math.min(1.0, (MCTraceConfig.hdrPeakLuminance - 400.0f) / 2100.0f));
        peakLumSlider = new CalibrationSlider(
                col2X, sliderStartY, sliderWidth, sliderHeight, initialPeak,
                val -> {
                    int nits = (int) (400.0 + val * 2100.0);
                    return Component.literal(String.format("Peak White: %d Nits", nits));
                },
                val -> MCTraceConfig.hdrPeakLuminance = (float) (400.0 + val * 2100.0)
        );
        this.addRenderableWidget(peakLumSlider);

        // 4. Middle Gray Contrast / Gamma (0.80x to 1.50x)
        double initialContrast = Math.max(0.0, Math.min(1.0, (MCTraceConfig.hdrMiddleGrayContrast - 0.80f) / 0.70f));
        contrastSlider = new CalibrationSlider(
                col2X, sliderStartY + spacing, sliderWidth, sliderHeight, initialContrast,
                val -> {
                    float contrast = (float) (0.80 + val * 0.70);
                    return Component.literal(String.format("Contrast: %.2fx", contrast));
                },
                val -> MCTraceConfig.hdrMiddleGrayContrast = (float) (0.80 + val * 0.70)
        );
        this.addRenderableWidget(contrastSlider);

        // 5. Reset Defaults Button
        this.addRenderableWidget(
                Button.builder(Component.literal("Reset Defaults"), btn -> {
                    MCTraceConfig.hdrMinLuminance = 0.000f;
                    MCTraceConfig.hdrPaperWhite = 200.0f;
                    MCTraceConfig.hdrPeakLuminance = 1000.0f;
                    MCTraceConfig.hdrMiddleGrayContrast = 1.00f;

                    minLumSlider.updateNormalizedValue(0.0);
                    paperWhiteSlider.updateNormalizedValue((200.0 - 80.0) / 320.0);
                    peakLumSlider.updateNormalizedValue((1000.0 - 400.0) / 2100.0);
                    contrastSlider.updateNormalizedValue((1.00 - 0.80) / 0.70);
                }).bounds(centerX - 105, sliderStartY + spacing * 2 + 10, 100, 20).build()
        );

        // 6. Save & Done Button
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> {
                    MCTraceConfig.hdrCalibrated = true;
                    MCTraceConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.lastScreen);
                    }
                }).bounds(centerX + 5, sliderStartY + spacing * 2 + 10, 100, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Header Title
        extractor.centeredText(this.font, Component.literal("§6MCTrace Display & HDR Calibration§r"), centerX, 12, 0xFFFFFF);
        extractor.centeredText(this.font, Component.literal("§7Calibrate Black Level, Middle Gray, and Peak White for your monitor§r"), centerX, 24, 0xAAAAAA);

        int cardW = 100;
        int cardH = 75;
        int cardY = 40;

        int card1X = centerX - 165;
        int card2X = centerX - 50;
        int card3X = centerX + 65;

        // -------------------------------------------------------------
        // Card 1: Black Level (Lowest Light Floor)
        // -------------------------------------------------------------
        drawCardBorder(extractor, card1X, cardY, cardW, cardH, 0xFF3A3A3A);
        extractor.fill(card1X, cardY, card1X + cardW, cardY + cardH, 0xFF000000); // Pure Black
        extractor.centeredText(this.font, "§7Black Level§r", card1X + cardW / 2, cardY + 5, 0xCCCCCC);

        // Inner reactive emblem
        int blackShade = Math.min(255, Math.max(3, (int) (MCTraceConfig.hdrMinLuminance * 2200.0f)));
        int emblemColor = 0xFF000000 | (blackShade << 16) | (blackShade << 8) | blackShade;
        extractor.fill(card1X + 35, cardY + 22, card1X + 65, cardY + 52, emblemColor);
        extractor.centeredText(this.font, "MC", card1X + 50, cardY + 33, 0xFF000000);
        extractor.centeredText(this.font, "§8Barely visible§r", card1X + cardW / 2, cardY + 58, 0x777777);

        // -------------------------------------------------------------
        // Card 2: Paper White / Middle Gray (Exposure & Contrast)
        // -------------------------------------------------------------
        int midVal = Math.min(255, Math.max(10, (int) (30 + (MCTraceConfig.hdrPaperWhite / 400.0f) * 160.0f * MCTraceConfig.hdrMiddleGrayContrast)));
        int midColor = 0xFF000000 | (midVal << 16) | (midVal << 8) | midVal;
        drawCardBorder(extractor, card2X, cardY, cardW, cardH, 0xFF555555);
        extractor.fill(card2X, cardY, card2X + cardW, cardY + cardH, midColor);
        extractor.centeredText(this.font, "§fMiddle Gray§r", card2X + cardW / 2, cardY + 5, 0xFFFFFF);

        // Sample UI text badge inside middle gray patch
        extractor.fill(card2X + 15, cardY + 26, card2X + 85, cardY + 48, 0xBB1E1E1E);
        extractor.centeredText(this.font, "UI Text", card2X + 50, cardY + 33, 0xFFFFFF);
        extractor.centeredText(this.font, "§fComfortable§r", card2X + cardW / 2, cardY + 58, 0xFFFFFF);

        // -------------------------------------------------------------
        // Card 3: Peak White (Highest Light Clipping Point)
        // -------------------------------------------------------------
        drawCardBorder(extractor, card3X, cardY, cardW, cardH, 0xFF999999);
        extractor.fill(card3X, cardY, card3X + cardW, cardY + cardH, 0xFFFFFFFF); // Pure Peak White
        extractor.centeredText(this.font, "§0Peak White§r", card3X + cardW / 2, cardY + 5, 0x000000);

        // Highlight emblem that dissolves into white at monitor clipping
        int clipDelta = Math.max(0, Math.min(80, (int) ((2500.0f - MCTraceConfig.hdrPeakLuminance) / 2100.0f * 80.0f)));
        int highlightShade = 255 - clipDelta;
        int highlightColor = 0xFF000000 | (highlightShade << 16) | (highlightShade << 8) | highlightShade;
        extractor.fill(card3X + 35, cardY + 22, card3X + 65, cardY + 52, highlightColor);
        extractor.centeredText(this.font, "SUN", card3X + 50, cardY + 33, 0xFFFFFFFF);
        extractor.centeredText(this.font, "§0Dissolves in white§r", card3X + cardW / 2, cardY + 58, 0x222222);
    }

    private void drawCardBorder(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int borderColor) {
        extractor.fill(x - 1, y - 1, x + w + 1, y, borderColor);
        extractor.fill(x - 1, y + h, x + w + 1, y + h + 1, borderColor);
        extractor.fill(x - 1, y, x, y + h, borderColor);
        extractor.fill(x + w, y, x + w + 1, y + h, borderColor);
    }

    @Override
    public void onClose() {
        MCTraceConfig.hdrCalibrated = true;
        MCTraceConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}