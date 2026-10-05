package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.item.FusionCrystalItem;
import com.arnau.fusionmon.network.FusionChoicePayload;
import com.arnau.fusionmon.network.FusionPreview;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import com.arnau.fusionmon.network.UnfuseChoicePayload;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.callback.PartySelectCallbacks;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import kotlin.Unit;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Flujo del Cristal de Fusión (solo servidor):
 * 1. selector de equipo de Cobblemon → un Pokémon normal o una fusión
 * 2a. fusión → pantalla "¿Separar?" → se separa (FusionService.unfuse)
 * 2b. normal → segundo selector → pantalla de confirmación (orden, naturaleza, habilidad) → se fusiona
 * Antes de actuar se vuelve a comprobar todo: el cliente solo elige, el servidor decide.
 */
public final class FusionSelection {

    /** Fusión esperando respuesta de la pantalla de confirmación, por jugador. */
    private record PendingFusion(UUID firstId, UUID secondId) {
    }

    private static final Map<UUID, PendingFusion> PENDING_FUSIONS = new HashMap<>();
    /** Fusión esperando respuesta de la pantalla de separar, por jugador. */
    private static final Map<UUID, UUID> PENDING_UNFUSES = new HashMap<>();

    private FusionSelection() {
    }

    public static void start(ServerPlayer player) {
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.in_battle"));
            return;
        }

