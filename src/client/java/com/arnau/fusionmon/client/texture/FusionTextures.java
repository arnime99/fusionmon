package com.arnau.fusionmon.client.texture;

import com.arnau.fusionmon.Fusionmon;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
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
import java.util.TreeSet;

/**
 * Texturas de las fusiones, generadas en memoria la primera vez que se pintan (solo cliente, hilo de render).
 *
 * El Pokémon visible es la cabeza: Cobblemon ya ha elegido su textura normal (con su shiny, sexo...).
 * Aquí le cambiamos los colores por los de la textura del cuerpo (ver FusionPalette), que sacamos del
 * resolver del cuerpo con sus propios aspects, los que FusionAspects manda con prefijo.
 */
public final class FusionTextures {

    private static final String SHINY = "shiny";
    private static final String GENERATED_PREFIX = "fusion_textures/";

    /** "textura de cabeza|especie del cuerpo|aspects del cuerpo" → textura a usar (la generada o la original si falló). */
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> GENERATED = new HashSet<>();
    private static int nextId;
    // Estado "suelto" (sin entidad) para preguntar al resolver del cuerpo; se crea al usarlo por primera vez
    private static FloatingState bodyState;

    private FusionTextures() {
    }

    /**
     * La textura con la que pintar un Pokémon: la original si no es una fusión.
     * Se llama en cada fotograma, así que lo normal es que salga de la caché.
     */
    public static ResourceLocation textureFor(ResourceLocation headTexture, Set<String> aspects) {
        if (headTexture == null || !aspects.contains(FusionAspects.FUSION)) {
            return headTexture;
        }

        String bodySpecies = null;
        Set<String> bodyAspects = new TreeSet<>();
        for (String aspect : aspects) {
            if (aspect.startsWith(FusionAspects.BODY_ASPECT_PREFIX)) {
                bodyAspects.add(aspect.substring(FusionAspects.BODY_ASPECT_PREFIX.length()));
            } else if (aspect.startsWith(FusionAspects.BODY_SPECIES_PREFIX)) {
                bodySpecies = aspect.substring(FusionAspects.BODY_SPECIES_PREFIX.length());
            }
        }
        if (bodySpecies == null) {
            return headTexture;
        }
        // Una fusión se ve shiny si lo es cualquiera de sus partes: se usan los colores shiny del cuerpo
        if (aspects.contains(SHINY)) {
            bodyAspects.add(SHINY);
        }

        String key = headTexture + "|" + bodySpecies + "|" + bodyAspects;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        ResourceLocation result = headTexture;
        try {
            ResourceLocation generated = generate(headTexture, bodySpecies, bodyAspects);
            if (generated != null) {
                result = generated;
            }
        } catch (Exception e) {
            // Mejor una fusión con los colores de la cabeza que un crash al pintar
            Fusionmon.LOGGER.warn("No se pudo generar la textura de fusión {}", key, e);
        }
        // También se guarda el fallo: así no se reintenta (y se avisa) en cada fotograma
        CACHE.put(key, result);
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
        CACHE.clear();
    }

    private static ResourceLocation generate(ResourceLocation headTexture, String bodySpecies, Set<String> bodyAspects)
            throws IOException {
        ResourceLocation bodyTexture = bodyTexture(bodySpecies, bodyAspects);
        if (bodyTexture == null) {
            return null;
        }

        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        try (NativeImage head = read(resources, headTexture); NativeImage body = read(resources, bodyTexture)) {
            int[] recolored = FusionPalette.recolor(pixels(head), pixels(body));

            // La imagen pasa a ser de la DynamicTexture, que la libera al liberar la textura (no va en el try)
            NativeImage image = new NativeImage(head.getWidth(), head.getHeight(), false);
            for (int y = 0; y < head.getHeight(); y++) {
                for (int x = 0; x < head.getWidth(); x++) {
                    image.setPixelRGBA(x, y, toAbgr(recolored[y * head.getWidth() + x]));
                }
            }

            ResourceLocation id = Fusionmon.id(GENERATED_PREFIX + nextId++);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            GENERATED.add(id);
            return id;
        }
    }

    /** La textura que tendría el cuerpo, preguntando a su resolver con sus aspects. */
    private static ResourceLocation bodyTexture(String bodySpecies, Set<String> bodyAspects) {
        Map<ResourceLocation, VaryingRenderableResolver> resolvers = VaryingModelRepository.INSTANCE.getVariations();
        // El aspect solo lleva la ruta ("charizard"): casi todas las especies son de Cobblemon
        VaryingRenderableResolver resolver = resolvers.get(ResourceLocation.fromNamespaceAndPath("cobblemon", bodySpecies));
        if (resolver == null) {
            // Especies de otros mods (datapacks/addons): buscamos por la ruta
            for (Map.Entry<ResourceLocation, VaryingRenderableResolver> entry : resolvers.entrySet()) {
                if (entry.getKey().getPath().equals(bodySpecies)) {
                    resolver = entry.getValue();
                    break;
                }
            }
        }
        if (resolver == null) {
            return null;
        }

        if (bodyState == null) {
            bodyState = new FloatingState();
        }
        // Sin "fusionmon-fusion": el mixin de getTexture lo deja pasar sin tocarlo
        bodyState.setCurrentAspects(bodyAspects);
        return resolver.getTexture(bodyState);
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
