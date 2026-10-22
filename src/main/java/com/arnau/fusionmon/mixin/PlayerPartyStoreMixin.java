package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionBodyForm;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cobblemon comprueba cada poco que el Pokémon del hombro todavía puede ir ahí (validateShoulder) y si no, lo baja.
 * Mira la forma del Pokémon, que en una fusión es la de la cabeza: la del cuerpo, como al subirlo (PokemonEntityMixin).
 * En ese método getForm solo se usa para eso. require = 0: si no se engancha, decide la cabeza, como antes.
 */
@Mixin(value = PlayerPartyStore.class, remap = false)
public abstract class PlayerPartyStoreMixin {

    @WrapOperation(method = "validateShoulder", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;getForm()Lcom/cobblemon/mod/common/pokemon/FormData;"))
    private FormData fusionmon$bodyShoulder(Pokemon pokemon, Operation<FormData> original) {
        FormData body = FusionBodyForm.of(pokemon);
        return body != null ? body : original.call(pokemon);
    }
}
