package com.arnau.fusionmon.client;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.client.screen.FusionConfirmScreen;
import com.arnau.fusionmon.client.screen.FusionDexScreen;
import com.arnau.fusionmon.client.screen.SpeciesInspectorScreen;
import com.arnau.fusionmon.client.screen.UnfuseConfirmScreen;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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

		// /fusionvisual colors|graft: elige cómo se ven las fusiones en este cliente (graft = prototipo cabeza
		// sobre cuerpo, ver FusionGraft); /fusionvisual tail|decor on|off: si se cambia también la cola / se pegan
		// los adornos de la especie de la cabeza.
		// Es un comando de cliente: no pasa por el servidor ni necesita trucos.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("fusionvisual")
						.then(ClientCommandManager.literal("colors").executes(context -> {
							FusionGraft.setEnabled(false);
							context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.colors"));
							return 1;
						}))
						.then(ClientCommandManager.literal("graft").executes(context -> {
							FusionGraft.setEnabled(true);
							context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.graft"));
							return 1;
						}))
						.then(ClientCommandManager.literal("tail")
								.then(ClientCommandManager.literal("on").executes(context -> {
									FusionGraft.setTails(true);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.tail.on"));
									return 1;
								}))
								.then(ClientCommandManager.literal("off").executes(context -> {
									FusionGraft.setTails(false);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.tail.off"));
									return 1;
								})))
						.then(ClientCommandManager.literal("decor")
								.then(ClientCommandManager.literal("on").executes(context -> {
									FusionGraft.setDecorations(true);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.decor.on"));
									return 1;
								}))
								.then(ClientCommandManager.literal("off").executes(context -> {
									FusionGraft.setDecorations(false);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.decor.off"));
									return 1;
								})))
						// /fusionvisual top on|off: cuerpos sin cabeza (Voltorb, Lunatone...) con los complementos de la cabeza o en
						// modo colores
						.then(ClientCommandManager.literal("top")
								.then(ClientCommandManager.literal("on").executes(context -> {
									FusionGraft.setTops(true);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.top.on"));
									return 1;
								}))
								.then(ClientCommandManager.literal("off").executes(context -> {
									FusionGraft.setTops(false);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.top.off"));
									return 1;
								})))
						// /fusionvisual align pivot|skull|base: cabeza pegada pivote con pivote, cráneo por cráneo o base con
						// base (ver FusionGraft.Align)
						.then(ClientCommandManager.literal("align")
								.then(ClientCommandManager.literal("pivot").executes(context -> {
									FusionGraft.setAlign(FusionGraft.Align.PIVOT);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.align.pivot"));
									return 1;
								}))
								.then(ClientCommandManager.literal("skull").executes(context -> {
									FusionGraft.setAlign(FusionGraft.Align.SKULL);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.align.skull"));
									return 1;
								}))
								.then(ClientCommandManager.literal("base").executes(context -> {
									FusionGraft.setAlign(FusionGraft.Align.BASE);
									context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.align.base"));
									return 1;
								})))));

		// /fusiondex: visor de fusiones (FusionDexScreen). La pantalla se abre en la siguiente vuelta del bucle del
		// juego: al terminar un comando, Minecraft cierra el chat, y cerraría también una pantalla abierta aquí mismo
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("fusiondex").executes(context -> {
					Minecraft client = context.getSource().getClient();
					client.tell(() -> client.setScreen(new FusionDexScreen()));
					return 1;
				})));
		// /fusioninspect: inspector de especies (SpeciesInspectorScreen), para revisar una a una lo que detecta el graft
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("fusioninspect").executes(context -> {
					Minecraft client = context.getSource().getClient();
					client.tell(() -> client.setScreen(new SpeciesInspectorScreen()));
					return 1;
				})));

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
