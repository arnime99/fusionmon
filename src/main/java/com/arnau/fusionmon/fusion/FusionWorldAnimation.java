package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

/**
 * Animación de fusión en el mundo (solo al fusionar, no al separar ni invertir): si la cabeza y el cuerpo están fuera
 * de la Poké Ball y cerca del jugador, la fusión se ve en el mundo, como una evolución de Cobblemon, en vez de en la
 * pantalla. Pensada para que se vea bien en vídeo y para que la vean los demás jugadores.
 */
public final class FusionWorldAnimation {

    /** Distancia máxima (bloques) de cada Pokémon al jugador. */
    private static final double MAX_DISTANCE = 16;

    private FusionWorldAnimation() {
    }

    /** Si la fusión de head + body se puede animar en el mundo: los dos fuera, vivos, en su mundo y cerca. */
    public static boolean canPlay(ServerPlayer player, Pokemon head, Pokemon body) {
        return isNear(player, head.getEntity()) && isNear(player, body.getEntity());
    }

    private static boolean isNear(ServerPlayer player, PokemonEntity entity) {
        return entity != null
                && entity.isAlive()
                && entity.level() == player.level()
                && entity.distanceTo(player) <= MAX_DISTANCE;
    }
}
