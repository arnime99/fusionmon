package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource;
import com.cobblemon.mod.common.api.pokemon.experience.SidemodExperienceSource;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PartyPosition;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
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

    private static final ExperienceSource EXPERIENCE_SOURCE = new SidemodExperienceSource(Fusionmon.MOD_ID);

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
        // Si la cabeza tenía una evolución pendiente, era de su especie: a partir de ahora las evoluciones
        // de la fusión son las de sus dos partes (FusionEvolutions) y Cobblemon las volverá a comprobar
        head.getEvolutionProxy().server().clear();

        // Lo que la cabeza podía recordar antes de cambiar de nivel: la media puede bajarle el nivel
        Set<MoveTemplate> headMovesBefore = head.getAllAccessibleMoves();

        applyAverages(head, body);
        FusionData.markStartExperience(head);
        addMovesToBench(head, headMovesBefore, body);

        if (natureFromBody) {
            head.setNature(body.getNature());
        }
        if (abilityFromBody) {
            // Forzada: si no, Cobblemon la cambiaría por una de la especie de la cabeza en cuanto pudiera
            head.updateAbility(body.getAbility().getTemplate().create(true, Priority.NORMAL));
        }

        // Los PS máximos han cambiado: mantenemos el mismo porcentaje de vida
        applyHealthRatio(head, healthRatio);

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        party.remove(body);
        resendToClient(player, party, head);

        Fusionmon.LOGGER.info("{} ha fusionado {} + {}",
                player.getName().getString(), head.getSpecies().getName(), body.getSpecies().getName());
    }

    /** Las dos partes guardadas de una fusión, ya leídas y listas para volver al equipo. */
    public record Parts(Pokemon head, Pokemon body) {
    }

    /**
     * Comprueba que se puede separar la fusión: que se pueden leer sus dos partes y que hay sitio para el cuerpo
     * (la cabeza ocupa el hueco de la fusión). No toca nada. Si algo lo impide, avisa al jugador y devuelve null.
     * Hay que llamarlo ANTES de gastar el cristal: así un fallo nunca cuesta un cristal ni, sobre todo, un Pokémon.
     */
    public static Parts prepareUnfuse(ServerPlayer player, Pokemon fused) {
        // La especie de una parte puede venir de un mod que ya no está (p. ej. AllTheMons): la fusión no se toca,
        // y vuelve a funcionar si se reinstala
        String missing = FusionData.missingSpecies(fused);
        if (missing != null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.missing_species", missing));
            return null;
        }

        Parts parts;
        try {
            RegistryAccess registryAccess = player.registryAccess();
            parts = new Parts(FusionData.readHead(fused, registryAccess), FusionData.readBody(fused, registryAccess));
        } catch (RuntimeException e) {
            // Datos dañados o de un formato que Cobblemon ya no entiende: mejor no separar que perder una parte
            Fusionmon.LOGGER.error("No se han podido leer las partes de la fusión {} de {}",
                    fused.getUuid(), player.getName().getString(), e);
            player.sendSystemMessage(Component.translatable("message.fusionmon.unfuse_failed"));
            return null;
        }

        // Cobblemon manda el cuerpo al PC si el equipo está lleno; si el PC también lo está, se perdería
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
        if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.no_room",
                    parts.body().getDisplayName(false)));
            return null;
        }
        return parts;
    }

    /** Separa la fusión en las partes que ha devuelto prepareUnfuse (en el mismo tick: el equipo no ha cambiado). */
    public static void unfuse(ServerPlayer player, Pokemon fused, Parts parts) {
        Pokemon head = parts.head();
        Pokemon body = parts.body();
        int experienceGained = FusionData.experienceGained(fused);

        // Los dos salen con el % de vida de la fusión (si no, fusionar y separar curaría gratis)
        double healthRatio = (double) fused.getCurrentHealth() / fused.getMaxHealth();
        applyHealthRatio(head, healthRatio);
        applyHealthRatio(body, healthRatio);

        // Cualquier objeto que se le haya dado a la fusión vuelve al jugador
        returnHeldItem(player, fused);
        fused.recall();

        // El cuerpo se coloca ANTES de quitar la fusión: si no hubiera sitio, la fusión se queda como estaba en vez
        // de perderse el cuerpo. Si el equipo está lleno, Cobblemon lo manda al PC (y avisa al jugador)
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (!party.add(body)) {
            Fusionmon.LOGGER.error("No hay sitio para el cuerpo de la fusión {} de {}: no se separa",
                    fused.getUuid(), player.getName().getString());
            return;
        }
        int slot = slotOf(party, fused);
        party.remove(fused);
        party.set(slot, head);

        // Cada parte recibe toda la experiencia ganada como fusión. Se da después de colocarlos para que
        // Cobblemon haga lo normal al subir de nivel: avisar al jugador, aprender movimientos, evoluciones...
        if (experienceGained > 0) {
            head.addExperienceWithPlayer(player, EXPERIENCE_SOURCE, experienceGained);
            body.addExperienceWithPlayer(player, EXPERIENCE_SOURCE, experienceGained);
        }

        Fusionmon.LOGGER.info("{} ha desfusionado {} + {}",
                player.getName().getString(), head.getSpecies().getName(), body.getSpecies().getName());
    }

    /** Pone los PS actuales al mismo porcentaje; un Pokémon con algo de vida nunca baja a 0 por redondeo. */
    static void applyHealthRatio(Pokemon pokemon, double ratio) {
        int maxHealth = pokemon.getMaxHealth();
        pokemon.setCurrentHealth(ratio > 0 ? Math.max(1, (int) Math.round(ratio * maxHealth)) : 0);
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

    /** Lo mismo, para un Pokémon que esté en el equipo del jugador (si no está ahí, no hace nada). */
    static void resendToClient(ServerPlayer player, Pokemon pokemon) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party.get(pokemon.getUuid()) != null) {
            resendToClient(player, party, pokemon);
        }
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
