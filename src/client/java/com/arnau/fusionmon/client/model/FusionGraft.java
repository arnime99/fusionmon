package com.arnau.fusionmon.client.model;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.client.texture.FusionBody;
import com.arnau.fusionmon.client.texture.FusionTextures;
import com.cobblemon.mod.common.client.render.ModelLayer;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * PROTOTIPO "cabeza sobre cuerpo" (se activa con /fusionvisual graft).
 *
 * En Cobblemon cada modelo es un árbol de huesos (ModelPart de Minecraft) y casi todos tienen una cabeza
 * reconocible (ver findHeads). Para una fusión:
 *  1. Cuando Cobblemon pide el modelo, la textura o las capas de la cabeza, le damos los del CUERPO
 *     (VaryingModelRepositoryMixin). Así anda, vuela y nada con las animaciones del cuerpo.
 *     Colores como en Infinite Fusion: el cuerpo pintado con los colores de la cabeza.
 *  2. Al pintar ese modelo (PosableModelMixin) ocultamos sus cabezas y, al terminar, pintamos en el sitio de cada
 *     una la cabeza del modelo de la otra especie (con sus hijos y su textura original), escalada a su tamaño.
 *     La cabeza pegada se anima con SUS animaciones (reposo, parpadeo, mirar): así se orienta como en su modelo
 *     y las piezas que sus animaciones ocultan (boca abierta/cerrada, párpados...) no salen duplicadas.
 *
 *  3. Si las dos especies tienen cola, la del cuerpo se cambia por la de la especie de la cabeza, del mismo modo
 *     que la cabeza: en el sitio de la del cuerpo, orientada como en su modelo y del largo de la que sustituye
 *     (/fusionvisual tail on|off). Si el cuerpo no tiene cola, se coloca sobre su tronco como un adorno.
 *  4. Los adornos del tronco de la especie de la cabeza (la planta de Ivysaur, las setas de Paras, alas, concha...)
 *     se pegan en el tronco del cuerpo, en el mismo sitio proporcional; los del cuello (el pelo de Eevee) van con la
 *     cabeza pegada. Si el cuerpo tiene adornos de la misma clase (alas por alas), se quitan los suyos
 *     (/fusionvisual decor on|off). Ver findDecorations.
 *  Todo lo pegado se pinta también con las capas de su especie (la llama de Charmander, ojos que brillan...).
 *
 * Si la especie de la cabeza es "todo cabeza" (sin hueso de cabeza, o cuya "cabeza" es casi todo el modelo:
 * Magikarp, Voltorb, Koffing...), se pega su modelo entero, como en Infinite Fusion, apoyado donde acababa la
 * cabeza del cuerpo. Si es el cuerpo el que no tiene cabeza, o son la misma especie, se pinta como siempre
 * (modelo de la cabeza con los colores del cuerpo).
 *
 * Todo ocurre en el hilo de render, de uno en uno: por eso basta con campos estáticos.
 */
public final class FusionGraft {

    private static final String SHINY = "shiny";
    /** Para que una cabeza enorme o diminuta no quede absurda del todo. */
    private static final float MIN_SCALE = 0.25F;
    private static final float MAX_SCALE = 2.5F;
    /**
     * Límites de escala de colas y adornos, más amplios que los de las cabezas: entre Pokémon tan dispares como
     * Groudon y Deoxys hace falta bajar a 0,35 (con el mínimo de las cabezas la cola de Groudon salía enorme).
     */
    private static final float MIN_PIECE_SCALE = 0.1F;
    private static final float MAX_PIECE_SCALE = 4F;
    /**
     * Topes de largo de una cola pegada: como mucho TAIL_GROWTH veces la que sustituye o, si el cuerpo no tiene cola,
     * lo que mide su tronco. La de Groudon es tan larga como todo su cuerpo: en proporción salía gigante.
     */
    private static final float TAIL_GROWTH = 1.5F;
    /** Una cola puesta en un cuerpo sin cola sale de la franja central espalda-barriga: sin este margen arriba/abajo. */
    private static final float TAIL_BAND = 0.25F;
    /** Cuánto puede ajustarse un adorno a la forma de cada eje del cuerpo, respecto a su escala normal. */
    private static final float HUG_MIN = 0.67F;
    private static final float HUG_MAX = 1.5F;
    /**
     * Una cola solo se pone en un cuerpo sin cola si nace cerca del tronco de su especie: hasta este margen fuera de
     * su caja (en proporción). La de Gyarados es la aleta del final de su cuerpo de serpiente, a nueve segmentos.
     */
    private static final float TAIL_REACH = 0.25F;

    /**
     * Un modelo sin patas cuya cola es más de esta parte del volumen es una serpiente o un pez: su cola es medio cuerpo
     * y solo se cambia la punta (ver findTail). Simulado: 28 modelos (Ekans, Milotic, Magikarp, Feebas, Huntail...).
     */
    private static final float SERPENT_TAIL_SHARE = 0.25F;
    private static final Set<String> LEGS = Set.of("leg", "legs", "foot", "feet");

    /** Clase de "adorno" de los brazos (ver findDecorations y graft). */
    private static final String ARM = "arm";

    /** Clase de adorno de las colas puestas en un cuerpo sin cola. */
    private static final String TAIL = "tail";
    /** Trozos de nombre que hacen adorno a un hueso aunque lleve anatomía en el nombre: la cría de Kangaskhan ("torso_kid"). */
    private static final Set<String> ALWAYS_DECORATION = Set.of("kid", "baby", "child");
    /**
     * La cabeza nueva se pinta un poco más grande: donde sus caras coinciden con las del cuello del cuerpo
     * (mismo plano), así gana siempre la cabeza en vez de parpadear las dos (z-fighting).
     */
    private static final float INFLATE = 1.02F;
    /**
     * Si la cabeza encontrada por "locator_head" ocupa al menos esta parte del volumen del modelo, ese hueso es en
     * realidad casi todo el cuerpo (Koffing, Voltorb, Tentacool, Clefairy...): como cabeza se pega el modelo entero.
     */
    private static final float WHOLE_MODEL_SHARE = 0.8F;
    /** Para medir una cabeza (ver size): qué cuenta como pieza grande pegada al cráneo, y hasta cuánto crece. */
    private static final float PIECE_SHARE = 0.1F;
    private static final float SKULL_GROWTH = 1.5F;
    /**
     * Racimo de cabezas (Exeggutor): el racimo entero mide esto respecto a la cabeza del cuerpo, y las cabezas se
     * separan de la principal esto respecto a su modelo (ver pastedSize).
     */
    private static final float CLUSTER_SIZE = 1F;
    private static final float CLUSTER_SPREAD = 1.4F;
    /**
     * Al revés, un cuerpo con racimo (Exeggutor): cada cabeza pegada en una de las suyas mide esto de ella (se cambian
     * cráneo por cráneo, ver skullOffset). 1 = mismo tamaño, un reemplazo.
     */
    private static final float CLUSTER_BODY_SCALE = 1F;
    /**
     * Cuerpo sin cabeza con una cabeza "normal": los complementos de la cabeza (orejas, pelo...) se ponen en el
     * cuerpo escalados como su cráneo respecto al cuerpo, por esta parte (ver renderOnBody).
     */
    private static final float ACCESSORY_SCALE = 1F;

    /** Modo de ver las fusiones: cabeza sobre cuerpo (por defecto) o colores (/fusionvisual colors). */
    private static boolean enabled = true;
    /** Si se cambia la cola del cuerpo por la de la especie de la cabeza (/fusionvisual tail on|off). */
    private static boolean tails = true;
    /** Si se pegan al cuerpo los adornos de la especie de la cabeza (/fusionvisual decor on|off). */
    private static boolean decorations = true;
    /** Si los cuerpos sin cabeza llevan la cabeza encima o van en modo colores (/fusionvisual top on|off). */
    private static boolean tops = true;
    /** Cómo se coloca una cabeza normal en el sitio de la del cuerpo (/fusionvisual align ...). */
    public enum Align {
        /** Pivote con pivote: el pivote suele estar en el cuello. */
        PIVOT,
        /** Cráneo por cráneo, centro con centro (por defecto), como los modelos enteros y Exeggutor (ver skullOffset). */
        SKULL,
        /**
         * Base con base: el centro de la cara del cráneo que mira al cuello (ver attachPoint). En 195 modelos el pivote
         * está en medio de la cabeza (Charizard, Pidgey) y en 886 en el cuello: al juntar uno de cada, pivote con
         * pivote, la cabeza nueva quedaba media cabeza hundida (Charizard en Snorlax) o flotando.
         */
        BASE
    }

    /** Por defecto cráneo por cráneo: probado por el usuario con muchas parejas, el que mejor queda (BASE va detrás). */
    private static Align align = Align.SKULL;

    /**
     * Cómo se pasan los tamaños de una especie a otra (/fusionvisual size ...). Las escalas son proporciones: el cráneo
     * del cuerpo respecto al de la cabeza, un tronco respecto al otro. Exactas, una cabeza normal sobre Snorlax u Onix
     * (cráneos enormes) salía gigante y las alas de Butterfree sobre Snorlax, x4.
     */
    public enum Sizing {
        /** La proporción tal cual. */
        EXACT,
        /** La proporción elevada a SOFT_SIZE: más cerca de 1, en los dos sentidos (x2,5 → x1,7; x0,5 → x0,66). */
        SOFT,
        /** Suavizada solo al agrandar: lo que se encoge (la cabeza de Onix en Charmander), exacto. */
        SOFT_UP
    }

    /** Exponente de Sizing.SOFT. */
    private static final float SOFT_SIZE = 0.6F;
    /** Por defecto suavizado solo al agrandar: probado por el usuario, el que mejor queda. */
    private static Sizing sizing = Sizing.SOFT_UP;

    /**
     * Trozos de nombre de hueso que no dicen qué pieza es, solo dónde está o en qué estado ("wing_left",
     * "closed_wings", "leaf_front2"...): no cuentan para saber la clase de un adorno.
     */
    private static final Set<String> NAME_MODIFIERS = Set.of("left", "right", "l", "r", "front", "back", "top",
            "bottom", "upper", "lower", "base", "master", "main", "mid", "middle", "inner", "outer", "open", "closed",
            "side", "flying", "big", "small");
    /**
     * Anatomía común a casi todos los modelos (contado en los 1142 de Cobblemon 1.8.1): un hueso con alguno de estos
     * trozos en el nombre no es un adorno. "ftorso"/"fleg"/"fbody" son grupos de brazos y patas delanteras;
     * "bone", "cube", "group", "bb" ("bb_main")... son nombres sin significado que deja Blockbench. "main" no: en
     * "frill_main" (el volante del cuello de Vaporeon) solo dice cuál es la pieza principal.
     * "mounch" es la boca en los modelos de AllTheMons (Gulpin, Swalot): la cara de Swalot se pegaba como un adorno.
     */
    private static final Set<String> ANATOMY = Set.of("head", "neck", "torso", "ftorso", "body", "fbody", "chest",
            "belly", "abdomen", "thorax", "waist", "hip", "pelvis", "butt", "spine", "segment", "leg", "fleg", "thigh",
            "knee", "foot", "feet", "toe", "arm", "shoulder", "hand", "finger", "tail", "tentacle", "jaw", "mouth",
            "mounch", "eye", "face", "tongue", "locator", "bone", "cube", "group", "root", "bb", "seat", "shadow");

    /** Estado (entidad o menú) → especie de la cabeza; se apunta cuando Cobblemon pide el modelo. */
    private static final Map<PosableState, ResourceLocation> HEADS = new WeakHashMap<>();
    /** Estado de animación propio de la cabeza pegada de cada fusión (para que parpadee a su ritmo, etc.). */
    private static final Map<PosableState, FloatingState> HEAD_STATES = new WeakHashMap<>();
    /** Cabezas de cada modelo (no cambian). */
    private static final Map<PosableModel, ModelHeads> HEAD_BONES = new WeakHashMap<>();
    /** Tamaño de cada cabeza con sus hijos, para escalar la cabeza nueva. */
    private static final Map<ModelPart, Float> SIZES = new WeakHashMap<>();
    /** Núcleo de cada cuerpo sin cabeza (ver coreBox). */
    private static final Map<ModelPart, float[]> CORES = new WeakHashMap<>();
    /** Tamaño de cada racimo de cabezas, por su cabeza principal (ver pastedSize). */
    private static final Map<ModelPart, Float> CLUSTERS = new WeakHashMap<>();
    /** Cráneo y complementos de cada cabeza, para los cuerpos sin cabeza (ver renderOnBody). */
    private static final Map<ModelPart, HeadExtras> EXTRAS = new WeakHashMap<>();
    /** Caja de cada hueso con sus hijos, en su propio marco (para apoyar los modelos enteros, ver groundOffset). */
    private static final Map<ModelPart, float[]> BOXES = new WeakHashMap<>();
    /** Caja de los cubos propios de cada tronco (sin sus hijos), en su propio marco: para colocar los adornos. */
    private static final Map<ModelPart, float[]> OWN_BOXES = new WeakHashMap<>();
    /** Lo mismo, solo con los cubos con volumen (ver solidOwnBox). */
    private static final Map<ModelPart, float[]> SOLID_BOXES = new WeakHashMap<>();
    /** Modelos de cabeza que ya han fallado al pintarse (para avisar en el log una sola vez). */
    private static final Set<PosableModel> WARNED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Parejas cuyo graft ha fallado al montarse (ver graft): se pintan en modo colores hasta recargar recursos. */
    private static final Set<String> FAILED = new HashSet<>();

    // NAME_RESULTS: lo que se deduce del nombre de un hueso (o de la ruta de un cubo) no cambia nunca, y se pregunta
    // muchísimo: skull() mira cada cubo de cada cabeza pegada en cada fotograma. Calcularlo cada vez (partir el
    // nombre, quitar números con expresiones regulares...) era el 70 % del tiempo de pintar 60 fusiones. Se guarda
    // la respuesta la primera vez (los nombres son pocos: los de los modelos cargados)
    private static final Map<String, Optional<String>> CATEGORIES = new HashMap<>();
    private static final Map<String, Boolean> IN_DECORATION = new HashMap<>();
    private static final Map<String, Boolean> ARMS = new HashMap<>();
    private static final Map<String, Boolean> LEG_NAMES = new HashMap<>();
    private static final Pattern TRAILING_DIGITS = Pattern.compile("\\d+$");
    private static final Pattern STUCK_SIDE_LEG = Pattern.compile("[lr](leg|legs|foot|feet)");

    /** El montaje de una fusión para un estado, y con qué se montó (si algo de eso cambia, se vuelve a montar). */
    private record CachedGraft(ResourceLocation name, Set<String> aspects, int version, Graft graft) {
    }

    /**
     * Montaje guardado por estado (cada Pokémon pintado, en el mundo o en un menú, tiene el suyo). Se pide 4 veces por
     * fotograma (modelo, textura, capas y al pintar) y montarlo crea listas cada vez: así se monta una sola vez.
     * Las claves son débiles: al desaparecer el Pokémon se va solo.
     */
    private static final Map<PosableState, CachedGraft> GRAFTS = new WeakHashMap<>();
    /** Sube cuando cambia algo que cambia el montaje (modos de /fusionvisual, recursos): los guardados no valen. */
    private static int graftVersion;

    // Estado "suelto" para pedir la textura original de la cabeza (con el shiny del cuerpo si hace falta)
    private static FloatingState textureState;

    // Lo que hay que deshacer al terminar de pintar el modelo del cuerpo
    private static Graft active;
    private static final List<Boolean> hiddenWereVisible = new ArrayList<>();

    /**
     * @param head        la cabeza que se pega (la principal del modelo de la cabeza)
     * @param whole       si lo que se pega es el modelo entero de la cabeza ("todo cabeza")
     * @param limbs       extremidades que no se pintan al pegar el modelo entero (ver ModelHeads)
     * @param bodies      las cabezas del cuerpo que se sustituyen (varias en Doduo, Dodrio...)
     * @param headTail    la cola de la especie de la cabeza que sustituye a la del cuerpo, bodyTail; null las dos
     *                    si no se cambia la cola
     * @param trunkDecorations adornos de la especie de la cabeza que se colocan sobre el tronco del cuerpo
     *                    (incluida su cola si el cuerpo no tiene), del tronco headTrunk de su modelo al bodyTrunk
     * @param neckDecorations adornos del cuello de la especie de la cabeza, que van con la cabeza pegada
     * @param headSpine   principio del cuello (o la cabeza) de cada modelo, para sus ejes (ver spineFrame);
     *                    bodySpine el del cuerpo
     * @param hidden      huesos del cuerpo que no se pintan: sus cabezas, su cola y sus adornos sustituidos
     * @param chains      si la especie de la cabeza tiene varias (Dodrio, Scovillain...): todas, cada una con su cuello,
     *                    que se pegan donde empezaba el cuello del cuerpo en vez de una sola cabeza (ver renderChains);
     *                    vacía si no
     * @param companions  las demás cabezas del racimo de Exeggutor, que se pintan con la principal (ver ModelHeads)
     * @param top         si el cuerpo es un cuerpo-cabeza (bodies vacía): el hueso sobre el que se ponen los
     *                    complementos (ver renderOnBody); si no, null
     * @param headTop     si la especie de la cabeza es un cuerpo-cabeza sin tronco: su pieza principal, que hace de
     *                    tronco para colocar sus brazos (ver ballSpace); si no, null
     */
    private record Graft(FusionBody body, VaryingRenderableResolver headResolver, VaryingRenderableResolver bodyResolver,
                         PosableModel headModel, PosableModel bodyModel, HeadBone head, boolean whole,
                         Map<ModelPart, String> limbs, List<HeadBone> bodies, Tail headTail, Tail bodyTail,
                         List<Decoration> trunkDecorations, List<Decoration> neckDecorations, HeadBone headTrunk,
                         HeadBone bodyTrunk, HeadBone headSpine, HeadBone bodySpine, List<ModelPart> hidden,
                         List<HeadBone> chains, List<HeadBone> companions, HeadBone top, HeadBone headTop) {
    }

    /**
     * Un adorno de un modelo: un hueso que cuelga del camino hasta la cabeza y no es anatomía común (ver
     * findDecorations).
     *
     * @param category su clase, sacada del nombre ("wing" para "wing_left" y "closed_wings"): un adorno del cuerpo
     *                 se quita si la especie de la cabeza trae uno de la misma clase
     * @param neck     si cuelga del cuello (entre el tronco y la cabeza: el pelo del cuello de Eevee). Esos van con
     *                 la cabeza pegada; los demás se colocan sobre el tronco
     */
    private record Decoration(String category, HeadBone bone, boolean neck) {
    }

