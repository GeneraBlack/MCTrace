package net.mctrace.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.mctrace.vulkan.shader.ShaderPackLoader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mctrace$onInit(CallbackInfo ci) {
        ShaderManager self = (ShaderManager) (Object) this;
        ShaderPackLoader.setPostChainInvalidator(self::close);
    }

    @Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
    private void mctrace$onGetShader(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        if (id != null && "mctrace".equals(id.getNamespace())) {
            String ext = (type == ShaderType.FRAGMENT) ? ".fsh" : ".vsh";
            String candidatePath = id.getPath() + ext;
            String customSource = ShaderPackLoader.getShaderSource(candidatePath);
            if (customSource != null && !customSource.isBlank()) {
                cir.setReturnValue(customSource);
            }
        }
    }
}
