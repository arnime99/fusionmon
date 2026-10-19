package com.arnau.fusionmon.client;

import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.ModelLayer;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Lo que cambia entre Cobblemon 1.7.3 (Cobbleverse) y 1.8: un solo jar para las dos (antes había una rama y un jar
 * para cada una, y la app de CurseForge daba a la gente el que no era).
 *
 * El mod se compila contra 1.8, así que lo de 1.8 se llama directamente, pero solo desde la clase Modern: Java no busca
 * las clases y métodos a los que llama un código hasta que ese código se ejecuta, y Modern solo se usa si estamos en
 * 1.8 (en 1.7.3 no existen ProfileTransformType ni las capas que se desplazan). Lo de 1.7.3 no se puede escribir tal
 * cual (no compilaría contra 1.8): se busca el método por su nombre y tipos al arrancar (MethodHandle).
 */
public final class CobblemonCompat {

    /** Cobblemon 1.8 o posterior. */
    public static final boolean MODERN = classExists("com.cobblemon.mod.common.client.gui.ProfileTransformType");

    /**
     * drawProfilePokemon de 1.7.3: (…, escala, aplicar retrato, aplicar escala base, r, g, b, a, rotación extra x2),
     * sin luz (pinta siempre con la máxima). En 1.8 los dos booleanos pasaron a ser ProfileTransformType + uno, y se
     * añadió la luz al final.
     */
    private static final MethodHandle LEGACY_DRAW = MODERN ? null : findLegacyDraw();

    private CobblemonCompat() {
    }

    /** Pinta un Pokémon como en los menús de Cobblemon, sin sus valores de retrato (ver ModelViewport). */
    public static void drawProfilePokemon(RenderablePokemon model, PoseStack pose, Quaternionf rotation,
                                          PoseType poseType, PosableState state, float partialTick, float scale,
                                          float red, float green, float blue, float alpha, int light) {
        if (MODERN) {
            Modern.drawProfilePokemon(model, pose, rotation, poseType, state, partialTick, scale,
                    red, green, blue, alpha, light);
            return;
        }
        try {
            LEGACY_DRAW.invokeExact(model, pose, rotation, poseType, state, partialTick, scale, false, false,
                    red, green, blue, alpha, 0F, 0F);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    /** La capa con textura que se desplaza (agua, energía) si la tiene; null si no (o en 1.7.3, que no las tiene). */
    public static RenderType scrollingLayer(PosableModel model, ResourceLocation texture, ModelLayer layer) {
        return MODERN ? Modern.scrollingLayer(model, texture, layer) : null;
    }

    private static boolean classExists(String name) {
        // Sin inicializarla (false): solo saber si está
        try {
            Class.forName(name, false, CobblemonCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static MethodHandle findLegacyDraw() {
        try {
            // Las clases de Minecraft (PoseStack) Loom las traduce al nombre del juego publicado al compilar, también
            // aquí: el mismo código vale en desarrollo y en el jar
            MethodType type = MethodType.methodType(void.class, RenderablePokemon.class, PoseStack.class,
                    Quaternionf.class, PoseType.class, PosableState.class, float.class, float.class, boolean.class,
                    boolean.class, float.class, float.class, float.class, float.class, float.class, float.class);
            return MethodHandles.publicLookup().findStatic(PokemonGuiUtilsKt.class, "drawProfilePokemon", type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cobblemon sin el drawProfilePokemon de 1.7.3 ni el de 1.8", e);
        }
    }

    /** Solo se carga en Cobblemon 1.8 o posterior (ver la explicación de la clase). */
    private static final class Modern {

        static void drawProfilePokemon(RenderablePokemon model, PoseStack pose, Quaternionf rotation,
                                       PoseType poseType, PosableState state, float partialTick, float scale,
                                       float red, float green, float blue, float alpha, int light) {
            PokemonGuiUtilsKt.drawProfilePokemon(model, pose, rotation, poseType, state, partialTick, scale,
                    ProfileTransformType.NONE, false, red, green, blue, alpha, 0F, 0F, light);
        }

        static RenderType scrollingLayer(PosableModel model, ResourceLocation texture, ModelLayer layer) {
            return layer.getScrolling() != null ? model.getScrollingLayer(texture, layer.getScrolling(), layer) : null;
        }
    }
}
