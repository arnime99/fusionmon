package com.arnau.fusionmon.network;

import com.arnau.fusionmon.FusionConfig;
import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.arnau.fusionmon.fusion.FusionMegas;
import com.arnau.fusionmon.fusion.FusionSelection;
import com.arnau.fusionmon.fusion.FusionWorldAnimation;
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
        PayloadTypeRegistry.playS2C().register(OpenFusionAlbumPayload.TYPE, OpenFusionAlbumPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionResultPayload.TYPE, FusionResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FusionRulesPayload.TYPE, FusionRulesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FusionChoicePayload.TYPE, FusionChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UnfuseChoicePayload.TYPE, UnfuseChoicePayload.CODEC);

        // Fabric ejecuta estos receptores en el hilo principal del servidor: se puede tocar el equipo directamente
        ServerPlayNetworking.registerGlobalReceiver(FusionChoicePayload.TYPE,
                (payload, context) -> FusionSelection.handleChoice(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(UnfuseChoicePayload.TYPE,
                (payload, context) -> FusionSelection.handleUnfuseChoice(context.player(), payload));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FusionSelection.forget(handler.getPlayer());
            // Una fusión animándose en el mundo se hace ya: el inyector está gastado
            FusionWorldAnimation.finishFor(handler.getPlayer());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Las fusiones creadas antes de que existiera el registro cuentan como descubiertas (una vez por jugador)
            FusionDiscovery.registerExisting(handler.getPlayer());
            try {
                // Pokémon que no volvían a la Poké Ball por una animación de Mega Showdown que se cortó
                FusionMegas.clearStuckAnimations(handler.getPlayer());
            } catch (RuntimeException e) {
                FusionSafety.failed("animaciones de Mega Showdown a medias", e);
            }
            // Las opciones del servidor que el cliente necesita (un cliente con una versión vieja no lo conoce)
            if (ServerPlayNetworking.canSend(handler.getPlayer(), FusionRulesPayload.TYPE)) {
                sender.sendPacket(new FusionRulesPayload(FusionConfig.configuredStatBonusPercent(),
                        FusionConfig.forceColorsMode()));
            }
        });
    }
}
