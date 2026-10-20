package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.client.model.FusionCustomModels;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cada especie tiene un VaryingRenderableResolver que elige la textura según los aspects del Pokémon.
 * Todo lo que pinta Pokémon pasa por aquí (mundo, hombro, combate, menús), así que cuando ha elegido la
 * textura de una fusión (la de su cabeza) la cambiamos por la recoloreada con los colores del cuerpo.
 */
@Mixin(value = VaryingRenderableResolver.class, remap = false)
public abstract class VaryingRenderableResolverMixin {

    @Inject(method = "getTexture", at = @At("RETURN"), cancellable = true)
    private void fusionmon$fusionTexture(PosableState state, CallbackInfoReturnable<ResourceLocation> cir) {
        // Fusión hecha a mano en un resource pack: su textura ya es la buena, no se recolorea
        if (FusionCustomModels.isCustom((VaryingRenderableResolver) (Object) this, state.getCurrentAspects())) {
            return;
        }
        ResourceLocation original = cir.getReturnValue();
        ResourceLocation texture = FusionTextures.textureFor(original, state.getCurrentAspects());
        if (texture != original) {
            cir.setReturnValue(texture);
        }
    }
}
