package net.mctrace.gui;

import net.mctrace.vulkan.shader.ShaderPackLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.nio.file.Path;
import java.util.List;

/**
 * Interactive screen for discovering, selecting, and hot-reloading custom shader packs
 * from .minecraft/shaderpacks/ (both directories and .zip files).
 */
public class MCTraceShaderPackScreen extends Screen {

    private final Screen lastScreen;
    private int page = 0;
    private static final int PACKS_PER_PAGE = 6;
    private String statusMessage = "";

    public MCTraceShaderPackScreen(Screen lastScreen) {
        super(Component.literal("MCTrace Shader Packs"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 48;
        int buttonWidth = 320;
        int buttonHeight = 20;
        int spacing = 24;

        List<String> packs = ShaderPackLoader.listAvailablePacks();
        String active = ShaderPackLoader.getActiveShaderPackName();

        int totalPages = Math.max(1, (int) Math.ceil((double) packs.size() / PACKS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * PACKS_PER_PAGE;
        int endIndex = Math.min(startIndex + PACKS_PER_PAGE, packs.size());

        for (int i = startIndex; i < endIndex; i++) {
            String packName = packs.get(i);
            boolean isActive = packName.equalsIgnoreCase(active);

            String displayName;
            if (ShaderPackLoader.INTERNAL_PACK.equalsIgnoreCase(packName)) {
                displayName = isActive ? "§a✔ [Active] Internal (Default Vulkan RT)§r" : "§fInternal (Default Vulkan RT)§r";
            } else {
                displayName = isActive ? "§a✔ [Active] " + packName + "§r" : "§f" + packName + "§r";
            }

            final String chosenPack = packName;
            this.addRenderableWidget(
                    Button.builder(Component.literal(displayName), btn -> {
                        if (!chosenPack.equalsIgnoreCase(ShaderPackLoader.getActiveShaderPackName())) {
                            long t0 = System.currentTimeMillis();
                            ShaderPackLoader.setActiveShaderPackName(chosenPack);
                            long dt = System.currentTimeMillis() - t0;
                            statusMessage = "§aActivated & reloaded in " + dt + " ms§r";
                            this.clearWidgets();
                            this.init();
                        }
                    }).bounds(centerX - buttonWidth / 2, startY + (i - startIndex) * spacing, buttonWidth, buttonHeight).build()
            );
        }

        // Pagination if more than 6 packs
        if (totalPages > 1) {
            int navY = startY + PACKS_PER_PAGE * spacing;
            this.addRenderableWidget(
                    Button.builder(Component.literal("§7< Prev§r"), btn -> {
                        if (page > 0) {
                            page--;
                            this.clearWidgets();
                            this.init();
                        }
                    }).bounds(centerX - 100, navY, 48, 18).build()
            );

            this.addRenderableWidget(
                    Button.builder(Component.literal("§7Next >§r"), btn -> {
                        if (page < totalPages - 1) {
                            page++;
                            this.clearWidgets();
                            this.init();
                        }
                    }).bounds(centerX + 52, navY, 48, 18).build()
            );
        }

        // Bottom action buttons
        int bottomY = Math.min(this.height - 28, startY + PACKS_PER_PAGE * spacing + (totalPages > 1 ? 28 : 12));

        this.addRenderableWidget(
                Button.builder(Component.literal("📁 Open Shader Pack Folder"), btn -> {
                    try {
                        Path folder = ShaderPackLoader.getShaderPacksDirectory().toAbsolutePath();
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                            Desktop.getDesktop().open(folder.toFile());
                        } else {
                            new ProcessBuilder("explorer.exe", folder.toString()).start();
                        }
                    } catch (Throwable t) {
                        try {
                            new ProcessBuilder("explorer.exe", ShaderPackLoader.getShaderPacksDirectory().toAbsolutePath().toString()).start();
                        } catch (Throwable ignored) {}
                    }
                }).bounds(centerX - 162, bottomY, 158, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("⚡ Reload Shaders"), btn -> {
                    long t0 = System.currentTimeMillis();
                    ShaderPackLoader.reloadShaders();
                    long dt = System.currentTimeMillis() - t0;
                    statusMessage = "§aAll pipelines recompiled in " + dt + " ms!§r";
                    this.clearWidgets();
                    this.init();
                }).bounds(centerX + 4, bottomY, 96, 20).build()
        );

        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_DONE, btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(this.lastScreen);
                    }
                }).bounds(centerX + 104, bottomY, 58, 20).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;

        extractor.centeredText(this.font, this.title, centerX, 12, 0xFFFFFF);
        extractor.centeredText(this.font, Component.literal("§7Place folders or .zip shaderpacks into .minecraft/shaderpacks/§r"), centerX, 26, 0xAAAAAA);

        if (!statusMessage.isEmpty()) {
            extractor.centeredText(this.font, Component.literal(statusMessage), centerX, 37, 0x55FF55);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.lastScreen);
        }
    }
}
