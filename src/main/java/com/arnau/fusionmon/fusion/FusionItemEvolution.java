package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution;

/**
 * Evolución de una parte al usar un objeto sobre la fusión (piedras evolutivas...). Hereda de la clase de
 * Cobblemon porque al hacer clic con un objeto sobre un Pokémon busca exactamente ese tipo de evolución.
 */
public class FusionItemEvolution extends ItemInteractionEvolution implements FusionPartEvolution {

    private final FusionPart part;
    private final ItemInteractionEvolution original;

    public FusionItemEvolution(FusionPart part, ItemInteractionEvolution original) {
        super(part.evolutionId(original.getId()), original.getResult(), original.getShedder(),
                original.getRequiredContext(), original.getOptional(), original.getConsumeHeldItem(),
                original.getRequirements(), original.getLearnableMoves(), original.getDrops());
        this.part = part;
        this.original = original;
    }

    @Override
    public FusionPart part() {
        return part;
    }

    @Override
    public ItemInteractionEvolution original() {
        return original;
    }

    @Override
    public void forceEvolve(Pokemon pokemon) {
        FusionEvolutions.forceEvolve(pokemon, this);
    }

    @Override
    public void evolutionMethod(Pokemon pokemon) {
        FusionEvolutions.evolvePart(pokemon, this);
    }
}
