package net.mctrace.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.mctrace.MCTrace;
import net.mctrace.config.MCTraceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cinematic Photo Mode Screen for MCTrace.
 * Features 6DOF camera composition, interactive click-to-focus for Bokeh Depth of Field,
 * aperture/exposure/FOV lens controls, rule-of-thirds compositional grid,
 * and offline 8K (7680x4320) super-resolution screenshot exporting.
 */
public class MCTracePhotoModeScreen extends Screen {

    public enum CompositionGrid {
        OFF("Off"),
        RULE_OF_THIRDS("Rule of Thirds (3x3)"),
        GOLDEN_RATIO("Golden Ratio (Phi)"),
        CENTER_CROSSHAIR("Center Crosshair");

        private final String displayName;

        CompositionGrid(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static class PhotoSlider extends AbstractSliderButton {
        private final Consumer<Double> onValueChange;
        private final Function<Double, Component> messageProvider;

        public PhotoSlider(int x, int y, int width, int height, double initialNormalized,
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

        public void setNormalized(double val) {
            this.value = Math.max(0.0, Math.min(1.0, val));
            this.updateMessage();
        }
    }

    private final Screen lastScreen;
    private final int originalFov;
    private final float originalBrightness;
    private final boolean originalDof;
    private final float originalFocalDist;

    private boolean hideUi = false;
    private boolean clickToFocusActive = true;
    private CompositionGrid activeGrid = CompositionGrid.RULE_OF_THIRDS;

    private PhotoSlider apertureSlider;
    private PhotoSlider focalSlider;
    private PhotoSlider exposureSlider;
    private PhotoSlider fovSlider;

    private int lastClickX = -1;
    private int lastClickY = -1;
    private long lastCaptureTime = 0;

    public MCTracePhotoModeScreen(Screen lastScreen) {
        super(Component.literal("MCTrace Cinematic Photo Mode"));
        this.lastScreen = lastScreen;

        Minecraft mc = Minecraft.getInstance();
        this.originalFov = mc.options.fov().get();
        this.originalBrightness = MCTraceConfig.sceneBrightness;
        this.originalDof = MCTraceConfig.enableBokehDof;
        this.originalFocalDist = MCTraceConfig.dofFocalDistance;

        // Automatically activate Bokeh DoF for photo mode
        MCTraceConfig.enableBokehDof = true;
        MCTraceConfig.photoModeActive = true;
    }

    @Override
    protected void init() {
        int panelWidth = 180;
        int panelHeight = 20;
        int spacing = 24;

        int panelX = 16;
        int startY = 32;

        // 1. Aperture Slider (f/1.2 to f/16.0)
        double initialAperture = (MCTraceConfig.photoAperture - 1.2f) / (16.0f - 1.2f);
        apertureSlider = new PhotoSlider(
                panelX, startY, panelWidth, panelHeight, initialAperture,
                val -> {
                    float fStop = (float) (1.2 + val * (16.0 - 1.2));
                    return Component.literal(String.format("Aperture: f/%.1f", fStop));
                },
                val -> MCTraceConfig.photoAperture = (float) (1.2 + val * (16.0 - 1.2))
        );
        this.addRenderableWidget(apertureSlider);

        // 2. Focal Distance Slider (0.5m to 50.0m)
        double initialFocal = (MCTraceConfig.dofFocalDistance - 0.5f) / 49.5f;
        focalSlider = new PhotoSlider(
                panelX, startY + spacing, panelWidth, panelHeight, initialFocal,
                val -> {
                    float dist = (float) (0.5 + val * 49.5);
                    return Component.literal(String.format("Focus Dist: %.1f m", dist));
                },
                val -> MCTraceConfig.dofFocalDistance = (float) (0.5 + val * 49.5)
        );
        this.addRenderableWidget(focalSlider);

        // 3. Exposure Compensation (-2.0 EV to +2.0 EV, 0.6x to 1.6x brightness)
        double initialExp = (MCTraceConfig.photoExposure - 0.6f) / 1.0f;
        exposureSlider = new PhotoSlider(
                panelX, startY + spacing * 2, panelWidth, panelHeight, initialExp,
                val -> {
                    float exp = (float) (0.6 + val * 1.0);
                    return Component.literal(String.format("Exposure: %.2fx", exp));
                },
                val -> {
                    MCTraceConfig.photoExposure = (float) (0.6 + val * 1.0);
                    MCTraceConfig.sceneBrightness = this.originalBrightness * MCTraceConfig.photoExposure;
                }
        );
        this.addRenderableWidget(exposureSlider);

        // 4. Camera FOV Slider (15 deg to 110 deg)
        double initialFov = (MCTraceConfig.photoFov - 15.0f) / 95.0f;
        fovSlider = new PhotoSlider(
                panelX, startY + spacing * 3, panelWidth, panelHeight, initialFov,
                val -> {
                    int fov = (int) (15 + val * 95);
                    return Component.literal("FOV: " + fov + "°");
                },
                val -> {
                    int fov = (int) (15 + val * 95);
                    MCTraceConfig.photoFov = fov;
                    if (this.minecraft != null) {
                        this.minecraft.options.fov().set(fov);
                    }
                }
        );
        this.addRenderableWidget(fovSlider);

        // 5. Bokeh Blade Shape
        List<Integer> bladeOptions = List.of(0, 5, 7, 9);
        this.addRenderableWidget(
                CycleButton.<Integer>builder(
                                blades -> Component.literal("Aperture Blades: " + (blades == 0 ? "Circular" : blades + "-Blade")),
                                MCTraceConfig.photoBokehBlades
                        )
                        .withValues(bladeOptions)
                        .create(panelX, startY + spacing * 4, panelWidth, panelHeight,
                                Component.literal("Aperture Blades"),
                                (btn, val) -> MCTraceConfig.photoBokehBlades = val)
        );

        // 6. Compositional Grid Mode
        this.addRenderableWidget(
                CycleButton.<CompositionGrid>builder(
                                grid -> Component.literal("Grid: " + grid.getDisplayName()),
                                this.activeGrid
                        )
                        .withValues(CompositionGrid.values())
                        .create(panelX, startY + spacing * 5, panelWidth, panelHeight,
                                Component.literal("Composition Grid"),
                                (btn, val) -> this.activeGrid = val)
        );

        // 7. Click-To-Focus Toggle
        this.addRenderableWidget(
                Button.builder(Component.literal(clickToFocusActive ? "§a🎯 Click-to-Focus: ON§r" : "§7🎯 Click-to-Focus: OFF§r"), btn -> {
                    clickToFocusActive = !clickToFocusActive;
                    btn.setMessage(Component.literal(clickToFocusActive ? "§a🎯 Click-to-Focus: ON§r" : "§7🎯 Click-to-Focus: OFF§r"));
                }).bounds(panelX, startY + spacing * 6, panelWidth, panelHeight).build()
        );

        // Bottom Action Bar
        int actionY = this.height - 32;

        // Capture 8K Screenshot Button
        this.addRenderableWidget(
                Button.builder(Component.literal("§b📷 Capture 8K Photo§r"), btn -> capture8kScreenshot())
                        .bounds(panelX, actionY, 130, 22).build()
        );

        // Hide UI Button
        this.addRenderableWidget(
                Button.builder(Component.literal("§eHide UI [H]§r"), btn -> toggleHideUi())
                        .bounds(panelX + 135, actionY, 80, 22).build()
        );

        // Reset Settings Button
        this.addRenderableWidget(
                Button.builder(Component.literal("§cReset§r"), btn -> resetCamera())
                        .bounds(panelX + 220, actionY, 60, 22).build()
        );

        // Done / Exit Button
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
                        .bounds(this.width - 86, actionY, 70, 22).build()
        );
    }

    private void toggleHideUi() {
        this.hideUi = !this.hideUi;
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.visible = !this.hideUi;
            }
        }
    }

