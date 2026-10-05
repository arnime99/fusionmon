package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution;

/**
 * Evolución "pasiva" de una parte (por nivel, amistad, hora del día...). Hereda de la clase de Cobblemon para
 * que su código la reconozca (comprueba "instanceof PassiveEvolution" cada segundo en el equipo y tras combatir).
 * Los requisitos se comprueban contra la fusión: su nivel, su amistad, su objeto...
 */
public class FusionLevelUpEvolution extends LevelUpEvolution implements FusionPartEvolution {

    private final FusionPart part;
    private final LevelUpEvolution original;

    public FusionLevelUpEvolution(FusionPart part, LevelUpEvolution original) {
        super(part.evolutionId(original.getId()), original.getResult(), original.getShedder(),
                original.getOptional(), original.getConsumeHeldItem(), original.getRequirements(),
                original.getLearnableMoves(), original.getDrops(), original.getPermanent());
        this.part = part;
        this.original = original;
    }

    @Override
    public FusionPart part() {
        return part;
    }

    @Override
    public LevelUpEvolution original() {
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
