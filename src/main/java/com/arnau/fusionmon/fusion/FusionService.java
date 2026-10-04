package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Operaciones de fusión sobre el equipo del jugador (solo servidor).
 *
 * Pokémon visible = el propio objeto de A (mantiene su sitio en el equipo y su aspecto).
 * B sale del equipo y queda guardado dentro de A.
 */
public final class FusionService {

    /** PS, Ataque, Defensa, At. Esp., Def. Esp., Velocidad (también es el orden de la vista previa). */
    static final List<Stat> PERMANENT_STATS = List.of(
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED);

    private FusionService() {
    }

    public static void fuse(ServerPlayer player, Pokemon head, Pokemon body,
                            boolean natureFromBody, boolean abilityFromBody) {
        // Primero quitamos los objetos: así no quedan dentro de las copias guardadas y no se duplican al desfusionar
        returnHeldItem(player, head);
        returnHeldItem(player, body);

        body.recall();

        double healthRatio = (double) head.getCurrentHealth() / head.getMaxHealth();

        // Las copias se guardan antes de tocar nada: son los originales para desfusionar
        RegistryAccess registryAccess = player.registryAccess();
        FusionData.write(head, head, body, registryAccess);

        // Lo que la cabeza podía recordar antes de cambiar de nivel: la media puede bajarle el nivel
        Set<MoveTemplate> headMovesBefore = head.getAllAccessibleMoves();

        applyAverages(head, body);
        addMovesToBench(head, headMovesBefore, body);

        if (natureFromBody) {
            head.setNature(body.getNature());
        }
        if (abilityFromBody) {
            // Forzada: si no, Cobblemon la cambiaría por una de la especie de la cabeza en cuanto pudiera
            head.updateAbility(body.getAbility().getTemplate().create(true, Priority.NORMAL));
        }

        // Los PS máximos han cambiado: mantenemos el mismo porcentaje de vida
        int maxHealth = head.getMaxHealth();
        int health = healthRatio > 0 ? Math.max(1, (int) Math.round(healthRatio * maxHealth)) : 0;
        head.setCurrentHealth(health);

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        party.remove(body);
        resendToClient(player, party, head);

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

    /** Nivel, IVs y EVs del Pokémon visible pasan a ser la media de A y B. */
    private static void applyAverages(Pokemon visible, Pokemon other) {
        visible.setLevel((visible.getLevel() + other.getLevel()) / 2);

        for (Stat stat : PERMANENT_STATS) {
            visible.getIvs().set(stat, (visible.getIvs().getOrDefault(stat) + other.getIvs().getOrDefault(stat)) / 2);
            visible.getEvs().set(stat, (visible.getEvs().getOrDefault(stat) + other.getEvs().getOrDefault(stat)) / 2);
        }
    }

    /**
     * Cobblemon no avisa al cliente cuando cambia persistentData, así que le volvemos a mandar el Pokémon
     * completo a su hueco (el mismo paquete que usa Cobblemon al colocar un Pokémon en el equipo).
     * Ojo: no usar party.sendTo(), que envía el equipo como si fuera de otro jugador y desincroniza el cliente.
     */
    private static void resendToClient(ServerPlayer player, PlayerPartyStore party, Pokemon pokemon) {
        PartyPosition position = new PartyPosition(slotOf(party, pokemon));
        CobblemonNetwork.INSTANCE.sendPacket(player,
                new SetPartyPokemonPacket(party.getUuid(), position, registryAccess -> pokemon));
    }

    /**
     * La fusión mantiene los movimientos de la cabeza. Todo lo demás que cualquiera de las dos partes podía
     * usar o recordar va a los "benched moves": así aparece en el panel de cambiar movimientos de Cobblemon,
     * y su servidor acepta el cambio porque valida contra esa misma lista.
     */
    private static void addMovesToBench(Pokemon visible, Set<MoveTemplate> headMovesBefore, Pokemon body) {
        Set<MoveTemplate> candidates = new LinkedHashSet<>(headMovesBefore);
        candidates.addAll(body.getMoveSet().getMoveTemplates());
        candidates.addAll(body.getAllAccessibleMoves());
        // Movimientos por nivel del cuerpo hasta el nivel de la fusión (por si es mayor que el del cuerpo)
        candidates.addAll(body.getForm().getMoves().getLevelUpMovesUpTo(visible.getLevel()));

        Set<MoveTemplate> alreadyAvailable = new HashSet<>(visible.getAllAccessibleMoves());
        alreadyAvailable.addAll(visible.getMoveSet().getMoveTemplates());

        List<BenchedMove> toBench = new ArrayList<>();
        for (MoveTemplate move : candidates) {
            if (alreadyAvailable.add(move)) {
                toBench.add(new BenchedMove(move, raisedPpStages(body, move)));
            }
        }
        visible.getBenchedMoves().addAll(toBench);
    }

    /** Si el cuerpo ya conocía el movimiento con PP aumentados (Más PP), se conservan. */
    private static int raisedPpStages(Pokemon body, MoveTemplate template) {
        for (Move move : body.getMoveSet().getMoves()) {
            if (move.getTemplate() == template) {
                return move.getRaisedPpStages();
            }
        }
        return 0;
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
