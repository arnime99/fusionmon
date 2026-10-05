package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.client.texture.FusionTextures;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Los menús (equipo, PC, resumen) piden la textura con getTextureNoSubstitute, que además comprueba que
 * exista como archivo en los recursos; si no, Cobblemon pinta un sustituto. Nuestras texturas solo existen en
 * memoria y no pasarían esa comprobación, así que para las fusiones devolvemos la generada antes de llegar a ella.
 */
@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class VaryingModelRepositoryMixin {

    @Inject(method = "getTextureNoSubstitute", at = @At("HEAD"), cancellable = true)
    private void fusionmon$fusionTexture(ResourceLocation name, PosableState state,
                                         CallbackInfoReturnable<ResourceLocation> cir) {
        if (!state.getCurrentAspects().contains(FusionAspects.FUSION)) {
            return;
        }

        VaryingRenderableResolver resolver = ((VaryingModelRepository) (Object) this).getVariations().get(name);
        if (resolver == null) {
            return;
        }
        try {
            // Pasa por VaryingRenderableResolverMixin, que genera (o saca de la caché) la textura de la fusión
            ResourceLocation texture = resolver.getTexture(state);
            if (FusionTextures.isGenerated(texture)) {
                cir.setReturnValue(texture);
            }
        } catch (IllegalStateException e) {
            // El original también la captura (variación mal definida): dejamos que haga lo de siempre
        }
    }
}
