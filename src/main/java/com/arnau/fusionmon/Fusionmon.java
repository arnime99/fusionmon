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
import com.cobblemon.mod.common.api.pokemon.stats.StatProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Fusionmon implements ModInitializer {
	public static final String MOD_ID = "fusionmon";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Opciones del servidor (config/fusionmon.json): antes de nada que calcule stats
		FusionConfig.load();
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

		ServerLifecycleEvents.SERVER_STARTING.register(server -> checkStatProvider());
		// Enganches opcionales que no se han podido aplicar (ver MixinHealth): al log
		ServerLifecycleEvents.SERVER_STARTED.register(server -> MixinHealth.check());

		LOGGER.info("Hello Fabric world!");
	}

	/**
	 * Otro mod puede cambiar la calculadora de stats de Cobblemon después de nosotros (un mod de stats, de dificultad...):
	 * la nuestra desaparecería y las fusiones pelearían con los stats de su cabeza, sin que nadie lo notase. Antes de
	 * arrancar el servidor se comprueba; si no es la nuestra, se envuelve la suya (las fusiones con nuestros stats, el
	 * resto con los del otro mod). Si el otro mod envolvió la nuestra, queda envuelta dos veces: no pasa nada.
	 */
	private static void checkStatProvider() {
		StatProvider current = Cobblemon.INSTANCE.getStatProvider();
		if (!(current instanceof FusionStatProvider)) {
			LOGGER.info("Fusionmon: otro mod ha cambiado la calculadora de stats de Cobblemon ({}); se envuelve para"
					+ " que las fusiones sigan con sus stats", current.getClass().getName());
			Cobblemon.INSTANCE.setStatProvider(new FusionStatProvider(current));
		}
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/**
	 * Herramientas para grabar (/fusionmon spawn y forget en el servidor, para ops; /fusionviewer con renders en el
	 * cliente): en desarrollo (runClient/runServer), o con el jar publicado si "creatorTools": true en
	 * config/fusionmon.json (la forma documentada, para creadores de contenido y servidores; 0.1.3-beta). Siguen valiendo
	 * las de antes: el archivo config/fusionmon-devtools o el argumento de Java -Dfusionmon.devtools=true (CurseForge
	 * borra los argumentos al lanzar, por eso el archivo). Ningún jugador lo tiene sin ponerlo a mano: la gracia del mod
	 * es descubrir las fusiones jugando.
	 */
	public static boolean recordingTools() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isDevelopmentEnvironment()
				|| FusionConfig.creatorTools()
				|| Boolean.getBoolean("fusionmon.devtools")
				|| Files.exists(loader.getConfigDir().resolve("fusionmon-devtools"));
	}
}
