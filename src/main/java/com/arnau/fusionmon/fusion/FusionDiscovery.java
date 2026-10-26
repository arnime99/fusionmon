package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.network.OpenFusionAlbumPayload;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.serialization.Codec;
import kotlin.Unit;
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
 * invertir) y las formas que han tenido después (una mega de la fusión cuenta como otra fusión: ver initialize). Una
 * pareja sin descubrir sale en la pantalla de fusionar como silueta, sin nombre, tipos ni stats: se ve
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
    /**
     * Todas las parejas cuentan como descubiertas (ninguna sale en silueta), sin apuntarlas en el Fusion Album: para
     * grabar o enseñar fusiones sin tener que crearlas antes (/fusionmon reveal, solo desarrollo). Se guarda con el
     * jugador hasta que lo quite (reveal off o forget).
     */
    private static final AttachmentType<Boolean> REVEAL_ALL = AttachmentRegistry.create(
            Fusionmon.id("reveal_all"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath().initializer(() -> false));

    private FusionDiscovery() {
    }

    /**
     * Al iniciar el mod: carga la clase (el tipo de dato tiene que estar registrado antes de cargar un mundo) y escucha
     * los cambios de aspects. Una fusión que cambia de forma (megaevoluciona, por la cabeza o por el cuerpo, o evoluciona
     * una de sus partes) es otra fusión: se apunta con las formas que tiene ahora (activeKey). Cobblemon avisa de
     * cualquier cambio de aspects, así que también sirve para las formas que vengan después (formas por objeto).
     */
    public static void initialize() {
        CobblemonEvents.POKEMON_ASPECTS_CHANGED.subscribe(Priority.NORMAL, event -> {
            try {
                Pokemon pokemon = event.getPokemon();
                ServerPlayer owner = FusionData.isFusion(pokemon) ? pokemon.getOwnerPlayer() : null;
                if (owner != null) {
                    registerActive(owner, pokemon);
                }
            } catch (RuntimeException e) {
                // Solo faltaría en su Fusion Album: que nunca impida cambiar de forma
                FusionSafety.failed("Fusion Album (cambio de forma)", e);
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * Clave de la fusión con las formas que tiene ahora: la de la cabeza es la del Pokémon visible (en mega, la mega) y
     * la del cuerpo, FusionData.bodyForm (con su mega si la tiene). Null si no es una fusión.
     */
    public static String activeKey(Pokemon fusion) {
        FormData body = FusionData.bodyForm(fusion);
        return body == null ? null : key(fusion.getForm(), body);
    }

    /** Apunta la fusión con sus formas de ahora (ver activeKey). */
    public static void registerActive(ServerPlayer player, Pokemon fusion) {
        String key = activeKey(fusion);
        if (key != null) {
            register(player, key);
        }
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
        return player.getAttachedOrCreate(REVEAL_ALL) || player.getAttachedOrCreate(DISCOVERED).contains(key);
    }

    /** Ver REVEAL_ALL. */
    public static void setRevealAll(ServerPlayer player, boolean reveal) {
        player.setAttached(REVEAL_ALL, reveal);
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
     * Olvida todas las fusiones descubiertas del jugador (vuelven a salir en silueta) y devuelve cuántas eran. Solo para
     * desarrollo: para volver a grabar la revelación de una pareja. No se vuelve a llamar a registerExisting, así que
     * las fusiones que ya tiene en el equipo o el PC tampoco cuentan hasta que las vuelva a crear.
     */
    public static int forgetAll(ServerPlayer player) {
        int count = player.getAttachedOrCreate(DISCOVERED).size();
        player.setAttached(DISCOVERED, List.of());
        // Si no, con reveal puesto seguirían sin salir en silueta, que es para lo que se usa forget
        setRevealAll(player, false);
        return count;
    }

    /**
     * Una vez por jugador: las fusiones que ya tiene en el equipo o el PC cuentan como descubiertas (se crearon antes
     * de que existiera el registro y, si no, no saldrían en su Fusion Album).
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
            // No es grave: solo faltarían en su Fusion Album. Que nunca impida entrar al mundo
            Fusionmon.LOGGER.warn("No se han podido apuntar las fusiones de {}", player.getName().getString(), e);
        }
    }

    private static void registerFusion(ServerPlayer player, Pokemon pokemon) {
        FormData head = FusionData.headForm(pokemon);
        FormData body = FusionData.bodyForm(pokemon);
        if (head != null && body != null) {
            register(player, key(head, body));
            // Y como está ahora, si no es igual (en mega)
            registerActive(player, pokemon);
        }
    }

    /** Abre el Fusion Album del jugador: sus fusiones descubiertas, de la más reciente a la más antigua. */
    public static void open(ServerPlayer player) {
        List<String> discovered = new ArrayList<>(player.getAttachedOrCreate(DISCOVERED));
        Collections.reverse(discovered);
        ServerPlayNetworking.send(player, new OpenFusionAlbumPayload(discovered));
    }
}
