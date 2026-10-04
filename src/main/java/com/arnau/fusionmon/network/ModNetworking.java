package com.arnau.fusionmon.network;

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
        PayloadTypeRegistry.playC2S().register(FusionChoicePayload.TYPE, FusionChoicePayload.CODEC);

        // Fabric ejecuta este receptor en el hilo principal del servidor: se puede tocar el equipo directamente
        ServerPlayNetworking.registerGlobalReceiver(FusionChoicePayload.TYPE,
                (payload, context) -> FusionSelection.handleChoice(context.player(), payload));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                FusionSelection.forget(handler.getPlayer()));
    }
}
