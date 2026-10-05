package com.arnau.fusionmon;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.arnau.fusionmon.command.FusionCommands;
import com.arnau.fusionmon.fusion.FusionLevelUp;
import com.arnau.fusionmon.fusion.FusionStatProvider;
import com.arnau.fusionmon.item.ModItems;
import com.arnau.fusionmon.network.ModNetworking;
import com.cobblemon.mod.common.Cobblemon;

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

		LOGGER.info("Hello Fabric world!");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
