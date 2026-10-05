package com.arnau.fusionmon.client.texture;

import com.arnau.fusionmon.fusion.FusionAspects;
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * El cuerpo de una fusión tal como lo ve el cliente: su especie y sus aspects, leídos de los aspects con
 * prefijo que pone FusionAspects. Sirve para preguntar al resolver del cuerpo su textura, capas o modelo.
 *
 * @param species ruta de la especie ("charizard")
 * @param aspects aspects del cuerpo sin prefijo ("shiny", "female"...), ordenados (sirven de clave de caché)
 */
public record FusionBody(String species, Set<String> aspects) {

    private static final String SHINY = "shiny";

    // Estado "suelto" (sin entidad) para preguntar al resolver del cuerpo; se crea al usarlo por primera vez
    private static FloatingState state;

    /** El cuerpo según los aspects de un Pokémon, o null si no es una fusión. */
    public static FusionBody of(Set<String> pokemonAspects) {
        if (!pokemonAspects.contains(FusionAspects.FUSION)) {
            return null;
        }

        String species = null;
        Set<String> aspects = new TreeSet<>();
        for (String aspect : pokemonAspects) {
            if (aspect.startsWith(FusionAspects.BODY_ASPECT_PREFIX)) {
                aspects.add(aspect.substring(FusionAspects.BODY_ASPECT_PREFIX.length()));
            } else if (aspect.startsWith(FusionAspects.BODY_SPECIES_PREFIX)) {
                species = aspect.substring(FusionAspects.BODY_SPECIES_PREFIX.length());
            }
        }
        if (species == null) {
            return null;
        }
        // Una fusión se ve shiny si lo es cualquiera de sus partes: se usan los colores shiny del cuerpo
        if (pokemonAspects.contains(SHINY)) {
            aspects.add(SHINY);
        }
        return new FusionBody(species, aspects);
    }

    /** El resolver de la especie del cuerpo, o null si este cliente no la conoce. */
    public VaryingRenderableResolver resolver() {
        Map<ResourceLocation, VaryingRenderableResolver> resolvers = VaryingModelRepository.INSTANCE.getVariations();
        // El aspect solo lleva la ruta ("charizard"): casi todas las especies son de Cobblemon
        VaryingRenderableResolver resolver = resolvers.get(ResourceLocation.fromNamespaceAndPath("cobblemon", species));
        if (resolver != null) {
            return resolver;
        }
        // Especies de otros mods (datapacks/addons): buscamos por la ruta
        for (Map.Entry<ResourceLocation, VaryingRenderableResolver> entry : resolvers.entrySet()) {
            if (entry.getKey().getPath().equals(species)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Un estado con los aspects del cuerpo, para pedirle a su resolver textura o capas.
     * Es siempre el mismo objeto (solo se usa en el hilo de render, justo después de pedirlo).
     * No lleva "fusionmon-fusion", así que los mixins de Fusionmon lo dejan pasar sin tocarlo.
     */
    public FloatingState state() {
        if (state == null) {
            state = new FloatingState();
        }
        state.setCurrentAspects(aspects);
        return state;
    }
}
