package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.client.model.FusionGraft;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PosableModel.render pinta un modelo de Cobblemon (huesos con la textura principal y luego cada capa).
 * Prototipo "cabeza sobre cuerpo": antes se oculta la cabeza del cuerpo y después se pinta la de la otra
 * especie en su sitio (ver FusionGraft). Si no es una fusión, o el prototipo está apagado, no hace nada.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class PosableModelMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void fusionmon$hideBodyHead(RenderContext context, PoseStack poseStack, VertexConsumer buffer,
                                        int light, int overlay, int color, CallbackInfo ci) {
        FusionGraft.beforeRender((PosableModel) (Object) this);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void fusionmon$renderGraftedHead(RenderContext context, PoseStack poseStack, VertexConsumer buffer,
                                             int light, int overlay, int color, CallbackInfo ci) {
        FusionGraft.afterRender((PosableModel) (Object) this, poseStack, light, overlay, color);
    }
}
