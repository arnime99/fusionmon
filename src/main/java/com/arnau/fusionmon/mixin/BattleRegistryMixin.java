package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionShowdown;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Justo antes de que Cobblemon mande el combate a Showdown, registramos las especies de fusión que participan.
 * require = 0: si otro mod o una versión de Cobblemon cambia este método, el juego arranca igual y las fusiones pelean
 * como su cabeza (FusionShowdown.isUsable); CommonMixinPlugin lo avisa en el log.
 */
@Mixin(value = BattleRegistry.class, remap = false)
public abstract class BattleRegistryMixin {

    @Inject(method = "startShowdown", at = @At("HEAD"), require = 0)
    private void fusionmon$registerFusions(PokemonBattle battle, CallbackInfo ci) {
        FusionShowdown.registerFusions(battle);
    }
}
