package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.riding.RidingProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * La forma del CUERPO de una fusión, en el servidor y en el cliente.
 *
 * La fusión es la especie de la cabeza, pero se pinta con el modelo del cuerpo (FusionGraft): su tamaño (escala y
 * caja de colisión) y su montura salen del cuerpo, para que encajen con lo que se ve.
 *
 * En el servidor está en persistentData (FusionData). En el cliente, el Pokémon de una entidad no recibe
 * persistentData: se saca de los aspects que pone FusionAspects, que sí se sincronizan con la entidad.
 */
public final class FusionBodyForm {

    private FusionBodyForm() {
    }

    /** Forma del cuerpo de la fusión de esta entidad, o null si no es una fusión. */
    public static FormData of(PokemonEntity entity) {
        // Mientras Minecraft construye la entidad, Cobblemon aún no le ha puesto el Pokémon (Kotlin lo da por
        // no nulo, pero el campo todavía lo es)
        Pokemon pokemon = entity.getPokemon();
        FormData form = pokemon == null ? null : FusionData.bodyForm(pokemon);
        if (form != null) {
            return form;
        }
        return fromAspects(entity.getEntityData().get(PokemonEntity.Companion.getASPECTS()));
    }

    /** Forma del cuerpo de una fusión (del equipo, del PC...), o null si no es una fusión. */
    public static FormData of(Pokemon pokemon) {
        FormData form = FusionData.bodyForm(pokemon);
        return form != null ? form : fromAspects(pokemon.getAspects());
    }

    /**
     * Montura de la fusión: la del cuerpo (es su modelo, y sus asientos están pensados para él). Si el cuerpo no se
     * puede montar, la de la cabeza.
     */
    public static RidingProperties riding(FormData body, RidingProperties head) {
        if (body == null) {
            return head;
        }
        RidingProperties riding = body.getRiding();
        return riding.getSeats().isEmpty() ? head : riding;
    }

    private static FormData fromAspects(Set<String> aspects) {
        if (aspects == null || !aspects.contains(FusionAspects.FUSION)) {
            return null;
        }
        String path = null;
        Set<String> bodyAspects = new HashSet<>();
        for (String aspect : aspects) {
            if (aspect.startsWith(FusionAspects.BODY_ASPECT_PREFIX)) {
                bodyAspects.add(aspect.substring(FusionAspects.BODY_ASPECT_PREFIX.length()));
            } else if (aspect.startsWith(FusionAspects.BODY_SPECIES_PREFIX)) {
                path = aspect.substring(FusionAspects.BODY_SPECIES_PREFIX.length());
            }
        }
        if (path == null) {
            return null;
        }
        // El aspect solo lleva la ruta ("charizard"): casi todas las especies son de Cobblemon
        Species species = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
        if (species == null) {
            species = PokemonSpecies.getByName(path);
        }
        // La forma según los aspects del cuerpo (alolan, galarian...)
        return species == null ? null : species.getForm(bodyAspects);
    }
}
