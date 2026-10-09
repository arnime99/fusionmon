package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registro de fusiones descubiertas de cada jugador: las parejas cabeza + cuerpo que ha creado él (al fusionar o
 * invertir). Una pareja sin descubrir sale en la pantalla de fusionar como silueta, sin nombre, tipos ni stats: se ve
 * por primera vez en la animación (decisión del usuario: descubrir las fusiones jugando).
 *
 * Se guarda pegado al jugador con los "data attachments" de Fabric API: se guarda con su partida (en el servidor) y
 * sobrevive a morir (copyOnDeath). Es una lista de claves "cabeza>cuerpo" (el orden cuenta: cada orden se ve distinto);
 * aunque alguien descubra miles, son pocos KB.
 */
public final class FusionDiscovery {

    private static final AttachmentType<List<String>> DISCOVERED = AttachmentRegistry.create(
            Fusionmon.id("discovered_fusions"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath().initializer(List::of));

    private FusionDiscovery() {
    }

    /** Fuerza a cargar la clase al iniciar el mod: el tipo de dato tiene que estar registrado antes de cargar un mundo. */
    public static void initialize() {
    }

    /**
     * Clave de una pareja: especie y forma de cada parte ("cobblemon:charizard/normal>cobblemon:pikachu/normal"). Con
     * la forma porque una forma regional se ve distinta (Raichu de Alola).
     */
    public static String key(Pokemon head, Pokemon body) {
        return part(head) + ">" + part(body);
    }

    private static String part(Pokemon pokemon) {
        return pokemon.getSpecies().getResourceIdentifier() + "/" + pokemon.getForm().getName().toLowerCase(Locale.ROOT);
    }

    public static boolean isDiscovered(ServerPlayer player, String key) {
        return player.getAttachedOrCreate(DISCOVERED).contains(key);
    }

    /** Apunta una pareja como descubierta (si ya lo estaba, no hace nada). */
    public static void register(ServerPlayer player, String key) {
        List<String> discovered = player.getAttachedOrCreate(DISCOVERED);
        if (discovered.contains(key)) {
            return;
        }
        // Lista nueva en vez de cambiar la guardada: lo que devuelve el codec al cargar puede no admitir cambios
        List<String> updated = new ArrayList<>(discovered);
        updated.add(key);
        player.setAttached(DISCOVERED, List.copyOf(updated));
    }
}
