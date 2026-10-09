package com.arnau.fusionmon.client.screen;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.util.math.QuaternionUtilsKt;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Visor 3D de un Pokémon dentro de una pantalla (lo usan el visor de fusiones y el inspector de especies): encuadrado
 * solo y girando solo mientras no se toque; arrastrar con el botón izquierdo gira, con el derecho mueve, y la rueda
 * acerca.
 *
 * Cobblemon coloca los modelos de los menús con unos valores de retrato de cada especie (profileTranslation/Scale),
 * pensados para su cuadrito del resumen: en una caja grande el modelo salía cortado por arriba. Con
 * ProfileTransformType.NONE no los usa (solo escala x20, gira y pinta), y lo colocamos aquí con la caja real del
 * modelo que se pinta (el del cuerpo, si es cabeza sobre cuerpo).
 */
final class ModelViewport {

    // Parte de la caja que ocupa el modelo, y la escala que aplica drawProfilePokemon por su cuenta
    private static final float FIT = 0.85F;
    private static final float PROFILE_SCALE = 20F;
    private static final int MODEL_LIGHT = 15;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 5F;
    /** Mientras no se arrastre, el modelo gira solo (una vuelta en este tiempo). */
    private static final long TURN_MILLIS = 12000;

    // Cámara: giro (grados), zoom y desplazamiento (píxeles)
    private float yaw = 30;
    private float pitch = 13;
    private float zoom = 1;
    private float panX;
    private float panY;
    /** Si se está arrastrando el modelo (con qué botón), y si ya se ha movido a mano (deja de girar solo). */
    private int dragButton = -1;
    private boolean manualCamera;
    /** Encuadre (ver measure): de qué modelo es, centro del modelo y radio que ocupa, en bloques. */
    private String fitKey;
    private Vector3f fitCenter = new Vector3f();
    private float fitRadius = 1;

    /**
     * Pinta el modelo en la caja cuadrada (x, y, lado box).
     *
     * @param fitExtra lo que, además del modelo, cambia su encuadre (los modos de /fusionvisual en el visor)
     * @param before   se ejecuta justo antes de pintar el modelo, con la caja ya recortada; puede ser null
     * @param after    justo después, aún recortado (para dibujar encima); puede ser null
     */
    void render(GuiGraphics graphics, RenderablePokemon model, FloatingState state, String fitExtra, int x, int y,
                int box, float partialTick, Runnable before, Runnable after) {
        state.setCurrentAspects(model.getAspects());
        graphics.fill(x - 1, y - 1, x + box + 1, y + box + 1, 0xFF555555);
        graphics.fill(x, y, x + box, y + box, 0xFF1E1E1E);
        measure(model, state, fitExtra);

        float shownYaw = manualCamera ? yaw : yaw + (System.currentTimeMillis() % TURN_MILLIS) * 360F / TURN_MILLIS;
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(pitch, shownYaw, 0F));
        // Píxeles por bloque para que el modelo quepa; drawProfilePokemon escala además x20 (el "20F" de abajo)
        float pixels = box * FIT / (2 * fitRadius) * zoom;
        // El centro del modelo, ya girado, en el centro de la caja
        Vector3f center = rotation.transform(new Vector3f(fitCenter));