        List<Pokemon> party = partyOf(player);
        long unfused = party.stream().filter(pokemon -> !FusionData.isFusion(pokemon)).count();
        boolean canFuse = unfused >= 2;
        boolean canUnfuse = unfused < party.size();
        if (!canFuse && !canUnfuse) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.not_enough_pokemon"));
            return;
        }

        // Las fusiones siempre se pueden elegir (para separarlas); los normales, solo si hay con quién fusionarlos
        PartySelectCallbacks.INSTANCE.createFromPokemon(
                player,
                Component.translatable("gui.fusionmon.select_first"),
                party,
                pokemon -> FusionData.isFusion(pokemon) || canFuse,
                cancelledBy -> Unit.INSTANCE,
                first -> {
                    if (FusionData.isFusion(first)) {
                        openUnfuseConfirmation(player, first.getUuid());
                    } else {
                        selectSecond(player, first.getUuid());
                    }
                    return Unit.INSTANCE;
                }
        );
    }

    private static void selectSecond(ServerPlayer player, UUID firstId) {
        PartySelectCallbacks.INSTANCE.createFromPokemon(
                player,
                Component.translatable("gui.fusionmon.select_second"),
                partyOf(player),
                pokemon -> !pokemon.getUuid().equals(firstId) && !FusionData.isFusion(pokemon),
                cancelledBy -> Unit.INSTANCE,
                second -> {
                    openConfirmation(player, firstId, second.getUuid());
                    return Unit.INSTANCE;
                }
        );
    }

    private static void openConfirmation(ServerPlayer player, UUID firstId, UUID secondId) {
        Pokemon first = findValid(player, firstId);
        Pokemon second = findValid(player, secondId);
        if (first == null || second == null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.selection_changed"));
            return;
        }

        PENDING_FUSIONS.put(player.getUUID(), new PendingFusion(firstId, secondId));
        ServerPlayNetworking.send(player, new OpenFusionScreenPayload(
                first.getDisplayName(false), second.getDisplayName(false),
                preview(first, second), preview(second, first),
                Component.translatable(first.getNature().getDisplayName()),
                Component.translatable(second.getNature().getDisplayName()),
                natureEffect(first.getNature()),
                natureEffect(second.getNature()),
                Component.translatable(first.getAbility().getDisplayName()),
                Component.translatable(second.getAbility().getDisplayName()),
                Component.translatable(first.getAbility().getDescription()),
                Component.translatable(second.getAbility().getDescription())));
    }

    /** "+Ataque  −At. Esp." o "Neutra" si la naturaleza no cambia ningún stat. */
    private static Component natureEffect(Nature nature) {
        Stat increased = nature.getIncreasedStat();
        Stat decreased = nature.getDecreasedStat();
        if (increased == null || decreased == null) {
            return Component.translatable("gui.fusionmon.nature.neutral");
        }
        return Component.translatable("gui.fusionmon.nature.effect", increased.getDisplayName(), decreased.getDisplayName());
    }

    public static void handleChoice(ServerPlayer player, FusionChoicePayload choice) {
        PendingFusion pending = PENDING_FUSIONS.remove(player.getUUID());
        // Sin selección pendiente (respuesta duplicada o de un cliente trucado): se ignora
        if (pending == null || !choice.accepted()) {
            return;
        }

        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.in_battle"));
            return;
        }

        // Mientras la pantalla estaba abierta el equipo podría haber cambiado: se vuelve a comprobar
        Pokemon first = findValid(player, pending.firstId());
        Pokemon second = findValid(player, pending.secondId());
        if (first == null || second == null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.selection_changed"));
            return;
        }

        // El cristal se gasta al confirmar, no al abrir el selector (se puede cancelar)
        if (!FusionCrystalItem.consumeOne(player)) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.no_crystal"));
            return;
        }

        Pokemon head = choice.swapped() ? second : first;
        Pokemon body = choice.swapped() ? first : second;
        Pokemon natureSource = choice.natureFromB() ? second : first;
        Pokemon abilitySource = choice.abilityFromB() ? second : first;

        // Los nombres se leen antes de fusionar: después el cuerpo ya no está en el equipo
        Component headName = head.getDisplayName(false);
        Component bodyName = body.getDisplayName(false);
        FusionService.fuse(player, head, body, natureSource == body, abilitySource == body);
        player.sendSystemMessage(Component.translatable("message.fusionmon.fused", headName, bodyName));
    }

    private static void openUnfuseConfirmation(ServerPlayer player, UUID fusedId) {
        Pokemon fused = findFusion(player, fusedId);
        if (fused == null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.selection_changed"));
            return;
        }

        Pokemon head = FusionData.readHead(fused, player.registryAccess());
        Pokemon body = FusionData.readBody(fused, player.registryAccess());
        PENDING_UNFUSES.put(player.getUUID(), fusedId);
        ServerPlayNetworking.send(player, new OpenUnfuseScreenPayload(
                fused.getDisplayName(false),
                head.getDisplayName(false), head.getLevel(),
                body.getDisplayName(false), body.getLevel(),
                FusionData.experienceGained(fused)));
    }

    public static void handleUnfuseChoice(ServerPlayer player, UnfuseChoicePayload choice) {
        UUID fusedId = PENDING_UNFUSES.remove(player.getUUID());
        if (fusedId == null || !choice.accepted()) {
            return;
        }

        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.in_battle"));
            return;
        }

        Pokemon fused = findFusion(player, fusedId);
        if (fused == null) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.selection_changed"));
            return;
        }

        if (!FusionCrystalItem.consumeOne(player)) {
            player.sendSystemMessage(Component.translatable("message.fusionmon.no_crystal"));
            return;
        }

        Component fusedName = fused.getDisplayName(false);
        FusionService.unfuse(player, fused);
        player.sendSystemMessage(Component.translatable("message.fusionmon.unfused", fusedName));
    }

    public static void forget(ServerPlayer player) {
        PENDING_FUSIONS.remove(player.getUUID());
        PENDING_UNFUSES.remove(player.getUUID());
    }

    private static FusionPreview preview(Pokemon head, Pokemon body) {
        FormData headForm = head.getForm();
        FormData bodyForm = body.getForm();

        List<Component> types = new ArrayList<>();
        for (ElementalType type : FusionCalculator.types(headForm, bodyForm)) {
            types.add(type.getDisplayName());
        }

        List<Integer> baseStats = new ArrayList<>();
        for (Stat stat : FusionService.PERMANENT_STATS) {
            baseStats.add(FusionCalculator.baseStat(headForm, bodyForm, stat));
        }

        // Para el visor 3D: la cabeza con los aspects que tendrá como fusión (sin objeto: al fusionar vuelve al jugador)
        Set<String> aspects = new HashSet<>(head.getAspects());
        aspects.addAll(FusionAspects.fusionAspects(body.getSpecies(), body.getAspects()));
        RenderablePokemon model = new RenderablePokemon(head.getSpecies(), aspects, ItemStack.EMPTY);

        return new FusionPreview(
                Component.literal(FusionCalculator.name(head.getSpecies().getName(), body.getSpecies().getName())),
                types,
                (head.getLevel() + body.getLevel()) / 2,
                baseStats,
                model);
    }

    /** El Pokémon con ese UUID si sigue en el equipo y no es una fusión; si no, null. */
    private static Pokemon findValid(ServerPlayer player, UUID id) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party.get(id);
        return pokemon != null && !FusionData.isFusion(pokemon) ? pokemon : null;
    }

    /** La fusión con ese UUID si sigue en el equipo; si no, null. */
    private static Pokemon findFusion(ServerPlayer player, UUID id) {
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(id);
        return pokemon != null && FusionData.isFusion(pokemon) ? pokemon : null;
    }

    private static List<Pokemon> partyOf(ServerPlayer player) {
        List<Pokemon> result = new ArrayList<>();
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            result.add(pokemon);
        }
        return result;
    }
}
