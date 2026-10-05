package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cobblemon pinta cada Pokémon a la escala de su especie (form.baseScale: Charizard 1, Lucario 0,65...). Una
 * fusión es la especie de la cabeza, pero tiene el tamaño del cuerpo (como su caja de colisión, ver
 * PokemonEntityMixin): sin esto, con cabeza Charizard el modelo de Lucario salía enorme y al revés, diminuto.
 *
 * scale(PokemonEntity, ...) es el método de Cobblemon (el de Minecraft, con LivingEntity, solo lo llama): se
 * llama igual en desarrollo y en el juego publicado.
 */
@Mixin(value = PokemonRenderer.class, remap = false)
public abstract class PokemonRendererMixin {

    @ModifyExpressionValue(method = "scale", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/FormData;getBaseScale()F"))
    private float fusionmon$bodyScale(float scale, PokemonEntity entity, PoseStack poseStack, float partialTick) {
        FormData body = FusionBodyForm.of(entity);
        return body == null ? scale : body.getBaseScale();
    }
}
