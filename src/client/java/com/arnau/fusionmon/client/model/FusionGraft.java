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
import java.util.Collections;
import java.util.HashSet;
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
 * Si a alguno de los dos no se le encuentra la cabeza, o son la misma especie, se pinta como siempre
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

    private static boolean enabled;

    /** Estado (entidad o menú) → especie de la cabeza; se apunta cuando Cobblemon pide el modelo. */
    private static final Map<PosableState, ResourceLocation> HEADS = new WeakHashMap<>();
    /** Estado de animación propio de la cabeza pegada de cada fusión (para que parpadee a su ritmo, etc.). */
    private static final Map<PosableState, FloatingState> HEAD_STATES = new WeakHashMap<>();
    /** Cabezas de cada modelo (no cambian). */
    private static final Map<PosableModel, List<HeadBone>> HEAD_BONES = new WeakHashMap<>();
    /** Tamaño de cada cabeza con sus hijos, para escalar la cabeza nueva. */
    private static final Map<ModelPart, Float> SIZES = new WeakHashMap<>();
    /** Modelos de cabeza que ya han fallado al pintarse (para avisar en el log una sola vez). */
    private static final Set<PosableModel> WARNED = Collections.newSetFromMap(new WeakHashMap<>());

    // Estado "suelto" para pedir la textura original de la cabeza (con el shiny del cuerpo si hace falta)
    private static FloatingState textureState;

    // Lo que hay que deshacer al terminar de pintar el modelo del cuerpo
    private static Graft active;
    private static boolean[] hiddenWereVisible = new boolean[0];

    /**
     * @param head   la cabeza que se pega (la principal del modelo de la cabeza)
     * @param bodies las cabezas del cuerpo que se sustituyen (varias en Doduo, Dodrio...)
     */
    private record Graft(FusionBody body, VaryingRenderableResolver headResolver, VaryingRenderableResolver bodyResolver,
                         PosableModel headModel, PosableModel bodyModel, HeadBone head, List<HeadBone> bodies) {
    }

    /**
     * Una cabeza de un modelo.
     *
     * @param part el hueso de la cabeza
     * @param path huesos desde la raíz hasta la cabeza, incluidos los dos
     */
    private record HeadBone(ModelPart part, List<ModelPart> path) {
    }

    private FusionGraft() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
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
            return null;
        }
        HEADS.put(state, name);
        return graft.bodyModel;
    }

    /** Textura del cuerpo pintada con los colores de la cabeza (estilo Infinite Fusion). */
    public static ResourceLocation bodyTexture(ResourceLocation name, PosableState state) {
        Graft graft = graft(name, state, false);
        if (graft == null) {
            return null;
        }
        // El estado del cuerpo no lleva "fusionmon-fusion": sale su textura original, sin recolorear
        ResourceLocation body = graft.bodyResolver.getTexture(graft.body.state());
        return FusionTextures.recolored(body, headTexture(graft, state));
    }

    public static Iterable<ModelLayer> bodyLayers(ResourceLocation name, PosableState state) {
        Graft graft = graft(name, state, false);
        return graft == null ? null : graft.bodyResolver.getLayers(graft.body.state());
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
        List<HeadBone> heads = HEAD_BONES.computeIfAbsent(headModel, FusionGraft::findHeads);
        List<HeadBone> bodies = HEAD_BONES.computeIfAbsent(bodyModel, FusionGraft::findHeads);
        if (heads.isEmpty() || bodies.isEmpty()) {
            return null;
        }
        return new Graft(body, headResolver, bodyResolver, headModel, bodyModel, heads.get(0), bodies);
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
            poseStack.pushPose();
            try {
                // Mismas transformaciones que ha recibido la cabeza del cuerpo: raíz → ... → cuello → cabeza
                for (ModelPart part : bodyHead.path) {
                    part.translateAndRotate(poseStack);
                }
                poseStack.mulPose(correction);
                poseStack.scale(scale, scale, scale);

                // La cabeza nueva sin su posición ni giro propios (ya van arriba), con su pivote en el del cuerpo
                head.setPos(0, 0, 0);
                head.setRotation(0, 0, 0);
                head.visible = true;
                head.render(poseStack, consumer, light, overlay, color);
            } finally {
                // Pase lo que pase, la pila de transformaciones y la cabeza quedan como estaban
                head.loadPose(saved);
                head.visible = visible;
                poseStack.popPose();
            }
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
     *  - además, el resto de "head2", "head3"... que no estén dentro de otra cabeza (Doduo, Dodrio...).
     */
    private static List<HeadBone> findHeads(PosableModel model) {
        if (!((Object) model.getRootPart() instanceof ModelPart root)) {
            return List.of();
        }

        List<List<ModelPart>> paths = new ArrayList<>();
        List<ModelPart> primary = firstPath(root, "head"::equals);
        if (primary == null) {
            primary = firstPath(root, "locator_head"::equals);
            if (primary != null) {
                // Quitamos el localizador: la cabeza es su padre
                primary.remove(primary.size() - 1);
            }
        }
        if (primary != null) {
            paths.add(primary);
        }
        List<ModelPart> current = new ArrayList<>();
        current.add(root);
        collectPaths(root, name -> name.matches("head\\d*"), current, paths);

        List<HeadBone> heads = new ArrayList<>();
        for (List<ModelPart> path : paths) {
            ModelPart part = path.get(path.size() - 1);
            // Si la "cabeza" fuese la raíz, ocultarla ocultaría el Pokémon entero
            if (path.size() < 2 || insideAnother(path, heads) || containsHead(heads, part)) {
                continue;
            }
            heads.add(new HeadBone(part, path));
        }
        return heads;
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

    /** Tamaño medio (ancho, alto, fondo) de un hueso con todos sus hijos, en bloques. */
    private static float size(ModelPart part) {
        Float cached = SIZES.get(part);
        if (cached != null) {
            return cached;
        }
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            // Las medidas de los cubos van en píxeles de modelo (1/16 de bloque)
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = pose.pose().transformPosition(new Vector3f(
                        ((corner & 1) == 0 ? cube.minX : cube.maxX) / 16F,
                        ((corner & 2) == 0 ? cube.minY : cube.maxY) / 16F,
                        ((corner & 4) == 0 ? cube.minZ : cube.maxZ) / 16F));
                min[0] = Math.min(min[0], point.x);
                min[1] = Math.min(min[1], point.y);
                min[2] = Math.min(min[2], point.z);
                max[0] = Math.max(max[0], point.x);
                max[1] = Math.max(max[1], point.y);
                max[2] = Math.max(max[2], point.z);
            }
        });
        // Sin cubos (hueso vacío): tamaño neutro, la cabeza se queda a escala 1
        float size = min[0] > max[0] ? 1F : ((max[0] - min[0]) + (max[1] - min[1]) + (max[2] - min[2])) / 3F;
        size = Math.max(size, 0.01F);
        SIZES.put(part, size);
        return size;
    }
}
