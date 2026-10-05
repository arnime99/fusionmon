package com.arnau.fusionmon.command;

import com.arnau.fusionmon.fusion.FusionData;
import com.arnau.fusionmon.fusion.FusionService;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.TreeSet;

/**
 * Comandos de prueba (requieren trucos activados):
 * /fusionmon info <hueco>    muestra si el Pokémon del hueco 1-6 es una fusión, qué guarda dentro y sus aspects
 * /fusionmon unfuse <hueco>  desfusiona el Pokémon de ese hueco
 */
public final class FusionCommands {

    private static final String SLOT = "slot";

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
                                        .executes(FusionCommands::unfuse)))));
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Pokemon pokemon = pokemonInSlot(context, player);
        if (pokemon == null) {
            return 0;
        }

        if (FusionData.isFusion(pokemon)) {
            RegistryAccess registryAccess = player.registryAccess();
            Pokemon head = FusionData.readHead(pokemon, registryAccess);
            Pokemon body = FusionData.readBody(pokemon, registryAccess);
            context.getSource().sendSuccess(() -> Component.translatable("command.fusionmon.info",
                    pokemon.getDisplayName(false),
                    head.getDisplayName(false), head.getLevel(),
                    body.getDisplayName(false), body.getLevel()), false);
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

        FusionService.unfuse(player, pokemon);
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
