package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Operaciones de fusión sobre el equipo del jugador (solo servidor).
 *
 * Pokémon visible = el propio objeto de A (mantiene su sitio en el equipo y su aspecto).
 * B sale del equipo y queda guardado dentro de A.
 */
public final class FusionService {

    private FusionService() {
    }

    public static void fuse(ServerPlayer player, Pokemon head, Pokemon body) {
        // Primero quitamos los objetos: así no quedan dentro de las copias guardadas y no se duplican al desfusionar
        returnHeldItem(player, head);
        returnHeldItem(player, body);

        body.recall();

        RegistryAccess registryAccess = player.registryAccess();
        FusionData.write(head, head, body, registryAccess);

        Cobblemon.INSTANCE.getStorage().getParty(player).remove(body);

        Fusionmon.LOGGER.info("{} ha fusionado {} + {}",
                player.getName().getString(), head.getSpecies().getName(), body.getSpecies().getName());
    }

    public static void unfuse(ServerPlayer player, Pokemon fused) {
        RegistryAccess registryAccess = player.registryAccess();
        Pokemon head = FusionData.readHead(fused, registryAccess);
        Pokemon body = FusionData.readBody(fused, registryAccess);

        // Cualquier objeto que se le haya dado a la fusión vuelve al jugador
        returnHeldItem(player, fused);
        fused.recall();

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int slot = slotOf(party, fused);
        party.remove(fused);
        party.set(slot, head);
        // Si el equipo está lleno, Cobblemon lo manda al PC
        if (!party.add(body)) {
            Cobblemon.INSTANCE.getStorage().getPC(player).add(body);
        }

        Fusionmon.LOGGER.info("{} ha desfusionado {} + {}",
                player.getName().getString(), head.getSpecies().getName(), body.getSpecies().getName());
    }

    private static void returnHeldItem(ServerPlayer player, Pokemon pokemon) {
        ItemStack item = pokemon.removeHeldItem();
        if (!item.isEmpty()) {
            // Al inventario; si está lleno, cae al suelo
            player.getInventory().placeItemBackInInventory(item);
        }
    }

    private static int slotOf(PlayerPartyStore party, Pokemon pokemon) {
        for (int i = 0; i < party.size(); i++) {
            if (party.get(i) == pokemon) {
                return i;
            }
        }
        throw new IllegalStateException("El Pokémon no está en el equipo");
    }
}
