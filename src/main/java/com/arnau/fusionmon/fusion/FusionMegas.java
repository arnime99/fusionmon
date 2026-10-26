package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

/**
 * Megaevoluciones de Mega Showdown (mod opcional) en las fusiones. Sin compilar contra sus clases: su marca en
 * persistentData, su etiqueta de objetos y, para la mega del cuerpo, unos pocos métodos suyos buscados al vuelo
 * (MegaShowdown, abajo). Sin Mega Showdown nada de esto hace nada.
 *
 * La mega que se hace fuera de combate es solo de aspecto (Mega Showdown la deshace al empezar cada combate), pero
 * cambia la forma del Pokémon. Si se fusionaba así, la fusión guardaba la forma mega (tipos, stats, Fusion Album) y
 * Mega Showdown la deshacía un momento después al quitarle la piedra: la fusión quedaba a medias. Por eso las partes
 * se fusionan siempre en su forma normal (las copias guardadas, el Fusion Album), la fusión se queda con la
 * megapiedra y, si alguna parte estaba en mega, vuelve a megaevolucionar en silencio al acabar (megaEvolveAgain). La
 * parte que cambia es siempre la de la piedra que lleva la fusión, sea cabeza o cuerpo (partFor).
 *
 * Mega del cuerpo: Mega Showdown solo mira la especie visible (la cabeza), así que una piedra del cuerpo no hacía
 * nada. MegaShowdownMegaGimmickMixin le dice que sí puede (bodyFits) y, al megaevolucionar, en vez de cambiar la
 * cabeza cambiamos la forma del cuerpo (megaEvolveBody), guardada aparte en FusionData (bodyMega). Si la piedra
 * sirve para las dos partes, gana la cabeza, como siempre.
 */
public final class FusionMegas {

    /** Marca que pone Mega Showdown en persistentData mientras dura una mega de fuera de combate. */
    private static final String IS_MEGA = "is_mega";
    /** La característica (species feature) con la que Mega Showdown cambia a la forma mega; "none" la deshace. */
    private static final String MEGA_FEATURE = "mega_evolution";
    private static final TagKey<Item> MEGA_STONES =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mega_showdown", "mega_stone"));

    private FusionMegas() {
    }

    /**
     * Si el Pokémon está megaevolucionado fuera de combate, lo devuelve a su forma normal en el acto (lo mismo que hace
     * Mega Showdown al deshacerla, sin su animación, que la retrasa). True si estaba en mega.
     */
    public static boolean revert(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (data == null || !data.getBoolean(IS_MEGA)) {
            return false;
        }
        // La marca primero: si no, al quitarle la piedra Mega Showdown intentaría deshacerla otra vez
        data.remove(IS_MEGA);
        // Si la mega era del cuerpo, la cabeza no ha cambiado: basta con quitar la capa
        if (!FusionData.clearBodyMega(pokemon)) {
            new StringSpeciesFeature(MEGA_FEATURE, "none").apply(pokemon);
        }
        // Mega Showdown no deja intercambiar un Pokémon en mega
        pokemon.setTradeable(true);
        return true;
    }

    /** Le quita la megapiedra si lleva una (y la devuelve); si lleva otra cosa o nada, no toca nada y devuelve vacío. */
    public static ItemStack takeStone(Pokemon pokemon) {
        return pokemon.heldItem().is(MEGA_STONES) ? pokemon.removeHeldItem() : ItemStack.EMPTY;
    }

    /**
     * Para canMega de Mega Showdown, cuando la piedra no sirve para la cabeza: si sirve para el cuerpo de una fusión.
     *
     * @param pokemons las especies de la piedra (nombres, "Charizard"), tal como las compara Mega Showdown
     */
    public static boolean bodyFits(Pokemon pokemon, List<?> pokemons) {
        if (!FusionData.isFusion(pokemon) || FusionData.hasBodyMega(pokemon)) {
            return false;
        }
        FormData body = FusionData.bodyForm(pokemon);
        return body != null && pokemons.contains(body.getSpecies().getName());
    }

    /**
     * En lugar de MegaGimmick.megaEvolve (la rueda de Mega Showdown): si la piedra de la fusión es del cuerpo (y no de
     * la cabeza), megaevoluciona el cuerpo y hace lo demás que haría Mega Showdown (marca, no intercambiable,
     * animación). True si lo ha hecho; false para dejar que Mega Showdown siga como siempre.
     */
    public static boolean megaEvolveBody(Pokemon pokemon) {
        ServerPlayer owner = pokemon.getOwnerPlayer();
        if (owner == null || !FusionData.isFusion(pokemon)) {
            return false;
        }
        MegaShowdown.Stone stone = MegaShowdown.stone(pokemon.heldItem());
        if (stone == null || partFor(pokemon, stone) != FusionPart.BODY || !megaBody(owner, pokemon, stone)) {
            return false;
        }
        markMega(pokemon);
        MegaShowdown.playMegaEffect(pokemon);
        // persistentData solo llega al cliente con el Pokémon entero (el resumen calcula los stats con ella)
        FusionService.resendToClient(owner, pokemon);
        return true;
    }