        // Lo que se salga de la caja no se pinta
        graphics.enableScissor(x, y, x + box, y + box);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + box / 2.0 + panX, y + box / 2.0 + panY, 0);
        float scale = pixels / PROFILE_SCALE;
        pose.scale(scale, scale, scale);
        pose.translate(-center.x * PROFILE_SCALE, -center.y * PROFILE_SCALE, -center.z * PROFILE_SCALE);
        try {
            if (before != null) {
                before.run();
            }
            PokemonGuiUtilsKt.drawProfilePokemon(model, pose, rotation, PoseType.PROFILE, state, partialTick,
                    PROFILE_SCALE, ProfileTransformType.NONE, false, 1F, 1F, 1F, 1F, 0F, 0F, MODEL_LIGHT);
        } finally {
            // Pase lo que pase: "after" deshace lo de "before" (el inspector apaga la inspección)
            pose.popPose();
            if (after != null) {
                after.run();
            }
            graphics.disableScissor();
        }
    }

    /**
     * Pinta el modelo libre, sin marco ni recorte (para la animación de fusión): centrado en (centerX, centerY) y
     * ocupando size píxeles, girado yaw grados sobre la vertical.
     */
    void renderAt(GuiGraphics graphics, RenderablePokemon model, FloatingState state, float centerX, float centerY,
                  float size, float yaw, float partialTick) {
        state.setCurrentAspects(model.getAspects());
        measure(model, state, "");
        Quaternionf rotation = QuaternionUtilsKt.fromEulerXYZDegrees(new Quaternionf(), new Vector3f(pitch, yaw, 0F));
        float pixels = size / (2 * fitRadius);
        Vector3f center = rotation.transform(new Vector3f(fitCenter));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        float scale = pixels / PROFILE_SCALE;
        pose.scale(scale, scale, scale);
        pose.translate(-center.x * PROFILE_SCALE, -center.y * PROFILE_SCALE, -center.z * PROFILE_SCALE);
        try {
            PokemonGuiUtilsKt.drawProfilePokemon(model, pose, rotation, PoseType.PROFILE, state, partialTick,
                    PROFILE_SCALE, ProfileTransformType.NONE, false, 1F, 1F, 1F, 1F, 0F, 0F, MODEL_LIGHT);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Mide el modelo que se va a pintar (su caja con todos sus cubos, en bloques) cuando cambia el modelo o un modo.
     * Solo entonces: medido en cada fotograma, el encuadre bailaría con las animaciones.
     */
    private void measure(RenderablePokemon model, FloatingState state, String fitExtra) {
        String key = model.getSpecies().getResourceIdentifier() + "|" + model.getAspects() + "|" + fitExtra;
        if (key.equals(fitKey)) {
            return;
        }
        fitKey = key;
        fitCenter = new Vector3f();
        fitRadius = 1;
        // El mismo que pedirá drawProfilePokemon (con cabeza sobre cuerpo, el del cuerpo: ver VaryingModelRepositoryMixin)
        PosableModel poser = VaryingModelRepository.INSTANCE.getPoser(model.getSpecies().getResourceIdentifier(), state);
        if (!((Object) poser.getRootPart() instanceof ModelPart root)) {
            return;
        }
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        root.visit(new PoseStack(), (cubePose, path, index, cube) -> {
            for (int corner = 0; corner < 8; corner++) {
                // Los cubos van en píxeles de modelo (1/16 de bloque)
                Vector3f point = cubePose.pose().transformPosition(new Vector3f(
                        ((corner & 1) == 0 ? cube.minX : cube.maxX) / 16F,
                        ((corner & 2) == 0 ? cube.minY : cube.maxY) / 16F,
                        ((corner & 4) == 0 ? cube.minZ : cube.maxZ) / 16F));
                for (int axis = 0; axis < 3; axis++) {
                    box[axis] = Math.min(box[axis], point.get(axis));
                    box[axis + 3] = Math.max(box[axis + 3], point.get(axis));
                }
            }
        });
        if (box[0] > box[3]) {
            return;
        }
        fitCenter.set((box[0] + box[3]) / 2F, (box[1] + box[4]) / 2F, (box[2] + box[5]) / 2F);
        // Radio que cabe girando sobre la vertical: el alto, o la diagonal de la planta
        float halfX = (box[3] - box[0]) / 2F;
        float halfY = (box[4] - box[1]) / 2F;
        float halfZ = (box[5] - box[2]) / 2F;
        fitRadius = Math.max(0.05F, Math.max(halfY, (float) Math.sqrt(halfX * halfX + halfZ * halfZ)));
    }

    // ---- Ratón ----

    /** Se ha pulsado un botón del ratón fuera de los botones y cuadros de texto: empieza a arrastrar. */
    void press(int button) {
        dragButton = button;
    }

    void release() {
        dragButton = -1;
    }

    /** Arrastrar: true si lo ha usado el visor. */
    boolean drag(double dragX, double dragY) {
        if (dragButton == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!manualCamera) {
                // Seguir desde donde estaba girando solo
                yaw += (System.currentTimeMillis() % TURN_MILLIS) * 360F / TURN_MILLIS;
                manualCamera = true;
            }
            yaw += (float) dragX;
            pitch = Mth.clamp(pitch + (float) dragY, -90F, 90F);
            return true;
        }
        if (dragButton == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            panX += (float) dragX;
            panY += (float) dragY;
            return true;
        }
        return false;
    }

    void scroll(double scrollY) {
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.1F : 1 / 1.1F), MIN_ZOOM, MAX_ZOOM);
    }

    /** Cámara como al principio (girando sola). */
    void reset() {
        yaw = 30;
        pitch = 13;
        zoom = 1;
        panX = 0;
        panY = 0;
        manualCamera = false;
    }
}
