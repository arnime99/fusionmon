package com.arnau.fusionmon.client.texture;

import com.arnau.fusionmon.Fusionmon;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Texturas de las fusiones, generadas en memoria la primera vez que se pintan (solo cliente, hilo de render).
 *
 * Modo normal: el Pokémon visible es la cabeza y Cobblemon ya ha elegido su textura (con su shiny, sexo...).
 * Aquí le cambiamos los colores por los de la textura del cuerpo (ver FusionPalette), que sacamos del
 * resolver del cuerpo con sus propios aspects (ver FusionBody).
 * Prototipo cabeza sobre cuerpo (FusionGraft): al revés, el cuerpo se pinta con los colores de la cabeza.
 */
public final class FusionTextures {

    private static final String GENERATED_PREFIX = "fusion_textures/";

    /** "textura de cabeza|especie del cuerpo|aspects del cuerpo" → textura a usar (ahorra preguntar al resolver). */
    private static final Map<String, ResourceLocation> FUSIONS = new HashMap<>();
    /** "textura|textura de la paleta" → textura recoloreada (o la original si falló). */
    private static final Map<String, ResourceLocation> RECOLORED = new HashMap<>();
    private static final Set<ResourceLocation> GENERATED = new HashSet<>();
    private static int nextId;

    private FusionTextures() {
    }

    /**
     * La textura con la que pintar un Pokémon: la original si no es una fusión.
     * Se llama en cada fotograma, así que lo normal es que salga de la caché.
     */
    public static ResourceLocation textureFor(ResourceLocation headTexture, Set<String> aspects) {
        if (headTexture == null) {
            return null;
        }
        FusionBody body = FusionBody.of(aspects);
        if (body == null) {
            return headTexture;
        }

        String key = headTexture + "|" + body.species() + "|" + body.aspects();
        ResourceLocation cached = FUSIONS.get(key);
        if (cached != null) {
            return cached;
        }

        ResourceLocation result = headTexture;
        VaryingRenderableResolver bodyResolver = body.resolver();
        if (bodyResolver != null) {
            // La textura que tendría el cuerpo, preguntando a su resolver con sus aspects
            result = recolored(headTexture, bodyResolver.getTexture(body.state()));
        }
        FUSIONS.put(key, result);
        return result;
    }

    /** La textura "target" pintada con los colores de "palette" (o target tal cual si no se pudo generar). */
    public static ResourceLocation recolored(ResourceLocation target, ResourceLocation palette) {
        String key = target + "|" + palette;
        ResourceLocation cached = RECOLORED.get(key);
        if (cached != null) {
            return cached;
        }

        ResourceLocation result = target;
        try {
            result = generate(target, palette);
        } catch (Exception e) {
            // Mejor una fusión con sus colores originales que un crash al pintar
            Fusionmon.LOGGER.warn("No se pudo generar la textura de fusión {}", key, e);
        }
        // También se guarda el fallo: así no se reintenta (y se avisa) en cada fotograma
        RECOLORED.put(key, result);
        return result;
    }

    public static boolean isGenerated(ResourceLocation texture) {
        return GENERATED.contains(texture);
    }

    /** Al recargar recursos (F3+T, cambiar de resource pack) las texturas de base pueden haber cambiado. */
    public static void clear() {
        for (ResourceLocation texture : GENERATED) {
            Minecraft.getInstance().getTextureManager().release(texture);
        }
        GENERATED.clear();
        FUSIONS.clear();
        RECOLORED.clear();
    }

    private static ResourceLocation generate(ResourceLocation target, ResourceLocation palette) throws IOException {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        try (NativeImage targetImage = read(resources, target); NativeImage paletteImage = read(resources, palette)) {
            int[] recolored = FusionPalette.recolor(pixels(targetImage), pixels(paletteImage));

            // La imagen pasa a ser de la DynamicTexture, que la libera al liberar la textura (no va en el try)
            int width = targetImage.getWidth();
            NativeImage image = new NativeImage(width, targetImage.getHeight(), false);
            for (int y = 0; y < targetImage.getHeight(); y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixelRGBA(x, y, toAbgr(recolored[y * width + x]));
                }
            }

            ResourceLocation id = Fusionmon.id(GENERATED_PREFIX + nextId++);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            GENERATED.add(id);
            return id;
        }
    }

    private static NativeImage read(ResourceManager resources, ResourceLocation texture) throws IOException {
        try (InputStream stream = resources.open(texture)) {
            return NativeImage.read(stream);
        }
    }

    /** NativeImage guarda los píxeles como 0xAABBGGRR; FusionPalette trabaja en 0xAARRGGBB. */
    private static int[] pixels(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = toAbgr(image.getPixelRGBA(x, y));
            }
        }
        return pixels;
    }

    /** Intercambia rojo y azul: sirve en los dos sentidos (ABGR ↔ ARGB). */
    private static int toAbgr(int color) {
        return (color & 0xFF00FF00) | ((color >> 16) & 0xFF) | ((color & 0xFF) << 16);
    }
}
