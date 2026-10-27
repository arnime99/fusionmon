package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.fusion.FusionData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.CobblemonEvolutionProxy;
import com.cobblemon.mod.common.pokemon.evolution.controller.ServerEvolutionController;
import com.cobblemon.mod.common.util.codec.internal.PokemonP2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Las evoluciones pendientes de una fusión se perdían cada vez que se cargaba (al entrar al servidor). Al leer un
 * Pokémon guardado, Cobblemon (PokemonP2.into) crea su controlador de evoluciones con los ids guardados, buscándolos en
 * pokemon.getEvolutions(), y DESPUÉS le pone su persistentData. En ese momento todavía no es una fusión, así que
 * getEvolutions() (PokemonMixin) da las de la cabeza y los ids de Fusionmon (fusionmon_body_...) no aparecen. Un
 * segundo después de entrar Cobblemon la volvía a encontrar y mandaba AddEvolutionPacket; si llegaba antes que la
 * lista de especies que el servidor manda al entrar, el cliente no conocía la especie ("Cannot resolve species from
 * cobblemon:pikachu") y se desconectaba, unas veces sí y otras no (servidor CobbleFusion, 2026-10-26).
 *
 * Al acabar de cargarlo, si es una fusión, se vuelve a crear el controlador con lo guardado (lo mismo que hace
 * Cobblemon, into$lambda$4), ya con sus evoluciones de fusión. require = 0: si Cobblemon cambia esta clase, solo vuelve
 * a pasar lo de antes.
 */
@Mixin(value = PokemonP2.class, remap = false)
public abstract class PokemonP2Mixin {

    @Inject(method = "into(Lcom/cobblemon/mod/common/pokemon/Pokemon;)Lcom/cobblemon/mod/common/pokemon/Pokemon;",
            at = @At("RETURN"), require = 0)
    private void fusionmon$restoreFusionEvolutions(Pokemon pokemon, CallbackInfoReturnable<Pokemon> cir) {
        try {
            if (!FusionData.isFusion(pokemon)
                    || !(pokemon.getEvolutionProxy() instanceof CobblemonEvolutionProxy proxy)) {
                return;
            }
            Optional<ServerEvolutionController.Intermediate> saved = ((PokemonP2) (Object) this).getEvolutionController();
            saved.ifPresent(controller -> proxy.overrideController$common(controller.create(pokemon)));
        } catch (RuntimeException | LinkageError e) {
            // Solo se pierden las evoluciones pendientes (Cobblemon las vuelve a encontrar)
            FusionSafety.failed("evoluciones pendientes de una fusión al cargarla", e);
        }
    }
}
