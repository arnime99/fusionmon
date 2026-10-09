package com.arnau.fusionmon.network;

import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.arnau.fusionmon.fusion.FusionSelection;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Registra los mensajes propios de Fusionmon (parte común; el receptor del cliente está en FusionmonClient). */
public final class ModNetworking {

    private ModNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(OpenFusionScreenPayload.TYPE, OpenFusionScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenUnfuseScreenPayload.TYPE, OpenUnfuseScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenFusionDexPayload.TYPE, OpenFusionDexPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionChoicePayload.TYPE, FusionChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UnfuseChoicePayload.TYPE, UnfuseChoicePayload.CODEC);

        // Fabric ejecuta estos receptores en el hilo principal del servidor: se puede tocar el equipo directamente
        ServerPlayNetworking.registerGlobalReceiver(FusionChoicePayload.TYPE,
                (payload, context) -> FusionSelection.handleChoice(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(UnfuseChoicePayload.TYPE,
                (payload, context) -> FusionSelection.handleUnfuseChoice(context.player(), payload));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                FusionSelection.forget(handler.getPlayer()));
        // Las fusiones creadas antes de que existiera el registro cuentan como descubiertas (una vez por jugador)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                FusionDiscovery.registerExisting(handler.getPlayer()));
    }
}