    /**
     * La cola de un modelo: una o varias piezas "tail*" que cuelgan del mismo hueso (Luxray: la cadena "tail_1"... y
     * cinco mechones "tail_fluff_*"; Ninetales: sus nueve colas).
     *
     * @param roots  las piezas, cada una con lo que lleva colgando
     * @param anchor la principal (la que tiene más cubos): marca dónde va y cuánto mide la cola
     */
    private record Tail(List<HeadBone> roots, HeadBone anchor) {
    }

    /**
     * Un hueso de un modelo (una cabeza o una cola) con todo lo que lleva colgando.
     *
     * @param part el hueso
     * @param path huesos desde la raíz hasta él, incluidos los dos
     */
    private record HeadBone(ModelPart part, List<ModelPart> path) {
    }

    /**
     * Las cabezas de un modelo, que se usan distinto según el papel de la especie en la fusión.
     *
     * @param heads cabezas del modelo; vacía si no tiene (Koffing, Voltorb...). En un cuerpo-cabeza (whole) no se
     *              sustituyen cuando es el CUERPO: se queda entero (ver buildGraft)
     * @param whole si es un cuerpo-cabeza, un Pokémon cuyo cuerpo es su cabeza (sin cabeza, como Voltorb, o "todo
     *              cabeza", como Gengar): como CABEZA se pega entero; como CUERPO se queda entero, con su cara, y lleva
     *              los complementos de la otra especie
     * @param limbs al pegar el modelo entero, extremidades que no se pintan: las de moverse que quedan fuera de su
     *              "cabeza" (los tentáculos de Tentacool, la cola de Haunter) y los brazos (Gengar): el cuerpo ya pone
     *              los suyos. Las manos sueltas de Haunter no son brazos y se quedan.
     *              Hueso → su ruta tal como la da ModelPart.visit ("/tentacool/body/tentacle_left")
     * @param tail  la cola del modelo (ver findTail), o null
     * @param trunk el tronco del modelo (ver findTrunk), o null
     * @param decorations los adornos que cuelgan del camino hasta la cabeza principal (ver findDecorations)
     * @param spineEnd el principio del cuello (ver findSpineEnd), o null
     * @param chains   con varias cabezas, cada una con su cuello desde donde se separan (ver findChains); si no, vacía
     * @param companions con varias cabezas sin cuello que cuelgan del mismo hueso (el racimo de Exeggutor): las que
     *                 no son la principal, que van pegadas con ella como en su modelo; entonces chains está vacía
     * @param top      si es un cuerpo-cabeza (whole: sin cabeza o "todo cabeza"), el hueso sobre el que se ponen los
     *                 complementos de la otra especie cuando es el CUERPO (ver findTop); si no, null
     * @param arms     sus brazos ("arms", "arm_left", "shoulder_right"...: los de más arriba, con lo que cuelga), para
     *                 ponérselos a un cuerpo-cabeza que no tenga (ver findArms)
     */
    private record ModelHeads(List<HeadBone> heads, boolean whole, Map<ModelPart, String> limbs, Tail tail,
                              HeadBone trunk, List<Decoration> decorations, HeadBone spineEnd, List<HeadBone> chains,
                              List<HeadBone> companions, HeadBone top, List<HeadBone> arms) {
    }

    private FusionGraft() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void setTails(boolean value) {
        tails = value;
        graftVersion++;
    }

    public static void setDecorations(boolean value) {
        decorations = value;
        graftVersion++;
    }

    public static void setTops(boolean value) {
        tops = value;
        graftVersion++;
    }

    public static boolean hasTails() {
        return tails;
    }

    public static boolean hasDecorations() {
        return decorations;
    }

    public static boolean hasTops() {
        return tops;
    }

    public static void setAlign(Align value) {
        align = value;
    }

    public static Align getAlign() {
        return align;
    }

    public static void setSizing(Sizing value) {
        sizing = value;
    }

    public static Sizing getSizing() {
        return sizing;
    }

    /** Una proporción de tamaños pasada por el modo de Sizing. */
    private static float soften(float ratio) {
        if (ratio <= 0 || sizing == Sizing.EXACT || sizing == Sizing.SOFT_UP && ratio < 1) {
            return ratio;
        }
        return (float) Math.pow(ratio, SOFT_SIZE);
    }

    /**
     * ¿Se podría pintar esta fusión con cabeza sobre cuerpo? (aunque el prototipo esté apagado).
     * Para el visor de la pantalla de fusión.
     */
    public static boolean canGraft(ResourceLocation headSpecies, PosableState state) {
        return graft(headSpecies, state, true) != null;
    }

    // ---- 1. Modelo, textura y capas del cuerpo (desde VaryingModelRepositoryMixin) ----

    /** El modelo con el que pintar la fusión, o null para dejar el de siempre. */
    public static PosableModel bodyModel(ResourceLocation name, PosableState state) {
        Graft graft = graft(name, state, false);
        if (graft == null) {
            FusionBody only = onlyBody(name, state);
            return only == null ? null : only.resolver().getPoser(only.state());
        }
        HEADS.put(state, name);
        return graft.bodyModel;
    }

    /** Textura del cuerpo pintada con los colores de la cabeza (estilo Infinite Fusion). */
    public static ResourceLocation bodyTexture(ResourceLocation name, PosableState state) {
        Graft graft = graft(name, state, false);
        if (graft == null) {
            FusionBody only = onlyBody(name, state);
            return only == null ? null : only.resolver().getTexture(only.state());
        }
        // El estado del cuerpo no lleva "fusionmon-fusion": sale su textura original, sin recolorear
        ResourceLocation body = graft.bodyResolver.getTexture(graft.body.state());
        return FusionTextures.recolored(body, headTexture(graft, state));
    }

    public static Iterable<ModelLayer> bodyLayers(ResourceLocation name, PosableState state) {
        Graft graft = graft(name, state, false);
        if (graft == null) {
            FusionBody only = onlyBody(name, state);
            return only == null ? null : only.resolver().getLayers(only.state());
        }
        return graft.bodyResolver.getLayers(graft.body.state());
    }

    /**
     * Si la especie de la cabeza no tiene modelo en este cliente (en Cobblemon 1.8.1 Groudon, Kyogre, Raikou... solo
     * traen datos) pero el cuerpo sí, la fusión se pinta como el cuerpo, con sus colores: no hay textura de la
     * cabeza con la que recolorearlo, y es mejor que el muñeco sustituto. En cualquier modo de ver las fusiones.
     * (Al revés, cuerpo sin modelo, ya funciona: el modelo de la cabeza con sus propios colores.)
     */
    private static FusionBody onlyBody(ResourceLocation name, PosableState state) {
        FusionBody body = FusionBody.of(state.getCurrentAspects());
        if (body == null || VaryingModelRepository.INSTANCE.getVariations().containsKey(name)) {
            return null;
        }
        return body.resolver() == null ? null : body;
    }

    /**
     * El graft de una fusión, o null para pintarla en modo colores. Analizar los modelos son muchas reglas sobre
     * modelos de cualquier mod: si algo falla con una pareja, se avisa una vez en el log y esa pareja se queda en modo
     * colores, en vez de lanzar el error en cada fotograma (se llama desde el render: sería un crash del juego).
     */
    private static Graft graft(ResourceLocation name, PosableState state, boolean evenIfDisabled) {
        if (!enabled && !evenIfDisabled) {
            return null;
        }
        Set<String> aspects = state.getCurrentAspects();
        CachedGraft cached = GRAFTS.get(state);
        if (cached != null && cached.version() == graftVersion && cached.name().equals(name)
                && cached.aspects().equals(aspects)) {
            return cached.graft();
        }
        Graft graft = null;
        if (FAILED.isEmpty() || !FAILED.contains(failureKey(name, state))) {
            try {
                graft = buildGraft(name, state);
            } catch (RuntimeException e) {
                String key = failureKey(name, state);
                FAILED.add(key);
                Fusionmon.LOGGER.warn("No se pudo montar la fusión {}: se pinta solo con colores", key, e);
            }
        }
        // También se guarda "sin graft" (null): no es fusión, no se puede o ha fallado
        GRAFTS.put(state, new CachedGraft(name, Set.copyOf(aspects), graftVersion, graft));
        return graft;
    }

    /** Al recargar recursos: los modelos son otros, así que se vuelve a intentar con las parejas que fallaron. */
    public static void clearFailures() {
        FAILED.clear();
        // Los modelos son otros: los montajes guardados apuntan a los de antes
        graftVersion++;
    }

    /** Cabeza + aspects (en ellos van la especie y los aspects del cuerpo): identifica la pareja que ha fallado. */
    private static String failureKey(ResourceLocation name, PosableState state) {
        return name + " " + new TreeSet<>(state.getCurrentAspects());
    }

    private static Graft buildGraft(ResourceLocation name, PosableState state) {
        FusionBody body = FusionBody.of(state.getCurrentAspects());
        if (body == null) {
            return null;
        }
        VaryingRenderableResolver headResolver = VaryingModelRepository.INSTANCE.getVariations().get(name);
        VaryingRenderableResolver bodyResolver = body.resolver();
        if (headResolver == null || bodyResolver == null || headResolver == bodyResolver) {
            return null;
        }
        PosableModel headModel = headResolver.getPoser(state);
        // El modelo del cuerpo, con los aspects del CUERPO (como su textura): con los de la fusión (los de la cabeza)
        // una Pyroar hembra con cabeza de macho salía con el modelo de macho y la textura de hembra, que tiene otro
        // reparto: partes del cuerpo caían en zonas vacías de la textura y se veían transparentes
        PosableModel bodyModel = bodyResolver.getPoser(body.state());
        if (headModel == bodyModel) {
            return null;
        }
        ModelHeads heads = HEAD_BONES.computeIfAbsent(headModel, FusionGraft::findHeads);
        ModelHeads bodyHeads = HEAD_BONES.computeIfAbsent(bodyModel, FusionGraft::findHeads);
        // Normas por familia (cuerpo-cabeza = whole: Voltorb, Gengar... / normal):
        //  - cuerpo normal: se cambia su cabeza por la de la otra especie (o por su modelo entero si es cuerpo-cabeza)
        //  - cuerpo-cabeza: no se le quita nada. Se queda entero, con su cara, y lleva los complementos de la cabeza
        //    (orejas, cuernos, pelo: nunca su cara), su cola y sus alas, y sus brazos si él no tiene. Sin pegar otro
        //    modelo encima: sabes que son los dos Pokémon (Charizard + Voltorb, Pikachu + Gengar, Gengar + Voltorb)
        List<HeadBone> bodies = bodyHeads.whole() ? List.of() : bodyHeads.heads();
        HeadBone top = null;
        if (bodies.isEmpty()) {
            if (!tops || bodyHeads.top() == null) {
                return null;
            }
            top = bodyHeads.top();
        }
        // Una cabeza "todo cabeza" (Magikarp, Voltorb, Koffing...): se pega el modelo entero, como en Infinite Fusion
        boolean whole = heads.whole();
        HeadBone head = whole ? wholeModel(headModel) : heads.heads().get(0);
        if (head == null) {
            return null;
        }
        // Cola por cola si hay en los dos lados
        boolean swapTail = tails && heads.tail() != null && bodyHeads.tail() != null;
        List<Decoration> trunkDecorations = new ArrayList<>();
        List<Decoration> neckDecorations = new ArrayList<>();
        // Un cuerpo-cabeza hace de tronco entero (ver ballSpace), también en la especie de la cabeza (los brazos de
        // Gengar salen de él)
        HeadBone headTop = heads.trunk() != null && heads.spineEnd() != null ? null : heads.top();
        boolean trunks = (headTop != null || heads.trunk() != null && heads.spineEnd() != null)
                && (top != null || bodyHeads.trunk() != null && bodyHeads.spineEnd() != null);
        // Varias cabezas (Dodrio, Doduo, Scovillain...): van todas igual, cada una con su cuello, donde empezaba el
        // cuello del cuerpo, que se quita. Si el cuerpo ya tiene varias cabezas, cada una lleva la principal: más
        // serían demasiadas
        boolean allHeads = decorations && !whole && bodies.size() == 1 && !heads.chains().isEmpty()
                && bodyHeads.spineEnd() != null;
        // Un racimo de cabezas (Exeggutor) va entero con la principal, con la misma regla
        List<HeadBone> companions = decorations && !whole && bodies.size() <= 1 ? heads.companions() : List.of();
        // Al pegar un modelo entero, sus adornos ya van con él
        if (!whole) {
            if (decorations) {
                for (Decoration decoration : heads.decorations()) {
                    // Los brazos van aparte (abajo): solo a un cuerpo-cabeza sin brazos
                    if (decoration.category.equals(ARM)) {
                        continue;
                    }
                    // Sin una cabeza pegada no hay con qué llevar los del cuello (los cuellos van con sus cabezas)
                    if (decoration.neck && allHeads) {
                        continue;
                    }
                    (decoration.neck ? neckDecorations : trunkDecorations).add(decoration);
                }
            }
            // Si el cuerpo no tiene cola no hay una donde engancharla: se coloca sobre su tronco como un adorno
            if (tails && heads.tail() != null && bodyHeads.tail() == null) {
                for (HeadBone root : heads.tail().roots()) {
                    trunkDecorations.add(new Decoration(TAIL, root, false));
                }
            }
        }
        // Un cuerpo-cabeza sin brazos lleva los de la especie de la cabeza (Charizard + Voltorb, Gengar + Voltorb).
        // Uno con brazos (Gengar) conserva los suyos, como un cuerpo normal
        if (decorations && top != null && bodyHeads.arms().isEmpty()) {
            for (HeadBone arm : heads.arms()) {
                trunkDecorations.add(new Decoration(ARM, arm, false));
            }
        }
        // Sin tronco en algún lado no hay dónde colocarlos
        if (!trunks) {
            trunkDecorations.clear();
        }

        List<ModelPart> hidden = new ArrayList<>();
        for (HeadBone bodyHead : bodies) {
            hidden.add(bodyHead.part);
        }
        if (allHeads && bodyHeads.spineEnd().part != bodies.get(0).part) {
            // El cuello del cuerpo (con su cabeza): las cabezas nuevas traen el suyo
            hidden.add(bodyHeads.spineEnd().part);
        }
        if (swapTail) {
            for (HeadBone root : bodyHeads.tail().roots()) {
                hidden.add(root.part);
            }
        }
        // Alas por alas, aletas por aletas...: el cuerpo pierde sus adornos de las clases que trae la cabeza
        Set<String> replaced = new HashSet<>();
        for (Decoration decoration : trunkDecorations) {
            replaced.add(decoration.category);
        }
        for (Decoration decoration : neckDecorations) {
            replaced.add(decoration.category);
        }
        for (Decoration decoration : bodyHeads.decorations()) {
            if (replaced.contains(decoration.category)) {
                hidden.add(decoration.bone.part);
            }
        }
        return new Graft(body, headResolver, bodyResolver, headModel, bodyModel, head, whole, heads.limbs(), bodies,
                swapTail ? heads.tail() : null, swapTail ? bodyHeads.tail() : null, trunkDecorations,
                neckDecorations, heads.trunk(), bodyHeads.trunk(), heads.spineEnd(), bodyHeads.spineEnd(), hidden,
                allHeads ? heads.chains() : List.of(), companions, top, headTop);
    }

    /** El modelo entero como si fuera una cabeza (su raíz, con todos sus huesos). */
    private static HeadBone wholeModel(PosableModel model) {
        return (Object) model.getRootPart() instanceof ModelPart root ? new HeadBone(root, List.of(root)) : null;
    }

    /**
     * Textura original de la cabeza (getResolvedTexture no pasa por VaryingRenderableResolverMixin, así que no
     * sale recoloreada). Una fusión se ve shiny si lo es cualquiera de las dos partes.
     */
    private static ResourceLocation headTexture(Graft graft, PosableState state) {
        Set<String> aspects = new HashSet<>(state.getCurrentAspects());
        if (graft.body.aspects().contains(SHINY)) {
            aspects.add(SHINY);
        }
        if (textureState == null) {
            textureState = new FloatingState();
        }
        textureState.setCurrentAspects(aspects);
        return graft.headResolver.getResolvedTexture(textureState);
    }

    // ---- 2. Pintar (desde PosableModelMixin) ----

    /** Antes de pintar el modelo del cuerpo: ocultar sus cabezas (y su cola y adornos sustituidos). */
    public static void beforeRender(PosableModel model) {
        // Si el pintado anterior se cortó a medias (un error antes de afterRender), sus piezas siguen ocultas: el
        // modelo del cuerpo es el de la especie, así que TODOS los de esa especie saldrían sin cabeza hasta reiniciar
        if (active != null) {
            restoreVisible(active.hidden, hiddenWereVisible);
        }
        active = null;
        if (inspectView != null) {
            beforeInspect(model);
            return;
        }
        PosableState state = model.getCurrentState();
        if (!enabled || state == null) {
            return;
        }
        ResourceLocation headName = HEADS.get(state);
        Graft graft = headName == null ? null : graft(headName, state, false);
        // El mismo estado puede pintar otros modelos (p. ej. el sustituto): solo tocamos el del cuerpo
        if (graft == null || graft.bodyModel != model) {
            return;
        }

        hiddenWereVisible.clear();
        setVisible(graft.hidden, hiddenWereVisible, false);
        active = graft;
    }

    /** Después: volver a mostrar lo oculto y pintar en su sitio la cabeza (cola, adornos) de la otra especie. */
    public static void afterRender(PosableModel model, PoseStack poseStack, int light, int overlay, int color) {
        if (inspectView != null && inspected == model) {
            afterInspect(model, poseStack, light, overlay);
            return;
        }
        Graft graft = active;
        if (graft == null || graft.bodyModel != model) {
            return;
        }
        active = null;
        restoreVisible(graft.hidden, hiddenWereVisible);

        MultiBufferSource buffers = model.getBufferProvider();
        PosableState state = model.getCurrentState();
        if (buffers == null || state == null) {
            return;
        }

        try {
            renderHeads(model, graft, state, buffers, poseStack, light, overlay, color);
        } catch (RuntimeException e) {
            // Es un prototipo: si algo falla con una pareja, mejor una fusión sin cabeza que un crash
            if (WARNED.add(graft.headModel)) {
                Fusionmon.LOGGER.warn("No se pudo pintar la cabeza pegada de una fusión", e);
            }
        }
    }

