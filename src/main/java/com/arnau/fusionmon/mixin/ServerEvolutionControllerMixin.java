package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionData;
import com.arnau.fusionmon.fusion.FusionPartEvolution;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.evolution.controller.ServerEvolutionController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Red de seguridad: una fusión solo puede evolucionar con evoluciones de sus partes. Si le queda pendiente una
 * evolución normal de la especie visible (p. ej. de una fusión creada antes de esta fase, o guardada así), al
 * aceptarla cambiaría la especie sin tocar los datos de la fusión. La quitamos en vez de ejecutarla.
 */
@Mixin(value = ServerEvolutionController.class, remap = false)
public abstract class ServerEvolutionControllerMixin {

    // Hay dos start(): el de la interfaz (EvolutionLike) solo llama a este, así que basta con interceptar este
    @Inject(method = "start(Lcom/cobblemon/mod/common/api/pokemon/evolution/Evolution;)V", at = @At("HEAD"), cancellable = true)
    private void fusionmon$onlyPartEvolutions(Evolution evolution, CallbackInfo ci) {
        ServerEvolutionController self = (ServerEvolutionController) (Object) this;
        if (FusionData.isFusion(self.pokemon()) && !(evolution instanceof FusionPartEvolution)) {
            self.remove(evolution);
            ci.cancel();
        }
    }
}