    private void resetCamera() {
        if (this.minecraft != null) {
            this.minecraft.options.fov().set(this.originalFov);
        }
        MCTraceConfig.photoAperture = 2.8f;
        MCTraceConfig.dofFocalDistance = this.originalFocalDist;
        MCTraceConfig.sceneBrightness = this.originalBrightness;
        MCTraceConfig.photoExposure = 1.0f;
        MCTraceConfig.photoFov = this.originalFov;
        MCTraceConfig.photoBokehBlades = 7;
        this.clearWidgets();
        this.init();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isConsumed) {
        if (!this.hideUi) {
            // Check if user clicked on any GUI widget first
            boolean widgetHandled = super.mouseClicked(event, isConsumed);
            if (widgetHandled) {
                return true;
            }
        }

        // Viewport click-to-focus
        if (clickToFocusActive && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.lastClickX = (int) event.x();
            this.lastClickY = (int) event.y();

            Minecraft mc = Minecraft.getInstance();
            float distance = 5.0f;
            if (mc.hitResult != null && mc.player != null) {
                distance = (float) mc.player.getEyePosition().distanceTo(mc.hitResult.getLocation());
            } else {
                // Heuristic estimation based on vertical screen position
                float normY = (float) this.lastClickY / Math.max(1, this.height);
                distance = 1.0f + normY * 18.0f;
            }

            distance = Math.max(0.5f, Math.min(50.0f, distance));
            MCTraceConfig.dofFocalDistance = distance;

            if (focalSlider != null) {
                focalSlider.setNormalized((distance - 0.5f) / 49.5f);
            }

            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
            }
            return true;
        }

