package net.mctrace.config;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class MCTraceKeybinds {

    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key.mctrace.settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            KeyMapping.Category.MISC
    );
}
