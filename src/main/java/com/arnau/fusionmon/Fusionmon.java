package com.arnau.fusionmon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.arnau.fusionmon.command.FusionCommands;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionDiscovery;
import com.arnau.fusionmon.fusion.FusionLevelUp;
import com.arnau.fusionmon.fusion.FusionStatProvider;
import com.arnau.fusionmon.fusion.FusionWorldAnimation;
import com.arnau.fusionmon.item.ModItems;
import com.arnau.fusionmon.network.ModNetworking;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;

public class Fusionmon implements ModInitializer {
	public static final String MOD_ID = "fusionmon";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		FusionCommands.register();
		ModNetworking.initialize();
		// Envuelve la calculadora de stats de Cobblemon para que las fusiones usen sus propios stats base
		Cobblemon.INSTANCE.setStatProvider(new FusionStatProvider(Cobblemon.INSTANCE.getStatProvider()));
		// Al subir de nivel, la fusión también aprende los movimientos del cuerpo
		FusionLevelUp.register();
		// Etiquetas (aspects) que dicen al cliente que un Pokémon es una fusión y quién es su cuerpo
		AspectProvider.Companion.register(new FusionAspects());
		// Registro de fusiones descubiertas de cada jugador (dato guardado con su partida)
		FusionDiscovery.initialize();
		// Fusión animada en el mundo cuando los dos Pokémon están fuera (avanza en cada tick del servidor)
		FusionWorldAnimation.initialize();

		LOGGER.info("Hello Fabric world!");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Comandos de grabación (/fusionmon spawn y forget): en desarrollo (runClient/runServer), o en una instancia normal
	 * con el jar publicado (para grabar vídeos con shaders...) si existe el archivo config/fusionmon-devtools o con el
	 * argumento de Java -Dfusionmon.devtools=true. El archivo es por si el lanzador no deja poner argumentos (CurseForge
	 * los borra al lanzar). Ningún jugador lo tiene sin ponerlo a mano: la gracia del mod es descubrir las fusiones
	 * jugando.
	 */
	public static boolean recordingTools() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isDevelopmentEnvironment()
				|| Boolean.getBoolean("fusionmon.devtools")
				|| Files.exists(loader.getConfigDir().resolve("fusionmon-devtools"));
	}
}
