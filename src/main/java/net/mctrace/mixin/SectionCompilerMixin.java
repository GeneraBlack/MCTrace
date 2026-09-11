package net.mctrace.mixin;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.mctrace.vulkan.rt.BlasManager;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {

    @Inject(
            method = "compile",
            at = @At("RETURN")
    )
    private void mctrace$onSectionCompileReturn(
            SectionPos sectionPos,
            RenderSectionRegion renderRegion,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builderPack,
            CallbackInfoReturnable<SectionCompiler.Results> cir
    ) {
        SectionCompiler.Results results = cir.getReturnValue();
        if (results != null && results.renderedLayers != null) {
            BlasManager.onSectionCompiled(sectionPos, results);
        }
    }
}
