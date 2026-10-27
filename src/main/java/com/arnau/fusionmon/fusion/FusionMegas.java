package com.arnau.fusionmon.fusion;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.scheduling.SchedulingFunctionsKt;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.util.DistributionUtilsKt;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    /** Rayquaza megaevoluciona sin piedra si sabe Ascenso Draco, con esta característica (como en Mega Showdown). */
    private static final String RAYQUAZA = "Rayquaza";
    private static final String DRAGON_ASCENT = "dragonascent";
    private static final List<String> RAYQUAZA_MEGA = List.of("mega_evolution=mega");
    /** Sin Mega Showdown no hay megas (y su clase MegaShowdown, abajo, no se puede cargar). */
    private static final boolean ENABLED = FabricLoader.getInstance().isModLoaded("mega_showdown");
    /**
     * Marca que pone Mega Showdown en persistentData mientras dura la animación de un cambio de forma (mega, objeto),
     * junto con sus datos para deshacerlo (REVERT_DATA). La quita la propia animación al acabar, que va programada en
     * la entidad. Mientras está puesta no deja volver a la Poké Ball ni deshacer la mega.
     */
    private static final String FORM_CHANGING = "form_changing";
    /** Marca nuestra: la mega del cuerpo se hizo en combate y se quita al acabarlo (revertBattleBody). */
    private static final String BATTLE_MEGA = "fusionmon_battle_mega";
    /** La habilidad de la fusión mientras lleva la de su mega en combate (setBattleAbility), para devolvérsela. */
    private static final String BATTLE_ABILITY = "fusionmon_battle_ability";
    private static final String ABILITY_NAME = "name";
    private static final String ABILITY_FORCED = "forced";
    private static final String ABILITY_PRIORITY = "priority";
    private static final String REVERT_DATA = "aspects";

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

    /**
     * Quita la marca de animación de Mega Showdown a medias (FORM_CHANGING) de los datos de un Pokémon que no se está
     * animando: una copia guardada de una parte, o un Pokémon sin entidad. Si se fusionaba durante la animación (dar o
     * quitar un objeto y fusionar unos segundos después), la copia se guardaba con la marca y nadie la quitaba nunca:
     * al separar, esa parte (y las fusiones que se hicieran con ella) no volvía a la Poké Ball y su mega no se podía
     * deshacer, así que el jugador no podía hacer ninguna otra. True si la había.
     */
    public static boolean clearFormChanging(CompoundTag persistentData) {
        if (persistentData == null || !persistentData.getBoolean(FORM_CHANGING)) {
            return false;
        }
        // Lo mismo que hace Mega Showdown al acabar la animación (resetFormChangingState)
        persistentData.remove(FORM_CHANGING);
        persistentData.remove(REVERT_DATA);
        return true;
    }

    /**
     * Al entrar el jugador: ningún Pokémon suyo tiene entidad todavía, así que una marca de animación es de una que se
     * cortó (la entidad desapareció antes de acabar). Arregla también las partidas que ya tienen alguno atascado.
     */
    public static void clearStuckAnimations(ServerPlayer player) {
        int cleared = 0;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            cleared += clearIfIdle(pokemon) ? 1 : 0;
        }
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
            cleared += clearIfIdle(pokemon) ? 1 : 0;
        }
        if (cleared > 0) {
            Fusionmon.LOGGER.info("Fusionmon: quitadas {} animaciones de Mega Showdown a medias de los Pokémon de {}",
                    cleared, player.getName().getString());
        }
    }

    /**
     * Si el Pokémon está a mitad de una animación de cambio de forma de Mega Showdown (mega, objeto). Su forma cambia al
     * final de la animación, unos segundos después de pulsar: si se fusionaba, separaba o invertía en medio, la forma
     * llegaba a la fusión cuando ya estaba hecha (una mega sin su marca, que ya no se podía hacer ni deshacer). Sin
     * entidad no hay animación: la marca es de una que se cortó y se quita.
     */
    public static boolean isChangingForm(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (data == null || !data.getBoolean(FORM_CHANGING)) {
            return false;
        }
        return !clearIfIdle(pokemon);
    }

    /** clearFormChanging en un Pokémon, solo si no tiene entidad (si la tiene, su animación puede seguir en marcha). */
    static boolean clearIfIdle(Pokemon pokemon) {
        if (pokemon.getEntity() != null || !clearFormChanging(pokemon.getPersistentData())) {
            return false;
        }
        // Para que Cobblemon lo guarde
        pokemon.onChange(null);
        return true;
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
        if (plan == null || plan.part() != FusionPart.BODY) {
            return false;
        }
        // Como Mega Showdown: la marca ya (no se puede hacer otra mega mientras dura la animación) y la forma en el
        // destello de la animación, no antes
        markMega(pokemon);
        float delay = MegaShowdown.playMegaEffect(pokemon);
        Runnable change = () -> finishBodyMega(owner, pokemon, plan.aspects());
        if (delay > 0) {
            afterOnServer(delay, change);
        } else {
            change.run();
        }
        return true;
    }

    /**
     * El final de megaEvolveBody, en el destello de la animación. Si mientras tanto se ha deshecho la mega (empezar un
     * combate, quitarle la piedra) o ha cambiado lo que toca, no se hace; si no se puede, se quita la marca.
     */
    private static void finishBodyMega(ServerPlayer owner, Pokemon fusion, List<String> aspects) {
        try {
            MegaPlan plan = plan(fusion);
            if (!FusionData.isFusion(fusion) || !isMega(fusion) || FusionData.hasBodyMega(fusion) || plan == null
                    || plan.part() != FusionPart.BODY || !plan.aspects().equals(aspects)) {
                return;
            }
            if (!megaBody(owner, fusion, aspects)) {
                revert(fusion);
                return;
            }
            // persistentData solo llega al cliente con el Pokémon entero (el resumen calcula los stats con ella)
            FusionService.resendToClient(owner, fusion);
            Fusionmon.LOGGER.info("Fusionmon: el cuerpo de la fusión de {} ha megaevolucionado ({})",
                    owner.getName().getString(), FusionData.bodyForm(fusion).getName());
        } catch (RuntimeException | LinkageError e) {
            FusionSafety.failed("mega del cuerpo", e);
        }
    }

    /** Programa algo en el hilo del servidor dentro de unos segundos (el planificador de Cobblemon). */
    static void afterOnServer(float seconds, Runnable action) {
        SchedulingFunctionsKt.afterOnServer(seconds, () -> {
            action.run();
            return Unit.INSTANCE;
        });
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
     * La mega que una fusión puede hacer en combate (FusionShowdown): las formas de cabeza y cuerpo de la fusión en
     * mega (su especie de Showdown) y el id de Showdown de la piedra con la que la hace.
     */
    /**
     * @param item  el id de Showdown del objeto que la hace (la piedra, el Ultranecrozium Z), o null
     * @param move  el movimiento que la hace en vez de un objeto (Rayquaza: "dragonascent"), o null
     * @param ultra si es la Ultraexplosión (forma "Ultra" en Showdown) en vez de una mega
     */
    public record BattleMega(FusionPart part, FormData head, FormData body, String item, String move, boolean ultra) {
    }

    /**
     * Antes de un combate: la mega que puede hacer la fusión, con la misma regla que fuera de combate (plan), o null.
     * La de la cabeza o la del cuerpo, con su piedra o (Rayquaza) con Ascenso Draco.
     */
    public static BattleMega battleMega(Pokemon fusion, RegistryAccess registryAccess) {
        if (!ENABLED || !FusionData.isFusion(fusion) || isMega(fusion)) {
            return null;
        }
        MegaPlan plan = plan(fusion);
        if (plan == null) {
            return null;
        }
        boolean dragonAscent = plan.aspects() == RAYQUAZA_MEGA;
        String item = dragonAscent ? null : MegaShowdown.stoneShowdownId(fusion.heldItem());
        if (!dragonAscent && item == null) {
            return null;
        }
        // La forma mega de la parte: una copia suya guardada, megaevolucionada (como megaBody)
        boolean head = plan.part() == FusionPart.HEAD;
        Pokemon part = head ? FusionData.readHead(fusion, registryAccess) : FusionData.readBody(fusion, registryAccess);
        if (!applyStone(part, plan.aspects())) {
            return null;
        }
        FormData headForm = head ? part.getForm() : FusionData.activeHeadForm(fusion);
        FormData bodyForm = head ? FusionData.bodyForm(fusion) : part.getForm();
        return headForm == null || bodyForm == null ? null
                : new BattleMega(plan.part(), headForm, bodyForm, item, dragonAscent ? DRAGON_ASCENT : null, false);
    }

    /**
     * En lugar de MegaGimmick.megaEvolveInBattle (Showdown ya ha megaevolucionado la fusión, ver FusionShowdownMegas):
     * si la mega es la del cuerpo, la animación de Mega Showdown y, en su destello, la capa del cuerpo, marcada para
     * quitarla al acabar el combate (revertBattleBody). True si lo ha hecho; la de la cabeza la sigue haciendo él.
     */
    public static boolean megaEvolveBodyInBattle(Pokemon fusion, BattlePokemon battlePokemon) {
        if (!FusionData.isFusion(fusion)) {
            return false;
        }
        MegaPlan plan = plan(fusion);
        if (plan == null || plan.part() != FusionPart.BODY) {
            return false;
        }
        fusion.getPersistentData().putBoolean(BATTLE_MEGA, true);
        float delay = MegaShowdown.playBattleEffect(fusion, battlePokemon, "mega_showdown:mega_evolution");
        Runnable change = () -> {
            try {
                if (!FusionData.isFusion(fusion) || FusionData.hasBodyLayer(fusion)
                        || !megaBody(DistributionUtilsKt.server().registryAccess(), fusion, plan.aspects())) {
                    return;
                }
                setBattleAbility(fusion, FusionData.bodyForm(fusion));
                // La interfaz del combate (forma, nombre, habilidad) y el Pokémon entero (el resumen)
                MegaShowdown.updateBattlePackets(battlePokemon);
                ServerPlayer owner = fusion.getOwnerPlayer();
                if (owner != null) {
                    FusionService.resendToClient(owner, fusion);
                }
                Fusionmon.LOGGER.info("Fusionmon: el cuerpo de la fusión {} ha megaevolucionado en combate ({})",
                        fusion.getSpecies().getName(), FusionData.bodyForm(fusion).getName());
            } catch (RuntimeException | LinkageError e) {
                FusionSafety.failed("mega del cuerpo en combate", e);
            }
        };
        if (delay > 0) {
            afterOnServer(delay, change);
        } else {
            change.run();
        }
        return true;
    }

    /**
     * Después de MegaGimmick.megaEvolveInBattle, cuando la mega es la de la cabeza (la hace Mega Showdown): la
     * habilidad de su forma mega, como con la del cuerpo (setBattleAbility).
     */
    public static void afterHeadMegaInBattle(Pokemon fusion) {
        if (!FusionData.isFusion(fusion)) {
            return;
        }
        MegaPlan plan = plan(fusion);
        if (plan == null || plan.part() != FusionPart.HEAD) {
            return;
        }
        Pokemon head = FusionData.readHead(fusion, DistributionUtilsKt.server().registryAccess());
        if (applyStone(head, plan.aspects())) {
            setBattleAbility(fusion, head.getForm());
        }
    }

    /**
     * Al megaevolucionar en combate Showdown pone la habilidad de la forma mega (Sequía, Impulso...), pero la fusión
     * de Cobblemon seguía con la suya, que es la que enseña el menú del equipo: la de una fusión va forzada (la del
     * cuerpo, si no Cobblemon la cambiaría), así que no cambia al cambiar de forma como en un Pokémon normal. Se le
     * pone la de la mega durante el combate y la suya se guarda para devolvérsela al acabar (revertBattle).
     */
    static void setBattleAbility(Pokemon fusion, FormData megaForm) {
        Ability mega = megaForm.getAbilities().select(megaForm.getSpecies(), Set.of()).getFirst();
        if (mega == null) {
            return;
        }
        CompoundTag data = fusion.getPersistentData();
        if (!data.contains(BATTLE_ABILITY)) {
            Ability own = fusion.getAbility();
            CompoundTag saved = new CompoundTag();
            saved.putString(ABILITY_NAME, own.getName());
            saved.putBoolean(ABILITY_FORCED, own.getForced());
            saved.putString(ABILITY_PRIORITY, own.getPriority().name());
            data.put(BATTLE_ABILITY, saved);
        }
        fusion.updateAbility(mega.getTemplate().create(true, Priority.NORMAL));
    }

    /**
     * Al acabar el combate (AspectUtils.revertPokemonsIfRequired, también al empezar el siguiente): la mega del cuerpo
     * hecha en combate se quita, como Mega Showdown hace con la de la cabeza, y la fusión recupera su habilidad.
     */
    public static void revertBattle(Pokemon pokemon) {
        CompoundTag data = pokemon.getPersistentData();
        if (data == null || !data.getBoolean(BATTLE_MEGA) && !data.contains(BATTLE_ABILITY)) {
            return;
        }
        boolean changed = data.getBoolean(BATTLE_MEGA) && FusionData.clearBodyMega(pokemon);
        data.remove(BATTLE_MEGA);
        if (data.contains(BATTLE_ABILITY)) {
            CompoundTag saved = data.getCompound(BATTLE_ABILITY);
            data.remove(BATTLE_ABILITY);
            AbilityTemplate own = Abilities.get(saved.getString(ABILITY_NAME));
            if (own != null) {
                pokemon.updateAbility(own.create(saved.getBoolean(ABILITY_FORCED),
                        Priority.valueOf(saved.getString(ABILITY_PRIORITY))));
                changed = true;
            }
        }
        ServerPlayer owner = pokemon.getOwnerPlayer();
        if (changed && owner != null) {
            FusionService.resendToClient(owner, pokemon);
        }
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
        return megaBody(owner.registryAccess(), fusion, aspects);
    }

    private static boolean megaBody(RegistryAccess registryAccess, Pokemon fusion, List<String> aspects) {
        Pokemon megaBody = FusionData.readBody(fusion, registryAccess);
        if (!applyStone(megaBody, aspects)) {
            Fusionmon.LOGGER.warn("Fusionmon: {} no cambia la forma de {}; no se megaevoluciona el cuerpo",
                    aspects, megaBody.getSpecies().getName());
            return false;
        }
        FusionData.setBodyMega(fusion, megaBody);
        return true;
    }

    /**
     * Pone las características de la piedra o del objeto ("mega_evolution=mega_x", "multitype=fire"), como lo hace
     * Mega Showdown. True si la forma ha cambiado (si no, no era de esta especie o esa forma no existe).
     */
    static boolean applyStone(Pokemon pokemon, List<String> aspects) {
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
     * Después de cambiarle el objeto a una fusión (HELD_ITEM_POST): si se le ha quitado la megapiedra de su cuerpo en
     * mega, la mega se deshace. Mega Showdown lo hace al quitar la piedra (megaToggle), pero solo si la piedra es de la
     * especie visible (la cabeza): con la del cuerpo la mega se quedaba puesta sin piedra, el objeto nuevo (Griseous
     * Core...) cambiaba la cabeza encima y no se podía volver a megaevolucionar. True si ha cambiado algo.
     */
    public static boolean revertBodyIfStoneRemoved(Pokemon fusion, ItemStack returned, ItemStack received) {
        // La misma piedra otra vez: nada (como Mega Showdown)
        if (!FusionData.hasBodyMega(fusion) || returned.isEmpty() || ItemStack.isSameItem(returned, received)) {
            return false;
        }
        MegaShowdown.Stone stone = MegaShowdown.stone(returned);
        FormData body = FusionData.bodyForm(fusion);
        if (stone == null || body == null || !stone.pokemons().contains(body.getSpecies().getName())) {
            return false;
        }
        // Sin su animación, como al fusionar: quita la capa del cuerpo y la marca de mega
        return revert(fusion);
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
    static final class MegaShowdown {

        private static final String PACKAGE = "com.github.yajatkaul.mega_showdown.";

        record Stone(List<?> pokemons, List<String> applyAspects) {
        }

        /** Un objeto que cambia la forma al llevarlo: a qué especies sirve, qué pone, qué la deshace y su efecto. */
        record ItemForm(List<?> pokemons, List<String> applyAspects, List<String> revertAspects, String effect) {

            boolean fits(Pokemon pokemon) {
                return pokemons.contains(pokemon.getSpecies().getName());
            }
        }

        private static final Class<?> MEGA_GIMMICK;
        private static final MethodHandle HAS_MEGA;
        private static final MethodHandle HAS_GIMMICK;
        private static final MethodHandle GET_COMPONENT;
        private static final MethodHandle POKEMONS;
        private static final MethodHandle STONE_SHOWDOWN_ID;
        private static final MethodHandle ASPECT_CONDITIONS;
        private static final MethodHandle ASPECT_APPLY;
        private static final MethodHandle ASPECTS;
        private static final MethodHandle GET_EFFECT;
        private static final MethodHandle APPLY_EFFECTS;
        private static final MethodHandle REVERT_EFFECTS;
        // Cuándo cambia la forma en la animación (segundos desde el principio): Effect.snowStorm().apply_after()
        private static final MethodHandle SNOWSTORM;
        private static final MethodHandle APPLY_AFTER;
        private static final MethodHandle REVERT_AFTER;
        // Ultraexplosión (FusionUltra): el cristal Z que lleva y los datos con los que Mega Showdown la deshace
        private static final Class<?> Z_CRYSTAL;
        private static final MethodHandle Z_ITEM_ID;
        private static final MethodHandle APPEND_REVERT;
        // En combate: su animación (Effect.applyEffectsBattle) y avisar a la interfaz del combate (updatePackets)
        private static final MethodHandle APPLY_EFFECTS_BATTLE;
        private static final MethodHandle UPDATE_PACKETS;
        // Formas por objeto: los objetos con código propio (placas, máscaras...) guardan sus datos en campos privados;
        // las de datos (HeldItemFormChange) son un componente del objeto, como las megapiedras
        private static final Class<?> FORM_CHANGE_ITEM;
        private static final Field ITEM_POKEMONS;
        private static final Field ITEM_APPLY_ASPECT;
        private static final Field ITEM_REVERT_ASPECT;
        private static final Field ITEM_EFFECT;
        private static final MethodHandle ASPECT_REVERT;
        private static final Class<?> HELD_ITEM_FORM_CHANGE;
        private static final MethodHandle HELD_POKEMONS;
        private static final MethodHandle HELD_ASPECT_CONDITIONS;
        private static final MethodHandle HELD_EFFECT;

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
                STONE_SHOWDOWN_ID = lookup.findVirtual(MEGA_GIMMICK, "showdown_id",
                        MethodType.methodType(String.class));
                ASPECT_CONDITIONS = lookup.findVirtual(MEGA_GIMMICK, "aspect_conditions",
                        MethodType.methodType(aspectConditions));
                ASPECT_APPLY = lookup.findVirtual(aspectConditions, "aspectApply", MethodType.methodType(conditions));
                ASPECTS = lookup.findVirtual(conditions, "aspects", MethodType.methodType(List.class));
                GET_EFFECT = lookup.findStatic(effect, "getEffect", MethodType.methodType(effect, String.class));
                APPLY_EFFECTS = lookup.findVirtual(effect, "applyEffects", MethodType.methodType(void.class,
                        Pokemon.class, List.class, Optional.class, PokemonEntity.class));
                REVERT_EFFECTS = lookup.findVirtual(effect, "revertEffects", MethodType.methodType(void.class,
                        Pokemon.class, List.class, Optional.class, PokemonEntity.class));
                Class<?> snowStorm = Class.forName(PACKAGE + "api.codec.particles.SnowStormParticle", false, loader);
                SNOWSTORM = lookup.findVirtual(effect, "snowStorm", MethodType.methodType(Optional.class));
                APPLY_AFTER = lookup.findVirtual(snowStorm, "apply_after", MethodType.methodType(Optional.class));
                REVERT_AFTER = lookup.findVirtual(snowStorm, "revert_after", MethodType.methodType(Optional.class));
                Z_CRYSTAL = Class.forName(PACKAGE + "api.codec.ZCrystal", false, loader);
                Z_ITEM_ID = lookup.findVirtual(Z_CRYSTAL, "showdown_item_id", MethodType.methodType(String.class));
                Class<?> aspectUtils = Class.forName(PACKAGE + "utils.AspectUtils", false, loader);
                APPEND_REVERT = lookup.findStatic(aspectUtils, "appendRevertDataPokemon", MethodType.methodType(
                        void.class, effect, List.class, Optional.class, Pokemon.class, String.class));
                APPLY_EFFECTS_BATTLE = lookup.findVirtual(effect, "applyEffectsBattle", MethodType.methodType(
                        void.class, Pokemon.class, List.class, Optional.class, PokemonEntity.class,
                        BattlePokemon.class));
                UPDATE_PACKETS = lookup.findStatic(aspectUtils, "updatePackets",
                        MethodType.methodType(void.class, BattlePokemon.class));

                FORM_CHANGE_ITEM = Class.forName(PACKAGE + "item.custom.form_change.FormChangeHeldItem", false,
                        loader);
                ITEM_POKEMONS = privateField(FORM_CHANGE_ITEM, "pokemons");
                ITEM_APPLY_ASPECT = privateField(FORM_CHANGE_ITEM, "applyAspect");
                ITEM_REVERT_ASPECT = privateField(FORM_CHANGE_ITEM, "revertAspect");
                ITEM_EFFECT = privateField(FORM_CHANGE_ITEM, "effectId");
                ASPECT_REVERT = lookup.findVirtual(aspectConditions, "aspectRevert", MethodType.methodType(conditions));
                HELD_ITEM_FORM_CHANGE = Class.forName(PACKAGE + "api.codec.HeldItemFormChange", false, loader);
                HELD_POKEMONS = lookup.findVirtual(HELD_ITEM_FORM_CHANGE, "pokemons",
                        MethodType.methodType(List.class));
                HELD_ASPECT_CONDITIONS = lookup.findVirtual(HELD_ITEM_FORM_CHANGE, "aspect_conditions",
                        MethodType.methodType(aspectConditions));
                HELD_EFFECT = lookup.findVirtual(HELD_ITEM_FORM_CHANGE, "effect",
                        MethodType.methodType(Optional.class));
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

        /** Si lleva el Aro Z (o lo que Mega Showdown acepte para los movimientos Z), como pide la Ultraexplosión. */
        static boolean hasZRing(ServerPlayer player) {
            try {
                return (boolean) HAS_GIMMICK.invoke(ShowdownMoveset.Gimmick.Z_POWER, (LivingEntity) player);
            } catch (Throwable e) {
                throw new IllegalStateException("No se ha podido comprobar el Aro Z con Mega Showdown", e);
            }
        }

        /** El id de Showdown del cristal Z que lleva ("ultranecroziumz"...), o null si no es un cristal Z. */
        static String zCrystal(ItemStack held) {
            if (held.isEmpty()) {
                return null;
            }
            try {
                Object crystal = GET_COMPONENT.invoke(Z_CRYSTAL, held);
                return crystal == null ? null : (String) Z_ITEM_ID.invoke(crystal);
            } catch (Throwable e) {
                throw new IllegalStateException("No se ha podido leer el cristal Z de Mega Showdown", e);
            }
        }

        /**
         * Lo que guarda Mega Showdown al hacer la Ultraexplosión para deshacerla al empezar un combate (revertAspects
         * bajo key, con el efecto effectId), como en UltraGimmick.ultraBurst.
         */
        static void appendRevertData(Pokemon pokemon, String effectId, List<String> revertAspects, String key) {
            try {
                Object effect = GET_EFFECT.invoke(effectId);
                if (effect != null) {
                    APPEND_REVERT.invoke(effect, revertAspects, Optional.empty(), pokemon, key);
                }
            } catch (Throwable e) {
                // Solo se pierde que Mega Showdown la deshaga al empezar un combate
                Fusionmon.LOGGER.warn("Fusionmon: no se han podido guardar los datos para deshacer {}", effectId, e);
            }
        }

        /** El id de Showdown de la megapiedra ("charizarditey", como item.id allí), o null si no es una megapiedra. */
        static String stoneShowdownId(ItemStack held) {
            try {
                Object gimmick = GET_COMPONENT.invoke(MEGA_GIMMICK, held);
                if (gimmick == null) {
                    return null;
                }
                // Como toID de Showdown: minúsculas, solo letras y números
                return ((String) STONE_SHOWDOWN_ID.invoke(gimmick)).toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
            } catch (Throwable e) {
                throw new IllegalStateException("No se ha podido leer el id de Showdown de la megapiedra", e);
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

        private static Field privateField(Class<?> owner, String name) throws NoSuchFieldException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        /** La forma que da el objeto al llevarlo, o null si no cambia ninguna forma. */
        @SuppressWarnings("unchecked")
        static ItemForm itemForm(ItemStack held) {
            if (held.isEmpty()) {
                return null;
            }
            try {
                Object item = held.getItem();
                if (FORM_CHANGE_ITEM.isInstance(item)) {
                    return new ItemForm((List<?>) ITEM_POKEMONS.get(item),
                            List.of((String) ITEM_APPLY_ASPECT.get(item)),
                            List.of((String) ITEM_REVERT_ASPECT.get(item)), (String) ITEM_EFFECT.get(item));
                }
                Object change = GET_COMPONENT.invoke(HELD_ITEM_FORM_CHANGE, held);
                if (change == null) {
                    return null;
                }
                Object conditions = HELD_ASPECT_CONDITIONS.invoke(change);
                Object apply = ASPECT_APPLY.invoke(conditions);
                Object revert = ASPECT_REVERT.invoke(conditions);
                Optional<?> effect = (Optional<?>) HELD_EFFECT.invoke(change);
                return new ItemForm((List<?>) HELD_POKEMONS.invoke(change), (List<String>) ASPECTS.invoke(apply),
                        (List<String>) ASPECTS.invoke(revert), effect.map(Object::toString).orElse(null));
            } catch (Throwable e) {
                throw new IllegalStateException("No se ha podido leer la forma del objeto de Mega Showdown", e);
            }
        }

        /**
         * Una animación de Mega Showdown en combate (la de megaEvolveInBattle o ultraBurstInBattle, con su pausa del
         * combate), sin cambiarle nada al Pokémon. Devuelve los segundos hasta su destello (0 sin animación o si falla).
         */
        static float playBattleEffect(Pokemon pokemon, BattlePokemon battlePokemon, String id) {
            try {
                Object effect = GET_EFFECT.invoke(id);
                if (effect == null) {
                    return 0;
                }
                APPLY_EFFECTS_BATTLE.invoke(effect, pokemon, List.of(), Optional.empty(), (PokemonEntity) null,
                        battlePokemon);
                if (pokemon.getEntity() == null) {
                    return 0;
                }
                Optional<?> snowStorm = (Optional<?>) SNOWSTORM.invoke(effect);
                Optional<?> delay = snowStorm.isEmpty() ? Optional.empty()
                        : (Optional<?>) APPLY_AFTER.invoke(snowStorm.get());
                return delay.map(seconds -> ((Number) seconds).floatValue()).orElse(0f);
            } catch (Throwable e) {
                Fusionmon.LOGGER.warn("Fusionmon: no se ha podido mostrar la animación {} del cuerpo", id, e);
                return 0;
            }
        }

        /** Lo que hace Mega Showdown tras un cambio de forma en combate: la interfaz del combate ve la forma nueva. */
        static void updateBattlePackets(BattlePokemon battlePokemon) {
            try {
                UPDATE_PACKETS.invoke(battlePokemon);
            } catch (Throwable e) {
                // Solo la interfaz del combate se queda con la forma de antes hasta el próximo cambio
                Fusionmon.LOGGER.warn("Fusionmon: no se ha podido avisar al combate del cambio de forma", e);
            }
        }

        /** La animación de megaevolucionar de Mega Showdown (destello, sonido), sin cambiarle nada al Pokémon. */
        static float playMegaEffect(Pokemon pokemon) {
            return playEffect(pokemon, "mega_showdown:mega_evolution", true);
        }

        /**
         * Una animación de Mega Showdown por su id (la de poner la forma, o la de quitarla), sin cambiarle nada al
         * Pokémon. Devuelve los segundos hasta el momento de la animación en el que Mega Showdown cambia la forma (el
         * destello; 0 si no hay animación porque el Pokémon no está fuera). Mientras, Mega Showdown le pone su marca
         * de cambio de forma (no vuelve a la Poké Ball, no se fusiona). Si falla, solo se pierde la animación: 0.
         */
        static float playEffect(Pokemon pokemon, String id, boolean apply) {
            try {
                Object effect = GET_EFFECT.invoke(id);
                if (effect == null) {
                    return 0;
                }
                (apply ? APPLY_EFFECTS : REVERT_EFFECTS).invoke(effect, pokemon, List.of(), Optional.empty(),
                        (PokemonEntity) null);
                if (pokemon.getEntity() == null) {
                    return 0;
                }
                Optional<?> snowStorm = (Optional<?>) SNOWSTORM.invoke(effect);
                if (snowStorm.isEmpty()) {
                    return 0;
                }
                Optional<?> delay = (Optional<?>) (apply ? APPLY_AFTER : REVERT_AFTER).invoke(snowStorm.get());
                return delay.map(seconds -> ((Number) seconds).floatValue()).orElse(0f);
            } catch (Throwable e) {
                Fusionmon.LOGGER.warn("Fusionmon: no se ha podido mostrar la animación {} del cuerpo", id, e);
                return 0;
            }
        }
    }
}
