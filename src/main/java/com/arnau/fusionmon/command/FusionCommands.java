package com.arnau.fusionmon.command;

import com.arnau.fusionmon.fusion.FusionData;
import com.arnau.fusionmon.fusion.FusionService;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Comandos de prueba (requieren trucos activados):
 * /fusionmon info <hueco>    muestra si el Pokémon del hueco 1-6 es una fusión, qué guarda dentro y sus aspects
 * /fusionmon unfuse <hueco>  desfusiona el Pokémon de ese hueco
 * /fusionmon spawn <cabeza> <cuerpo> [n]  hace aparecer n fusiones salvajes alrededor ("random" = especie al azar en
 *                            cada una; cuerpo "none" = Pokémon normales, para comparar). Para medir rendimiento con
 *                            muchas fusiones a la vista, y para grabar contenido
 */
public final class FusionCommands {

    private static final String SLOT = "slot";
    private static final String HEAD = "head";
    private static final String BODY = "body";
    private static final String COUNT = "count";
    private static final String RANDOM = "random";
    private static final String NONE = "none";
    private static final int SPAWN_LEVEL = 30;
    private static final int MAX_SPAWN = 64;

    private FusionCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("fusionmon")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info")
                                .then(Commands.argument(SLOT, IntegerArgumentType.integer(1, 6))
                                        .executes(FusionCommands::info)))
                        .then(Commands.literal("unfuse")
                                .then(Commands.argument(SLOT, IntegerArgumentType.integer(1, 6))
                                        .executes(FusionCommands::unfuse)))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument(HEAD, StringArgumentType.word())
                                        .suggests((context, builder) -> suggestSpecies(builder, false))
                                        .then(Commands.argument(BODY, StringArgumentType.word())
                                                .suggests((context, builder) -> suggestSpecies(builder, true))
                                                .executes(context -> spawn(context, 1))
                                                .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, MAX_SPAWN))
                                                        .executes(context -> spawn(context,
                                                                IntegerArgumentType.getInteger(context, COUNT)))))))));
    }

    private static CompletableFuture<Suggestions> suggestSpecies(SuggestionsBuilder builder, boolean body) {
        List<String> names = new ArrayList<>();
        names.add(RANDOM);
        if (body) {
            names.add(NONE);
        }
        for (Species species : PokemonSpecies.getImplemented()) {
            names.add(species.getResourceIdentifier().getPath());
        }
        return SharedSuggestionProvider.suggest(names, builder);
    }

    /** Fusiones salvajes en corro alrededor del jugador (cada vez más lejos si son muchas). */
    private static int spawn(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String headName = StringArgumentType.getString(context, HEAD);
        String bodyName = StringArgumentType.getString(context, BODY);
        // Se comprueban antes de crear nada: con un nombre mal escrito no aparece ninguno
        if (species(headName) == null || !NONE.equals(bodyName) && species(bodyName) == null) {
            context.getSource().sendFailure(Component.translatable("command.fusionmon.unknown_species",
                    species(headName) == null ? headName : bodyName));
            return 0;
        }

        for (int i = 0; i < count; i++) {
            Pokemon head = species(headName).create(SPAWN_LEVEL);
            if (!NONE.equals(bodyName)) {
                FusionService.makeFusion(level.registryAccess(), head, species(bodyName).create(SPAWN_LEVEL),
                        false, false);
            }
            PokemonEntity entity = new PokemonEntity(level, head, CobblemonEntities.POKEMON);
            double angle = 2 * Math.PI * i / Math.min(count, 12);
            double radius = 3 + 2.5 * (i / 12);
            entity.moveTo(player.getX() + Math.cos(angle) * radius, player.getY(),
                    player.getZ() + Math.sin(angle) * radius, (float) Math.toDegrees(angle) + 90, 0);
            level.addFreshEntity(entity);
        }
        context.getSource().sendSuccess(() -> Component.translatable("command.fusionmon.spawned", count), false);
        return count;
    }

    /** La especie por su nombre ("charizard"), una al azar con "random", o null si no existe. */
    private static Species species(String name) {
        if (RANDOM.equals(name)) {
            List<Species> implemented = PokemonSpecies.getImplemented();
            return implemented.get(ThreadLocalRandom.current().nextInt(implemented.size()));
        }
        return PokemonSpecies.getByName(name.toLowerCase(Locale.ROOT));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Pokemon pokemon = pokemonInSlot(context, player);
        if (pokemon == null) {
            return 0;
        }

        if (FusionData.isFusion(pokemon)) {
            String missing = FusionData.missingSpecies(pokemon);
            if (missing != null) {
                context.getSource().sendFailure(Component.translatable("message.fusionmon.missing_species", missing));
            } else {
                RegistryAccess registryAccess = player.registryAccess();
                Pokemon head = FusionData.readHead(pokemon, registryAccess);
                Pokemon body = FusionData.readBody(pokemon, registryAccess);
                context.getSource().sendSuccess(() -> Component.translatable("command.fusionmon.info",
                        pokemon.getDisplayName(false),
                        head.getDisplayName(false), head.getLevel(),
                        body.getDisplayName(false), body.getLevel()), false);
            }
        } else {
            context.getSource().sendSuccess(() -> Component.translatable("command.fusionmon.not_fusion",
                    pokemon.getDisplayName(false)), false);
        }

        // Los aspects son lo que usa el cliente para elegir modelo y textura (ver FusionAspects).
        // También en los que no son fusión, para comprobar que al separar no se quedan los de Fusionmon.
        String aspects = String.join(", ", new TreeSet<>(pokemon.getAspects()));
        context.getSource().sendSuccess(() -> Component.translatable("command.fusionmon.aspects", aspects), false);
        return 1;
    }

    private static int unfuse(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Pokemon pokemon = pokemonInSlot(context, player);
        if (pokemon == null) {
            return 0;
        }

        if (!FusionData.isFusion(pokemon)) {
            context.getSource().sendFailure(Component.translatable("command.fusionmon.not_fusion",
                    pokemon.getDisplayName(false)));
            return 0;
        }

        FusionService.Parts parts = FusionService.prepareUnfuse(player, pokemon);
        if (parts == null) {
            return 0;
        }
        FusionService.unfuse(player, pokemon, parts);
        context.getSource().sendSuccess(() -> Component.translatable("command.fusionmon.unfused"), false);
        return 1;
    }

    private static Pokemon pokemonInSlot(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        int slot = IntegerArgumentType.getInteger(context, SLOT);
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(slot - 1);
        if (pokemon == null) {
            context.getSource().sendFailure(Component.translatable("command.fusionmon.empty_slot", slot));
        }
        return pokemon;
    }
}
