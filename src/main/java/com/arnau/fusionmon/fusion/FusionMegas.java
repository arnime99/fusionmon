package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
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
 * nada. MegaShowdownMegaGimmickMixin le dice que sí puede (canMegaBody) y, al megaevolucionar, en vez de cambiar la
 * cabeza cambiamos la forma del cuerpo (megaEvolveBody), guardada aparte en FusionData (bodyMega). Qué parte cambia
 * lo decide plan: primero la piedra (la cabeza si le sirve, si no el cuerpo), luego Rayquaza con Ascenso Draco. Al
 * separar, la parte que estaba en mega sale con su piedra y en mega (keepMega).
 */
public final class FusionMegas {

    /** Marca que pone Mega Showdown en persistentData mientras dura una mega de fuera de combate. */
    private static final String IS_MEGA = "is_mega";
    /** La característica (species feature) con la que Mega Showdown cambia a la forma mega; "none" la deshace. */
    private static final String MEGA_FEATURE = "mega_evolution";
    private static final TagKey<Item> MEGA_STONES =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mega_showdown", "mega_stone"));
    /** Rayquaza megaevoluciona sin piedra si sabe Ascenso Draco, con esta característica (como en Mega Showdown). */
    private static final String RAYQUAZA = "Rayquaza";
    private static final String DRAGON_ASCENT = "dragonascent";
    private static final List<String> RAYQUAZA_MEGA = List.of("mega_evolution=mega");

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
     * Para canMega de Mega Showdown, cuando él ha dicho que no: si la fusión puede megaevolucionar por el cuerpo. Las
     * mismas condiciones que pide él (llevar el Mega Aro y no tener ya otra mega), con nuestra regla de partes (plan).
     * La cabeza la sigue decidiendo Mega Showdown.
     */
    public static boolean canMegaBody(Pokemon fusion) {
        if (!FusionData.isFusion(fusion)) {
            return false;
        }
        ServerPlayer owner = fusion.getOwnerPlayer();
        if (owner == null || !MegaShowdown.canUseMega(owner)) {
            return false;
        }
        MegaPlan plan = plan(fusion);
        return plan != null && plan.part() == FusionPart.BODY;
    }

    /**
     * En lugar de MegaGimmick.megaEvolve (la rueda de Mega Showdown): si la mega que toca es la del cuerpo (plan),
     * megaevoluciona el cuerpo y hace lo demás que haría Mega Showdown (marca, no intercambiable, animación). True si
     * lo ha hecho; false para dejar que Mega Showdown siga como siempre (la mega de la cabeza es suya).
     */
    public static boolean megaEvolveBody(Pokemon pokemon) {
        ServerPlayer owner = pokemon.getOwnerPlayer();
        if (owner == null || !FusionData.isFusion(pokemon)) {
            return false;
        }
        MegaPlan plan = plan(pokemon);
        if (plan == null || plan.part() != FusionPart.BODY || !megaBody(owner, pokemon, plan.aspects())) {
            return false;
        }
        markMega(pokemon);
        MegaShowdown.playMegaEffect(pokemon);
        // persistentData solo llega al cliente con el Pokémon entero (el resumen calcula los stats con ella)
        FusionService.resendToClient(owner, pokemon);
        Fusionmon.LOGGER.info("Fusionmon: el cuerpo de la fusión de {} ha megaevolucionado ({})",
                owner.getName().getString(), FusionData.bodyForm(pokemon).getName());
        return true;
    }

