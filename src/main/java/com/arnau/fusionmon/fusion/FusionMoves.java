package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Movimientos que la fusión gana después de crearse (al evolucionar una parte).
 *
 * Ojo: estos métodos NO avisan al cliente; quien los llame debe reenviarle el Pokémon al terminar.
 * Motivo: cada add() normal de Cobblemon manda un paquete que lleva la lista de movimientos en sí (no una copia),
 * y el hilo de red la recorre un instante después. Si mientras tanto añadimos otro movimiento, salta una
 * ConcurrentModificationException y el servidor desconecta al jugador. Por eso se hace todo con
 * doWithoutEmitting (igual que en la evolución de Cobblemon) y se sincroniza una sola vez.
 */
final class FusionMoves {

    private FusionMoves() {
    }

    /**
     * Como hace Cobblemon con un movimiento nuevo: si hay hueco en los 4 movimientos lo aprende y avisa;
     * si no, queda para recordar (benched moves). No hace nada si la fusión ya lo tiene a mano.
     */
    static void learn(Pokemon fusion, MoveTemplate move, ServerPlayer player) {
        if (isAvailable(fusion, move)) {
            return;
        }

        if (fusion.getMoveSet().hasSpace()) {
            fusion.getMoveSet().doWithoutEmitting(() -> {
                fusion.getMoveSet().add(move.create());
                return Unit.INSTANCE;
            });
            if (player != null) {
                // Misma frase que usa Cobblemon ("X aprendió Y.")
                player.sendSystemMessage(Component.translatable("cobblemon.experience.learned_move",
                        fusion.getDisplayName(false), move.getDisplayName()));
            }
        } else {
            bench(fusion, List.of(move));
        }
    }

    /** Deja los movimientos como recordables (sin aviso) si la fusión aún no los tiene a mano. */
    static void bench(Pokemon fusion, Iterable<MoveTemplate> moves) {
        List<BenchedMove> toBench = new ArrayList<>();
        for (MoveTemplate move : moves) {
            if (!isAvailable(fusion, move)) {
                toBench.add(new BenchedMove(move, 0));
            }
        }
        if (toBench.isEmpty()) {
            return;
        }

        fusion.getBenchedMoves().doWithoutEmitting(() -> {
            fusion.getBenchedMoves().addAll(toBench);
            return Unit.INSTANCE;
        });
    }

    private static boolean isAvailable(Pokemon fusion, MoveTemplate move) {
        return fusion.getMoveSet().getMoveTemplates().contains(move) || fusion.getAllAccessibleMoves().contains(move);
    }
}
