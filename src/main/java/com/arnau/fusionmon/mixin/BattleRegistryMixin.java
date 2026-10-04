package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionShowdown;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Justo antes de que Cobblemon mande el combate a Showdown, registramos las especies de fusión que participan. */
@Mixin(value = BattleRegistry.class, remap = false)
public abstract class BattleRegistryMixin {

    @Inject(method = "startShowdown", at = @At("HEAD"))
    private void fusionmon$registerFusions(PokemonBattle battle, CallbackInfo ci) {
        FusionShowdown.registerFusions(battle);
    }
}
