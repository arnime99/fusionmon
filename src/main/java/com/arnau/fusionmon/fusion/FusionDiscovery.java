package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.network.OpenFusionDexPayload;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
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
    /** Si ya se han apuntado las fusiones que el jugador tenía de antes del registro (ver registerExisting). */
    private static final AttachmentType<Boolean> EXISTING_REGISTERED = AttachmentRegistry.create(
            Fusionmon.id("discovered_existing"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

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
        return key(head.getForm(), body.getForm());
    }

    public static String key(FormData head, FormData body) {
        return part(head) + ">" + part(body);
    }

    private static String part(FormData form) {
        return form.getSpecies().getResourceIdentifier() + "/" + form.getName().toLowerCase(Locale.ROOT);
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

    /**
     * Una vez por jugador: las fusiones que ya tiene en el equipo o el PC cuentan como descubiertas (se crearon antes
     * de que existiera el registro y, si no, no saldrían en su FusionDex).
     */
    public static void registerExisting(ServerPlayer player) {
        if (player.getAttachedOrCreate(EXISTING_REGISTERED)) {
            return;
        }
        player.setAttached(EXISTING_REGISTERED, true);
        try {
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                registerFusion(player, pokemon);
            }
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
                registerFusion(player, pokemon);
            }
        } catch (RuntimeException e) {
            // No es grave: solo faltarían en su FusionDex. Que nunca impida entrar al mundo
            Fusionmon.LOGGER.warn("No se han podido apuntar las fusiones de {}", player.getName().getString(), e);
        }
    }

    private static void registerFusion(ServerPlayer player, Pokemon pokemon) {
        FormData head = FusionData.headForm(pokemon);
        FormData body = FusionData.bodyForm(pokemon);
        if (head != null && body != null) {
            register(player, key(head, body));
        }
    }

    /** Abre el FusionDex del jugador: sus fusiones descubiertas, de la más reciente a la más antigua. */
    public static void open(ServerPlayer player) {
        List<String> discovered = new ArrayList<>(player.getAttachedOrCreate(DISCOVERED));
        Collections.reverse(discovered);
        ServerPlayNetworking.send(player, new OpenFusionDexPayload(discovered));
    }
}
