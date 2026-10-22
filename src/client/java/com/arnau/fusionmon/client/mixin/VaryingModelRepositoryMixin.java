package com.arnau.fusionmon.client.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.client.model.FusionCustomModels;
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
 *
 * Todo esto se llama en cada fotograma y pide cosas a resolvers de otros mods y resource packs: si algo falla, se
 * deja lo que Cobblemon iba a pintar (la fusión sale como su cabeza) en vez de un crash (FusionSafety).
 */
@Mixin(value = VaryingModelRepository.class, remap = false)
public abstract class VaryingModelRepositoryMixin {

    @Inject(method = "getPoser", at = @At("HEAD"), cancellable = true)
    private void fusionmon$graftModel(ResourceLocation name, PosableState state,
                                      CallbackInfoReturnable<PosableModel> cir) {
        try {
            PosableModel body = FusionGraft.bodyModel(name, state);
            if (body != null) {
                cir.setReturnValue(body);
            }
        } catch (RuntimeException e) {
            FusionSafety.failed("el modelo del cuerpo de " + name, e);
        }
    }

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void fusionmon$graftTexture(ResourceLocation name, PosableState state,
                                        CallbackInfoReturnable<ResourceLocation> cir) {
        try {
            ResourceLocation body = FusionGraft.bodyTexture(name, state);
            if (body != null) {
                cir.setReturnValue(body);
            }
        } catch (RuntimeException e) {
            FusionSafety.failed("la textura del cuerpo de " + name, e);
        }
    }

    @Inject(method = "getLayers", at = @At("HEAD"), cancellable = true)
    private void fusionmon$graftLayers(ResourceLocation name, PosableState state,
                                       CallbackInfoReturnable<Iterable<ModelLayer>> cir) {
        try {
            Iterable<ModelLayer> body = FusionGraft.bodyLayers(name, state);
            if (body != null) {
                cir.setReturnValue(body);
            }
        } catch (RuntimeException e) {
            FusionSafety.failed("las capas del cuerpo de " + name, e);
        }
    }

    @Inject(method = "getTextureNoSubstitute", at = @At("HEAD"), cancellable = true)
    private void fusionmon$fusionTexture(ResourceLocation name, PosableState state,
                                         CallbackInfoReturnable<ResourceLocation> cir) {
        try {
            // Las fusiones hechas a mano tienen texturas de verdad: que Cobblemon las compruebe como siempre
            if (!state.getCurrentAspects().contains(FusionAspects.FUSION)
                    || FusionCustomModels.isCustom(name, state.getCurrentAspects())) {
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
            // Pasa por VaryingRenderableResolverMixin, que genera (o saca de la caché) la textura de la fusión
            ResourceLocation texture = resolver.getTexture(state);
            if (FusionTextures.isGenerated(texture)) {
                cir.setReturnValue(texture);
            }
        } catch (IllegalStateException e) {
            // Variación mal definida: el original también la captura, sin avisar. Dejamos que haga lo de siempre
        } catch (RuntimeException e) {
            FusionSafety.failed("la textura de menú de " + name, e);
        }
    }
}