    // ---- Inspector de especies (SpeciesInspectorScreen) ----
    // Enseña lo que detectan estas reglas en UNA especie, con el mismo código que usan las fusiones: así lo que se
    // revisa en el inspector es exactamente lo que hará el juego

    /** Qué enseña el inspector de una especie. */
    public enum InspectView {
        /** El modelo entero, con lo que pasa de un Pokémon a otro teñido: adornos, brazos y cola. */
        PARTS,
        /** Solo lo que se pega cuando la especie es la CABEZA (con lo que va con ella). */
        HEAD,
        /** Lo que queda cuando es el CUERPO: sin sus cabezas, con el sitio donde va la nueva y el tronco. */
        BODY
    }

    /** Tintes (ARGB) del inspector para cada clase de pieza, y colores de sus marcadores. */
    public static final int INSPECT_TRUNK_DECOR = 0xFF40E0FF;
    public static final int INSPECT_NECK_DECOR = 0xFFFF55FF;
    public static final int INSPECT_ARM = 0xFF88FF55;
    public static final int INSPECT_TAIL = 0xFFFFA020;
    public static final int INSPECT_PIVOT = 0xFFFFFF00;
    public static final int INSPECT_BASE = 0xFF00FF80;
    public static final int INSPECT_SKULL = 0xFFFF3030;
    public static final int INSPECT_TRUNK = 0xFF3080FF;
    public static final int INSPECT_SPINE = 0xFFFFFFFF;

    /**
     * Una caja que el inspector dibuja encima del modelo, con líneas que se ven a través de él.
     *
     * @param pose transformación desde la pantalla hasta el marco de la caja (la del momento en que se pintó)
     * @param box  la caja en ese marco, en bloques
     */
    public record Marker(Matrix4f pose, float[] box, int color) {
    }

    /**
     * Lo que detectan las reglas en un modelo, en texto, para el inspector.
     *
     * @param kind   "normal", "whole" (como cabeza se pega entero), "headless" (sin cabeza: como cuerpo, la cabeza va
     *               encima), "chains" (varias cabezas con cuello) o "cluster" (racimo de cabezas)
     * @param attach dónde está el pivote de la cabeza (lo que se pega en el cuello del cuerpo) respecto a su cráneo:
     *               "base" (en el cuello), "center" (en medio de la cabeza) o "far" (fuera); "" sin cabeza
     * @param top    sin cabeza: el hueso sobre el que se pone la cabeza nueva (ver findTop)
     */
    public record Anatomy(String kind, String head, String attach, String skull, String trunk, String spine,
                          String tail, List<String> decorations, List<String> neckDecorations, String top) {
    }

    private static InspectView inspectView;
    private static ResourceLocation inspectTexture;
    /** El modelo que se inspecciona: el primero que se pinta con la inspección en marcha. */
    private static PosableModel inspected;
    private static final List<Marker> markers = new ArrayList<>();
    private static final List<ModelPart> inspectHidden = new ArrayList<>();
    private static final List<Boolean> inspectWereVisible = new ArrayList<>();

    /** Lo próximo que se pinte se pinta como en el inspector (ver InspectView), con esa textura para lo teñido. */
    public static void startInspection(InspectView view, ResourceLocation texture) {
        inspectView = view;
        inspectTexture = texture;
        inspected = null;
        markers.clear();
    }

    /** Termina la inspección y devuelve los marcadores que hay que dibujar encima del modelo. */
    public static List<Marker> stopInspection() {
        inspectView = null;
        inspectTexture = null;
        inspected = null;
        List<Marker> result = List.copyOf(markers);
        markers.clear();
        return result;
    }

    /** Lo detectado en un modelo, en texto (ver Anatomy). */
    public static Anatomy describe(PosableModel model) {
        ModelHeads heads = HEAD_BONES.computeIfAbsent(model, FusionGraft::findHeads);
        List<String> decorations = new ArrayList<>();
        List<String> neck = new ArrayList<>();
        for (Decoration decoration : heads.decorations()) {
            (decoration.neck ? neck : decorations).add(name(decoration.bone) + " [" + decoration.category + "]");
        }
        String tail = "";
        if (heads.tail() != null) {
            tail = String.join(", ", heads.tail().roots().stream().map(FusionGraft::name).toList());
        }
        if (heads.heads().isEmpty()) {
            return new Anatomy("headless", "", "", "", "", "", tail, decorations, neck,
                    heads.top() == null ? "" : name(heads.top()));
        }
        HeadBone head = heads.heads().get(0);
        String kind = heads.whole() ? "whole" : !heads.chains().isEmpty() ? "chains"
                : !heads.companions().isEmpty() ? "cluster" : "normal";
        String headName = name(head) + (heads.heads().size() > 1 ? " (+" + (heads.heads().size() - 1) + ")" : "");
        Skull skull = skull(head.part, Map.of());
        String skullName = skull == null ? "" : skull.path.isEmpty() ? name(head)
                : skull.path.substring(skull.path.lastIndexOf('/') + 1);
        return new Anatomy(kind, headName, attach(skull), skullName,
                heads.trunk() == null ? "" : name(heads.trunk()), heads.spineEnd() == null ? "" : name(heads.spineEnd()),
                tail, decorations, neck, "");
    }

    /** Dónde está el pivote de una cabeza (el origen de su marco) respecto a su cráneo (ver Anatomy.attach). */
    private static String attach(Skull skull) {
        if (skull == null) {
            return "";
        }
        float[] box = skull.box;
        boolean inner = true;
        float distance2 = 0;
        for (int axis = 0; axis < 3; axis++) {
            float size = box[axis + 3] - box[axis];
            // En medio = en la mitad central del cráneo en los tres ejes
            if (size <= 0 || -box[axis] / size < 0.25F || -box[axis] / size > 0.75F) {
                inner = false;
            }
            float outside = Math.max(0, Math.max(box[axis], -box[axis + 3]));
            distance2 += outside * outside;
        }
        if (inner) {
            return "center";
        }
        return Math.sqrt(distance2) > meanSide(box) ? "far" : "base";
    }

    /** Nombre del hueso (el último del camino) tal como cuelga de su padre. */
    private static String name(HeadBone bone) {
        int size = bone.path.size();
        return size < 2 ? "root" : boneName(bone.path.get(size - 2), bone.part);
    }

    /** Antes de pintar el modelo inspeccionado: ocultar lo que se pintará después teñido (o todo, si es "cabeza"). */
    private static void beforeInspect(PosableModel model) {
        if (inspected != null || !((Object) model.getRootPart() instanceof ModelPart root)) {
            return;
        }
        ModelHeads heads = HEAD_BONES.computeIfAbsent(model, FusionGraft::findHeads);
        inspectHidden.clear();
        inspectWereVisible.clear();
        if (inspectView == InspectView.HEAD) {
            inspectHidden.add(root);
        } else {
            if (inspectView == InspectView.BODY) {
                for (HeadBone head : heads.heads()) {
                    inspectHidden.add(head.part);
                }
            }
            for (Decoration decoration : heads.decorations()) {
                inspectHidden.add(decoration.bone.part);
            }
            if (heads.tail() != null) {
                for (HeadBone tailRoot : heads.tail().roots()) {
                    inspectHidden.add(tailRoot.part);
                }
            }
        }
        setVisible(inspectHidden, inspectWereVisible, false);
        inspected = model;
    }

