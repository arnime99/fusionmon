package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.HashSet;
import java.util.Set;

/**
 * Añade "aspects" a las fusiones. Un aspect es una etiqueta de texto del Pokémon (shiny, female, alolan...)
 * que Cobblemon calcula en el servidor, envía solo al cliente (entidad en el mundo, equipo, PC, combates)
 * y usa para elegir modelo y textura en los "resolvers" (assets/cobblemon/bedrock/pokemon/resolvers).
 *
 * Una fusión de cabeza Pikachu y cuerpo Charizard shiny lleva, además de los de Pikachu:
 *  - "fusionmon-fusion"
 *  - "fusionmon-body-charizard"      (la especie del cuerpo)
 *  - "fusionmon-bodyaspect-shiny"    (cada aspect del cuerpo, con prefijo para que no pinte shiny a la cabeza)
 *
 * Así el cliente sabe quién es el cuerpo sin depender de persistentData (que solo llega en envíos completos),
 * y un resource pack puede dar texturas propias a una combinación con una variación como
 * {"aspects": ["fusionmon-body-charizard"], "texture": ...} en el resolver de Pikachu.
 */
public final class FusionAspects implements AspectProvider {

    public static final String FUSION = "fusionmon-fusion";
    public static final String BODY_SPECIES_PREFIX = "fusionmon-body-";
    // Prefijo distinto (no "fusionmon-body-aspect-") para que ningún aspect de cuerpo se confunda con una especie
    public static final String BODY_ASPECT_PREFIX = "fusionmon-bodyaspect-";

    @Override
    public Set<String> provide(Pokemon pokemon) {
        // Cobblemon llama a esto cada vez que recalcula los aspects de cualquier Pokémon: tiene que ser barato.
        // isFusion ya comprueba que persistentData no sea null (lo es mientras se construye el Pokémon).
        if (!FusionData.isFusion(pokemon)) {
            return new HashSet<>();
        }
        FormData bodyForm = FusionData.bodyForm(pokemon);
        return fusionAspects(bodyForm == null ? null : bodyForm.getSpecies(), FusionData.bodyAspects(pokemon));
    }

    /**
     * Los aspects que añade Fusionmon a una fusión con ese cuerpo. También los usa la vista previa de la pantalla
     * de fusión, para pintar una fusión que todavía no existe.
     */
    public static Set<String> fusionAspects(Species bodySpecies, Set<String> bodyAspects) {
        Set<String> aspects = new HashSet<>();
        aspects.add(FUSION);
        if (bodySpecies != null) {
            // Solo la ruta ("charizard", no "cobblemon:charizard"): es lo cómodo de escribir en un resource pack
            aspects.add(BODY_SPECIES_PREFIX + bodySpecies.getResourceIdentifier().getPath());
        }
        for (String aspect : bodyAspects) {
            aspects.add(BODY_ASPECT_PREFIX + aspect);
        }
        return aspects;
    }

    @Override
    public Set<String> provide(PokemonProperties properties) {
        // Las propiedades ("pikachu shiny=yes level=5") aún no pueden describir una fusión
        return new HashSet<>();
    }
}
