package com.arnau.fusionmon.client;

import com.arnau.fusionmon.client.screen.FusionConfirmScreen;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FusionmonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// El servidor pide abrir la pantalla de confirmación (se ejecuta en el hilo del cliente)
		ClientPlayNetworking.registerGlobalReceiver(OpenFusionScreenPayload.TYPE,
				(payload, context) -> context.client().setScreen(new FusionConfirmScreen(payload)));
	}
}