    /**
     * Después de fusionar o invertir partes que estaban en mega: la fusión vuelve a megaevolucionar, en silencio (sin la
     * animación de Mega Showdown: ya se ha visto la de fusionar), con la regla de siempre (plan). Hay que llamarlo ANTES
     * de mandar la fusión al cliente con el Pokémon entero: así llega ya en mega, y al invertir el cliente nunca recibe
     * un cambio de forma de un Pokémon que aún no tiene (eso lo desconectaba: FormUpdatePacket busca el Pokémon al
     * leerlo). Si no puede o falla, se queda sin mega: nunca impide la fusión.
     */
    public static void megaEvolveAgain(ServerPlayer player, Pokemon fusion) {
        try {
            MegaPlan plan = plan(fusion);
            boolean done = plan != null && (plan.part() == FusionPart.HEAD
                    ? applyStone(fusion, plan.aspects())
                    : megaBody(player, fusion, plan.aspects()));
            if (done) {
                markMega(fusion);
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: la fusión no ha podido volver a megaevolucionar", e);
        }
    }

    /**
     * Para las pantallas de separar e invertir: las partes leídas de la fusión son copias en forma normal; si la fusión
     * está en mega, se le pone a la copia de la parte que lo está, como quedará (al invertir, megaEvolveAgain; al
     * separar, keepMega). Las copias son solo para la pantalla: al aceptar se vuelven a leer.
     */
    public static void previewMega(Pokemon fused, FusionService.Parts parts) {
        if (!isMega(fused)) {
            return;
        }
        try {
            Pokemon part = FusionData.hasBodyMega(fused) ? parts.body() : parts.head();
            if (megaPart(part, fused.heldItem())) {
                markMega(part);
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: no se ha podido enseñar la mega en la pantalla", e);
        }
    }

    /**
     * Al separar una fusión en mega: la parte que lo estaba sale con su piedra y en mega (sin animación, antes de
     * ponerla en el equipo), como se veía en la pantalla. Si no puede, sale normal con la piedra.
     */
    public static void keepMega(Pokemon part, ItemStack stone) {
        try {
            if (!stone.isEmpty()) {
                part.swapHeldItem(stone, false, true);
            }
            if (megaPart(part, part.heldItem())) {
                markMega(part);
            }
        } catch (RuntimeException | LinkageError e) {
            Fusionmon.LOGGER.warn("Fusionmon: la parte separada no ha podido seguir en mega", e);
        }
    }

    public static boolean isMega(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        return data != null && data.getBoolean(IS_MEGA);
    }

    /** Qué parte de una fusión megaevoluciona y con qué características ("mega_evolution=mega_x"). */
    private record MegaPlan(FusionPart part, List<String> aspects) {
    }

    /**
     * La mega que puede hacer la fusión, por este orden (decisión del usuario): primero la piedra que lleva, para la
     * parte a la que le sirva (la cabeza si le vale, si no el cuerpo); si no, Rayquaza con Ascenso Draco, sea cabeza o
     * cuerpo (es lo que pide Mega Showdown: Rayquaza no usa piedra). Null si ninguna.
     */
    private static MegaPlan plan(Pokemon fusion) {
        FormData body = FusionData.bodyForm(fusion);
        String bodyName = body == null ? null : body.getSpecies().getName();
        MegaShowdown.Stone stone = MegaShowdown.stone(fusion.heldItem());
        if (stone != null) {
            if (stone.pokemons().contains(fusion.getSpecies().getName())) {
                return new MegaPlan(FusionPart.HEAD, stone.applyAspects());
            }
            if (bodyName != null && stone.pokemons().contains(bodyName)) {
                return new MegaPlan(FusionPart.BODY, stone.applyAspects());
            }
        }
        if (knows(fusion, DRAGON_ASCENT)) {
            if (RAYQUAZA.equals(fusion.getSpecies().getName())) {
                return new MegaPlan(FusionPart.HEAD, RAYQUAZA_MEGA);
            }
            if (RAYQUAZA.equals(bodyName)) {
                return new MegaPlan(FusionPart.BODY, RAYQUAZA_MEGA);
            }
        }
        return null;
    }

    /** Megaevoluciona un Pokémon que no es fusión (una parte): con su piedra si le sirve, o Rayquaza. */
    private static boolean megaPart(Pokemon part, ItemStack held) {
        MegaShowdown.Stone stone = MegaShowdown.stone(held);
        if (stone != null && stone.pokemons().contains(part.getSpecies().getName())) {
            return applyStone(part, stone.applyAspects());
        }
        return RAYQUAZA.equals(part.getSpecies().getName()) && applyStone(part, RAYQUAZA_MEGA);
    }

    private static boolean knows(Pokemon pokemon, String move) {
        for (Move known : pokemon.getMoveSet().getMoves()) {
            if (known.getName().equals(move)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Megaevoluciona el cuerpo: una copia suya, fuera de todo almacén (no manda nada al cliente), con esas
     * características, guardada como capa encima del cuerpo (FusionData.setBodyMega).
     */
    private static boolean megaBody(ServerPlayer owner, Pokemon fusion, List<String> aspects) {
        Pokemon megaBody = FusionData.readBody(fusion, owner.registryAccess());
        if (!applyStone(megaBody, aspects)) {
            Fusionmon.LOGGER.warn("Fusionmon: {} no cambia la forma de {}; no se megaevoluciona el cuerpo",
                    aspects, megaBody.getSpecies().getName());
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
        private static final MethodHandle HAS_MEGA;
        private static final MethodHandle HAS_GIMMICK;
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
                Class<?> turnCheck = Class.forName(PACKAGE + "gimmick.GimmickTurnCheck", false, loader);

                HAS_MEGA = lookup.findStatic(MEGA_GIMMICK, "hasMega",
                        MethodType.methodType(boolean.class, ServerPlayer.class));
                HAS_GIMMICK = lookup.findStatic(turnCheck, "hasGimmick",
                        MethodType.methodType(boolean.class, ShowdownMoveset.Gimmick.class, LivingEntity.class));
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

        /** Lo mismo que mira canMega antes de la piedra: que lleve el Mega Aro y que no tenga ya otra mega. */
        static boolean canUseMega(ServerPlayer player) {
            try {
                return (boolean) HAS_GIMMICK.invoke(ShowdownMoveset.Gimmick.MEGA_EVOLUTION, (LivingEntity) player)
                        && !(boolean) HAS_MEGA.invoke(player);
            } catch (Throwable e) {
                throw new IllegalStateException("No se ha podido comprobar el Mega Aro con Mega Showdown", e);
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
