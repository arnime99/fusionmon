package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.fusion.FusionUltra;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ultraexplosión de fuera de combate del cuerpo de una fusión (ver FusionUltra), como MegaShowdownMegaGimmickMixin
 * con las megas. Mega Showdown solo mira la especie visible, la cabeza; aquí:
 * - canUltraBurst: si él dice que no, vale también si el cuerpo es Necrozma en forma de fusión (FusionUltra.canUltraBody,
 *   con sus mismas condiciones: Aro Z y Ultranecrozium Z).
 * - isUltra: una fusión con el cuerpo en Ultra también lo está (botón de la rueda para deshacerla, y ultraBurstToggle).
 * - ultraBurst / unUltraBurst: si es la del cuerpo, la hacemos o deshacemos nosotros en vez de cambiar la cabeza.
 * @Pseudo + require = 0: sin Mega Showdown, o si una versión suya cambia, no se aplica y el cuerpo no la hace.
 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.gimmick.UltraGimmick", remap = false)
public abstract class MegaShowdownUltraGimmickMixin {

    @Inject(method = "canUltraBurst", require = 0, at = @At("RETURN"), cancellable = true)
    private static void fusionmon$bodyCanUltra(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        try {
            if (FusionUltra.canUltraBody(pokemon)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("Ultraexplosión del cuerpo (canUltraBurst)", e);
        }
    }

    @Inject(method = "isUltra", require = 0, at = @At("RETURN"), cancellable = true)
    private static void fusionmon$bodyIsUltra(Pokemon pokemon, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;
        }
        try {
            if (FusionUltra.isBodyUltra(pokemon)) {
                cir.setReturnValue(true);
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("Ultraexplosión del cuerpo (isUltra)", e);
        }
    }

    @Inject(method = "ultraBurst", require = 0, at = @At("HEAD"), cancellable = true)
    private static void fusionmon$ultraBurstBody(Pokemon pokemon, CallbackInfo ci) {
        try {
            if (FusionUltra.ultraBurstBody(pokemon)) {
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("Ultraexplosión del cuerpo (ultraBurst)", e);
        }
    }

    /**
     * En combate (Showdown ya ha hecho la Ultraexplosión de la fusión, ver FusionShowdownMegas): si es la del cuerpo,
     * la ponemos en el cuerpo; si no, Mega Showdown cambia la cabeza como siempre.
     */
    @Inject(method = "ultraBurstInBattle", require = 0, at = @At("HEAD"), cancellable = true)
    private static void fusionmon$ultraBurstBodyInBattle(Pokemon pokemon, BattlePokemon battlePokemon,
                                                         CallbackInfo ci) {
        try {
            if (FusionUltra.ultraBurstBodyInBattle(pokemon, battlePokemon)) {
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("Ultraexplosión del cuerpo (ultraBurstInBattle)", e);
        }
    }

    /** Al acabar ultraBurstInBattle (la de la cabeza, de Mega Showdown): la habilidad de la forma Ultra. */
    @Inject(method = "ultraBurstInBattle", require = 0, at = @At("RETURN"))
    private static void fusionmon$headUltraAbilityInBattle(Pokemon pokemon, BattlePokemon battlePokemon,
                                                           CallbackInfo ci) {
        try {
            FusionUltra.afterHeadUltraInBattle(pokemon);
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("habilidad de la Ultraexplosión de la cabeza en combate", e);
        }
    }

    @Inject(method = "unUltraBurst", require = 0, at = @At("HEAD"), cancellable = true)
    private static void fusionmon$unUltraBurstBody(Pokemon pokemon, CallbackInfo ci) {
        try {
            if (FusionUltra.unUltraBurstBody(pokemon)) {
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("Ultraexplosión del cuerpo (unUltraBurst)", e);
        }
    }
}
