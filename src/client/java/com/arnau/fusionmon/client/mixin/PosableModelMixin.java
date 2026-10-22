package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.FusionSafety;
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
 * Cabeza sobre cuerpo: antes de pintar el modelo del cuerpo se ocultan sus cabezas, y después se pinta la cabeza de la
 * otra especie en su sitio (ver FusionGraft). Pasa por aquí TODO Pokémon que se pinta, no solo las fusiones: si algo
 * falla, se devuelven las piezas a su sitio y se sigue (FusionSafety), en vez de tumbar el juego.
 */
@Mixin(value = PosableModel.class, remap = false)
public abstract class PosableModelMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void fusionmon$hideBodyHead(RenderContext context, PoseStack poseStack, VertexConsumer buffer,
                                        int light, int overlay, int color, CallbackInfo ci) {
        try {
            FusionGraft.beforeRender((PosableModel) (Object) this);
        } catch (RuntimeException e) {
            FusionGraft.recover();
            FusionSafety.failed("preparar la cabeza pegada", e);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void fusionmon$renderGraftedHead(RenderContext context, PoseStack poseStack, VertexConsumer buffer,
                                             int light, int overlay, int color, CallbackInfo ci) {
        try {
            FusionGraft.afterRender((PosableModel) (Object) this, poseStack, light, overlay, color);
        } catch (RuntimeException e) {
            FusionGraft.recover();
            FusionSafety.failed("pintar la cabeza pegada", e);
        }
    }
}
