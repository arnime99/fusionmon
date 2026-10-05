package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.ExperienceGainedEvent;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;

import java.util.Set;

/**
 * Movimientos del cuerpo al subir de nivel.
 *
 * Cobblemon, al ganar experiencia, solo mira la lista de movimientos por nivel de la especie visible (la cabeza).
 * Justo después lanza EXPERIENCE_GAINED_EVENT_POST con el nivel anterior y el nuevo: ahí añadimos los del cuerpo
 * que tocan en esos niveles (aprendidos si hay hueco, si no recordables).
 */
public final class FusionLevelUp {

    private FusionLevelUp() {
    }

    public static void register() {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_POST.subscribe(Priority.NORMAL, event -> {
            onExperienceGained(event);
            return Unit.INSTANCE;
        });
    }

    private static void onExperienceGained(ExperienceGainedEvent.Post event) {
        Pokemon fusion = event.getPokemon();
        if (event.getCurrentLevel() <= event.getPreviousLevel()) {
            return;
        }
        FormData body = FusionData.bodyForm(fusion);
        if (body == null) {
            // No es una fusión
            return;
        }

        // Los que el cuerpo aprende pasado el nivel anterior y hasta el nuevo (si sube varios de golpe, todos)
        Set<MoveTemplate> alreadyReached = body.getMoves().getLevelUpMovesUpTo(event.getPreviousLevel());
        boolean changed = false;
        for (MoveTemplate move : body.getMoves().getLevelUpMovesUpTo(event.getCurrentLevel())) {
            if (!alreadyReached.contains(move)) {
                changed |= FusionMoves.learn(fusion, move, fusion.getOwnerPlayer());
            }
        }

        if (changed) {
            // FusionMoves no avisa al cliente: un único paquete por lista con el estado final
            fusion.getMoveSet().update();
            fusion.getBenchedMoves().update();
        }
    }
}
