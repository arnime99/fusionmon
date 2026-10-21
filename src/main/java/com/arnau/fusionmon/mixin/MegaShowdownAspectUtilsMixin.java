package com.arnau.fusionmon.mixin;

import com.arnau.fusionmon.fusion.FusionData;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.pokemon.moves.LearnsetQuery;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mega Showdown (mod opcional) trae su propia copia de Pokemon.updateMovesOnFormChange para megaevolucionar y otros
 * cambios de forma, así que PokemonMixin no la cubre: borraba de una fusión todos los recordables que la forma nueva
 * de la cabeza no aprende (casi todos los del cuerpo) y, al borrarlos de uno en uno, echaba al jugador del servidor
 * (ver BenchedMovesUpdatePacketMixin). Igual que en PokemonMixin: en una fusión todo cuenta como aprendible.
 *
 * @Pseudo: si Mega Showdown no está instalado, este mixin se salta sin error. require = 0: si una versión suya cambia
 * el método, tampoco se rompe el juego (solo se pierde esta protección; el kick ya lo evita la copia del paquete).
 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.utils.AspectUtils", remap = false)
public abstract class MegaShowdownAspectUtilsMixin {

    /** El método de Mega Showdown es static, así que el nuestro también; el Pokémon llega como su argumento. */
    @WrapOperation(method = "updateMovesOnFormChange", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/pokemon/moves/LearnsetQuery;canLearn(Lcom/cobblemon/mod/common/api/moves/MoveTemplate;Lcom/cobblemon/mod/common/api/pokemon/moves/Learnset;)Z"))
    private static boolean fusionmon$keepFusionMoves(LearnsetQuery query, MoveTemplate move, Learnset learnset,
                                                     Operation<Boolean> original,
                                                     @Local(argsOnly = true) Pokemon pokemon) {
        if (FusionData.isFusion(pokemon)) {
            return true;
        }
        return original.call(query, move, learnset);
    }
}
