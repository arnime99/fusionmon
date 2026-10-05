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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 *     (/fusionvisual tail on|off). Es la primera "pieza" que se cambia; luego vendrán otros adornos.
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
    /** Modelos de cabeza que ya han fallado al pintarse (para avisar en el log una sola vez). */
    private static final Set<PosableModel> WARNED = Collections.newSetFromMap(new WeakHashMap<>());

    // Estado "suelto" para pedir la textura original de la cabeza (con el shiny del cuerpo si hace falta)
    private static FloatingState textureState;

    // Lo que hay que deshacer al terminar de pintar el modelo del cuerpo
    private static Graft active;
    private static boolean[] hiddenWereVisible = new boolean[0];
    private static boolean bodyTailWasVisible;

    /**
     * @param head     la cabeza que se pega (la principal del modelo de la cabeza)
     * @param whole    si lo que se pega es el modelo entero de la cabeza ("todo cabeza")
     * @param limbs    extremidades que no se pintan al pegar el modelo entero (ver ModelHeads)
     * @param bodies   las cabezas del cuerpo que se sustituyen (varias en Doduo, Dodrio...)
     * @param headTail la cola de la especie de la cabeza que se pega, y bodyTail la del cuerpo que sustituye;
     *                 null las dos si no se cambia la cola
     */
    private record Graft(FusionBody body, VaryingRenderableResolver headResolver, VaryingRenderableResolver bodyResolver,
                         PosableModel headModel, PosableModel bodyModel, HeadBone head, boolean whole,
                         Map<ModelPart, String> limbs, List<HeadBone> bodies, HeadBone headTail, HeadBone bodyTail) {
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
     */
    private record ModelHeads(List<HeadBone> heads, boolean whole, Map<ModelPart, String> limbs, HeadBone tail) {
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
        // La cola solo se cambia si hay una en cada lado: si falta la del cuerpo no hay sitio donde engancharla
        boolean tail = tails && heads.tail() != null && bodyHeads.tail() != null;
        return new Graft(body, headResolver, bodyResolver, headModel, bodyModel, head, whole, heads.limbs(), bodies,
                tail ? heads.tail() : null, tail ? bodyHeads.tail() : null);
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

    /** Antes de pintar el modelo del cuerpo: ocultar sus cabezas. */
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

        hiddenWereVisible = new boolean[graft.bodies.size()];
        for (int i = 0; i < graft.bodies.size(); i++) {
            ModelPart bodyHead = graft.bodies.get(i).part;
            hiddenWereVisible[i] = bodyHead.visible;
            bodyHead.visible = false;
        }
        if (graft.bodyTail != null) {
            bodyTailWasVisible = graft.bodyTail.part.visible;
            graft.bodyTail.part.visible = false;
        }
        active = graft;
    }

    /** Después: volver a mostrar sus cabezas y pintar en su sitio la de la otra especie. */
    public static void afterRender(PosableModel model, PoseStack poseStack, int light, int overlay, int color) {
        Graft graft = active;
        if (graft == null || graft.bodyModel != model) {
            return;
        }
        active = null;
        for (int i = 0; i < graft.bodies.size(); i++) {
            graft.bodies.get(i).part.visible = hiddenWereVisible[i];
        }
        if (graft.bodyTail != null) {
            graft.bodyTail.part.visible = bodyTailWasVisible;
        }

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

    private static void renderHeads(PosableModel model, Graft graft, PosableState state, MultiBufferSource buffers,
                                    PoseStack poseStack, int light, int overlay, int color) {
        // El modelo de la cabeza es compartido con los demás Pokémon de su especie: lo ponemos en la postura de
        // ESTA cabeza justo antes de pintarla (como hace Cobblemon con cada Pokémon).
        // Sus animaciones leen el contexto de render, que solo tiene si ya se ha pintado alguna vez por sí mismo:
        // le prestamos el del cuerpo, que se está pintando ahora
        graft.headModel.setContext(model.getContext());
        animateHead(graft, state);
        ModelPart head = graft.head.part;
        // Orientación de la cabeza en su propio modelo, ya animado (respecto a la raíz)
        Quaternionf headRotation = rotationAlong(graft.head.path);

        // Como Cobblemon: entityCutout descarta la cara de atrás. Sin eso, los planos de grosor cero (orejas,
        // bocas...) pintan sus dos caras en el mismo sitio y parpadean (z-fighting)
        ResourceLocation texture = headTexture(graft, state);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));

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
    }

    /**
     * Pinta la cola de la especie de la cabeza en el sitio de la del cuerpo, como las cabezas: mismo pivote, girada
     * como en su modelo (que ya está animado: se mueve con su animación de reposo) y escalada al largo de la cola que
     * sustituye, para que una cola de Pikachu en un Charizard no quede diminuta.
     */
    private static void renderTail(Graft graft, VertexConsumer consumer, PoseStack poseStack, int light, int overlay,
                                   int color) {
        ModelPart tail = graft.headTail.part;
        float headLength = length(tail);
        float bodyLength = length(graft.bodyTail.part);
        // Si alguna no tiene cubos (solo hijos vacíos), no hay con qué comparar: tamaño original
        float scale = (headLength <= 0 || bodyLength <= 0 ? 1F
                : Math.clamp(bodyLength / headLength, MIN_SCALE, MAX_SCALE)) * INFLATE;
        Quaternionf correction = rotationAlong(graft.bodyTail.path).conjugate().mul(rotationAlong(graft.headTail.path));

        PartPose saved = tail.storePose();
        boolean visible = tail.visible;
        poseStack.pushPose();
        try {
            for (ModelPart part : graft.bodyTail.path) {
                part.translateAndRotate(poseStack);
            }
            poseStack.mulPose(correction);
            poseStack.scale(scale, scale, scale);
            tail.setPos(0, 0, 0);
            tail.setRotation(0, 0, 0);
            // Al pegar un modelo entero su cola se oculta como extremidad mientras se pinta; aquí se pinta suelta
            tail.visible = true;
            tail.render(poseStack, consumer, light, overlay, color);
        } finally {
            tail.loadPose(saved);
            tail.visible = visible;
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
                // visit no mira la visibilidad: lo que se salta se reconoce por la ruta del hueso del cubo
                for (String limb : skip.values()) {
                    if (path.equals(limb) || path.startsWith(limb + "/")) {
                        return;
                    }
                }
                includeCube(box, pose, cube);
            });
        } finally {
            part.loadPose(saved);
            part.xScale = xScale;
            part.yScale = yScale;
            part.zScale = zScale;
        }
        BOXES.put(part, box);
        return box;
    }

    /** Muestra u oculta unos huesos, apuntando cómo estaban para restoreVisible. */
    private static void setVisible(Collection<ModelPart> parts, List<Boolean> were, boolean visible) {
        for (ModelPart part : parts) {
            were.add(part.visible);
            part.visible = visible;
        }
    }

    /** Deja como estaban los huesos que haya tocado setVisible (aunque no llegase a tocarlos todos). */
    private static void restoreVisible(Collection<ModelPart> parts, List<Boolean> were) {
        int i = 0;
        for (ModelPart part : parts) {
            if (i >= were.size()) {
                return;
            }
            part.visible = were.get(i++);
        }
    }

    /**
     * Aplica al modelo de la cabeza sus animaciones de reposo, con un estado propio por fusión (lo mismo que hace
     * Cobblemon para pintar un Pokémon en un menú). Si la fusión es una entidad, la cabeza mira hacia donde mira.
     */
    private static void animateHead(Graft graft, PosableState state) {
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
            return new ModelHeads(List.of(), true, Map.of(), null);
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
                    return new ModelHeads(List.of(), true, Map.of(), null);
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
        return new ModelHeads(heads, whole || heads.isEmpty(), limbs, findTail(root, heads));
    }

    /**
     * La cola de un modelo: el primer hueso cuyo nombre empieza por "tail" ("tail", "tail1", "tail_base"...), con
     * toda su cadena ("tail2", "tail3"... cuelgan de él). En Cobblemon 1.8.1 la tienen 692 de 1142 modelos, casi
     * siempre colgando del tronco. No cuentan las de dentro de una cabeza (pelos: ya van con la cabeza).
     * Si hay varias colas separadas (Ninetales, Vulpix...) devuelve null: no sabríamos cuál poner en qué sitio.
     */
    private static HeadBone findTail(ModelPart root, List<HeadBone> heads) {
        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, name -> name.startsWith("tail"), current, paths);

        HeadBone tail = null;
        // collectPaths recorre de arriba abajo: el principio de cada cola sale antes que los huesos de su cadena
        for (List<ModelPart> path : paths) {
            if (tail != null && path.contains(tail.part)) {
                continue;
            }
            if (path.size() < 2 || insideAnother(path, heads)) {
                continue;
            }
            if (tail != null) {
                return null;
            }
            tail = new HeadBone(path.get(path.size() - 1), path);
        }
        return tail;
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
