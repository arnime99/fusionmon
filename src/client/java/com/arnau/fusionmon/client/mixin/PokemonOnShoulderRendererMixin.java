package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.client.render.layer.PokemonOnShoulderRenderer;
import com.cobblemon.mod.common.client.render.layer.PokemonOnShoulderRenderer.ShoulderData;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext;
import com.cobblemon.mod.common.pokemon.FormData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * El Pokémon del hombro: Cobblemon lo pinta dos veces. Primero el modelo suelto, sin decirle qué Pokémon es
 * (model.currentState vacío), y luego otra vez dentro de withLayerContext, que sí se lo dice, con las capas. FusionGraft
 * necesita ese estado para ocultar la cabeza del cuerpo y pegar la de la cabeza: en la primera pasada no hacía nada y
 * la fusión salía con la cabeza original del cuerpo (aviso del servidor CobbleFusion). En una fusión nos saltamos esa
 * primera pasada: la segunda ya pinta el modelo entero, con su cabeza pegada.
 */
@Mixin(value = PokemonOnShoulderRenderer.class, remap = false)
public abstract class PokemonOnShoulderRendererMixin {

    // Solo por nombre: la firma lleva clases de Minecraft, que con remap = false no se traducen. Hay varios métodos
    // render y con el nombre solo Mixin coge el primero: "render*" los coge todos (también la segunda pasada, si
    // está en uno de ellos), y la que sobra se reconoce por no tener estado
    @WrapOperation(method = "render*", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/render/models/blockbench/PosableModel;render"))
    private void fusionmon$skipStatelessPass(PosableModel model, RenderContext context, PoseStack poseStack,
                                             VertexConsumer buffer, int light, int overlay, int color,
                                             Operation<Void> original) {
        boolean skip = false;
        try {
            PosableState state = context.request(RenderContext.Companion.getPOSABLE_STATE());
            skip = model.getCurrentState() == null && state != null
                    && state.getCurrentAspects().contains(FusionAspects.FUSION);
        } catch (RuntimeException e) {
            // Si no se puede saber, se pinta como siempre (a lo sumo, con la cabeza original encima)
            FusionSafety.failed("el Pokémon del hombro", e);
        }
        if (!skip) {
            original.call(model, context, poseStack, buffer, light, overlay, color);
        }
    }

    /**
     * Tamaño en el hombro: Cobblemon escala y coloca el modelo con la escala y la caja de la forma guardada, que en
     * una fusión es la de la cabeza. El modelo es el del cuerpo: su forma, como en el mundo (PokemonEntityMixin).
     */
    @WrapOperation(method = "render*", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/client/render/layer/PokemonOnShoulderRenderer$ShoulderData;getForm()Lcom/cobblemon/mod/common/pokemon/FormData;"))
    private FormData fusionmon$bodyForm(ShoulderData data, Operation<FormData> original) {
        FormData body = FusionBodyForm.fromAspects(data.getAspects());
        return body != null ? body : original.call(data);
    }
}
