package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;

/**
 * Marca las evoluciones de una fusión: envuelven una evolución de la cabeza o del cuerpo guardados.
 * Cobblemon las trata como cualquier otra (las comprueba, las muestra en su menú...), pero al aceptarlas
 * evolucionan esa parte en vez de cambiar la especie del Pokémon visible.
 */
public interface FusionPartEvolution extends Evolution {

    FusionPart part();

    /** La evolución original de la especie de esa parte. */
    Evolution original();
}
