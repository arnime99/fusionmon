package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionMegas;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mega de fuera de combate del cuerpo de una fusión (ver FusionMegas). Mega Showdown solo mira la especie visible,
 * la cabeza; aquí:
 * - canMega: si él dice que no, vale también si la mega que toca es la del cuerpo (FusionMegas.canMegaBody, con sus
 *   mismas condiciones: Mega Aro y ninguna otra mega). Al final del método y no en su comparación de especie: con
 *   Rayquaza de cabeza decide por Ascenso Draco y nunca llegaba a mirar la piedra (y el cuerpo).
 * - megaEvolve: si la mega que toca es la del cuerpo (primero la piedra, luego Rayquaza), megaevolucionamos el cuerpo
 *   en vez de la cabeza.
 * - unmegaEvolve: por donde pasa toda mega que se deshace (empezar un combate, quitar la piedra, volver a pulsar):
 *   quitamos la del cuerpo y Mega Showdown sigue con lo suyo.
 * canMega solo se usa fuera de combate (la rueda de interacción); en combate decide Showdown, así que esto no ofrece
 * megas a mitad de combate. @Pseudo + require = 0: sin Mega Showdown, o si una versión suya cambia, no se aplica y
 * el cuerpo simplemente no megaevoluciona.
 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.gimmick.MegaGimmick", remap = false)
public abstract class MegaShowdownMegaGimmickMixin {

    /** Una vez por partida, para ver en el log que el enganche está puesto. */
    @Unique
    private static boolean fusionmon$announced;

    @Inject(method = "canMega", require = 0, at = @At("RETURN"), cancellable = true)
    private static void fusionmon$bodyCanMega(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (!fusionmon$announced) {
            fusionmon$announced = true;
            Fusionmon.LOGGER.info("Fusionmon: megas del cuerpo de las fusiones enganchadas a Mega Showdown");
        }
        if (cir.getReturnValueZ()) {
            return;
        }
        try {
            if (FusionMegas.canMegaBody(pokemon)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("mega del cuerpo (canMega)", e);
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
