package com.arnau.fusionmon.client;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.client.screen.FusionConfirmScreen;
import com.arnau.fusionmon.client.screen.UnfuseConfirmScreen;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class FusionmonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// El servidor pide abrir una pantalla de confirmación (se ejecuta en el hilo del cliente)
		ClientPlayNetworking.registerGlobalReceiver(OpenFusionScreenPayload.TYPE,
				(payload, context) -> context.client().setScreen(new FusionConfirmScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(OpenUnfuseScreenPayload.TYPE,
				(payload, context) -> context.client().setScreen(new UnfuseConfirmScreen(payload)));

		// Las texturas de fusión se generan a partir de las de Cobblemon: si se recargan los recursos
		// (F3+T, otro resource pack), hay que volver a generarlas
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public ResourceLocation getFabricId() {
						return Fusionmon.id("fusion_textures");
					}

					@Override
					public void onResourceManagerReload(ResourceManager resourceManager) {
						FusionTextures.clear();
					}
				});
	}
}
