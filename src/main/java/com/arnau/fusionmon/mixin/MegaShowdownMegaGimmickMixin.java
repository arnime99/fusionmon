package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.fusion.FusionMegas;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mega de fuera de combate del cuerpo de una fusión (ver FusionMegas). Mega Showdown solo mira la especie visible,
 * la cabeza; aquí:
 * - canMega: si la piedra no sirve para la cabeza, también vale si sirve para el cuerpo (el resto de sus
 *   comprobaciones, como llevar el Mega Aro, siguen siendo suyas: solo cambiamos la de la especie).
 * - megaEvolve: si la piedra es del cuerpo, megaevolucionamos el cuerpo en vez de la cabeza.
 * - unmegaEvolve: por donde pasa toda mega que se deshace (empezar un combate, quitar la piedra, volver a pulsar):
 *   quitamos la del cuerpo y Mega Showdown sigue con lo suyo.
 * canMega solo se usa fuera de combate (la rueda de interacción); en combate decide Showdown, así que esto no ofrece
 * megas a mitad de combate. @Pseudo + require = 0: sin Mega Showdown, o si una versión suya cambia, no se aplica y
 * el cuerpo simplemente no megaevoluciona.
 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick", remap = false)
public abstract class MegaShowdownMegaGimmickMixin {

    /** canMega acaba con pokemons.contains(nombre de la especie): la única llamada a List.contains del método. */
    @WrapOperation(method = "canMega", require = 0, at = @At(value = "INVOKE",
            target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private static boolean fusionmon$bodyCanMega(List<?> pokemons, Object species, Operation<Boolean> original,
                                                 @Local(argsOnly = true) Pokemon pokemon) {
        if (original.call(pokemons, species)) {
            return true;
        }
        try {
            return FusionMegas.bodyFits(pokemon, pokemons);
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("mega del cuerpo (canMega)", e);
            return false;
        }
    }

    @Inject(method = "megaEvolve", require = 0, at = @At("HEAD"), cancellable = true)
    private static void fusionmon$megaEvolveBody(Pokemon pokemon, CallbackInfo ci) {
        try {
            if (FusionMegas.megaEvolveBody(pokemon)) {
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("mega del cuerpo (megaEvolve)", e);
        }
    }

    @Inject(method = "unmegaEvolve", require = 0, at = @At("HEAD"))
    private static void fusionmon$unmegaEvolveBody(Pokemon pokemon, CallbackInfo ci) {
        try {
            FusionMegas.revertBody(pokemon);
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("mega del cuerpo (unmegaEvolve)", e);
        }
    }
}