    /**
     * Después de fusionar o invertir partes que estaban en mega: la fusión vuelve a megaevolucionar con la piedra que
     * lleva, en silencio (sin la animación de Mega Showdown: ya se ha visto la de fusionar). La parte que cambia es la
     * de la piedra (partFor). Hay que llamarlo ANTES de mandar la fusión al cliente con el Pokémon entero: así llega
     * ya en mega, y al invertir el cliente nunca recibe un cambio de forma de un Pokémon que aún no tiene (eso lo
     * desconectaba: FormUpdatePacket busca el Pokémon al leerlo). Si no lleva megapiedra o falla, se queda sin mega:
     * nunca impide la fusión.
     */
    public static void megaEvolveAgain(ServerPlayer player, Pokemon fusion) {
        if (!fusion.heldItem().is(MEGA_STONES)) {
            return;
        }
        try {
            MegaShowdown.Stone stone = MegaShowdown.stone(fusion.heldItem());
            FusionPart part = stone == null ? null : partFor(fusion, stone);
            boolean done = part == FusionPart.HEAD
                    ? applyStone(fusion, stone.applyAspects())
                    : part == FusionPart.BODY && megaBody(player, fusion, stone);
            if (done) {
                markMega(fusion);
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: la fusión no ha podido volver a megaevolucionar", e);
        }
    }

    /**
     * Para la vista previa de invertir: las partes leídas de la fusión son copias en forma normal; si la fusión está en
     * mega, se le pone a la copia de la parte que lo está, como quedará la fusión nueva (megaEvolveAgain). Las copias
     * son solo para la pantalla: al aceptar se vuelven a leer.
     */
    public static void previewMega(Pokemon fused, FusionService.Parts parts) {
        CompoundTag data = fused.getPersistentData();
        if (data == null || !data.getBoolean(IS_MEGA)) {
            return;
        }
        try {
            MegaShowdown.Stone stone = MegaShowdown.stone(fused.heldItem());
            Pokemon part = FusionData.hasBodyMega(fused) ? parts.body() : parts.head();
            if (stone != null && applyStone(part, stone.applyAspects())) {
                markMega(part);
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido enseñar la mega en la vista previa de invertir", e);
        }
    }

    /** A qué parte de la fusión le sirve la piedra: la cabeza si le vale (como siempre), si no el cuerpo; o null. */
    private static FusionPart partFor(Pokemon fusion, MegaShowdown.Stone stone) {
        if (stone.pokemons().contains(fusion.getSpecies().getName())) {
            return FusionPart.HEAD;
        }
        FormData body = FusionData.bodyForm(fusion);
        if (body != null && !FusionData.hasBodyMega(fusion) && stone.pokemons().contains(body.getSpecies().getName())) {
            return FusionPart.BODY;
        }
        return null;
    }

    /**
     * Megaevoluciona el cuerpo: una copia suya, fuera de todo almacén (no manda nada al cliente), con las
     * características de la piedra, guardada como capa encima del cuerpo (FusionData.setBodyMega).
     */
    private static boolean megaBody(ServerPlayer owner, Pokemon fusion, MegaShowdown.Stone stone) {
        Pokemon megaBody = FusionData.readBody(fusion, owner.registryAccess());
        if (!applyStone(megaBody, stone.applyAspects())) {
            Fusionmon.LOGGER.warn("Fusionmon: la piedra {} no cambia la forma de {}; no se megaevoluciona el cuerpo",
                    fusion.heldItem(), megaBody.getSpecies().getName());
            return false;
        }
        FusionData.setBodyMega(fusion, megaBody);
        return true;
    }

    /**
     * Pone las características de la piedra ("mega_evolution=mega_x"), como lo hace Mega Showdown. True si la forma ha
     * cambiado (si no, la piedra no era de esta especie o la forma mega no existe).
     */
    private static boolean applyStone(Pokemon pokemon, List<String> aspects) {
        FormData before = pokemon.getForm();
        for (String aspect : aspects) {
            int equals = aspect.indexOf('=');
            if (equals > 0) {
                new StringSpeciesFeature(aspect.substring(0, equals), aspect.substring(equals + 1)).apply(pokemon);
            }
        }
        return pokemon.getForm() != before;
    }

    /** Lo que hace Mega Showdown al acabar una mega: su marca y que no se pueda intercambiar. */
    private static void markMega(Pokemon pokemon) {
        pokemon.getPersistentData().putBoolean(IS_MEGA, true);
        pokemon.setTradeable(false);
    }

    /**
     * La forma normal de un Pokémon que puede estar en mega (para la clave del Fusion Album en la vista previa: al
     * fusionar se apunta con la forma normal). Las megas salen de la forma estándar de la especie.
     */
    public static FormData normalForm(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        return data != null && data.getBoolean(IS_MEGA) ? pokemon.getSpecies().getStandardForm() : pokemon.getForm();
    }

    /** Al principio de MegaGimmick.unmegaEvolve: si la mega era del cuerpo, se quita (Mega Showdown hace el resto). */
    public static void revertBody(Pokemon pokemon) {
        if (FusionData.clearBodyMega(pokemon)) {
            ServerPlayer owner = pokemon.getOwnerPlayer();
            if (owner != null) {
                FusionService.resendToClient(owner, pokemon);
            }
        }
    }

    /**
     * Los métodos de Mega Showdown que hacen falta, buscados al usarlos por primera vez (como CobblemonCompat con
     * Cobblemon 1.7.3): así Fusionmon no depende de Mega Showdown para compilar ni para arrancar. Si una versión suya
     * los cambia, falla aquí, quien llama lo avisa en el log (FusionSafety) y el cuerpo simplemente no megaevoluciona.
     */
    private static final class MegaShowdown {

        private static final String PACKAGE = "com.github.yajatkaul.mega_showdown.";

        record Stone(List<?> pokemons, List<String> applyAspects) {
        }

        private static final Class<?> MEGA_GIMMICK;
        private static final MethodHandle GET_COMPONENT;
        private static final MethodHandle POKEMONS;
        private static final MethodHandle ASPECT_CONDITIONS;
        private static final MethodHandle ASPECT_APPLY;
        private static final MethodHandle ASPECTS;
        private static final MethodHandle GET_EFFECT;
        private static final MethodHandle APPLY_EFFECTS;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                ClassLoader loader = FusionMegas.class.getClassLoader();
                MEGA_GIMMICK = Class.forName(PACKAGE + "gimmick.MegaGimmick", false, loader);
                Class<?> locator = Class.forName(PACKAGE + "utils.RegistryLocator", false, loader);
                Class<?> aspectConditions = Class.forName(PACKAGE + "api.codec.AspectConditions", false, loader);
                Class<?> conditions = Class.forName(PACKAGE + "api.codec.Conditions", false, loader);
                Class<?> effect = Class.forName(PACKAGE + "api.codec.Effect", false, loader);

                GET_COMPONENT = lookup.findStatic(locator, "getComponent",
                        MethodType.methodType(Object.class, Class.class, ItemStack.class));
                POKEMONS = lookup.findVirtual(MEGA_GIMMICK, "pokemons", MethodType.methodType(List.class));
                ASPECT_CONDITIONS = lookup.findVirtual(MEGA_GIMMICK, "aspect_conditions",
                        MethodType.methodType(aspectConditions));
                ASPECT_APPLY = lookup.findVirtual(aspectConditions, "aspectApply", MethodType.methodType(conditions));
                ASPECTS = lookup.findVirtual(conditions, "aspects", MethodType.methodType(List.class));
                GET_EFFECT = lookup.findStatic(effect, "getEffect", MethodType.methodType(effect, String.class));
                APPLY_EFFECTS = lookup.findVirtual(effect, "applyEffects", MethodType.methodType(void.class,
                        Pokemon.class, List.class, Optional.class, PokemonEntity.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Mega Showdown ha cambiado: no se encuentran sus métodos de mega", e);
            }
        }

        /** La mega de la piedra que lleva el Pokémon, o null si no es una megapiedra. */
        @SuppressWarnings("unchecked")
        static Stone stone(ItemStack held) {
            try {
                Object gimmick = GET_COMPONENT.invoke(MEGA_GIMMICK, held);
                if (gimmick == null) {
                    return null;
                }
                Object apply = ASPECT_APPLY.invoke(ASPECT_CONDITIONS.invoke(gimmick));
                return new Stone((List<?>) POKEMONS.invoke(gimmick), (List<String>) ASPECTS.invoke(apply));
            } catch (Throwable e) {
                throw new IllegalStateException("No se ha podido leer la megapiedra de Mega Showdown", e);
            }
        }

        /** La animación de megaevolucionar de Mega Showdown (destello, sonido), sin cambiarle nada al Pokémon. */
        static void playMegaEffect(Pokemon pokemon) {
            try {
                Object effect = GET_EFFECT.invoke("mega_showdown:mega_evolution");
                if (effect != null) {
                    APPLY_EFFECTS.invoke(effect, pokemon, List.of(), Optional.empty(), (PokemonEntity) null);
                }
            } catch (Throwable e) {
                // Solo es la animación: la mega ya está hecha
                Fusionmon.LOGGER.warn("Fusionmon: no se ha podido mostrar la animación de mega del cuerpo", e);
            }
        }
    }
}
