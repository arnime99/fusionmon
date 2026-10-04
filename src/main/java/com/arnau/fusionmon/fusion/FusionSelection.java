package com.arnau.fusionmon.fusion;

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
 * 3. se comprueba que ambos siguen en el equipo y se fusionan (FusionService)
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
        if (party.stream().filter(pokemon -> !FusionData.isFusion(pokemon)).count() < 2) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.not_enough_pokemon"));
            return;
        }

        // Las fusiones salen bloqueadas en los selectores: de momento no hay fusión de fusiones
        PartySelectCallbacks.INSTANCE.createFromPokemon(
                player,
                Component.translatable("gui.fusionmon.select_head"),
                party,
                pokemon -> !FusionData.isFusion(pokemon),
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
                pokemon -> !pokemon.getUuid().equals(headId) && !FusionData.isFusion(pokemon),
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
        if (head == null || body == null || FusionData.isFusion(head) || FusionData.isFusion(body)) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.selection_changed"));
            return;
        }

        // Los nombres se leen antes de fusionar: después B ya no está en el equipo
        Component headName = head.getDisplayName(false);
        Component bodyName = body.getDisplayName(false);
        FusionService.fuse(player, head, body);
        player.sendSystemMessage(Component.translatable("message.fusionmon.fused", headName, bodyName));
    }

    private static List<Pokemon> partyOf(ServerPlayer player) {
        List<Pokemon> result = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            result.add(pokemon);
        }
        return result;
    }
}
