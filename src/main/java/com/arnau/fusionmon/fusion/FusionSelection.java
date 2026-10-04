package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Flujo de selección (solo servidor):
 * 1. selector de equipo de Cobblemon → Pokémon A (cabeza)
 * 2. segundo selector, sin A → Pokémon B (cuerpo)
 * 3. se comprueba que ambos siguen en el equipo y se fusionan
 */
public final class FusionSelection {

    private FusionSelection() {
    }

    public static void start(ServerPlayer player) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.in_battle"));
            return;
        }

        List<Pokemon> party = partyOf(player);
        if (party.size() < 2) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.not_enough_pokemon"));
            return;
        }

        PartySelectCallbacks.INSTANCE.createFromPokemon(
                player,
                Component.translatable("gui.fusionmon.select_head"),
                party,
                pokemon -> true,
                cancelledBy -> Unit.INSTANCE,
                head -> {
                    selectBody(player, head.getUuid());
                    return Unit.INSTANCE;
                }
        );
    }

    private static void selectBody(ServerPlayer player, UUID headId) {
        PartySelectCallbacks.INSTANCE.createFromPokemon(
                player,
                Component.translatable("gui.fusionmon.select_body"),
                partyOf(player),
                pokemon -> !pokemon.getUuid().equals(headId),
                cancelledBy -> Unit.INSTANCE,
                body -> {
                    finish(player, headId, body.getUuid());
                    return Unit.INSTANCE;
                }
        );
    }

    private static void finish(ServerPlayer player, UUID headId, UUID bodyId) {
        // Entre un selector y otro el jugador podría haber movido Pokémon al PC: volvemos a buscarlos en el equipo
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon head = party.get(headId);
        Pokemon body = party.get(bodyId);
        if (head == null || body == null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.selection_changed"));
            return;
        }

        // Fase 1: solo confirmamos la selección. La fusión de datos llega en la fase 2.
        Fusionmon.LOGGER.info("{} quiere fusionar {} (cabeza) + {} (cuerpo)",
                player.getName().getString(), head.getSpecies().getName(), body.getSpecies().getName());
        player.sendSystemMessage(Component.translatable("message.fusionmon.selected",
                head.getDisplayName(false), body.getDisplayName(false)));
    }

    private static List<Pokemon> partyOf(ServerPlayer player) {
        List<Pokemon> result = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            result.add(pokemon);
        }
        return result;
    }
}