    /** Después: volver a enseñarlo, pintar lo teñido y apuntar los marcadores. */
    private static void afterInspect(PosableModel model, PoseStack poseStack, int light, int overlay) {
        restoreVisible(inspectHidden, inspectWereVisible);
        ModelHeads heads = HEAD_BONES.get(model);
        MultiBufferSource buffers = model.getBufferProvider();
        if (heads == null || buffers == null || inspectTexture == null
                || !((Object) model.getRootPart() instanceof ModelPart root)) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(inspectTexture));
        // La pila está como al empezar a pintar el modelo, antes de su raíz (como en renderPieces)
        Matrix4f base = new Matrix4f(poseStack.last().pose());
        try {
            if (inspectView == InspectView.HEAD) {
                inspectHead(heads, root, base, consumer, poseStack, light, overlay);
            } else {
                inspectParts(heads, base, consumer, poseStack, light, overlay);
            }
        } catch (RuntimeException e) {
            if (WARNED.add(model)) {
                Fusionmon.LOGGER.warn("No se pudo pintar la inspección de un modelo", e);
            }
        }
    }

    /** Vista "cabeza": lo que se pega, como se pega (el modelo entero sin patas, o la cabeza con lo que va con ella). */
    private static void inspectHead(ModelHeads heads, ModelPart root, Matrix4f base, VertexConsumer consumer,
                                    PoseStack poseStack, int light, int overlay) {
        if (heads.heads().isEmpty() || heads.whole()) {
            List<Boolean> were = new ArrayList<>();
            setVisible(heads.limbs().keySet(), were, false);
            try {
                root.render(poseStack, consumer, light, overlay, -1);
            } finally {
                restoreVisible(heads.limbs().keySet(), were);
            }
            Skull skull = skull(root, heads.limbs());
            if (skull != null) {
                markers.add(new Marker(new Matrix4f(base).mul(matrixAlong(List.of(root))), skull.box, INSPECT_SKULL));
            }
            return;
        }
        HeadBone head = heads.heads().get(0);
        renderInPlace(head, consumer, poseStack, light, overlay, -1);
        for (HeadBone companion : heads.companions()) {
            renderInPlace(companion, consumer, poseStack, light, overlay, -1);
        }
        for (Decoration decoration : heads.decorations()) {
            if (decoration.neck) {
                renderInPlace(decoration.bone, consumer, poseStack, light, overlay, INSPECT_NECK_DECOR);
            }
        }
        markHead(base, head);
    }

    /** Vistas "partes" y "cuerpo": adornos, brazos y cola teñidos; cráneo, punto de pegado y tronco marcados. */
    private static void inspectParts(ModelHeads heads, Matrix4f base, VertexConsumer consumer, PoseStack poseStack,
                                     int light, int overlay) {
        for (Decoration decoration : heads.decorations()) {
            int tint = decoration.neck ? INSPECT_NECK_DECOR
                    : ARM.equals(decoration.category) ? INSPECT_ARM : INSPECT_TRUNK_DECOR;
            renderInPlace(decoration.bone, consumer, poseStack, light, overlay, tint);
        }
        if (heads.tail() != null) {
            for (HeadBone tailRoot : heads.tail().roots()) {
                renderInPlace(tailRoot, consumer, poseStack, light, overlay, INSPECT_TAIL);
            }
            // Dónde se engancha: la cola de la otra especie se pega en el pivote de la principal (ver renderTail)
            HeadBone anchor = heads.tail().anchor();
            markers.add(new Marker(new Matrix4f(base).mul(matrixAlong(anchor.path)),
                    point(Math.max(0.015F, length(anchor.part) * 0.1F)), INSPECT_TAIL));
        }
        if (heads.heads().isEmpty()) {
            // Sin cabeza: el núcleo sobre el que se ponen las cosas (ver coreBox)
            if (heads.top() != null) {
                markers.add(new Marker(new Matrix4f(base).mul(matrixAlong(heads.top().path)), coreBox(heads.top().part),
                        INSPECT_TRUNK));
            }
            return;
        }
        for (HeadBone head : heads.heads()) {
            markHead(base, head);
        }
        if (heads.trunk() != null && heads.spineEnd() != null) {
            TrunkSpace space = trunkSpace(heads.trunk(), heads.spineEnd());
            if (space.box()[0] <= space.box()[3]) {
                // La caja del tronco está en los ejes de la columna, con el origen en el pivote del tronco
                Matrix4f pose = new Matrix4f(base).translate(space.origin()).mul(new Matrix4f(space.frame()));
                markers.add(new Marker(pose, space.box(), INSPECT_TRUNK));
            }
            markers.add(new Marker(new Matrix4f(base).mul(matrixAlong(heads.spineEnd().path)), point(0.02F),
                    INSPECT_SPINE));
        }
    }

    /** Marcadores de una cabeza: su cráneo y su pivote (el punto que se pega donde estaba la cabeza del cuerpo). */
    private static void markHead(Matrix4f base, HeadBone head) {
        Matrix4f pose = new Matrix4f(base).mul(matrixAlong(head.path));
        Skull skull = skull(head.part, Map.of());
        if (skull != null) {
            markers.add(new Marker(pose, skull.box, INSPECT_SKULL));
        }
        float half = skull == null ? 0.02F : meanSide(skull.box) * 0.12F;
        markers.add(new Marker(pose, point(half), INSPECT_PIVOT));
        // Y el de "Pegar: base" (ver attachPoint)
        Vector3f attach = attachPoint(head);
        if (attach != null) {
            markers.add(new Marker(new Matrix4f(pose).translate(attach), point(half * 0.8F), INSPECT_BASE));
        }
    }

    /** Cajita alrededor del origen, para marcar un punto. */
    private static float[] point(float half) {
        return new float[]{-half, -half, -half, half, half, half};
    }

    /** Pinta un hueso (con lo que cuelga) en su sitio del modelo, con un tinte. */
    private static void renderInPlace(HeadBone bone, VertexConsumer consumer, PoseStack poseStack, int light,
                                      int overlay, int color) {
        poseStack.pushPose();
        try {
            for (ModelPart part : parentPath(bone)) {
                part.translateAndRotate(poseStack);
            }
            bone.part.render(poseStack, consumer, light, overlay, color);
        } finally {
            poseStack.popPose();
        }
    }

    /** Una pasada de pintado de las piezas pegadas: con qué textura y tipo de render, y con qué color. */
    private record Paint(RenderType type, int color) {
    }

    private static void renderHeads(PosableModel model, Graft graft, PosableState state, MultiBufferSource buffers,
                                    PoseStack poseStack, int light, int overlay, int color) {
        // El modelo de la cabeza es compartido con los demás Pokémon de su especie: lo ponemos en la postura de
        // ESTA cabeza justo antes de pintarla (como hace Cobblemon con cada Pokémon).
        // Sus animaciones leen el contexto de render, que solo tiene si ya se ha pintado alguna vez por sí mismo:
        // le prestamos el del cuerpo, que se está pintando ahora
        graft.headModel.setContext(model.getContext());
        FloatingState headState = animateHead(graft, state);

        // Como Cobblemon: entityCutout descarta la cara de atrás. Sin eso, los planos de grosor cero (orejas,
        // bocas...) pintan sus dos caras en el mismo sitio y parpadean (z-fighting)
        List<Paint> paints = new ArrayList<>();
        paints.add(new Paint(RenderType.entityCutout(headTexture(graft, state)), color));
        // Y las capas de la especie de la cabeza (la llama de Charmander, ojos que brillan...): como hace Cobblemon
        // en PosableModel.render, se vuelve a pintar todo con la textura y el tipo de render de cada capa.
        // textureState ya lleva los aspects de la cabeza (headTexture), con el shiny del cuerpo si hace falta
        for (ModelLayer layer : graft.headResolver.getLayers(textureState)) {
            RenderType type = layerType(graft.headModel, layer, headState);
            if (type != null) {
                paints.add(new Paint(type, tinted(color, layer.getTint())));
            }
        }
        // Una pasada detrás de otra: pedir el búfer de otro tipo de render puede cerrar el anterior
        for (Paint paint : paints) {
            renderPieces(graft, buffers.getBuffer(paint.type), poseStack, light, overlay, paint.color);
        }
    }

    /** Tipo de render de una capa, como lo elige Cobblemon (sin la interpolación entre fotogramas); null si no se pinta. */
    private static RenderType layerType(PosableModel model, ModelLayer layer, PosableState state) {
        if (!layer.getEnabled() || layer.getTexture() == null) {
            return null;
        }
        // Las texturas animadas (la llama) eligen el fotograma con el tiempo del estado
        ResourceLocation texture = layer.getTexture().invoke(state);
        if (texture == null) {
            return null;
        }
        if (layer.getScrolling() != null) {
            return model.getScrollingLayer(texture, layer.getScrolling(), layer);
        }
        return model.getLayer(texture, layer.getEmissive(), layer.getTranslucent(), layer.getTranslucent_cull());
    }

    /** El color con el tinte de una capa (componentes de 0 a 1) aplicado. */
    private static int tinted(int color, Vector4f tint) {
        if (tint == null) {
            return color;
        }
        return FastColor.ARGB32.color(
                (int) (FastColor.ARGB32.alpha(color) * tint.w),
                (int) (FastColor.ARGB32.red(color) * tint.x),
                (int) (FastColor.ARGB32.green(color) * tint.y),
                (int) (FastColor.ARGB32.blue(color) * tint.z));
    }

    /** Pinta todo lo que se pega (cabeza, cola, adornos) con una textura. */
    private static void renderPieces(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light,
                                     int overlay, int color) {
        ModelPart head = graft.head.part;
        // Orientación de la cabeza en su propio modelo, ya animado (respecto a la raíz)
        Quaternionf headRotation = rotationAlong(graft.head.path);
        // Para los adornos del cuello: del marco de la cabeza en su modelo al de cada uno (antes de que se le quiten
        // a la cabeza su posición y giro para pintarla). También para las demás cabezas del racimo de Exeggutor
        Matrix4f toHead = graft.neckDecorations.isEmpty() && graft.companions.isEmpty() ? null
                : matrixAlong(graft.head.path).invert();

        // Con varias cabezas se pegan todas con sus cuellos (renderChains), no una sola en el sitio de la del cuerpo
        // Un cuerpo con racimo de cabezas (Exeggutor): cada cabeza pegada sustituye a una de las suyas, cráneo por
        // cráneo (ver skullOffset), del tamaño CLUSTER_BODY_SCALE de ella
        ModelHeads bodyHeads = HEAD_BONES.get(graft.bodyModel);
        boolean clusterBody = bodyHeads != null && !bodyHeads.companions().isEmpty();
        float bodyCluster = clusterBody ? CLUSTER_BODY_SCALE : 1F;
        for (HeadBone bodyHead : graft.chains.isEmpty() ? graft.bodies : List.<HeadBone>of()) {
            float scale = Math.clamp(soften(bodyCluster * size(bodyHead.part) / pastedSize(graft)), MIN_SCALE, MAX_SCALE)
                    * INFLATE;
            // Quitamos el giro que traía la cabeza del cuerpo y ponemos el de la cabeza en su modelo:
            // así mira hacia donde mira en su modelo (y hacia el jugador, con su animación de mirar)
            Quaternionf correction = rotationAlong(bodyHead.path).conjugate().mul(headRotation);

            poseStack.pushPose();
            try {
                // Mismas transformaciones que ha recibido la cabeza del cuerpo: raíz → ... → cuello → cabeza
                for (ModelPart part : bodyHead.path) {
                    part.translateAndRotate(poseStack);
                }
                poseStack.mulPose(correction);
                if (align == Align.BASE && !graft.whole && !clusterBody) {
                    // Base con base: el punto donde el cuello entra en el cráneo de lo que se pega, en el de la cabeza
                    // que sustituye (ver attachPoint), estén donde estén sus pivotes
                    Vector3f target = attachPoint(bodyHead);
                    Vector3f own = attachPoint(graft.head);
                    if (target != null && own != null) {
                        Vector3f offset = new Quaternionf(correction).conjugate().transform(target).sub(own.mul(scale));
                        poseStack.translate(offset.x, offset.y, offset.z);
                    }
                } else if (graft.whole || clusterBody || align == Align.SKULL) {
                    // Reemplazo cráneo por cráneo: el centro del cubo principal de lo que se pega, en el centro del
                    // de la cabeza que sustituye (los detalles no cuentan). Un modelo entero no tiene un "cuello"
                    // que poner en el pivote (el suyo suele estar en el suelo), y apoyado por su punto más bajo
                    // Solrock quedaba flotando o hundido según sus rayos. En Exeggutor el pivote de cada cabeza está
                    // en su centro: la cabeza nueva, que lo tiene en el cuello, quedaba media metida en el tronco
                    Vector3f offset = skullOffset(bodyHead.part, correction, head, graft.limbs, scale);
                    if (offset == null) {
                        offset = graft.whole ? groundOffset(bodyHead.part, correction, head, graft.limbs, scale)
                                : new Vector3f();
                    }
                    poseStack.translate(offset.x, offset.y, offset.z);
                }
                poseStack.scale(scale, scale, scale);
                renderHead(graft, toHead, consumer, poseStack, light, overlay, color);
            } finally {
                poseStack.popPose();
            }
        }
        if (graft.top != null) {
            // Un cuerpo-cabeza se queda entero: solo se le ponen los complementos de la cabeza (orejas, pelo...), sea
            // la cabeza normal o también un cuerpo-cabeza (Gengar + Voltorb: Voltorb con las orejas de Gengar)
            renderOnBody(graft, consumer, poseStack, light, overlay, color);
        }

        if (graft.headTail != null) {
            renderTail(graft, consumer, poseStack, light, overlay, color);
        }
        if (!graft.trunkDecorations.isEmpty()) {
            renderDecorations(graft, consumer, poseStack, light, overlay, color);
        }
        if (!graft.chains.isEmpty()) {
            renderChains(graft, consumer, poseStack, light, overlay, color);
        }
    }

    /**
     * Varias cabezas (Dodrio, Doduo, Scovillain...): todas igual, cada una con su cuello, donde empezaba el cuello del
     * cuerpo (o donde estaba su cabeza, si no tiene cuello: Gyarados). El centro de las bases de los cuellos va en ese
     * punto; cada uno a su distancia del centro, con su giro respecto al Pokémon entero, como en su modelo. Se escalan
     * como el cuerpo de su especie respecto al del cuerpo (trunkScale) o, si falta algún tronco, como las cabezas.
     */
    private static void renderChains(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light, int overlay,
                                     int color) {
        float scale = trunkScale(graft);
        if (scale <= 0) {
            scale = Math.clamp(soften(size(graft.bodies.get(0).part) / size(graft.head.part)), MIN_SCALE, MAX_SCALE);
        }
        Vector3f anchor = matrixAlong(graft.bodySpine.path).getTranslation(new Vector3f());
        List<Matrix4f> matrices = new ArrayList<>();
        Vector3f center = new Vector3f();
        for (HeadBone chain : graft.chains) {
            Matrix4f matrix = matrixAlong(chain.path);
            matrices.add(matrix);
            center.add(matrix.getTranslation(new Vector3f()));
        }
        center.div(graft.chains.size());

        for (int i = 0; i < graft.chains.size(); i++) {
            ModelPart part = graft.chains.get(i).part;
            Matrix4f matrix = matrices.get(i);
            Vector3f offset = matrix.getTranslation(new Vector3f()).sub(center).mul(scale);
            PartPose saved = part.storePose();
            poseStack.pushPose();
            try {
                poseStack.translate(anchor.x + offset.x, anchor.y + offset.y, anchor.z + offset.z);
                poseStack.mulPose(matrix.getNormalizedRotation(new Quaternionf()));
                poseStack.scale(scale, scale, scale);
                // Su posición y giro ya van arriba
                part.setPos(0, 0, 0);
                part.setRotation(0, 0, 0);
                part.render(poseStack, consumer, light, overlay, color);
            } finally {
                part.loadPose(saved);
                poseStack.popPose();
            }
        }
    }

    /**
     * Pinta la cabeza pegada (la pila ya trae su sitio, giro y escala) con lo que va con ella: los adornos del cuello
     * y las demás cabezas del racimo de Exeggutor.
     */
    private static void renderHead(Graft graft, Matrix4f toHead, VertexConsumer consumer, PoseStack poseStack,
                                   int light, int overlay, int color) {
        ModelPart head = graft.head.part;
        PartPose saved = head.storePose();
        boolean visible = head.visible;
        List<Boolean> limbsWereVisible = new ArrayList<>();
        try {
            // La cabeza nueva sin su posición ni giro propios (ya van en la pila)
            head.setPos(0, 0, 0);
            head.setRotation(0, 0, 0);
            head.visible = true;
            setVisible(graft.limbs.keySet(), limbsWereVisible, false);
            head.render(poseStack, consumer, light, overlay, color);

            // Los adornos del cuello van con la cabeza: misma posición y giro respecto a ella que en su modelo
            if (toHead != null) {
                for (Decoration decoration : graft.neckDecorations) {
                    renderBeside(decoration.bone, toHead, poseStack, consumer, light, overlay, color);
                }
                // Las demás cabezas del racimo, igual: donde están respecto a la principal en su modelo y del mismo
                // tamaño que ella (como Dodrio no: con su escala de tronco las de Exeggutor salían diminutas,
                // porque su tronco es enorme)
                for (HeadBone companion : graft.companions) {
                    poseStack.pushPose();
                    // Algo más separadas que en su modelo: en Exeggutor están medio metidas unas en otras y, ya
                    // pequeñas, parecían una sola
                    Vector3f offset = new Matrix4f(toHead).mul(matrixAlong(companion.path))
                            .getTranslation(new Vector3f()).mul(CLUSTER_SPREAD - 1);
                    poseStack.translate(offset.x, offset.y, offset.z);
                    renderBeside(companion, toHead, poseStack, consumer, light, overlay, color);
                    poseStack.popPose();
                }
            }
        } finally {
            // Pase lo que pase, la cabeza queda como estaba
            restoreVisible(graft.limbs.keySet(), limbsWereVisible);
            head.loadPose(saved);
            head.visible = visible;
        }
    }

    /**
     * El cráneo de una cabeza (el primer hueso con cubos propios bajando desde ella: "head_angle" en Pikachu, la propia
     * "head" en Venusaur) y sus complementos: lo que cuelga de ella hasta el cráneo y no es la cara (ver findExtras).
     */
    private record HeadExtras(HeadBone skull, List<HeadBone> accessories) {
    }

    /**
     * Piezas de la cara que no se ponen en un cuerpo sin cabeza (el cuerpo conserva la suya): ojos, boca, mandíbula...
     * ya son anatomía (ver category); estas no lo son para los adornos, pero también son cara.
     */
    private static final Set<String> FACE_PARTS = Set.of("muzzle", "snout", "nose", "nostril", "beak", "teeth",
            "tooth", "fang", "lip", "brow", "eyebrow", "eyelid", "pupil", "blush", "cheek", "whisker", "chin",
            "eyeshine", "emote", "overlay");

    private static HeadExtras findExtras(HeadBone head) {
        // Bajamos por los grupos sin cubos ("head_ai", "head_correction"...) hasta el cráneo
        List<ModelPart> path = new ArrayList<>(head.path);
        while (!hasOwnCubes(path.getLast())) {
            ModelPart next = null;
            for (Bone child : ((Bone) (Object) path.getLast()).getChildren().values()) {
                if ((Object) child instanceof ModelPart part && cubes(part) > 0
                        && (next == null || cubes(part) > cubes(next))) {
                    next = part;
                }
            }
            if (next == null) {
                break;
            }
            path.add(next);
        }
        List<HeadBone> accessories = new ArrayList<>();
        for (int i = head.path.size() - 1; i < path.size(); i++) {
            for (Map.Entry<String, Bone> child : ((Bone) (Object) path.get(i)).getChildren().entrySet()) {
                if (!((Object) child.getValue() instanceof ModelPart part) || path.contains(part) || cubes(part) == 0
                        || category(child.getKey()) == null || isFacePart(child.getKey())) {
                    continue;
                }
                List<ModelPart> piece = new ArrayList<>(path.subList(0, i + 1));
                piece.add(part);
                accessories.add(new HeadBone(part, piece));
            }
        }
        return new HeadExtras(new HeadBone(path.getLast(), path), accessories);
    }

    private static boolean isFacePart(String name) {
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            token = stripModifiers(withoutNumber(token));
            // También en plural, como en category: las cejas de Pidgey ("brows") se ponían como complemento y, con su
            // pivote lejos de sus cubos, salían flotando encima de los cuerpos sin cabeza
            String singular = token.length() > 3 && token.endsWith("s") ? token.substring(0, token.length() - 1) : token;
            if (FACE_PARTS.contains(token) || FACE_PARTS.contains(singular)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cuerpo sin cabeza (Voltorb, Lunatone...) con una cabeza normal: el cuerpo hace también de cabeza, con su cara,
     * y se le ponen los complementos de la cabeza (orejas, pelo, crin...) y los adornos de su cuello. Cada uno va al
     * mismo sitio proporcional: respecto al cráneo en su modelo y respecto al cuerpo entero aquí (las orejas de
     * Pikachu, encima de la Voltorb). Se escalan como el cráneo respecto al cuerpo. Los adornos del tronco y la cola
     * van aparte, como siempre (renderDecorations, con el cuerpo como tronco: ver ballSpace).
     */
    private static void renderOnBody(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light,
                                     int overlay, int color) {
        HeadExtras extras = EXTRAS.computeIfAbsent(graft.head.part, part -> findExtras(graft.head));
        List<HeadBone> pieces = new ArrayList<>(extras.accessories);
        for (Decoration decoration : graft.neckDecorations) {
            pieces.add(decoration.bone);
        }
        float[] skull = boxInModel(extras.skull);
        float[] body = coreBox(graft.top.part);
        if (pieces.isEmpty() || skull[0] > skull[3] || body[0] > body[3]) {
            return;
        }
        Vector3f pivot = matrixAlong(graft.top.path).getTranslation(new Vector3f());
        Quaternionf turn = withoutRoll(rotationAlong(graft.top.path));
        float scale = Math.clamp(ACCESSORY_SCALE * size(graft.top.part) / size(graft.head.part), MIN_PIECE_SCALE,
                MAX_PIECE_SCALE);

        for (HeadBone piece : pieces) {
            ModelPart part = piece.part;
            if (!part.visible) {
                continue;
            }
            Matrix4f matrix = matrixAlong(piece.path);
            Vector3f from = matrix.getTranslation(new Vector3f());
            // Del cráneo al cuerpo, sin salirse de él, y con el giro del cuerpo (menos el de rodar)
            Vector3f placed = turn.transform(new Vector3f(
                    remap(from.x, skull, body, 0, 0F, 1F),
                    remap(from.y, skull, body, 1, 0F, 1F),
                    remap(from.z, skull, body, 2, 0F, 1F))).add(pivot);
            PartPose saved = part.storePose();
            poseStack.pushPose();
            try {
                poseStack.translate(placed.x, placed.y, placed.z);
                poseStack.mulPose(new Quaternionf(turn).mul(matrix.getNormalizedRotation(new Quaternionf())));
                poseStack.scale(scale, scale, scale);
                // Su posición y giro ya van arriba
                part.setPos(0, 0, 0);
                part.setRotation(0, 0, 0);
                part.render(poseStack, consumer, light, overlay, color);
            } finally {
                part.loadPose(saved);
                poseStack.popPose();
            }
        }
    }

    /**
     * Un giro sin su parte alrededor del eje X (descomposición "swing-twist"): sin rodar hacia delante. Voltorb rueda
     * al andar y lo que lleva pegado daría vueltas con él; Snorunt se balancea de lado y lo pegado tiene que seguirlo.
     */
    private static Quaternionf withoutRoll(Quaternionf rotation) {
        Quaternionf twist = new Quaternionf(rotation.x, 0, 0, rotation.w);
        if (twist.lengthSquared() < 1e-8F) {
            // Girado media vuelta en otro eje: no hay parte de rodar que quitar
            return new Quaternionf(rotation);
        }
        twist.normalize();
        return new Quaternionf(rotation).mul(twist.conjugate());
    }

    /** Caja de los cubos propios de un hueso, en el modelo (con la postura de ahora). */
    private static float[] boxInModel(HeadBone bone) {
        float[] own = ownBox(bone.part);
        float[] box = emptyBox();
        if (own[0] > own[3]) {
            return box;
        }
        Matrix4f matrix = matrixAlong(bone.path);
        for (int corner = 0; corner < 8; corner++) {
            include(box, matrix.transformPosition(new Vector3f(
                    own[(corner & 1) == 0 ? 0 : 3],
                    own[(corner & 2) == 0 ? 1 : 4],
                    own[(corner & 4) == 0 ? 2 : 5])));
        }
        return box;
    }

    /**
     * Un cuerpo sin cabeza como tronco, para colocar adornos y cola (ver renderDecorations): su pieza principal entera,
     * en su sitio y con su giro menos el de rodar (ver withoutRoll), con los ejes de un cuadrúpedo: la columna hacia
     * delante (hacia la cara, -Z en los modelos), así la espalda queda arriba. La flor de Venusaur va encima de la
     * Voltorb, y la cola, detrás.
     */
    private static TrunkSpace ballSpace(HeadBone top) {
        Vector3f origin = matrixAlong(top.path).getTranslation(new Vector3f());
        Vector3f across = new Vector3f(1, 0, 0);
        Vector3f spine = new Vector3f(0, 0, -1);
        Matrix3f still = new Matrix3f(across, spine, new Vector3f(across).cross(spine));
        // La caja, en los ejes sin girar (está en el marco propio del hueso); los ejes, ya girados con el cuerpo
        Matrix3f toFrame = new Matrix3f(still).transpose();
        Matrix3f frame = new Matrix3f().rotation(withoutRoll(rotationAlong(top.path))).mul(still);
        float[] own = coreBox(top.part);
        float[] box = emptyBox();
        if (own[0] <= own[3]) {
            for (int corner = 0; corner < 8; corner++) {
                include(box, toFrame.transform(new Vector3f(
                        own[(corner & 1) == 0 ? 0 : 3],
                        own[(corner & 2) == 0 ? 1 : 4],
                        own[(corner & 4) == 0 ? 2 : 5])));
            }
        }
        return new TrunkSpace(origin, frame, box);
    }

    /**
     * Lo que mide lo que se pega en el sitio de la cabeza, para escalarlo: la cabeza (ver size) o, con un racimo
     * (Exeggutor), el racimo entero, que ocupa CLUSTER_SIZE de lo que ocuparía una cabeza. Escalando cada cabeza como
     * una sola, las tres juntas eran mucho más grandes que la cabeza del cuerpo.
     */
    private static float pastedSize(Graft graft) {
        if (graft.companions.isEmpty()) {
            return size(graft.head.part);
        }
        return CLUSTERS.computeIfAbsent(graft.head.part, part -> {
            // Caja de todas las cabezas del racimo, en el modelo
            float[] box = emptyBox();
            List<HeadBone> all = new ArrayList<>(graft.companions);
            all.add(graft.head);
            for (HeadBone head : all) {
                PoseStack stack = new PoseStack();
                stack.mulPose(matrixAlong(parentPath(head)));
                head.part.visit(stack, (pose, path, index, cube) -> includeCube(box, pose, cube));
            }
            return box[0] > box[3] ? size(part) : Math.max(meanSide(box), 0.01F) / CLUSTER_SIZE;
        });
    }

    /**
     * Pinta una pieza que va con la cabeza pegada (la pila ya está en el marco de esa cabeza): en la misma posición y
     * giro respecto a ella que en su modelo. toHead pasa del marco del modelo al de la cabeza.
     */
    private static void renderBeside(HeadBone piece, Matrix4f toHead, PoseStack poseStack, VertexConsumer consumer,
                                     int light, int overlay, int color) {
        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f(toHead).mul(matrixAlong(parentPath(piece))));
        piece.part.render(poseStack, consumer, light, overlay, color);
        poseStack.popPose();
    }

    /** El camino hasta el hueso del que cuelga una pieza. */
    private static List<ModelPart> parentPath(HeadBone bone) {
        return bone.path.subList(0, bone.path.size() - 1);
    }

    /**
     * Pinta los adornos de la especie de la cabeza sobre el tronco del cuerpo. No hay un hueso equivalente donde
     * engancharlos (como la cola en la cola), así que se colocan en proporción: si la planta de Ivysaur nace arriba
     * y en el centro de su tronco, se pone arriba y en el centro del tronco del cuerpo.
     * "Arriba" no sirve tal cual: en un cuadrúpedo es la espalda, pero en un bípedo es el cuello (la planta de
     * Venusaur y el caparazón de Lapras acababan sobre la cabeza). Por eso todo se mide con los ejes de la columna
     * de cada Pokémon (ver spineFrame): a lo ancho, a lo largo de la columna (hacia la cabeza) y espalda-barriga.
     * Así el caparazón va en la espalda de los dos: encima de un cuadrúpedo, detrás de un bípedo. El giro de cada
     * adorno se pasa igual, de los ejes de un Pokémon a los del otro; menos las colas (las de un cuerpo sin cola),
     * que conservan su giro respecto al Pokémon entero, como cuando sustituyen a otra: hacia atrás en los dos (con
     * el giro de los ejes, la de un cuadrúpedo acababa apuntando al suelo en un bípedo).
     * Se escala por lo que mide un tronco respecto al otro. Las animaciones de su especie (ya aplicadas, ver
     * animateHead) los mueven y deciden cuáles se ven (alas abiertas o cerradas...): uno oculto no se pinta.
     */
    private static void renderDecorations(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light,
                                          int overlay, int color) {
        TrunkSpace head = graft.headTop != null ? ballSpace(graft.headTop) : trunkSpace(graft.headTrunk, graft.headSpine);
        TrunkSpace body = graft.top != null ? ballSpace(graft.top) : trunkSpace(graft.bodyTrunk, graft.bodySpine);
        if (head.box[0] > head.box[3] || body.box[0] > body.box[3]) {
            return;
        }
        float trunkScale = trunkScale(head, body);
        float bodyTrunkLength = longestSide(body.box);
        // De los ejes del Pokémon de la cabeza a los del cuerpo (un giro a lo ancho: el eje X es el mismo)
        Quaternionf frameChange = new Quaternionf().setFromNormalized(
                new Matrix3f(body.frame).mul(new Matrix3f(head.frame).transpose()));
        Matrix3f toHeadFrame = new Matrix3f(head.frame).transpose();

        for (Decoration decoration : graft.trunkDecorations) {
            ModelPart part = decoration.bone.part;
            if (!part.visible) {
                continue;
            }
            // Pivote del adorno respecto al pivote de su tronco, en los ejes de la columna de su modelo
            Matrix4f matrix = matrixAlong(decoration.bone.path);
            Vector3f pivot = toHeadFrame.transform(matrix.getTranslation(new Vector3f()).sub(head.origin));
            boolean tail = decoration.category.equals(TAIL);
            if (tail && !nearBox(pivot, head.box, TAIL_REACH)) {
                continue;
            }
            // El mismo sitio proporcional en el tronco del cuerpo, y de vuelta a los ejes del modelo. Una cola sale
            // siempre de la franja central espalda-barriga: la de Gyarados (una serpiente enroscada) cuelga por
            // delante de su cuerpo y en Venusaur acababa entre las patas
            float dorsalMin = tail ? TAIL_BAND : 0F;
            Vector3f placed = new Matrix3f(body.frame).transform(new Vector3f(
                    remap(pivot.x, head.box, body.box, 0, 0F, 1F),
                    remap(pivot.y, head.box, body.box, 1, 0F, 1F),
                    remap(pivot.z, head.box, body.box, 2, dorsalMin, 1F - dorsalMin))).add(body.origin);
            Quaternionf own = matrix.getNormalizedRotation(new Quaternionf());
            Quaternionf rotation = new Quaternionf(frameChange).mul(own);
            boolean arm = decoration.category.equals(ARM);
            if (arm) {
                // Un brazo cuelga como en su modelo: con los ejes de un cuerpo sin cabeza (cuadrúpedo, ver ballSpace),
                // el de un bípedo acababa apuntando hacia atrás
                rotation = own;
            }
            if (tail) {
                // Una cola, a medio camino entre su giro respecto al Pokémon entero y el que le toca con los ejes del
                // cuerpo: la de un cuadrúpedo en un bípedo acababa apuntando al suelo con los ejes, y la de un bípedo
                // en un cuadrúpedo colgando por debajo sin ellos. A mitad, hacia atrás y algo abajo en los dos
                rotation = new Quaternionf(own).slerp(rotation, 0.5F);
            }

            // Escala de cada eje del cuerpo (a lo ancho, columna, espalda-barriga)
            Vector3f axes = new Vector3f(trunkScale);
            if (tail) {
                // Una cola puesta en un cuerpo que no tenía: como mucho tan larga como su tronco
                float tailLength = length(part);
                if (tailLength > 0) {
                    axes.set(Math.min(trunkScale, bodyTrunkLength / tailLength));
                }
            } else if (!arm) {
                // Los adornos se ajustan algo a la forma de cada eje del cuerpo: el caparazón de Lapras se hundía en
                // Dragonite, mucho más grueso de delante a atrás. Sin pasarse, o una pieza se deformaría
                for (int axis = 0; axis < 3; axis++) {
                    float from = head.box[axis + 3] - head.box[axis];
                    float to = body.box[axis + 3] - body.box[axis];
                    if (from > 1e-4F) {
                        axes.setComponent(axis, Math.clamp(to / from, trunkScale * HUG_MIN, trunkScale * HUG_MAX));
                    }
                }
            }
            // Esa escala por ejes, vista en los del modelo
            Matrix3f stretch = new Matrix3f(body.frame).scale(axes).mul(new Matrix3f(body.frame).transpose());

            PartPose saved = part.storePose();
            poseStack.pushPose();
            try {
                poseStack.translate(placed.x, placed.y, placed.z);
                poseStack.mulPose(new Matrix4f(stretch));
                poseStack.mulPose(rotation);
                // Su posición y giro ya van arriba
                part.setPos(0, 0, 0);
                part.setRotation(0, 0, 0);
                part.render(poseStack, consumer, light, overlay, color);
            } finally {
                part.loadPose(saved);
                poseStack.popPose();
            }
        }
    }

    /**
     * Lleva una coordenada de la caja de un tronco a la misma posición proporcional en la del otro (eje 0, 1, 2).
     * Lo que nace fuera de la caja se queda en su borde: la cola de Kyogre cuelga muy por detrás de su tronco y,
     * llevada en proporción a otro, quedaba flotando lejos de él.
     */
    /** ¿Está el punto dentro de la caja o fuera como mucho "reach" de su tamaño en cada eje? */
    private static boolean nearBox(Vector3f point, float[] box, float reach) {
        for (int axis = 0; axis < 3; axis++) {
            float extent = box[axis + 3] - box[axis];
            float value = point.get(axis);
            if (value < box[axis] - reach * extent || value > box[axis + 3] + reach * extent) {
                return false;
            }
        }
        return true;
    }

    private static float remap(float value, float[] from, float[] to, int axis, float min, float max) {
        float extent = from[axis + 3] - from[axis];
        float t = extent < 1e-4F ? 0.5F : Math.clamp((value - from[axis]) / extent, min, max);
        return to[axis] + t * (to[axis + 3] - to[axis]);
    }

    /**
     * Ejes de la columna de un Pokémon, en los del modelo (columnas de la matriz): X a lo ancho (el del modelo), Y del
     * tronco hacia la cabeza (hacia delante en un cuadrúpedo, hacia arriba en un bípedo) y Z = X × Y, el de
     * espalda-barriga. Como X es el mismo en todos los modelos, pasar de unos ejes a otros es inclinar hacia
     * delante o atrás, como un cuadrúpedo que se pone de pie.
     * La columna va del pivote del tronco al principio del cuello (ver findSpineEnd), no a la cabeza: un cuello largo
     * la torcería (la de Lapras apuntaba al cielo y su caparazón, que está encima, acababa hacia el cuello en otros
     * Pokémon). Tampoco sirve la forma del tronco: el de un bípedo rechoncho (Dragonite) es más ancho que alto.
     */
    private static Matrix3f spineFrame(Vector3f trunk, Matrix4f spineEnd) {
        Vector3f across = new Vector3f(1, 0, 0);
        Vector3f spine = spineEnd.getTranslation(new Vector3f()).sub(trunk);
        // Solo cuenta la inclinación: lo que se desvíe a un lado no
        spine.x = 0;
        if (spine.lengthSquared() < 1e-8F) {
            // Cuello justo en el pivote del tronco: hacia arriba (en los modelos de Minecraft la Y crece hacia abajo)
            spine.set(0, -1, 0);
        }
        spine.normalize();
        Vector3f back = new Vector3f(across).cross(spine);
        return new Matrix3f(across, spine, back);
    }

    /**
     * El cuerpo de un Pokémon (sin cabeza, cuello ni extremidades) para colocar y escalar lo que se pega en él.
     *
     * @param origin pivote del tronco, en el modelo
     * @param frame  ejes de su columna (ver spineFrame)
     * @param box    caja de todo el cuerpo vista desde origin con esos ejes
     */
    private record TrunkSpace(Vector3f origin, Matrix3f frame, float[] box) {
    }

    /**
     * El espacio del tronco de un modelo, con su postura de ahora. La caja es la de los cubos propios de TODOS los
     * huesos del camino hasta el cuello ("torso" + "torso2" + "chest"..., o los segmentos de Gyarados), no solo los
     * del tronco: el tronco es el hueso más grande, pero en Vaporeon es el trozo de atrás y en Dragonite la parte de
     * abajo, y el caparazón de Lapras acababa en el culo o colgando como una cola. Si el tronco está al lado del camino
     * (ver trunkBeside), con toda su rama.
     */
    private static TrunkSpace trunkSpace(HeadBone trunk, HeadBone spineEnd) {
        Matrix4f trunkMatrix = matrixAlong(trunk.path);
        Vector3f origin = trunkMatrix.getTranslation(new Vector3f());
        List<HeadBone> branch = TRUNK_BRANCHES.getOrDefault(trunk.part, List.of());
        // La columna sale del pivote del tronco; con un tronco al lado, del centro de su rama: el pivote de la cadena de
        // Ekans está justo al lado de su cabeza y la columna salía en diagonal
        Vector3f spineStart = new Vector3f(origin);
        if (!branch.isEmpty()) {
            spineStart.zero();
            for (HeadBone bone : branch) {
                spineStart.add(matrixAlong(bone.path).getTranslation(new Vector3f()));
            }
            spineStart.div(branch.size());
        }
        Matrix3f frame = spineFrame(spineStart, matrixAlong(spineEnd.path));
        Matrix3f toFrame = new Matrix3f(frame).transpose();
        float[] box = emptyBox();
        // Los huesos del camino hasta el cuello y la rama del tronco, si está al lado
        List<ModelPart> spine = parentPath(spineEnd);
        List<List<ModelPart>> bones = new ArrayList<>();
        for (int i = 0; i < spine.size(); i++) {
            bones.add(spine.subList(0, i + 1));
        }
        for (HeadBone bone : branch) {
            bones.add(bone.path);
        }
        // Sin los planos de grosor cero si hay cubos con volumen: los harapos de la falda de Darkrai le daban un tronco
        // de 36 de ancho (el de verdad mide 14) y lo que se le pegaba salía enorme. Hay cuerpos hechos solo de planos
        // (Swalot es una caja hueca): esos se miden con ellos
        boolean solid = false;
        for (List<ModelPart> path : bones) {
            float[] own = solidOwnBox(path.getLast());
            solid |= own[0] <= own[3];
        }
        for (List<ModelPart> path : bones) {
            float[] own = solid ? solidOwnBox(path.getLast()) : ownBox(path.getLast());
            if (own[0] > own[3]) {
                continue;
            }
            Matrix4f bone = matrixAlong(path);
            for (int corner = 0; corner < 8; corner++) {
                include(box, toFrame.transform(bone.transformPosition(new Vector3f(
                        own[(corner & 1) == 0 ? 0 : 3],
                        own[(corner & 2) == 0 ? 1 : 4],
                        own[(corner & 4) == 0 ? 2 : 5])).sub(origin)));
            }
        }
        return new TrunkSpace(origin, frame, box);
    }

    /**
     * Escala de lo que se pega del cuerpo de la especie de la cabeza (cola, adornos): lo que mide el cuerpo del otro
     * respecto al suyo. Así cada pieza conserva su tamaño en proporción: la cola pequeña de Lapras sigue siendo
     * pequeña en otro Pokémon (al igualar largos de cola salía enorme). 0 si falta alguna caja.
     */
    private static float trunkScale(TrunkSpace head, TrunkSpace body) {
        if (head.box[0] > head.box[3] || body.box[0] > body.box[3]) {
            return 0;
        }
        return Math.clamp(soften(meanSide(body.box) / Math.max(meanSide(head.box), 0.01F)), MIN_PIECE_SCALE,
                MAX_PIECE_SCALE);
    }

    /** trunkScale de una fusión, o 0 si a algún modelo le falta tronco o cuello con los que medirlo. */
    private static float trunkScale(Graft graft) {
        if (graft.headTrunk == null || graft.bodyTrunk == null || graft.headSpine == null || graft.bodySpine == null) {
            return 0;
        }
        return trunkScale(trunkSpace(graft.headTrunk, graft.headSpine), trunkSpace(graft.bodyTrunk, graft.bodySpine));
    }

    private static float longestSide(float[] box) {
        return Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
    }

    /** Transformación acumulada a lo largo de un camino de huesos (de la raíz al último), en bloques. */
    private static Matrix4f matrixAlong(List<ModelPart> path) {
        PoseStack stack = new PoseStack();
        for (ModelPart part : path) {
            part.translateAndRotate(stack);
        }
        return new Matrix4f(stack.last().pose());
    }

    /**
     * Pinta la cola de la especie de la cabeza en el sitio de la del cuerpo, como las cabezas: su pieza principal en
     * el pivote de la principal del cuerpo, girada como en su modelo (que ya está animado: se mueve con su animación
     * de reposo) y escalada por lo que mide un tronco respecto al otro (trunkScale), para que una cola de Pikachu en
     * un Charizard no quede diminuta ni la de Lapras enorme. Las demás piezas (los mechones de Luxray, las colas de
     * Ninetales) van donde estén respecto a la principal en su modelo.
     */
    private static void renderTail(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light, int overlay,
                                   int color) {
        HeadBone headAnchor = graft.headTail.anchor();
        HeadBone bodyAnchor = graft.bodyTail.anchor();
        float headLength = length(headAnchor.part);
        float bodyLength = length(bodyAnchor.part);
        float scale = trunkScale(graft);
        if (scale <= 0) {
            // Sin troncos con los que comparar (modelos "todo cabeza"): igualar el largo de las colas
            scale = headLength <= 0 || bodyLength <= 0 ? 1F
                    : Math.clamp(soften(bodyLength / headLength), MIN_SCALE, MAX_SCALE);
        } else if (headLength > 0 && bodyLength > 0) {
            // Tope: no mucho más larga que la que sustituye (la de Groudon salía gigante)
            scale = Math.min(scale, TAIL_GROWTH * bodyLength / headLength);
        }
        scale *= INFLATE;
        Quaternionf correction = rotationAlong(bodyAnchor.path).conjugate().mul(rotationAlong(headAnchor.path));
        // Del marco de la pieza principal al del hueso del que cuelgan todas las piezas: así cada una se pinta tal
        // cual (con su posición y giro) y la principal queda justo en el pivote
        Matrix4f fromAnchor = matrixAlong(headAnchor.path).invert().mul(matrixAlong(parentPath(headAnchor)));

        poseStack.pushPose();
        try {
            for (ModelPart part : bodyAnchor.path) {
                part.translateAndRotate(poseStack);
            }
            poseStack.mulPose(correction);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(fromAnchor);
            // Al pegar un modelo entero sus colas se ocultan como extremidades mientras se pinta, pero ya están
            // restauradas: las que se vean ahora son las que deciden sus animaciones
            for (HeadBone root : graft.headTail.roots()) {
                root.part.render(poseStack, consumer, light, overlay, color);
            }
        } finally {
            poseStack.popPose();
        }
    }

    /** Largo de una cola: el lado más largo de su caja (con sus hijos), en bloques; 0 si no tiene cubos. */
    private static float length(ModelPart part) {
        float[] box = localBox(part, Map.of());
        if (box[0] > box[3]) {
            return 0;
        }
        return Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
    }

    /**
     * Desplazamiento (en el marco ya girado con la corrección) que pone el centro del cubo principal de lo que se pega
     * en el del cráneo de la cabeza del cuerpo; null si alguno no tiene cubos. Con la postura de cada fotograma
     * (Solrock flota y se balancea).
     */
    private static Vector3f skullOffset(ModelPart bodyHead, Quaternionf correction, ModelPart pasted,
                                        Map<ModelPart, String> limbs, float scale) {
        Vector3f target = skullCenter(bodyHead, Map.of());
        Vector3f own = skullCenter(pasted, limbs);
        if (target == null || own == null) {
            return null;
        }
        // El centro del cráneo del cuerpo está en el marco de su cabeza; se pinta tras girar con la corrección
        return new Quaternionf(correction).conjugate().transform(target).sub(own.mul(scale));
    }

    /**
     * Centro del cubo con más volumen de un hueso y sus hijos (sin las extremidades que se saltan), en su propio marco
     * y con la postura de ahora; null si no hay cubos con volumen.
     */
    private static Vector3f skullCenter(ModelPart part, Map<ModelPart, String> skip) {
        Skull skull = skull(part, skip);
        if (skull == null) {
            return null;
        }
        float[] box = skull.box;
        return new Vector3f((box[0] + box[3]) / 2F, (box[1] + box[4]) / 2F, (box[2] + box[5]) / 2F);
    }

    /** Cara del cráneo por la que entra el cuello de cada cabeza (ver neckFace): eje * 2 + 1 si es la del lado positivo. */
    private static final Map<ModelPart, Integer> NECK_FACES = new WeakHashMap<>();

    /**
     * Punto de pegado de una cabeza para Align.BASE, en su marco propio: el centro de la cara de su cráneo por la que
     * entra el cuello. Así todas las cabezas se pegan igual, tengan el pivote en el cuello (Bulbasaur), en medio de la
     * cabeza (Charizard) o lejos (Cresselia). null si no tiene cráneo.
     */
    private static Vector3f attachPoint(HeadBone head) {
        Skull skull = skull(head.part, Map.of());
        if (skull == null) {
            return null;
        }
        float[] box = skull.box;
        // La cara se elige una vez: con la postura de cada fotograma podría saltar de una a otra
        int face = NECK_FACES.computeIfAbsent(head.part, part -> neckFace(head, box));
        int axis = face / 2;
        Vector3f point = new Vector3f((box[0] + box[3]) / 2F, (box[1] + box[4]) / 2F, (box[2] + box[5]) / 2F);
        point.setComponent(axis, face % 2 == 1 ? box[axis + 3] : box[axis]);
        return point;
    }

    /**
     * La cara del cráneo (en su marco propio) que mira hacia el cuello: hacia el primer hueso con cubos subiendo por el
     * camino a la cabeza (el cuello, o el tronco si no tiene), el eje en que más se aleja. Sin ninguno, la de abajo (en
     * los modelos de Minecraft la Y crece hacia abajo).
     */
    private static int neckFace(HeadBone head, float[] skullBox) {
        Vector3f center = new Vector3f((skullBox[0] + skullBox[3]) / 2F, (skullBox[1] + skullBox[4]) / 2F,
                (skullBox[2] + skullBox[5]) / 2F);
        Matrix4f toHead = matrixAlong(head.path).invert();
        Vector3f direction = new Vector3f(0, 1, 0);
        for (int i = head.path.size() - 2; i >= 1; i--) {
            float[] own = solidOwnBox(head.path.get(i));
            if (own[0] > own[3]) {
                own = ownBox(head.path.get(i));
            }
            if (own[0] > own[3]) {
                continue;
            }
            Vector3f neck = new Matrix4f(toHead).mul(matrixAlong(head.path.subList(0, i + 1))).transformPosition(
                    new Vector3f((own[0] + own[3]) / 2F, (own[1] + own[4]) / 2F, (own[2] + own[5]) / 2F));
            if (neck.distanceSquared(center) > 1e-8F) {
                direction = neck.sub(center);
            }
            break;
        }
        int axis = 0;
        for (int i = 1; i < 3; i++) {
            if (Math.abs(direction.get(i)) > Math.abs(direction.get(axis))) {
                axis = i;
            }
        }
        return axis * 2 + (direction.get(axis) > 0 ? 1 : 0);
    }

    /**
     * El cubo del cráneo de una cabeza (ver skullCenter).
     *
     * @param box  su caja, en el marco propio del hueso de la cabeza, en bloques
     * @param path ruta del hueso del cubo desde la cabeza, como la da ModelPart.visit ("" = la propia cabeza,
     *             "/head_angle")
     */
    private record Skull(float[] box, String path) {
    }

    /** El cubo del cráneo (ver skullCenter); null si no hay cubos con volumen. */
    private static Skull skull(ModelPart part, Map<ModelPart, String> skip) {
        // El mejor cubo sin contar adornos [0] y contándolos [1], por si la cabeza es todo adornos (ver inDecoration)
        float[] best = {-1, -1};
        Skull[] found = new Skull[2];
        visitInOwnFrame(part, (pose, path, index, cube) -> {
            for (String limb : skip.values()) {
                if (path.equals(limb) || path.startsWith(limb + "/")) {
                    return;
                }
            }
            float[] box = emptyBox();
            includeCube(box, pose, cube);
            // Volumen del cubo en sí, no de su caja: la de un cubo girado es mayor y cambia con la animación (el cubo
            // de 6x6x6 girado de la cabeza de Nidoking "ganaba" al cráneo, y a ratos no: la cabeza daba saltos)
            float volume = cubeVolume(cube);
            for (int i = inDecoration(path) ? 1 : 0; i < 2; i++) {
                if (volume > best[i]) {
                    best[i] = volume;
                    found[i] = new Skull(box, path);
                }
            }
        });
        return best[0] > 0 ? found[0] : best[1] > 0 ? found[1] : null;
    }

    /**
     * ¿Cuelga el cubo (por su ruta, la de ModelPart.visit: "/hair/hair2") de algún hueso que sea un adorno (ver
     * category)? Para buscar el cráneo: el cubo más grande de una cabeza no siempre es suyo. En Darkrai es el pelo
     * (el modelo entero pegado quedaba centrado en él, flotando); en ~100 modelos, un sombrero, un casco, una crin, la
     * burbuja de Araquanid o el afro de Bouffalant.
     */
    private static boolean inDecoration(String path) {
        // skull() lo pregunta por cada cubo en cada fotograma: se calcula una vez por ruta (ver NAME_RESULTS)
        Boolean cached = IN_DECORATION.get(path);
        if (cached == null) {
            cached = false;
            for (String bone : path.split("/")) {
                if (!bone.isEmpty() && category(bone) != null) {
                    cached = true;
                    break;
                }
            }
            IN_DECORATION.put(path, cached);
        }
        return cached;
    }

    /**
     * Desplazamiento (en el marco ya girado con la corrección) que apoya el modelo entero sobre el sitio de la
     * cabeza del cuerpo. En los modelos de Minecraft la Y crece hacia abajo: "lo más bajo" es la Y máxima.
     */
    private static Vector3f groundOffset(ModelPart bodyHead, Quaternionf correction, ModelPart model,
                                         Map<ModelPart, String> limbs, float scale) {
        float[] head = localBox(bodyHead, Map.of());
        // Sin las extremidades que no se pintan: si no, Tentacool se apoyaría en la punta de unos tentáculos ocultos
        float[] pasted = liveBox(model, limbs);
        if (head[0] > head[3] || pasted[0] > pasted[3]) {
            // Alguno no tiene cubos: pivote con pivote
            return new Vector3f();
        }
        // La caja de la cabeza del cuerpo está en su marco; el modelo se pinta después de girar con la
        // corrección, así que la vemos desde ahí deshaciendo ese giro en sus esquinas
        Quaternionf undo = new Quaternionf(correction).conjugate();
        float[] box = emptyBox();
        for (int corner = 0; corner < 8; corner++) {
            include(box, undo.transform(new Vector3f(
                    head[(corner & 1) == 0 ? 0 : 3],
                    head[(corner & 2) == 0 ? 1 : 4],
                    head[(corner & 4) == 0 ? 2 : 5])));
        }
        return new Vector3f(
                (box[0] + box[3]) / 2F - scale * (pasted[0] + pasted[3]) / 2F,
                box[4] - scale * pasted[4],
                (box[2] + box[5]) / 2F - scale * (pasted[2] + pasted[5]) / 2F);
    }

    /**
     * Caja de un hueso con sus hijos en su propio marco (sin su posición, giro ni escala), en bloques.
     * Se calcula una vez, la primera vez que se pinta (ya animado: incluye, p. ej., lo que flota Koffing en reposo).
     * Para un mismo hueso, las extremidades que se saltan son siempre las mismas: se puede guardar en caché.
     */
    private static float[] localBox(ModelPart part, Map<ModelPart, String> skip) {
        float[] cached = BOXES.get(part);
        if (cached != null) {
            return cached;
        }
        float[] box = liveBox(part, skip);
        BOXES.put(part, box);
        return box;
    }

    /**
     * Como localBox, pero con la postura de ahora, sin caché. Para apoyar un modelo entero que se pega: Solrock flota y
     * se balancea con su animación, y medido una sola vez quedaba más alto o ladeado según el instante en que se
     * midió. Medido en cada fotograma, siempre queda apoyado y centrado (son pocos cubos).
     */
    private static float[] liveBox(ModelPart part, Map<ModelPart, String> skip) {
        // visit no mira la visibilidad: lo que se salta se reconoce por la ruta del hueso del cubo
        return boxInOwnFrame(part, path -> {
            for (String limb : skip.values()) {
                if (path.equals(limb) || path.startsWith(limb + "/")) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Caja de los cubos propios de un hueso, sin los de sus hijos, en su propio marco. Para un tronco es "el cuerpo"
     * en sí: con los hijos saldría la caja de todo el Pokémon (cabeza, patas...).
     */
    private static float[] ownBox(ModelPart part) {
        float[] cached = OWN_BOXES.get(part);
        if (cached != null) {
            return cached;
        }
        // ModelPart.visit da a los cubos del propio hueso la ruta "" y a los de sus hijos "/hijo...". Los cubos girados
        // del .geo son del hueso, pero un ModelPart no admite cubos girados: Cobblemon (TexturedModel) mete cada uno
        // en un hijo propio llamado "%hueso%n", con ese giro. Groudon de AllTheMons tiene así casi todos: sin
        // contarlos, su torso no tenía cubos y el "tronco" salía su cuello
        float[] box = boxInOwnFrame(part, path -> path.isEmpty() || isRotatedCube(path.substring(1)));
        OWN_BOXES.put(part, box);
        return box;
    }

    /** Como ownBox, pero solo con los cubos con volumen (sin planos de grosor cero). Ver trunkSpace. */
    private static float[] solidOwnBox(ModelPart part) {
        float[] cached = SOLID_BOXES.get(part);
        if (cached != null) {
            return cached;
        }
        float[] box = emptyBox();
        visitInOwnFrame(part, (pose, path, index, cube) -> {
            if ((path.isEmpty() || isRotatedCube(path.substring(1))) && cubeVolume(cube) > 0) {
                includeCube(box, pose, cube);
            }
        });
        SOLID_BOXES.put(part, box);
        return box;
    }

    /** ¿Es el nombre de un hijo que Cobblemon crea para un cubo girado ("%tophalf%0")? */
    private static boolean isRotatedCube(String name) {
        return name.startsWith("%") && name.indexOf('/') < 0;
    }

    /** Caja de los cubos de un hueso y sus hijos cuya ruta (la de ModelPart.visit) cumpla la condición. */
    private static float[] boxInOwnFrame(ModelPart part, Predicate<String> include) {
        float[] box = emptyBox();
        visitInOwnFrame(part, (pose, path, index, cube) -> {
            if (include.test(path)) {
                includeCube(box, pose, cube);
            }
        });
        return box;
    }

    /**
     * El núcleo de un cuerpo sin cabeza, en su propio marco: su cubo principal (el de más volumen real) con las piezas
     * grandes pegadas a él (como el cráneo en size). Para colocar lo que se le pega: con la caja de todo lo que cuelga
     * (las alas de Wingull, los brazos de Cacnea, los rayos de Solrock, las ramas de Corsola) las orejas acababan en
     * los bordes de esa caja, lejos del cuerpo, y la cola flotando detrás. Se calcula una vez (como localBox).
     */
    private static float[] coreBox(ModelPart part) {
        float[] cached = CORES.get(part);
        if (cached != null) {
            return cached;
        }
        List<float[]> boxes = new ArrayList<>();
        List<Float> volumes = new ArrayList<>();
        visitInOwnFrame(part, (pose, path, index, cube) -> {
            float[] box = emptyBox();
            includeCube(box, pose, cube);
            boxes.add(box);
            volumes.add(cubeVolume(cube));
        });
        int main = -1;
        for (int i = 0; i < boxes.size(); i++) {
            if (volumes.get(i) > 0 && (main < 0 || volumes.get(i) > volumes.get(main))) {
                main = i;
            }
        }
        float[] core;
        if (main < 0) {
            // Sin cubos con volumen (todo planos): la caja de todo
            core = localBox(part, Map.of());
        } else {
            core = boxes.get(main).clone();
            for (int i = 0; i < boxes.size(); i++) {
                if (volumes.get(i) >= PIECE_SHARE * volumes.get(main) && touches(boxes.get(i), boxes.get(main), 1 / 16F)) {
                    include(core, new Vector3f(boxes.get(i)[0], boxes.get(i)[1], boxes.get(i)[2]));
                    include(core, new Vector3f(boxes.get(i)[3], boxes.get(i)[4], boxes.get(i)[5]));
                }
            }
        }
        CORES.put(part, core);
        return core;
    }

    /** Recorre los cubos de un hueso y sus hijos en su propio marco (sin su posición, giro ni escala). */
    private static void visitInOwnFrame(ModelPart part, ModelPart.Visitor visitor) {
        PartPose saved = part.storePose();
        float xScale = part.xScale;
        float yScale = part.yScale;
        float zScale = part.zScale;
        try {
            part.loadPose(PartPose.ZERO);
            part.xScale = 1;
            part.yScale = 1;
            part.zScale = 1;
            part.visit(new PoseStack(), visitor);
        } finally {
            part.loadPose(saved);
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
        }
    }

    /** Muestra u oculta unos huesos, apuntando cómo estaban para restoreVisible. */
    private static void setVisible(Collection<ModelPart> parts, List<Boolean> were, boolean visible) {
        for (ModelPart part : parts) {
            were.add(part.visible);
            part.visible = visible;
        }
    }

    /**
     * Deja como estaban los huesos que haya tocado setVisible (aunque no llegase a tocarlos todos). Al revés de como
     * se ocultaron: si un hueso saliese dos veces, así recupera lo que tenía antes de la primera.
     */
    private static void restoreVisible(Collection<ModelPart> parts, List<Boolean> were) {
        List<ModelPart> list = new ArrayList<>(parts);
        for (int i = Math.min(list.size(), were.size()) - 1; i >= 0; i--) {
            list.get(i).visible = were.get(i);
        }
    }

    /**
     * Aplica al modelo de la cabeza sus animaciones de reposo, con un estado propio por fusión (lo mismo que hace
     * Cobblemon para pintar un Pokémon en un menú). Si la fusión es una entidad, la cabeza mira hacia donde mira.
     */
    private static FloatingState animateHead(Graft graft, PosableState state) {
        FloatingState headState = HEAD_STATES.computeIfAbsent(state, key -> new FloatingState());
        headState.setCurrentAspects(state.getCurrentAspects());
        headState.setCurrentModel(graft.headModel);
        headState.setPoseToFirstSuitable(PoseType.STAND);
        headState.updatePartialTicks(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));

        float headYaw = 0;
        float headPitch = 0;
        Entity entity = state.getEntity();
        if (entity instanceof LivingEntity living) {
            headYaw = Mth.wrapDegrees(living.getYHeadRot() - living.yBodyRot);
            headPitch = living.getXRot();
        }
        graft.headModel.applyAnimations(null, headState, 0, 0, 0, headYaw, headPitch);
        return headState;
    }

    // ---- Buscar las cabezas de un modelo ----

    /**
     * Cabezas de un modelo; la primera es la principal (la que se pega en las fusiones):
     *  - principal: el hueso "head"; si no hay, el padre de "locator_head" (el punto donde Cobblemon pone los
     *    sombreros, siempre en la cabeza principal; p. ej. "head4" en Dodrio); si tampoco, el primer "head2"...
     *  - además, el resto de "head2", "head3"... y "head_left"/"head_right" (Scovillain, Binacle) que no estén
     *    dentro de otra cabeza (Doduo, Dodrio...). Otros "head_algo" son piezas o pivotes ("head_AI",
     *    "head_locators", "head_rot"...): no se cuentan.
     * Ver ModelHeads para los modelos "todo cabeza".
     */
    private static ModelHeads findHeads(PosableModel model) {
        if (!((Object) model.getRootPart() instanceof ModelPart root)) {
            return new ModelHeads(List.of(), true, Map.of(), null, null, List.of(), null, List.of(), List.of(), null,
                    List.of());
        }
        List<HeadBone> arms = findArms(root);

        boolean whole = false;
        Map<ModelPart, String> limbs = Map.of();
        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> primary = firstPath(root, "head"::equals);
        if (primary != null && cubes(primary.getLast()) == cubes(root)) {
            // Su "head" lo lleva todo, patas incluidas (Corsola, Sunkern, Inkay, Gulpin, Nihilego: solo esos 6 en
            // Cobblemon y AllTheMons). Como cuerpo, ocultarla dejaba solo la cabeza nueva: es un cuerpo sin cabeza,
            // como Voltorb (conserva sus ramas y lleva los complementos). Como cabeza, se pega entero
            return headless(root, armsAsLimbs(root, Map.of()), null, arms);
        }
        if (primary == null) {
            primary = firstPath(root, "locator_head"::equals);
            if (primary != null) {
                // Quitamos el localizador: la cabeza es su padre
                primary.remove(primary.size() - 1);
                primary = withSkull(withFace(root, primary));
                // En los modelos sin hueso "head" ese padre casi siempre es casi todo el cuerpo ("torso", "body").
                // Como cabeza: se pega el modelo entero (bien apoyado, ver groundOffset), sin sus extremidades
                // para moverse (las manos de Haunter sí se quedan).
                // Como cuerpo se mira otra cosa: si al ocultarlo queda algo. El volumen no sirve para eso
                // (los tentáculos de Tentacool son finos: poco volumen, pero son lo que se ve de su cuerpo)
                ModelPart part = primary.get(primary.size() - 1);
                whole = volume(part) >= WHOLE_MODEL_SHARE * volume(root);
                if (cubes(part) == cubes(root)) {
                    return headless(root, armsAsLimbs(root, Map.of()), null, arms);
                }
                if (whole) {
                    limbs = new LinkedHashMap<>();
                    collectLimbs(root, part, "", limbs);
                    limbs = armsAsLimbs(root, limbs);
                }
            }
        }
        if (primary != null) {
            paths.add(primary);
        }
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, name -> name.matches("head\\d*|head_(left|right)"), current, paths);

        List<HeadBone> heads = new ArrayList<>();
        for (List<ModelPart> path : paths) {
            ModelPart part = path.get(path.size() - 1);
            // Si la "cabeza" fuese la raíz, ocultarla ocultaría el Pokémon entero
            if (path.size() < 2 || insideAnother(path, heads) || containsHead(heads, part)) {
                continue;
            }
            heads.add(new HeadBone(part, path));
        }
        Tail tail = findTail(root, heads);
        if (heads.isEmpty()) {
            return headless(root, armsAsLimbs(root, limbs), tail, arms);
        }
        List<ModelPart> headPath = heads.get(0).path;
        HeadBone trunk = findTrunk(headPath, heads, tail);
        if (tail == null && trunk != null) {
            tail = chainTail(root, trunk);
        }
        // Varias cabezas sin cuello, que cuelgan juntas del mismo hueso (Exeggutor: "head", "head2" y "head3" de
        // "upperHead"): son un racimo que va entero con la principal, no cabezas con cuello como las de Dodrio
        ModelPart cluster = heads.size() > 1 ? sharedParent(heads) : null;
        List<HeadBone> companions = cluster == null ? List.of() : List.copyOf(heads.subList(1, heads.size()));
        // Lo que cuelga de ese hueso (las hojas de Exeggutor) también va con las cabezas, salvo que sea el tronco
        if (cluster != null && trunk != null && headPath.indexOf(cluster) <= headPath.indexOf(trunk.part)) {
            cluster = null;
        }
        List<HeadBone> chains = heads.size() > 1 && companions.isEmpty() ? findChains(heads) : List.of();
        // Un "todo cabeza" (Gengar, Clefairy, Haunter) como cuerpo es un cuerpo-cabeza: se queda entero y los
        // complementos de la otra especie van sobre su pieza principal, como en Voltorb
        return new ModelHeads(heads, whole, limbs, tail, trunk, findDecorations(headPath, heads, tail, cluster),
                findSpineEnd(headPath, trunk), chains, companions, whole ? findTop(root) : null, arms);
    }

    /**
     * Un modelo sin cabeza (cuerpo-cabeza): como CABEZA se pega entero; como CUERPO se queda entero y lleva los
     * complementos de la otra especie (ver findTop, renderOnBody).
     */
    private static ModelHeads headless(ModelPart root, Map<ModelPart, String> limbs, Tail tail, List<HeadBone> arms) {
        return new ModelHeads(List.of(), true, limbs, tail, null, List.of(), null, List.of(), List.of(), findTop(root),
                arms);
    }

    /**
     * Los brazos de un modelo: los huesos de brazo u hombro de más arriba (ver isArm), cada uno con lo que cuelga.
     * Las manos sueltas no lo son (las de Haunter flotan aparte: "hands", "hand_right1").
     */
    private static List<HeadBone> findArms(ModelPart root) {
        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, FusionGraft::isArm, current, paths);
        List<HeadBone> arms = new ArrayList<>();
        Set<ModelPart> found = new HashSet<>();
        for (List<ModelPart> path : paths) {
            // Solo el de más arriba de cada grupo: "arms" y no también "arm_left", que va dentro
            if (path.stream().limit(path.size() - 1L).noneMatch(found::contains)) {
                found.add(path.getLast());
                arms.add(new HeadBone(path.getLast(), new ArrayList<>(path)));
            }
        }
        return arms;
    }

    /**
     * Las extremidades que no se pintan al pegar el modelo entero, más sus brazos (también los de dentro de la
     * "cabeza": los de Gengar salen de su cuerpo-cabeza). Pegado en el sitio de una cabeza, el cuerpo ya pone los suyos.
     */
    private static Map<ModelPart, String> armsAsLimbs(ModelPart root, Map<ModelPart, String> limbs) {
        Map<ModelPart, String> all = new LinkedHashMap<>(limbs);
        collectArms(root, "", all);
        return all;
    }

    /** Recorre el modelo apuntando cada brazo (ver isArm) con su ruta, sin bajar por dentro de él. */
    private static void collectArms(ModelPart node, String path, Map<ModelPart, String> limbs) {
        for (Map.Entry<String, Bone> child : ((Bone) (Object) node).getChildren().entrySet()) {
            if (!((Object) child.getValue() instanceof ModelPart part)) {
                continue;
            }
            String childPath = path + "/" + child.getKey();
            if (isArm(child.getKey())) {
                limbs.putIfAbsent(part, childPath);
            } else {
                collectArms(part, childPath, limbs);
            }
        }
    }

    /**
     * Sobre qué se pone la cabeza en un cuerpo sin cabeza: el hueso que lleva la pieza más grande, con todo lo que
     * cuelga de él. El mismo hueso que mueven las animaciones (Voltorb rueda y bota con "body", Koffing flota,
     * Snorunt se balancea con "torso"): así lo pegado lo sigue. Con el de primer nivel ("body" en Snorunt, que no se
     * mueve) los complementos se quedaban quietos y el cuerpo los atravesaba al balancearse.
     */
    private static HeadBone findTop(ModelPart root) {
        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, name -> true, current, paths);
        List<ModelPart> best = List.of(root);
        float bestVolume = hasOwnCubes(root) ? volume(ownBox(root)) : 0;
        for (List<ModelPart> path : paths) {
            float[] box = ownBox(path.getLast());
            if (box[0] <= box[3] && volume(box) > bestVolume) {
                bestVolume = volume(box);
                best = path;
            }
        }
        return new HeadBone(best.getLast(), new ArrayList<>(best));
    }

    /** El hueso del que cuelgan directamente todas las cabezas, o null si no es el mismo. */
    private static ModelPart sharedParent(List<HeadBone> heads) {
        ModelPart parent = parentPath(heads.get(0)).getLast();
        for (HeadBone head : heads) {
            if (parentPath(head).getLast() != parent) {
                return null;
            }
        }
        return parent;
    }

    /**
     * Con varias cabezas, cada una con su cuello: el hueso por el que su camino se separa del de las demás
     * ("neck_left"/"neck_right" en Scovillain y Doduo, "neck" y los dos de los lados en Dodrio), con lo que cuelga.
     */
    private static List<HeadBone> findChains(List<HeadBone> heads) {
        // Lo que comparten todos los caminos desde la raíz: hasta el hueso donde se separan
        int shared = heads.get(0).path.size();
        for (HeadBone head : heads) {
            int i = 0;
            while (i < shared && i < head.path.size() && head.path.get(i) == heads.get(0).path.get(i)) {
                i++;
            }
            shared = i;
        }
        List<HeadBone> chains = new ArrayList<>();
        for (HeadBone head : heads) {
            int end = Math.min(shared + 1, head.path.size());
            ModelPart root = head.path.get(end - 1);
            if (chains.stream().noneMatch(chain -> chain.part == root)) {
                chains.add(new HeadBone(root, new ArrayList<>(head.path.subList(0, end))));
            }
        }
        return chains;
    }


    /**
     * Hasta dónde llega la columna, para orientar el Pokémon (ver spineFrame): el primer hueso del cuello después del
     * tronco ("neck", "neck2", "lowernecc"...) o, si no tiene cuello, la cabeza. Lapras: "neck2", delante del tronco
     * (su cabeza está muy arriba); Dragonite: "neck", encima.
     */
    private static HeadBone findSpineEnd(List<ModelPart> headPath, HeadBone trunk) {
        // Desde donde el camino del tronco se separa del de la cabeza: el propio tronco si está en el camino, o el hueso
        // del que cuelga su rama si está al lado (ver trunkBeside)
        int from = 1;
        if (trunk != null) {
            from = 0;
            while (from < trunk.path.size() && from < headPath.size() && trunk.path.get(from) == headPath.get(from)) {
                from++;
            }
        }
        // Una serpiente (el tronco es un cuello, ver findTrunk): hasta el ÚLTIMO cuello, para que la columna sea todo
        // lo que se alza hasta la cabeza
        boolean serpent = trunk != null && headPath.contains(trunk.part) && isNeck(headPath, trunk.path.size() - 1);
        int end = -1;
        for (int i = Math.max(from, 1); i < headPath.size() - 1; i++) {
            if (isNeck(headPath, i)) {
                end = i;
                if (!serpent) {
                    break;
                }
            }
        }
        if (end < 0) {
            return new HeadBone(headPath.get(headPath.size() - 1), headPath);
        }
        return new HeadBone(headPath.get(end), new ArrayList<>(headPath.subList(0, end + 1)));
    }

    /** ¿Se llama como un cuello el hueso i del camino? ("neck", "neck2", "lowernecc"...) */
    private static boolean isNeck(List<ModelPart> path, int i) {
        if (i < 1) {
            return false;
        }
        String name = boneName(path.get(i - 1), path.get(i));
        return name.contains("neck") || name.contains("necc");
    }

    /**
     * El tronco: en el camino de la raíz a la cabeza principal, el hueso cuyos cubos propios ocupan más ("torso" en
     * Ivysaur, "thorax" en Scyther, "body" en Butterfree). Los grupos vacíos ("body" en Ivysaur) no cuentan, ni los
     * cuellos si hay otra cosa: el de Greninja o Zamazenta es más grueso que su torso.
     * Si todo el camino es cuello, es una serpiente (Gyarados: "neck1"... "neck5"; Dragonair, Milotic, Rayquaza...):
     * el tronco es el primer cuello con cubos, la base de lo que se alza. Con el más grueso salía "neck5", el trozo
     * de justo debajo de la cabeza, y adornos y cabezas no se colocaban bien.
     * Si en el camino no hay tronco, o solo un cuello, el cuerpo está al lado (ver trunkBeside).
     */
    private static HeadBone findTrunk(List<ModelPart> headPath, List<HeadBone> heads, Tail tail) {
        HeadBone trunk = biggestOwnCubes(headPath, i -> !isNeck(headPath, i));
        if (trunk != null) {
            return trunk;
        }
        int lastNeck = -1;
        int necks = 0;
        for (int i = 0; i < headPath.size() - 1; i++) {
            if (isNeck(headPath, i)) {
                lastNeck = i;
                necks++;
            }
        }
        for (int i = 0; i < lastNeck; i++) {
            if (isNeck(headPath, i) && hasOwnCubes(headPath.get(i))) {
                return new HeadBone(headPath.get(i), new ArrayList<>(headPath.subList(0, i + 1)));
            }
        }
        // Sin nada en el camino, o un solo cuello (el de Snorlax es un trozo pequeño bajo la cabeza, y su barriga
        // cuelga al lado): el tronco está en una rama al lado. Si no hay, el cuello
        HeadBone beside = trunkBeside(headPath, heads, tail);
        return beside != null ? beside : necks > 0 ? biggestOwnCubes(headPath, i -> true) : null;
    }

    /**
     * Huesos de la rama de un tronco que está al lado del camino a la cabeza (ver trunkBeside), para medirlo entero
     * (trunkSpace): los segmentos de Onix, la cadena de Ekans. Por el hueso del tronco.
     */
    private static final Map<ModelPart, List<HeadBone>> TRUNK_BRANCHES = new WeakHashMap<>();

    /**
     * Lo que no es tronco aunque cuelgue de su rama: extremidades (las manos de Haunter, los tentáculos de Tentacruel)
     * y piezas de la cara (la mandíbula de Wailmer).
     */
    private static final Set<String> NOT_TRUNK = Set.of("leg", "legs", "foot", "feet", "toe", "toes", "claw",
            "claws", "hand", "hands", "finger", "fingers", "tentacle", "tentacles", "jaw", "mouth", "eye", "eyes", "face",
            "tongue", "tooth", "teeth");

    /**
     * El tronco cuando no está en el camino a la cabeza: en ~60 modelos el cuerpo cuelga de una rama al lado de la
     * cabeza o del cuello, no por encima ("torso" → "belly" y "torso" → "neck" → "head" en Snorlax; "torso2" hermano de
     * "head" en Yamper; "thorax" en Ariados; la cadena "segment1" → "segment2"... de Onix; "body" → "tail"... de
     * Ekans). Sin esto no tenían tronco (sin adornos) o el tronco era un cuello pequeño.
     * Se buscan las ramas que cuelgan del camino y no son adorno (ver category); dentro, los huesos con cubos propios
     * sin entrar en patas, brazos, manos, tentáculos, cara, cola ni cabezas. El tronco es el de mayor caja propia, y
     * su rama entera se apunta para medirlo (TRUNK_BRANCHES). Simulado en todos los modelos con
     * tools/species-table.ps1: cambia el tronco de 51 modelos base, todos de los que no tenían o lo tenían en el cuello.
     */
    private static HeadBone trunkBeside(List<ModelPart> headPath, List<HeadBone> heads, Tail tail) {
        HeadBone best = null;
        float bestVolume = 0;
        List<HeadBone> bestBranch = null;
        for (int i = 0; i < headPath.size() - 1; i++) {
            for (Map.Entry<String, Bone> child : ((Bone) (Object) headPath.get(i)).getChildren().entrySet()) {
                if (!((Object) child.getValue() instanceof ModelPart part) || headPath.contains(part)
                        || category(child.getKey()) != null) {
                    continue;
                }
                List<ModelPart> path = new ArrayList<>(headPath.subList(0, i + 1));
                path.add(part);
                List<HeadBone> branch = new ArrayList<>();
                collectBranch(child.getKey(), part, path, heads, tail, branch);
                for (HeadBone bone : branch) {
                    float volume = volume(ownBox(bone.part));
                    if (volume > bestVolume) {
                        bestVolume = volume;
                        best = bone;
                        bestBranch = branch;
                    }
                }
            }
        }
        if (best != null) {
            TRUNK_BRANCHES.put(best.part, List.copyOf(bestBranch));
        }
        return best;
    }

    /** Niveles de una cadena de tronco (ver chainTail) a partir de los que es el cuerpo de una serpiente. */
    private static final int SERPENT_CHAIN = 4;

    /**
     * La cola de una serpiente sin huesos "tail" (Onix, Steelix, Rayquaza: "segment1" → "segment2"..., cada uno con su
     * roca): si el tronco es una cadena al lado de la cabeza de SERPENT_CHAIN niveles o más y el modelo no tiene patas,
     * el último segmento es la cola, como la punta de Milotic o Ekans (ver findTail), y sale de la rama del tronco.
     * null si no.
     */
    private static Tail chainTail(ModelPart root, HeadBone trunk) {
        List<HeadBone> branch = TRUNK_BRANCHES.get(trunk.part);
        if (branch == null || branch.size() < 2 || hasLegs(root)) {
            return null;
        }
        int shallow = Integer.MAX_VALUE;
        HeadBone tip = null;
        for (HeadBone bone : branch) {
            shallow = Math.min(shallow, bone.path.size());
            if (tip == null || bone.path.size() > tip.path.size()
                    || bone.path.size() == tip.path.size() && cubes(bone.part) > cubes(tip.part)) {
                tip = bone;
            }
        }
        if (tip.path.size() - shallow + 1 < SERPENT_CHAIN || tip.part == trunk.part) {
            return null;
        }
        HeadBone cut = tip;
        TRUNK_BRANCHES.put(trunk.part, branch.stream().filter(bone -> !holds(cut.part, bone.part)).toList());
        return new Tail(List.of(tip), tip);
    }

    /** Los huesos con cubos propios de una rama de tronco (ver trunkBeside). */
    private static void collectBranch(String name, ModelPart part, List<ModelPart> path, List<HeadBone> heads,
                                      Tail tail, List<HeadBone> found) {
        if (isNotTrunk(name) || holdsHead(part, heads) || insideTail(part, tail)) {
            return;
        }
        if (hasOwnCubes(part)) {
            found.add(new HeadBone(part, path));
        }
        for (Map.Entry<String, Bone> child : ((Bone) (Object) part).getChildren().entrySet()) {
            if ((Object) child.getValue() instanceof ModelPart childPart) {
                List<ModelPart> childPath = new ArrayList<>(path);
                childPath.add(childPart);
                collectBranch(child.getKey(), childPart, childPath, heads, tail, found);
            }
        }
    }

    /** ¿Es una extremidad o una pieza de la cara (ver NOT_TRUNK), una pata o un brazo? */
    private static boolean isNotTrunk(String name) {
        if (isLegName(name) || isArm(name)) {
            return true;
        }
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            String word = stripModifiers(withoutNumber(token));
            if (NOT_TRUNK.contains(word) || word.startsWith("tentacle")) {
                return true;
            }
        }
        return false;
    }

    /** ¿Es (o está dentro de) alguna pieza de la cola? */
    private static boolean insideTail(ModelPart part, Tail tail) {
        if (tail != null) {
            for (HeadBone root : tail.roots) {
                if (holds(root.part, part)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** El hueso del camino (sin la cabeza) con la mayor caja de cubos propios entre los que cumplen la condición. */
    private static HeadBone biggestOwnCubes(List<ModelPart> headPath, IntPredicate allowed) {
        HeadBone trunk = null;
        float best = 0;
        for (int i = 0; i < headPath.size() - 1; i++) {
            if (!allowed.test(i)) {
                continue;
            }
            ModelPart part = headPath.get(i);
            float[] box = ownBox(part);
            float volume = box[0] > box[3] ? 0 : volume(box);
            if (volume > best) {
                best = volume;
                trunk = new HeadBone(part, new ArrayList<>(headPath.subList(0, i + 1)));
            }
        }
        return trunk;
    }

    /**
     * Adornos: los huesos que cuelgan de algún hueso del camino de la raíz a la cabeza principal (el tronco y lo que
     * lo rodea), con algún cubo, y cuyo nombre no es anatomía común (ver category): "bulb_master" y "vines" en
     * Ivysaur, "mushrooms" en Paras, "wing_left"/"wing_right" en Butterfree, "shell" en Lapras...
     * Ni las cabezas ni la cola cuentan, ni nada que las lleve dentro (las cabezas de más, con sus cuellos, van
     * aparte: ver findChains).
     * Con esta regla, 526 de los 904 modelos con cabeza de Cobblemon 1.8.1 tienen algún adorno.
     * Los que cuelgan de un hueso del cuello ("neck", "neck2", "lowernecc"...: el pelo de Eevee) se marcan como del cuello. Por
     * el nombre y no por estar más arriba que el tronco: las alas de Charizard cuelgan de "torso2", encima del
     * tronco ("torso"), y son del tronco.
     */
    private static List<Decoration> findDecorations(List<ModelPart> headPath, List<HeadBone> heads, Tail tail,
                                                    ModelPart cluster) {
        List<Decoration> found = new ArrayList<>();
        for (int i = 0; i < headPath.size() - 1; i++) {
            // "neck", "neck2", "lowernecc" (así, con cc, los de Groudon de AllTheMons)... Y lo que cuelga del hueso
            // del que cuelga un racimo de cabezas (las hojas de Exeggutor, de "upperHead"): va con ellas
            boolean neck = isNeck(headPath, i) || headPath.get(i) == cluster;
            for (Map.Entry<String, Bone> child : ((Bone) (Object) headPath.get(i)).getChildren().entrySet()) {
                if (!((Object) child.getValue() instanceof ModelPart part) || headPath.contains(part)) {
                    continue;
                }
                if (cubes(part) == 0 || holdsHead(part, heads) || holdsTail(part, tail)) {
                    continue;
                }
                String category = category(child.getKey());
                // Los brazos son anatomía, pero se apuntan: un cuerpo sin cabeza puede llevarlos (ver graft)
                if (category == null && isArm(child.getKey())) {
                    category = ARM;
                }
                if (category == null) {
                    continue;
                }
                List<ModelPart> path = new ArrayList<>(headPath.subList(0, i + 1));
                path.add(part);
                found.add(new Decoration(category, new HeadBone(part, path), neck));
            }
        }
        return found;
    }

    /** Nombre (en minúsculas) con el que un hueso cuelga de su padre; "" si no es hijo suyo. */
    private static String boneName(ModelPart parent, ModelPart child) {
        for (Map.Entry<String, Bone> entry : ((Bone) (Object) parent).getChildren().entrySet()) {
            if ((Object) entry.getValue() == child) {
                return entry.getKey().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    /**
     * Clase de adorno de un hueso según su nombre, o null si es anatomía común. Se parte por "_", se quitan números
     * finales y trozos que solo dicen dónde está ("left", "front"...) y se pasa a singular: "wing_left" y
     * "closed_wings" → "wing", "bulb_master" → "bulb". Si algún trozo es anatomía ("front_legs", "inner_arm_left",
     * "neck_fluff") no es un adorno.
     * Algunos modelos (los de AllTheMons) juntan las palabras sin "_": "leftleg", "humanarm", "leftspike4". Por eso
     * a cada trozo se le quitan también esas palabras por delante y se mira si acaba en anatomía.
     */
    private static String category(String name) {
        // Se llama muchísimo (también en cada fotograma, desde skull): una vez por nombre (ver NAME_RESULTS)
        Optional<String> cached = CATEGORIES.get(name);
        if (cached == null) {
            cached = Optional.ofNullable(computeCategory(name));
            CATEGORIES.put(name, cached);
        }
        return cached.orElse(null);
    }

    private static String computeCategory(String name) {
        // Huesos que crea Cobblemon, no el autor del modelo: cubos girados ("%tophalf%0", ver ownBox) y localizadores
        // internos. El cubo girado del torso de Groudon se pegaba como si fuera un adorno
        if (name.startsWith("%") || name.startsWith("internal_locator")) {
            return null;
        }
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            if (ALWAYS_DECORATION.contains(withoutNumber(token))) {
                return "kid";
            }
        }
        String category = null;
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            token = stripModifiers(withoutNumber(token));
            if (token.isEmpty() || NAME_MODIFIERS.contains(token)) {
                continue;
            }
            String singular = token.length() > 3 && token.endsWith("s") ? token.substring(0, token.length() - 1) : token;
            if (isAnatomy(token) || isAnatomy(singular)) {
                return null;
            }
            if (category == null) {
                category = singular;
            }
        }
        return category;
    }

    /** ¿Es un brazo ("arm_right", "inner_arm_left", "upperarm", "shoulder_left")? */
    private static boolean isArm(String name) {
        return ARMS.computeIfAbsent(name, FusionGraft::computeIsArm);
    }

    private static boolean computeIsArm(String name) {
        // Los huesos de Cobblemon (cubos girados, localizadores) no
        if (name.startsWith("%") || name.startsWith("internal_locator")) {
            return false;
        }
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            String word = stripModifiers(withoutNumber(token));
            if (word.equals("arm") || word.equals("arms") || word.equals("shoulder")) {
                return true;
            }
        }
        return false;
    }

    /** Quita del principio las palabras de posición pegadas: "leftspike" → "spike", "upperarm" → "arm". */
    private static String stripModifiers(String token) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String modifier : NAME_MODIFIERS) {
                // "l"/"r" sueltas no: se comerían la primera letra de cualquier palabra
                if (modifier.length() >= 3 && token.startsWith(modifier) && token.length() - modifier.length() >= 3) {
                    token = token.substring(modifier.length());
                    changed = true;
                }
            }
        }
        return token;
    }

    /** ¿Es anatomía, o acaba en una palabra de anatomía ("humanarm", "forehead")? */
    private static boolean isAnatomy(String word) {
        if (ANATOMY.contains(word)) {
            return true;
        }
        for (String part : ANATOMY) {
            if (part.length() >= 3 && word.endsWith(part)) {
                return true;
            }
        }
        return false;
    }

    /** ¿Lleva dentro (o es) alguna pieza de la cola? */
    private static boolean holdsTail(ModelPart part, Tail tail) {
        if (tail != null) {
            for (HeadBone root : tail.roots) {
                if (holds(part, root.part)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** ¿Lleva dentro (o es) alguna de las cabezas? */
    private static boolean holdsHead(ModelPart part, List<HeadBone> heads) {
        for (HeadBone head : heads) {
            if (holds(part, head.part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holds(ModelPart node, ModelPart target) {
        if (node == target) {
            return true;
        }
        for (Bone child : ((Bone) (Object) node).getChildren().values()) {
            if ((Object) child instanceof ModelPart part && holds(part, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * La cola de un modelo: los huesos cuyo nombre empieza por "tail" ("tail", "tail1", "tail_fluff"...) que cuelgan
     * del mismo hueso que el primero, cada uno con toda su cadena ("tail2", "tail3"... cuelgan de él). En Cobblemon
     * 1.8.1 la tienen 692 de 1142 modelos, casi siempre colgando del tronco. Varias piezas: Luxray (cadena + cinco
     * mechones), Ninetales... No cuentan las de dentro de una cabeza (pelos: ya van con la cabeza) ni las que cuelgan
     * de otro sitio que la primera.
     */
    private static Tail findTail(ModelPart root, List<HeadBone> heads) {
        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, name -> name.startsWith("tail"), current, paths);

        List<HeadBone> roots = new ArrayList<>();
        ModelPart parent = null;
        // collectPaths recorre de arriba abajo: el principio de cada cola sale antes que los huesos de su cadena
        for (List<ModelPart> path : paths) {
            if (path.size() < 2 || insideAnother(path, heads) || insideAnother(path, roots)) {
                continue;
            }
            ModelPart from = path.get(path.size() - 2);
            if (parent == null) {
                parent = from;
            } else if (from != parent) {
                continue;
            }
            roots.add(new HeadBone(path.get(path.size() - 1), path));
        }
        if (roots.isEmpty()) {
            return null;
        }
        HeadBone anchor = roots.get(0);
        for (HeadBone candidate : roots) {
            if (cubes(candidate.part) > cubes(anchor.part)) {
                anchor = candidate;
            }
        }
        // Serpientes y peces: sin patas, la "cola" es media cuerpo (Ekans, Milotic, Magikarp, Volcarona...): se
        // cambiaba el cuerpo entero por la cola del otro. En esos la cola es solo la punta. Con patas, una cola enorme
        // es una cola (Pachirisu, Ninetales); y si es poca cosa (las aletas del final de Gyarados), también
        // Y si de la cola cuelgan patas, es el cuerpo de un ciempiés (Centiskorch: "tail", "tail2"... con sus patas)
        float share = 0;
        boolean legsOnTail = false;
        for (HeadBone candidate : roots) {
            share += volume(candidate.part);
            legsOnTail |= hasLegs(candidate.part);
        }
        if (share > SERPENT_TAIL_SHARE * volume(root) && !hasLegs(root) || legsOnTail) {
            HeadBone tip = tip(anchor);
            if (tip.part != anchor.part) {
                return new Tail(List.of(tip), tip);
            }
        }
        return new Tail(List.copyOf(roots), anchor);
    }

    /**
     * El último eslabón de una cola: siguiendo los hijos "tail..." con volumen propio ("tail2", "tail3"...), hasta el
     * último; con lo que lleve colgando (las aletas de Milotic, el cascabel de Ekans). Siguiendo cualquier hijo se
     * acababa en una aleta suelta: se ocultaba solo esa y se veían las dos colas.
     */
    private static HeadBone tip(HeadBone start) {
        List<ModelPart> path = new ArrayList<>(start.path);
        while (true) {
            ModelPart next = null;
            for (Map.Entry<String, Bone> child : ((Bone) (Object) path.getLast()).getChildren().entrySet()) {
                if (!child.getKey().toLowerCase(Locale.ROOT).startsWith("tail")
                        || !((Object) child.getValue() instanceof ModelPart part)) {
                    continue;
                }
                // Sin volumen propio es un grupo ("tail_fins") o una pieza plana: no es un eslabón
                float[] own = ownBox(part);
                if (own[0] > own[3] || volume(own) <= 0) {
                    continue;
                }
                if (next == null || cubes(part) > cubes(next)) {
                    next = part;
                }
            }
            if (next == null) {
                return new HeadBone(path.getLast(), path);
            }
            path.add(next);
        }
    }

    /** ¿Tiene el modelo algún hueso de pierna o pie ("leg_left", "leftleg", "foot_front", "legs"...)? */
    private static boolean hasLegs(ModelPart root) {
        List<List<ModelPart>> found = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, FusionGraft::isLegName, current, found);
        return !found.isEmpty();
    }

    /** ¿Es un hueso de pierna o pie ("leg_left", "leftleg", "legs", "foot_front", "lleg")? */
    private static boolean isLegName(String name) {
        return LEG_NAMES.computeIfAbsent(name, FusionGraft::computeIsLegName);
    }

    private static boolean computeIsLegName(String name) {
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            String word = stripModifiers(withoutNumber(token));
            // "lleg", "rfoot" (Groudon de AllTheMons): la l/r pegada; stripModifiers no quita letras sueltas
            if (LEGS.contains(word) || STUCK_SIDE_LEG.matcher(word).matches()) {
                return true;
            }
        }
        return false;
    }

    /** "spike12" → "spike" (con el patrón compilado una vez: replaceAll lo compila en cada llamada). */
    private static String withoutNumber(String token) {
        return TRAILING_DIGITS.matcher(token).replaceAll("");
    }

    /**
     * Extremidades para moverse que cuelgan fuera de la cabeza: huesos cuyo nombre empieza por pierna, pie, dedo,
     * tentáculo o cola (con todo lo que llevan colgando). Brazos, manos, alas... no se tocan.
     *
     * @param path ruta del hueso como la construye ModelPart.visit: la del padre + "/" + nombre
     */
    private static void collectLimbs(ModelPart node, ModelPart head, String path, Map<ModelPart, String> limbs) {
        for (Map.Entry<String, Bone> child : ((Bone) (Object) node).getChildren().entrySet()) {
            // Lo de dentro de la cabeza es de la cabeza (la cola de un peinado, p. ej.)
            if (!((Object) child.getValue() instanceof ModelPart part) || part == head) {
                continue;
            }
            String childPath = path + "/" + child.getKey();
            if (isLimb(child.getKey())) {
                limbs.put(part, childPath);
            } else {
                collectLimbs(part, head, childPath, limbs);
            }
        }
    }

    private static boolean isLimb(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return name.startsWith("leg") || name.startsWith("foot") || name.startsWith("feet")
                || name.startsWith("toe") || name.startsWith("tentacle") || name.startsWith("tail");
    }

    /**
     * Si la cabeza no contiene ninguna pieza de la cara (ojos, cara, boca), la amplía hasta el primer hueso que
     * contenga la cabeza y la cara. Koffing: "locator_head" cuelga de la esfera ("body_inflate"), pero la cara es
     * hermana de la esfera; sin esto, como cabeza se pegaba sin cara y como cuerpo la cara se quedaba flotando.
     * Dodrio no cambia: su "head4" ya tiene sus ojos.
     */
    private static List<ModelPart> withFace(ModelPart root, List<ModelPart> head) {
        List<List<ModelPart>> faces = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, FusionGraft::isFace, current, faces);

        ModelPart part = head.get(head.size() - 1);
        List<ModelPart> common = head;
        for (List<ModelPart> face : faces) {
            if (face.contains(part)) {
                return head;
            }
            // Parte común de los dos caminos desde la raíz = el primer hueso que contiene a los dos
            int shared = 0;
            while (shared < common.size() && shared < face.size() && common.get(shared) == face.get(shared)) {
                shared++;
            }
            common = common.subList(0, shared);
        }
        return new ArrayList<>(common);
    }

    /**
     * Si la cabeza es un grupo sin cubos propios (solo junta las piezas de la cara), la sube hasta el primer hueso del
     * camino que sí tenga cubos: el "cráneo" sobre el que va pegada la cara. Darmanitan: "torso_rotation" lleva ojos,
     * boca y cejas, pero su cabeza es "upper_torso"; sin esto, como cabeza se pegaba solo la cara (sin nada detrás) y
     * como cuerpo seguía entero, con la cabeza nueva metida dentro. Simulado en todos los modelos (Cobblemon y
     * AllTheMons): solo cambian los Darmanitan; si ningún hueso por encima tiene cubos (Wigglytuff), se queda igual.
     */
    private static List<ModelPart> withSkull(List<ModelPart> head) {
        if (hasOwnCubes(head.get(head.size() - 1))) {
            return head;
        }
        // La raíz no: ocultarla ocultaría el Pokémon entero
        for (int i = head.size() - 2; i >= 1; i--) {
            if (hasOwnCubes(head.get(i))) {
                return new ArrayList<>(head.subList(0, i + 1));
            }
        }
        return head;
    }

    private static boolean hasOwnCubes(ModelPart part) {
        float[] box = ownBox(part);
        return box[0] <= box[3];
    }

    private static boolean isFace(String name) {
        return name.startsWith("eye") || name.startsWith("face") || name.startsWith("mouth");
    }

    /** Volumen de los cubos de un hueso y sus hijos, en píxeles³ de modelo (sin giros: solo para comparar). */
    private static float volume(ModelPart part) {
        float[] total = {0};
        part.visit(new PoseStack(), (pose, path, index, cube) ->
                total[0] += (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ));
        return total[0];
    }

    /** Número de cubos de un hueso y sus hijos. */
    private static int cubes(ModelPart part) {
        int[] total = {0};
        part.visit(new PoseStack(), (pose, path, index, cube) -> total[0]++);
        return total[0];
    }

    private static boolean containsHead(List<HeadBone> heads, ModelPart part) {
        for (HeadBone head : heads) {
            if (head.part == part) {
                return true;
            }
        }
        return false;
    }

    /** ¿Alguna de las cabezas ya elegidas está por encima en el camino? (p. ej. un "head2" dentro de "head") */
    private static boolean insideAnother(List<ModelPart> path, List<HeadBone> heads) {
        for (HeadBone head : heads) {
            if (path.subList(0, path.size() - 1).contains(head.part)) {
                return true;
            }
        }
        return false;
    }

    /** Huesos desde la raíz hasta el primero cuyo nombre cumpla la condición, o null. */
    private static List<ModelPart> firstPath(ModelPart root, Predicate<String> name) {
        List<List<ModelPart>> found = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, name, current, found);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Todos los caminos desde la raíz hasta huesos cuyo nombre cumpla la condición. */
    private static void collectPaths(ModelPart node, Predicate<String> name, List<ModelPart> current,
                                     List<List<ModelPart>> found) {
        // Cobblemon hace que ModelPart implemente Bone con un mixin: para Java son tipos sin relación, de ahí el (Object)
        for (Map.Entry<String, Bone> child : ((Bone) (Object) node).getChildren().entrySet()) {
            if (!((Object) child.getValue() instanceof ModelPart part)) {
                continue;
            }
            current.add(part);
            // En minúsculas: la cabeza de Iron Valiant (AllTheMons) es "Head"; las colas de Iron Bundle, "Tail1"...
            if (name.test(child.getKey().toLowerCase(Locale.ROOT))) {
                found.add(new ArrayList<>(current));
            }
            collectPaths(part, name, current, found);
            current.remove(current.size() - 1);
        }
    }

    /** Giro acumulado a lo largo de un camino de huesos, tal como lo aplica ModelPart.translateAndRotate. */
    private static Quaternionf rotationAlong(List<ModelPart> path) {
        Quaternionf rotation = new Quaternionf();
        for (ModelPart part : path) {
            rotation.mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot));
        }
        return rotation;
    }

    /**
     * Tamaño de una cabeza, en bloques: la media de ancho, alto y fondo de su "cráneo" (el cubo más grande) junto
     * con las piezas grandes pegadas a él (hocico, mandíbula...).
     *  - Solo el cráneo se quedaba corto en cabezas alargadas: el de Charizard es 7x7x5, pero con hocico y mandíbula
     *    su cabeza se ve mucho mayor; la de Miltank, en cambio, es casi toda un cubo de 11x11x12.
     *  - Orejas, cuernos, pelos o bigotes no cuentan: los planos de grosor cero no tienen volumen y las piezas finas
     *    tienen poco (menos de PIECE_SHARE del cráneo).
     *  - Como mucho SKULL_GROWTH veces el cráneo, por si se cuela algo grande que no es cabeza (la cresta de
     *    Blaziken mide 26 de alto).
     * Si no hay ningún cubo con volumen, se usa la caja de todos los cubos.
     */
    private static float size(ModelPart part) {
        Float cached = SIZES.get(part);
        if (cached != null) {
            return cached;
        }
        float[] all = emptyBox();
        List<float[]> boxes = new ArrayList<>();
        // Volumen de cada cubo en sí (no el de su caja, mayor en los girados: ver skullCenter)
        List<Float> volumes = new ArrayList<>();
        List<Boolean> decorations = new ArrayList<>();
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            float[] box = emptyBox();
            includeCube(box, pose, cube);
            includeCube(all, pose, cube);
            boxes.add(box);
            volumes.add(cubeVolume(cube));
            decorations.add(inDecoration(path));
        });

        // El cráneo, como en skullCenter: sin contar los adornos si queda algún cubo
        int skullIndex = -1;
        for (boolean withDecorations : new boolean[]{false, true}) {
            for (int i = 0; i < boxes.size(); i++) {
                if (volumes.get(i) > 0 && (withDecorations || !decorations.get(i))
                        && (skullIndex < 0 || volumes.get(i) > volumes.get(skullIndex))) {
                    skullIndex = i;
                }
            }
            if (skullIndex >= 0) {
                break;
            }
        }

        float size;
        if (skullIndex >= 0) {
            float[] skull = boxes.get(skullIndex);
            float skullSize = meanSide(skull);
            float[] head = skull.clone();
            for (int i = 0; i < boxes.size(); i++) {
                float[] box = boxes.get(i);
                if (volumes.get(i) >= PIECE_SHARE * volumes.get(skullIndex) && touches(box, skull, 1 / 16F)) {
                    include(head, new Vector3f(box[0], box[1], box[2]));
                    include(head, new Vector3f(box[3], box[4], box[5]));
                }
            }
            size = Math.min(meanSide(head), skullSize * SKULL_GROWTH);
        } else if (all[0] <= all[3]) {
            size = ((all[3] - all[0]) + (all[4] - all[1]) + (all[5] - all[2])) / 3F;
        } else {
            // Sin cubos (hueso vacío): tamaño neutro, la cabeza se queda a escala 1
            size = 1F;
        }
        size = Math.max(size, 0.01F);
        SIZES.put(part, size);
        return size;
    }

    /** Volumen de un cubo (en píxeles³ de modelo), sin contar su giro. */
    private static float cubeVolume(ModelPart.Cube cube) {
        return (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
    }

    private static float volume(float[] box) {
        return (box[3] - box[0]) * (box[4] - box[1]) * (box[5] - box[2]);
    }

    private static float meanSide(float[] box) {
        return ((box[3] - box[0]) + (box[4] - box[1]) + (box[5] - box[2])) / 3F;
    }

    /** ¿Se tocan (o casi: a menos de "margin") las dos cajas? */
    private static boolean touches(float[] a, float[] b, float margin) {
        for (int axis = 0; axis < 3; axis++) {
            if (a[axis] > b[axis + 3] + margin || a[axis + 3] < b[axis] - margin) {
                return false;
            }
        }
        return true;
    }

    /** Caja vacía {minX, minY, minZ, maxX, maxY, maxZ}, lista para ir ampliándola con include. */
    private static float[] emptyBox() {
        return new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    }

    /** Amplía la caja con las 8 esquinas de un cubo ya transformadas (en bloques). */
    private static void includeCube(float[] box, PoseStack.Pose pose, ModelPart.Cube cube) {
        // Las medidas de los cubos van en píxeles de modelo (1/16 de bloque)
        for (int corner = 0; corner < 8; corner++) {
            include(box, pose.pose().transformPosition(new Vector3f(
                    ((corner & 1) == 0 ? cube.minX : cube.maxX) / 16F,
                    ((corner & 2) == 0 ? cube.minY : cube.maxY) / 16F,
                    ((corner & 4) == 0 ? cube.minZ : cube.maxZ) / 16F)));
        }
    }

    private static void include(float[] box, Vector3f point) {
        box[0] = Math.min(box[0], point.x);
        box[1] = Math.min(box[1], point.y);
        box[2] = Math.min(box[2], point.z);
        box[3] = Math.max(box[3], point.x);
        box[4] = Math.max(box[4], point.y);
        box[5] = Math.max(box[5], point.z);
    }
}
