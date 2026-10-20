package com.arnau.fusionmon.client.model;

import com.arnau.fusionmon.fusion.FusionAspects;
import com.cobblemon.mod.common.client.render.ModelAssetVariation;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Fusiones hechas a mano: un resource pack puede traer su propio modelo (o solo su textura) para una pareja concreta, y
 * entonces se usa ese en vez del que genera el mod (cabeza sobre cuerpo, o la cabeza recoloreada). Como los sprites
 * hechos por la comunidad de Infinite Fusion: todas las parejas existen, y las mejores se hacen a mano.
 *
 * El pack no necesita nada nuestro: es un resolver normal de Cobblemon para la especie de la cabeza con una variación
 * que pide el aspect del cuerpo ("fusionmon-body-charizard", ver FusionAspects), en un archivo propio con un "order"
 * alto. Cobblemon junta todos los resolvers de una especie ordenados por "order" y, para cada propiedad, usa la última
 * variación que encaja: la del pack gana sin borrar las del Pokémon. Aquí solo se mira si la variación que va a usar
 * Cobblemon es una de esas, para que FusionGraft y FusionTextures se aparten y Cobblemon la pinte tal cual. El nombre,
 * los tipos y los stats siguen siendo los calculados.
 */
public final class FusionCustomModels {

    /**
     * Si un resolver tiene alguna variación de fusión a mano. Por resolver (no por especie): al recargar los recursos
     * Cobblemon crea resolvers nuevos y esto se vuelve a mirar solo, sin depender del orden de recarga. Casi ninguna
     * especie tendrá: así las demás no recorren sus variaciones en cada fotograma.
     */
    private static final Map<VaryingRenderableResolver, Boolean> HAS_CUSTOM =
            Collections.synchronizedMap(new WeakHashMap<>());

    private FusionCustomModels() {
    }

    /** ¿Tiene un pack un modelo o textura hecho a mano para esta fusión? (species = la especie de la cabeza) */
    public static boolean isCustom(ResourceLocation species, Set<String> aspects) {
        if (!aspects.contains(FusionAspects.FUSION)) {
            return false;
        }
        return isCustom(VaryingModelRepository.INSTANCE.getVariations().get(species), aspects);
    }

    public static boolean isCustom(VaryingRenderableResolver resolver, Set<String> aspects) {
        if (resolver == null || !aspects.contains(FusionAspects.FUSION)
                || !HAS_CUSTOM.computeIfAbsent(resolver, FusionCustomModels::hasCustomVariation)) {
            return false;
        }
        // Como Cobblemon: de la última a la primera, la primera que encaja y pone modelo o textura es la que se usa
        // (sin su condición Molang, que estas variaciones no necesitan)
        List<ModelAssetVariation> variations = resolver.getVariations();
        for (int i = variations.size() - 1; i >= 0; i--) {
            ModelAssetVariation variation = variations.get(i);
            if ((variation.getModel() != null || variation.getTexture() != null)
                    && aspects.containsAll(variation.getAspects())) {
                return isCustomVariation(variation);
            }
        }
        return false;
    }

    private static boolean hasCustomVariation(VaryingRenderableResolver resolver) {
        for (ModelAssetVariation variation : resolver.getVariations()) {
            if (isCustomVariation(variation)) {
                return true;
            }
        }
        return false;
    }

    /** Una variación de fusión a mano pide el aspect de la especie del cuerpo. */
    private static boolean isCustomVariation(ModelAssetVariation variation) {
        for (String aspect : variation.getAspects()) {
            if (aspect.startsWith(FusionAspects.BODY_SPECIES_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
