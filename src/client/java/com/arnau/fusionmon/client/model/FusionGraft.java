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
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
    private static final float MIN_SCALE = 0.4F;
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

    /** Modo de ver las fusiones: cabeza sobre cuerpo (por defecto) o colores (/fusionvisual colors). */
    private static boolean enabled = true;
    /** Si se cambia la cola del cuerpo por la de la especie de la cabeza (/fusionvisual tail on|off). */
    private static boolean tails = true;
    /** Si se pegan al cuerpo los adornos de la especie de la cabeza (/fusionvisual decor on|off). */
    private static boolean decorations = true;

    /**
     * Trozos de nombre de hueso que no dicen qué pieza es, solo dónde está o en qué estado ("wing_left",
     * "closed_wings", "leaf_front2"...): no cuentan para saber la clase de un adorno.
     */
    private static final Set<String> NAME_MODIFIERS = Set.of("left", "right", "l", "r", "front", "back", "top",
            "bottom", "upper", "lower", "base", "master", "mid", "middle", "inner", "outer", "open", "closed", "side",
            "flying", "big", "small");
    /**
     * Anatomía común a casi todos los modelos (contado en los 1142 de Cobblemon 1.8.1): un hueso con alguno de estos
     * trozos en el nombre no es un adorno. "ftorso"/"fleg"/"fbody" son grupos de brazos y patas delanteras;
     * "bone", "cube", "group"... son nombres sin significado que deja Blockbench.
     */
    private static final Set<String> ANATOMY = Set.of("head", "neck", "torso", "ftorso", "body", "fbody", "chest",
            "belly", "abdomen", "thorax", "waist", "hip", "pelvis", "butt", "spine", "segment", "leg", "fleg", "thigh",
            "knee", "foot", "feet", "toe", "arm", "shoulder", "hand", "finger", "tail", "tentacle", "jaw", "mouth",
            "eye", "face", "tongue", "locator", "bone", "cube", "group", "root", "main", "seat", "shadow");

    /** Estado (entidad o menú) → especie de la cabeza; se apunta cuando Cobblemon pide el modelo. */
    private static final Map<PosableState, ResourceLocation> HEADS = new WeakHashMap<>();
    /** Estado de animación propio de la cabeza pegada de cada fusión (para que parpadee a su ritmo, etc.). */
    private static final Map<PosableState, FloatingState> HEAD_STATES = new WeakHashMap<>();
    /** Cabezas de cada modelo (no cambian). */
    private static final Map<PosableModel, ModelHeads> HEAD_BONES = new WeakHashMap<>();
    /** Tamaño de cada cabeza con sus hijos, para escalar la cabeza nueva. */
    private static final Map<ModelPart, Float> SIZES = new WeakHashMap<>();
    /** Caja de cada hueso con sus hijos, en su propio marco (para apoyar los modelos enteros, ver groundOffset). */
    private static final Map<ModelPart, float[]> BOXES = new WeakHashMap<>();
    /** Caja de los cubos propios de cada tronco (sin sus hijos), en su propio marco: para colocar los adornos. */
    private static final Map<ModelPart, float[]> OWN_BOXES = new WeakHashMap<>();
    /** Modelos de cabeza que ya han fallado al pintarse (para avisar en el log una sola vez). */
    private static final Set<PosableModel> WARNED = Collections.newSetFromMap(new WeakHashMap<>());

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
     */
    private record Graft(FusionBody body, VaryingRenderableResolver headResolver, VaryingRenderableResolver bodyResolver,
                         PosableModel headModel, PosableModel bodyModel, HeadBone head, boolean whole,
                         Map<ModelPart, String> limbs, List<HeadBone> bodies, Tail headTail, Tail bodyTail,
                         List<Decoration> trunkDecorations, List<Decoration> neckDecorations, HeadBone headTrunk,
                         HeadBone bodyTrunk, HeadBone headSpine, HeadBone bodySpine, List<ModelPart> hidden) {
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
     * @param heads cabezas que se sustituyen cuando es el CUERPO; vacía si al ocultarlas no quedaría nada
     *              (Koffing, Voltorb...). Tentacool sí tiene: su "cabeza" es casi todo, pero quedan los tentáculos
     * @param whole si cuando es la CABEZA se pega el modelo entero ("todo cabeza")
     * @param limbs al pegar el modelo entero, extremidades para moverse que quedan fuera de su "cabeza" y no se
     *              pintan (los tentáculos de Tentacool, la cola de Haunter): el cuerpo ya pone las suyas.
     *              Hueso → su ruta tal como la da ModelPart.visit ("/tentacool/body/tentacle_left")
     * @param tail  la cola del modelo (ver findTail), o null
     * @param trunk el tronco del modelo (ver findTrunk), o null
     * @param decorations los adornos que cuelgan del camino hasta la cabeza principal (ver findDecorations)
     * @param spineEnd el principio del cuello (ver findSpineEnd), o null
     */
    private record ModelHeads(List<HeadBone> heads, boolean whole, Map<ModelPart, String> limbs, Tail tail,
                              HeadBone trunk, List<Decoration> decorations, HeadBone spineEnd) {
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
    }

    public static void setDecorations(boolean value) {
        decorations = value;
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

    private static Graft graft(ResourceLocation name, PosableState state, boolean evenIfDisabled) {
        if (!enabled && !evenIfDisabled) {
            return null;
        }
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
        PosableModel bodyModel = bodyResolver.getPoser(state);
        if (headModel == bodyModel) {
            return null;
        }
        ModelHeads heads = HEAD_BONES.computeIfAbsent(headModel, FusionGraft::findHeads);
        ModelHeads bodyHeads = HEAD_BONES.computeIfAbsent(bodyModel, FusionGraft::findHeads);
        List<HeadBone> bodies = bodyHeads.heads();
        // Un cuerpo sin cabeza no tiene dónde pegar nada: modo colores
        if (bodies.isEmpty()) {
            return null;
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
        // Al pegar un modelo entero, sus adornos ya van con él
        if (!whole) {
            if (decorations) {
                for (Decoration decoration : heads.decorations()) {
                    (decoration.neck ? neckDecorations : trunkDecorations).add(decoration);
                }
            }
            // Si el cuerpo no tiene cola no hay una donde engancharla: se coloca sobre su tronco como un adorno
            if (tails && heads.tail() != null && bodyHeads.tail() == null) {
                for (HeadBone root : heads.tail().roots()) {
                    trunkDecorations.add(new Decoration("tail", root, false));
                }
            }
            // Sin tronco en algún lado no hay dónde colocarlos
            if (heads.trunk() == null || bodyHeads.trunk() == null || heads.spineEnd() == null
                    || bodyHeads.spineEnd() == null) {
                trunkDecorations.clear();
            }
        }

        List<ModelPart> hidden = new ArrayList<>();
        for (HeadBone bodyHead : bodies) {
            hidden.add(bodyHead.part);
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
                neckDecorations, heads.trunk(), bodyHeads.trunk(), heads.spineEnd(), bodyHeads.spineEnd(), hidden);
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
        active = null;
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
        // a la cabeza su posición y giro para pintarla)
        Matrix4f toHead = graft.neckDecorations.isEmpty() ? null : matrixAlong(graft.head.path).invert();

        for (HeadBone bodyHead : graft.bodies) {
            float scale = Math.clamp(size(bodyHead.part) / size(head), MIN_SCALE, MAX_SCALE) * INFLATE;
            // Quitamos el giro que traía la cabeza del cuerpo y ponemos el de la cabeza en su modelo:
            // así mira hacia donde mira en su modelo (y hacia el jugador, con su animación de mirar)
            Quaternionf correction = rotationAlong(bodyHead.path).conjugate().mul(headRotation);

            PartPose saved = head.storePose();
            boolean visible = head.visible;
            List<Boolean> limbsWereVisible = new ArrayList<>();
            poseStack.pushPose();
            try {
                // Mismas transformaciones que ha recibido la cabeza del cuerpo: raíz → ... → cuello → cabeza
                for (ModelPart part : bodyHead.path) {
                    part.translateAndRotate(poseStack);
                }
                poseStack.mulPose(correction);
                if (graft.whole) {
                    // Un modelo entero no tiene un "cuello" que poner en el pivote: su pivote suele estar en el
                    // suelo o en el centro de la esfera (se hundiría en el cuerpo). Lo apoyamos donde acababa la
                    // cabeza del cuerpo: su punto más bajo con el de ella, centrados en horizontal
                    Vector3f offset = groundOffset(bodyHead.part, correction, head, graft.limbs, scale);
                    poseStack.translate(offset.x, offset.y, offset.z);
                }
                poseStack.scale(scale, scale, scale);

                // La cabeza nueva sin su posición ni giro propios (ya van arriba), con su pivote en el del cuerpo
                head.setPos(0, 0, 0);
                head.setRotation(0, 0, 0);
                head.visible = true;
                setVisible(graft.limbs.keySet(), limbsWereVisible, false);
                head.render(poseStack, consumer, light, overlay, color);

                // Los adornos del cuello van con la cabeza: misma posición y giro respecto a ella que en su modelo
                if (toHead != null) {
                    for (Decoration decoration : graft.neckDecorations) {
                        poseStack.pushPose();
                        poseStack.mulPose(new Matrix4f(toHead).mul(matrixAlong(parentPath(decoration.bone))));
                        decoration.bone.part.render(poseStack, consumer, light, overlay, color);
                        poseStack.popPose();
                    }
                }
            } finally {
                // Pase lo que pase, la pila de transformaciones y la cabeza quedan como estaban
                restoreVisible(graft.limbs.keySet(), limbsWereVisible);
                head.loadPose(saved);
                head.visible = visible;
                poseStack.popPose();
            }
        }

        if (graft.headTail != null) {
            renderTail(graft, consumer, poseStack, light, overlay, color);
        }
        if (!graft.trunkDecorations.isEmpty()) {
            renderDecorations(graft, consumer, poseStack, light, overlay, color);
        }
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
     * adorno se pasa igual, de los ejes de un Pokémon a los del otro.
     * Se escala por lo que mide un tronco respecto al otro. Las animaciones de su especie (ya aplicadas, ver
     * animateHead) los mueven y deciden cuáles se ven (alas abiertas o cerradas...): uno oculto no se pinta.
     */
    private static void renderDecorations(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light,
                                          int overlay, int color) {
        Matrix4f headTrunk = matrixAlong(graft.headTrunk.path);
        Matrix4f bodyTrunk = matrixAlong(graft.bodyTrunk.path);
        Matrix3f headFrame = spineFrame(headTrunk, matrixAlong(graft.headSpine.path));
        Matrix3f bodyFrame = spineFrame(bodyTrunk, matrixAlong(graft.bodySpine.path));
        float[] headBox = frameBox(ownBox(graft.headTrunk.part), headTrunk, headFrame);
        float[] bodyBox = frameBox(ownBox(graft.bodyTrunk.part), bodyTrunk, bodyFrame);
        if (headBox[0] > headBox[3] || bodyBox[0] > bodyBox[3]) {
            return;
        }
        float trunkScale = trunkScale(graft);
        float[] bodyOwn = ownBox(graft.bodyTrunk.part);
        float bodyTrunkLength = Math.max(bodyOwn[3] - bodyOwn[0], Math.max(bodyOwn[4] - bodyOwn[1], bodyOwn[5] - bodyOwn[2]));
        Vector3f headOrigin = headTrunk.getTranslation(new Vector3f());
        Vector3f bodyOrigin = bodyTrunk.getTranslation(new Vector3f());
        // De los ejes del Pokémon de la cabeza a los del cuerpo (un giro a lo ancho: el eje X es el mismo)
        Quaternionf frameChange = new Quaternionf().setFromNormalized(
                new Matrix3f(bodyFrame).mul(new Matrix3f(headFrame).transpose()));

        for (Decoration decoration : graft.trunkDecorations) {
            ModelPart part = decoration.bone.part;
            if (!part.visible) {
                continue;
            }
            // Pivote del adorno respecto al pivote de su tronco, en los ejes de la columna de su modelo
            Matrix4f matrix = matrixAlong(decoration.bone.path);
            Vector3f pivot = new Matrix3f(headFrame).transpose()
                    .transform(matrix.getTranslation(new Vector3f()).sub(headOrigin));
            // El mismo sitio proporcional en el tronco del cuerpo, y de vuelta a los ejes del modelo
            Vector3f placed = bodyFrame.transform(new Vector3f(remap(pivot.x, headBox, bodyBox, 0),
                    remap(pivot.y, headBox, bodyBox, 1), remap(pivot.z, headBox, bodyBox, 2))).add(bodyOrigin);
            Quaternionf rotation = new Quaternionf(frameChange).mul(matrix.getNormalizedRotation(new Quaternionf()));
            float scale = trunkScale;
            if (decoration.category.equals("tail")) {
                // Una cola puesta en un cuerpo que no tenía: como mucho tan larga como su tronco
                float tailLength = length(part);
                if (tailLength > 0) {
                    scale = Math.min(scale, bodyTrunkLength / tailLength);
                }
            }

            PartPose saved = part.storePose();
            poseStack.pushPose();
            try {
                poseStack.translate(placed.x, placed.y, placed.z);
                poseStack.mulPose(rotation);
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
     * Lleva una coordenada de la caja de un tronco a la misma posición proporcional en la del otro (eje 0, 1, 2).
     * Lo que nace fuera de la caja se queda en su borde: la cola de Kyogre cuelga muy por detrás de su tronco y,
     * llevada en proporción a otro, quedaba flotando lejos de él.
     */
    private static float remap(float value, float[] from, float[] to, int axis) {
        float extent = from[axis + 3] - from[axis];
        float t = extent < 1e-4F ? 0.5F : Math.clamp((value - from[axis]) / extent, 0F, 1F);
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
    private static Matrix3f spineFrame(Matrix4f trunk, Matrix4f spineEnd) {
        Vector3f across = new Vector3f(1, 0, 0);
        Vector3f spine = spineEnd.getTranslation(new Vector3f()).sub(trunk.getTranslation(new Vector3f()));
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
     * Caja de los cubos propios de un tronco (ownBox, en su marco) vista desde su pivote con los ejes de su columna:
     * se llevan sus 8 esquinas al modelo con la transformación del tronco y de ahí a esos ejes.
     */
    private static float[] frameBox(float[] local, Matrix4f trunk, Matrix3f frame) {
        if (local[0] > local[3]) {
            return local;
        }
        Vector3f origin = trunk.getTranslation(new Vector3f());
        Matrix3f toFrame = new Matrix3f(frame).transpose();
        float[] box = emptyBox();
        for (int corner = 0; corner < 8; corner++) {
            include(box, toFrame.transform(trunk.transformPosition(new Vector3f(
                    local[(corner & 1) == 0 ? 0 : 3],
                    local[(corner & 2) == 0 ? 1 : 4],
                    local[(corner & 4) == 0 ? 2 : 5])).sub(origin)));
        }
        return box;
    }

    /**
     * Escala de lo que se pega del cuerpo de la especie de la cabeza (cola, adornos): lo que mide el tronco del
     * cuerpo respecto al suyo. Así cada pieza conserva su tamaño en proporción: la cola pequeña de Lapras sigue
     * siendo pequeña en otro Pokémon (al igualar largos de cola salía enorme). 0 si falta algún tronco.
     */
    private static float trunkScale(Graft graft) {
        if (graft.headTrunk == null || graft.bodyTrunk == null) {
            return 0;
        }
        float[] head = ownBox(graft.headTrunk.part);
        float[] body = ownBox(graft.bodyTrunk.part);
        if (head[0] > head[3] || body[0] > body[3]) {
            return 0;
        }
        return Math.clamp(meanSide(body) / Math.max(meanSide(head), 0.01F), MIN_PIECE_SCALE, MAX_PIECE_SCALE);
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
                    : Math.clamp(bodyLength / headLength, MIN_SCALE, MAX_SCALE);
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
     * Desplazamiento (en el marco ya girado con la corrección) que apoya el modelo entero sobre el sitio de la
     * cabeza del cuerpo. En los modelos de Minecraft la Y crece hacia abajo: "lo más bajo" es la Y máxima.
     */
    private static Vector3f groundOffset(ModelPart bodyHead, Quaternionf correction, ModelPart model,
                                         Map<ModelPart, String> limbs, float scale) {
        float[] head = localBox(bodyHead, Map.of());
        // Sin las extremidades que no se pintan: si no, Tentacool se apoyaría en la punta de unos tentáculos ocultos
        float[] pasted = localBox(model, limbs);
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
        // visit no mira la visibilidad: lo que se salta se reconoce por la ruta del hueso del cubo
        float[] box = boxInOwnFrame(part, path -> {
            for (String limb : skip.values()) {
                if (path.equals(limb) || path.startsWith(limb + "/")) {
                    return false;
                }
            }
            return true;
        });
        BOXES.put(part, box);
        return box;
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

    /** ¿Es el nombre de un hijo que Cobblemon crea para un cubo girado ("%tophalf%0")? */
    private static boolean isRotatedCube(String name) {
        return name.startsWith("%") && name.indexOf('/') < 0;
    }

    /** Caja de los cubos de un hueso y sus hijos cuya ruta (la de ModelPart.visit) cumpla la condición. */
    private static float[] boxInOwnFrame(ModelPart part, Predicate<String> include) {
        PartPose saved = part.storePose();
        float xScale = part.xScale;
        float yScale = part.yScale;
        float zScale = part.zScale;
        float[] box = emptyBox();
        try {
            part.loadPose(PartPose.ZERO);
            part.xScale = 1;
            part.yScale = 1;
            part.zScale = 1;
            part.visit(new PoseStack(), (pose, path, index, cube) -> {
                if (include.test(path)) {
                    includeCube(box, pose, cube);
                }
            });
        } finally {
            part.loadPose(saved);
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
        }
        return box;
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
            return new ModelHeads(List.of(), true, Map.of(), null, null, List.of(), null);
        }

        boolean whole = false;
        Map<ModelPart, String> limbs = Map.of();
        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> primary = firstPath(root, "head"::equals);
        if (primary == null) {
            primary = firstPath(root, "locator_head"::equals);
            if (primary != null) {
                // Quitamos el localizador: la cabeza es su padre
                primary.remove(primary.size() - 1);
                primary = withFace(root, primary);
                // En los modelos sin hueso "head" ese padre casi siempre es casi todo el cuerpo ("torso", "body").
                // Como cabeza: se pega el modelo entero (bien apoyado, ver groundOffset), sin sus extremidades
                // para moverse (las manos de Haunter sí se quedan).
                // Como cuerpo se mira otra cosa: si al ocultarlo queda algo. El volumen no sirve para eso
                // (los tentáculos de Tentacool son finos: poco volumen, pero son lo que se ve de su cuerpo)
                ModelPart part = primary.get(primary.size() - 1);
                whole = volume(part) >= WHOLE_MODEL_SHARE * volume(root);
                if (cubes(part) == cubes(root)) {
                    return new ModelHeads(List.of(), true, Map.of(), null, null, List.of(), null);
                }
                if (whole) {
                    limbs = new LinkedHashMap<>();
                    collectLimbs(root, part, "", limbs);
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
            return new ModelHeads(heads, true, limbs, tail, null, List.of(), null);
        }
        List<ModelPart> headPath = heads.get(0).path;
        HeadBone trunk = findTrunk(headPath);
        return new ModelHeads(heads, whole, limbs, tail, trunk, findDecorations(headPath, heads, tail),
                findSpineEnd(headPath, trunk));
    }

    /**
     * Hasta dónde llega la columna, para orientar el Pokémon (ver spineFrame): el primer hueso del cuello después del
     * tronco ("neck", "neck2", "lowernecc"...) o, si no tiene cuello, la cabeza. Lapras: "neck2", delante del tronco
     * (su cabeza está muy arriba); Dragonite: "neck", encima.
     */
    private static HeadBone findSpineEnd(List<ModelPart> headPath, HeadBone trunk) {
        int from = trunk == null ? 1 : trunk.path.size();
        for (int i = Math.max(from, 1); i < headPath.size() - 1; i++) {
            String name = boneName(headPath.get(i - 1), headPath.get(i));
            if (name.contains("neck") || name.contains("necc")) {
                return new HeadBone(headPath.get(i), new ArrayList<>(headPath.subList(0, i + 1)));
            }
        }
        return new HeadBone(headPath.get(headPath.size() - 1), headPath);
    }

    /**
     * El tronco: en el camino de la raíz a la cabeza principal, el hueso cuyos cubos propios ocupan más ("torso" en
     * Ivysaur, "thorax" en Scyther, "body" en Butterfree). Los grupos vacíos ("body" en Ivysaur) no cuentan.
     */
    private static HeadBone findTrunk(List<ModelPart> headPath) {
        HeadBone trunk = null;
        float best = 0;
        for (int i = 0; i < headPath.size() - 1; i++) {
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
     * Ni las cabezas ni la cola cuentan, ni nada que las lleve dentro.
     * Con esta regla, 526 de los 904 modelos con cabeza de Cobblemon 1.8.1 tienen algún adorno.
     * Los que cuelgan de un hueso del cuello ("neck", "neck2", "lowernecc"...: el pelo de Eevee) se marcan como del cuello. Por
     * el nombre y no por estar más arriba que el tronco: las alas de Charizard cuelgan de "torso2", encima del
     * tronco ("torso"), y son del tronco.
     */
    private static List<Decoration> findDecorations(List<ModelPart> headPath, List<HeadBone> heads, Tail tail) {
        List<Decoration> found = new ArrayList<>();
        for (int i = 0; i < headPath.size() - 1; i++) {
            // "neck", "neck2", "lowernecc" (así, con cc, los de Groudon de AllTheMons)...
            String parentName = i > 0 ? boneName(headPath.get(i - 1), headPath.get(i)) : "";
            boolean neck = parentName.contains("neck") || parentName.contains("necc");
            for (Map.Entry<String, Bone> child : ((Bone) (Object) headPath.get(i)).getChildren().entrySet()) {
                if (!((Object) child.getValue() instanceof ModelPart part) || headPath.contains(part)) {
                    continue;
                }
                String category = category(child.getKey());
                if (category == null || cubes(part) == 0 || holdsHeadOrTail(part, heads, tail)) {
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
        // Huesos que crea Cobblemon, no el autor del modelo: cubos girados ("%tophalf%0", ver ownBox) y localizadores
        // internos. El cubo girado del torso de Groudon se pegaba como si fuera un adorno
        if (name.startsWith("%") || name.startsWith("internal_locator")) {
            return null;
        }
        String category = null;
        for (String token : name.toLowerCase(Locale.ROOT).split("_")) {
            token = stripModifiers(token.replaceAll("\\d+$", ""));
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

    /** ¿Lleva dentro (o es) alguna de las cabezas o piezas de la cola? */
    private static boolean holdsHeadOrTail(ModelPart part, List<HeadBone> heads, Tail tail) {
        if (tail != null) {
            for (HeadBone root : tail.roots) {
                if (holds(part, root.part)) {
                    return true;
                }
            }
        }
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
        return new Tail(List.copyOf(roots), anchor);
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
            if (name.test(child.getKey())) {
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
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            float[] box = emptyBox();
            includeCube(box, pose, cube);
            includeCube(all, pose, cube);
            boxes.add(box);
        });

        float[] skull = null;
        for (float[] box : boxes) {
            if (volume(box) > 0 && (skull == null || volume(box) > volume(skull))) {
                skull = box;
            }
        }

        float size;
        if (skull != null) {
            float skullSize = meanSide(skull);
            float[] head = skull.clone();
            for (float[] box : boxes) {
                if (volume(box) >= PIECE_SHARE * volume(skull) && touches(box, skull, 1 / 16F)) {
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
