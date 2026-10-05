package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.cobblemon.mod.common.client.render.ModelLayer;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * El repositorio es donde Cobblemon pide, por especie y estado, el modelo, la textura y las capas de un Pokémon
 * (tanto en el mundo como en los menús).
 *
 * - Prototipo "cabeza sobre cuerpo" (FusionGraft, solo si está activado): para una fusión devolvemos el modelo,
 *   la textura y las capas del cuerpo.
 * - Menús: getTextureNoSubstitute comprueba además que la textura exista como archivo en los recursos; si no,
 *   Cobblemon pinta un sustituto. Nuestras texturas solo existen en memoria y no pasarían esa comprobación, así
 *   que para las fusiones devolvemos la generada antes de llegar a ella.
 */
@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class VaryingModelRepositoryMixin {

    @Inject(method = "getPoser", at = @At("HEAD"), cancellable = true)
    private void fusionmon$graftModel(ResourceLocation name, PosableState state,
                                      CallbackInfoReturnable<PosableModel> cir) {
        PosableModel body = FusionGraft.bodyModel(name, state);
        if (body != null) {
            cir.setReturnValue(body);
        }
    }

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void fusionmon$graftTexture(ResourceLocation name, PosableState state,
                                        CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation body = FusionGraft.bodyTexture(name, state);
        if (body != null) {
            cir.setReturnValue(body);
        }
    }

    @Inject(method = "getLayers", at = @At("HEAD"), cancellable = true)
    private void fusionmon$graftLayers(ResourceLocation name, PosableState state,
                                       CallbackInfoReturnable<Iterable<ModelLayer>> cir) {
        Iterable<ModelLayer> body = FusionGraft.bodyLayers(name, state);
        if (body != null) {
            cir.setReturnValue(body);
        }
    }

    @Inject(method = "getTextureNoSubstitute", at = @At("HEAD"), cancellable = true)
    private void fusionmon$fusionTexture(ResourceLocation name, PosableState state,
                                         CallbackInfoReturnable<ResourceLocation> cir) {
        if (!state.getCurrentAspects().contains(FusionAspects.FUSION)) {
            return;
        }

        // La textura original del cuerpo sí existe como archivo, pero así no hace falta repetir la comprobación
        ResourceLocation graftBody = FusionGraft.bodyTexture(name, state);
        if (graftBody != null) {
            cir.setReturnValue(graftBody);
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
