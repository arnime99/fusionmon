package com.arnau.fusionmon.client;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.client.model.FusionGraft;
import com.arnau.fusionmon.client.screen.DiscoveredDexScreen;
import com.arnau.fusionmon.client.screen.FusionConfirmScreen;
import com.arnau.fusionmon.client.screen.FusionDexScreen;
import com.arnau.fusionmon.client.screen.SpeciesInspectorScreen;
import com.arnau.fusionmon.client.screen.UnfuseConfirmScreen;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.arnau.fusionmon.network.OpenFusionDexPayload;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
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
		// El FusionDex del jugador (Shift + clic con el cristal, o /fusiondex)
		ClientPlayNetworking.registerGlobalReceiver(OpenFusionDexPayload.TYPE,
				(payload, context) -> context.client().setScreen(new DiscoveredDexScreen(payload.discovered())));

		// Herramientas para afinar los visuales (ajustes finos de /fusionvisual, /fusiondex, /fusioninspect): solo en
		// desarrollo (runClient). En el mod publicado no están: el visor enseñaría todas las fusiones sin jugar (la
		// gracia es capturar, probar y ver qué sale) y el inspector lee archivos que solo hay en el proyecto
		boolean dev = FabricLoader.getInstance().isDevelopmentEnvironment();

		// /fusionvisual colors|graft: elige cómo se ven las fusiones en este cliente (graft = cabeza sobre cuerpo, ver
		// FusionGraft; colors = la cabeza con los colores del cuerpo), por si alguna sale rara o va lenta.
		// Es un comando de cliente: no pasa por el servidor ni necesita trucos.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> visual = ClientCommandManager.literal("fusionvisual")
					.then(ClientCommandManager.literal("colors").executes(context -> {
						FusionGraft.setEnabled(false);
						context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.colors"));
						return 1;
					}))
					.then(ClientCommandManager.literal("graft").executes(context -> {
						FusionGraft.setEnabled(true);
						context.getSource().sendFeedback(Component.translatable("command.fusionmon.visual.graft"));
						return 1;
					}));
			if (dev) {
				// /fusionvisual tail|decor|top on|off, align pivot|skull|base: para comparar reglas del graft
				visual.then(ClientCommandManager.literal("tail")
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
								})));
			}
			dispatcher.register(visual);
		});

		if (dev) {
			// /fusionviewer: visor de TODAS las fusiones (FusionDexScreen), para probar visuales. No se llama /fusiondex:
			// ese es el del jugador, con solo sus fusiones descubiertas (y un comando de cliente taparía al del servidor).
			// La pantalla se abre en la siguiente vuelta del bucle del juego: al terminar un comando, Minecraft cierra el
			// chat, y cerraría también una pantalla abierta aquí mismo
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
					dispatcher.register(ClientCommandManager.literal("fusionviewer").executes(context -> {
						Minecraft client = context.getSource().getClient();
						client.tell(() -> client.setScreen(new FusionDexScreen()));
						return 1;
					})));
			// /fusioninspect: inspector de especies (SpeciesInspectorScreen), para revisar una a una lo que detecta el
			// graft
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
					dispatcher.register(ClientCommandManager.literal("fusioninspect").executes(context -> {
						Minecraft client = context.getSource().getClient();
						client.tell(() -> client.setScreen(new SpeciesInspectorScreen()));
						return 1;
					})));
		}

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
						// Con los modelos nuevos (otro resource pack...) una pareja que falló puede ir bien
						FusionGraft.clearFailures();
					}
				});
	}
}
