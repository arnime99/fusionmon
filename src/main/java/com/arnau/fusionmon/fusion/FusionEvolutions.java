package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.evolution.EvolutionController;
import com.cobblemon.mod.common.api.pokemon.evolution.PassiveEvolution;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState;
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution;
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Evolución de fusiones.
 *
 * Cobblemon pregunta a cada Pokémon por sus evoluciones con Pokemon.getEvolutions() (PokemonMixin la intercepta).
 * A una fusión le devolvemos las evoluciones de la cabeza y del cuerpo guardados, envueltas en FusionPartEvolution.
 * A partir de ahí Cobblemon hace lo de siempre: las comprueba cada segundo, añade las que se cumplen a las
 * "pendientes" (icono de evolución), las muestra en su menú con el modelo y, al elegir una, llama a forceEvolve.
 * Solo cambiamos ese último paso: evoluciona la parte guardada, no la especie del Pokémon visible.
 */
public final class FusionEvolutions {

    private FusionEvolutions() {
    }

    /** Evoluciones de las dos partes, o null si no es una fusión (entonces Cobblemon usa las suyas). */
    public static List<Evolution> evolutionsOf(Pokemon fusion) {
        FormData head = FusionData.headForm(fusion);
        FormData body = FusionData.bodyForm(fusion);
        if (head == null || body == null) {
            return null;
        }

        List<Evolution> evolutions = new ArrayList<>();
        addWrapped(evolutions, FusionPart.HEAD, head);
        addWrapped(evolutions, FusionPart.BODY, body);
        return evolutions;
    }

    /**
     * Sustituye al forceEvolve de Cobblemon. El suyo anuncia "X ha evolucionado en <especie visible>", que es
     * falso cuando evoluciona el cuerpo, y hace la animación en el mundo sobre el modelo de la cabeza.
     * Aquí: sonido, evolución y mensaje con el nombre nuevo de la fusión.
     */
    static void forceEvolve(Pokemon fusion, FusionPartEvolution evolution) {
        if (fusion.getState() instanceof ShoulderedState) {
            fusion.tryRecallWithAnimation();
        }

        Component oldName = fusion.getDisplayName(false);
        evolution.evolutionMethod(fusion);

        ServerPlayer player = fusion.getOwnerPlayer();
        if (player != null) {
            player.playNotifySound(CobblemonSounds.EVOLUTION_UI, SoundSource.PLAYERS, 1f, 1f);
            player.sendSystemMessage(Component.translatable("cobblemon.ui.evolve.into", oldName, fusionName(fusion)));
        }
    }