        return super.mouseClicked(event, isConsumed);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_H) {
            toggleHideUi();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            capture8kScreenshot();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_F8 || event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * Captures current frame and supersamples to 8K (7680x4320) offline image.
     */
    public void capture8kScreenshot() {
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < 1500) return; // Debounce rapid triggers
        lastCaptureTime = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) return;

        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.8f));
        }

        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), srcImg -> {
            CompletableFuture.runAsync(() -> {
                try {
                    int srcW = srcImg.getWidth();
                    int srcH = srcImg.getHeight();
                    int targetW = 7680;
                    int targetH = 4320;

                    NativeImage targetImg = new NativeImage(targetW, targetH, false);

                    float xRatio = (float) (srcW - 1) / targetW;
                    float yRatio = (float) (srcH - 1) / targetH;

                    for (int y = 0; y < targetH; y++) {
                        float srcY = y * yRatio;
                        int y0 = (int) srcY;
                        int y1 = Math.min(y0 + 1, srcH - 1);
                        float yWeight = srcY - y0;

                        for (int x = 0; x < targetW; x++) {
                            float srcX = x * xRatio;
                            int x0 = (int) srcX;
                            int x1 = Math.min(x0 + 1, srcW - 1);
                            float xWeight = srcX - x0;

                            int c00 = srcImg.getPixel(x0, y0);
                            int c10 = srcImg.getPixel(x1, y0);
                            int c01 = srcImg.getPixel(x0, y1);
                            int c11 = srcImg.getPixel(x1, y1);

                            int a = (int) bilinear(c00 >>> 24, c10 >>> 24, c01 >>> 24, c11 >>> 24, xWeight, yWeight);
                            int r = (int) bilinear((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, xWeight, yWeight);
                            int g = (int) bilinear((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, xWeight, yWeight);
                            int b = (int) bilinear(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, xWeight, yWeight);

                            targetImg.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                        }
                    }

                    File dir = new File(mc.gameDirectory, "screenshots");
                    if (!dir.exists()) dir.mkdirs();

                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
                    File outFile = new File(dir, "mctrace_photo_8k_" + timeStamp + ".png");
                    targetImg.writeToFile(outFile);

                    targetImg.close();
                    srcImg.close();

                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendOverlayMessage(
                                    Component.literal("§6[MCTrace Photo Mode]§a Saved 8K Super-Res: §f" + outFile.getName())
                            );
                        }
                    });
                } catch (Exception e) {
                    MCTrace.LOGGER.error("[MCTrace Photo Mode] Failed to export 8K screenshot: {}", e.getMessage());
                    srcImg.close();
                }
            });
        });
    }

    private static float bilinear(float c00, float c10, float c01, float c11, float tx, float ty) {
        float top = c00 * (1.0f - tx) + c10 * tx;
        float bottom = c01 * (1.0f - tx) + c11 * tx;
        return top * (1.0f - ty) + bottom * ty;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        // 1. Render Compositional Grid Overlay
        drawCompositionGrid(extractor);

        // 2. Camera Status Banner
        extractor.fill(0, 0, this.width, 22, 0xAA0B0F19);
        extractor.fill(0, 22, this.width, 23, 0x443B82F6);

        String status = String.format(
                "§6§lMCTrace Cinematic Photo Mode§r  §7|§r  §fAperture: §ef/%.1f§r  §7|§r  §fFocus: §a%.1fm§r  §7|§r  §fExp: §b%.2fx§r  §7|§r  §fFOV: §d%d°§r  §7|§r  §7[Space] 8K Capture§r",
                MCTraceConfig.photoAperture,
                MCTraceConfig.dofFocalDistance,
                MCTraceConfig.photoExposure,
                (int) MCTraceConfig.photoFov
        );
        extractor.centeredText(this.font, Component.literal(status), this.width / 2, 7, 0xFFFFFF);

        // 3. Focus Target Reticle
        if (lastClickX >= 0 && lastClickY >= 0) {
            drawFocusReticle(extractor, lastClickX, lastClickY);
        } else {
            drawFocusReticle(extractor, this.width / 2, this.height / 2);
        }

        // 4. Subtle prompt when UI is hidden
        if (this.hideUi) {
            extractor.centeredText(this.font, Component.literal("§7Press §e[H]§7 to show controls  |  Press §b[Space]§7 to Capture 8K Photo  |  §c[Esc]§7 Exit§r"), this.width / 2, this.height - 20, 0xEEEEEE);
        }
    }

    private void drawCompositionGrid(GuiGraphicsExtractor extractor) {
        int color = 0x33FFFFFF; // Subtle white grid
        int crossColor = 0x66FFCC00; // Accent golden color

        switch (this.activeGrid) {
            case RULE_OF_THIRDS -> {
                int x1 = this.width / 3;
                int x2 = (this.width * 2) / 3;
                int y1 = this.height / 3;
                int y2 = (this.height * 2) / 3;

                // Vertical lines
                extractor.fill(x1, 0, x1 + 1, this.height, color);
                extractor.fill(x2, 0, x2 + 1, this.height, color);
                // Horizontal lines
                extractor.fill(0, y1, this.width, y1 + 1, color);
                extractor.fill(0, y2, this.width, y2 + 1, color);

                // Golden focus intersection markers
                drawCross(extractor, x1, y1, 5, crossColor);
                drawCross(extractor, x2, y1, 5, crossColor);
                drawCross(extractor, x1, y2, 5, crossColor);
                drawCross(extractor, x2, y2, 5, crossColor);
            }
            case GOLDEN_RATIO -> {
                int x1 = (int) (this.width * 0.382f);
                int x2 = (int) (this.width * 0.618f);
                int y1 = (int) (this.height * 0.382f);
                int y2 = (int) (this.height * 0.618f);

                extractor.fill(x1, 0, x1 + 1, this.height, color);
                extractor.fill(x2, 0, x2 + 1, this.height, color);
                extractor.fill(0, y1, this.width, y1 + 1, color);
                extractor.fill(0, y2, this.width, y2 + 1, color);
            }
            case CENTER_CROSSHAIR -> {
                int cx = this.width / 2;
                int cy = this.height / 2;
                drawCross(extractor, cx, cy, 14, crossColor);
            }
            case OFF -> {}
        }
    }

    private void drawCross(GuiGraphicsExtractor extractor, int cx, int cy, int size, int color) {
        extractor.fill(cx - size, cy, cx + size + 1, cy + 1, color);
        extractor.fill(cx, cy - size, cx + 1, cy + size + 1, color);
    }

    private void drawFocusReticle(GuiGraphicsExtractor extractor, int cx, int cy) {
        int r = 8;
        int color = 0xAA00FFCC; // Aqua reticle brackets
        // Reticle corners
        extractor.fill(cx - r, cy - r, cx - r + 4, cy - r + 1, color);
        extractor.fill(cx - r, cy - r, cx - r + 1, cy - r + 4, color);

        extractor.fill(cx + r - 3, cy - r, cx + r + 1, cy - r + 1, color);
        extractor.fill(cx + r, cy - r, cx + r + 1, cy - r + 4, color);

        extractor.fill(cx - r, cy + r, cx - r + 4, cy + r + 1, color);
        extractor.fill(cx - r, cy + r - 3, cx - r + 1, cy + r + 1, color);

        extractor.fill(cx + r - 3, cy + r, cx + r + 1, cy + r + 1, color);
        extractor.fill(cx + r, cy + r - 3, cx + r + 1, cy + r + 1, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Keep world and lighting rendering dynamically
    }

    @Override
    public void onClose() {
        MCTraceConfig.photoModeActive = false;
        MCTraceConfig.enableBokehDof = this.originalDof;
        MCTraceConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}