    /** La evolución en sí: cambia la parte guardada y recalcula la fusión. */
    static void evolvePart(Pokemon fusion, FusionPartEvolution evolution) {
        ServerPlayer player = fusion.getOwnerPlayer();
        if (player == null || !FusionData.isFusion(fusion)) {
            // Solo evolucionan Pokémon del equipo de un jugador conectado (ahí es donde Cobblemon las comprueba)
            return;
        }

        FusionPart part = evolution.part();
        Evolution original = evolution.original();
        RegistryAccess registryAccess = player.registryAccess();
        double healthRatio = (double) fusion.getCurrentHealth() / fusion.getMaxHealth();

        // 1. Evolucionamos la copia guardada de esa parte, tal como lo haría Cobblemon con un Pokémon normal
        Pokemon stored = part == FusionPart.HEAD
                ? FusionData.readHead(fusion, registryAccess)
                : FusionData.readBody(fusion, registryAccess);
        AbilityTemplate abilityBefore = stored.getAbility().getTemplate();
        original.getResult().apply(stored);
        // Los movimientos que se aprenden al evolucionar también los conserva la parte para cuando se separe
        for (MoveTemplate move : original.getLearnableMoves()) {
            stored.getBenchedMoves().add(new BenchedMove(move, 0));
        }

        // 2. La guardamos: a partir de aquí stats, tipos y nombre de la fusión ya salen con la especie nueva
        FusionData.writePart(fusion, part, stored, registryAccess);

        // 3. Movimientos (sin avisar al cliente, ver FusionMoves). Van antes del cambio de especie de la cabeza
        //    para no tocar la lista de movimientos justo después de que Cobblemon la mande al cliente.
        if (part == FusionPart.BODY) {
            // Los movimientos por nivel de la nueva especie del cuerpo pasan a ser recordables
            FusionMoves.bench(fusion, stored.getForm().getMoves().getLevelUpMovesUpTo(fusion.getLevel()));
        }
        for (MoveTemplate move : original.getLearnableMoves()) {
            FusionMoves.learn(fusion, move, player);
        }

        if (part == FusionPart.HEAD) {
            // La cabeza es el Pokémon visible: cambia su especie (modelo, habilidad no forzada...) como siempre.
            // Cobblemon vacía entonces las evoluciones pendientes; la del cuerpo se vuelve a añadir abajo.
            original.getResult().apply(fusion);
        } else {
            // El modelo no cambia, pero sí la habilidad si la fusión tenía la del cuerpo (la guardamos forzada)
            if (fusion.getAbility().getForced() && fusion.getAbility().getTemplate() == abilityBefore) {
                fusion.updateAbility(stored.getAbility().getTemplate().create(true, Priority.NORMAL));
            }
            // Ya no está pendiente ninguna evolución de esta parte (p. ej. las otras ramas de Eevee)
            removePending(fusion, part);
        }

        // Los PS máximos han cambiado: mismo porcentaje de vida que antes
        FusionService.applyHealthRatio(fusion, healthRatio);

        // Como hace Cobblemon tras evolucionar: se vuelven a comprobar las evoluciones, así la de la otra parte
        // sigue pendiente (o aparece la siguiente de la cadena si ya se cumple)
        for (Evolution pending : fusion.getLockedEvolutions()) {
            if (pending instanceof PassiveEvolution passive) {
                passive.attemptEvolution(fusion);
            }
        }

        // Lo último: el cliente recibe el Pokémon completo (datos de fusión, movimientos y evoluciones pendientes).
        // Después ya no lo tocamos, porque el paquete se serializa un poco más tarde en el hilo de red.
        FusionService.resendToClient(player, fusion);

        Fusionmon.LOGGER.info("{}: {} de la fusión ha evolucionado a {}",
                player.getName().getString(), part, stored.getSpecies().getName());
    }

    private static void addWrapped(List<Evolution> into, FusionPart part, FormData form) {
        for (Evolution evolution : form.getEvolutions()) {
            if (evolution instanceof LevelUpEvolution levelUp) {
                into.add(new FusionLevelUpEvolution(part, levelUp));
            } else if (evolution instanceof ItemInteractionEvolution item) {
                into.add(new FusionItemEvolution(part, item));
            }
            // Las de intercambio y de clic en bloque no se ofrecen a las fusiones (de momento)
        }
    }

    /**
     * Quita de las pendientes las evoluciones de una parte. Uno a uno con remove(): así Cobblemon avisa al
     * cliente de cada una (un removeIf iría por el iterador y el cliente seguiría mostrándolas).
     */
    private static void removePending(Pokemon fusion, FusionPart part) {
        EvolutionController<Evolution, ?> pending = fusion.getEvolutionProxy().server();
        List<Evolution> toRemove = new ArrayList<>();
        for (Evolution evolution : pending) {
            if (evolution instanceof FusionPartEvolution partEvolution && partEvolution.part() == part) {
                toRemove.add(evolution);
            }
        }
        for (Evolution evolution : toRemove) {
            pending.remove(evolution);
        }
    }

    /** Nombre de fusión aunque tenga mote (el mensaje de evolución habla de la especie, no del mote). */
    private static Component fusionName(Pokemon fusion) {
        FormData head = FusionData.headForm(fusion);
        FormData body = FusionData.bodyForm(fusion);
        return Component.literal(FusionCalculator.name(head.getSpecies().getName(), body.getSpecies().getName()));
    }
}
