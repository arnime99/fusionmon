package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution;

/**
 * Evolución de una parte por intercambio (Kadabra, Machoke, Haunter...). Hereda de la clase de Cobblemon porque el
 * Cable Link (LinkCableItem) y el intercambio entre jugadores buscan exactamente ese tipo de evolución: sin ella, el
 * cable no hacía nada sobre una fusión.
 */
public class FusionTradeEvolution extends TradeEvolution implements FusionPartEvolution {

    private final FusionPart part;
    private final TradeEvolution original;

    public FusionTradeEvolution(FusionPart part, TradeEvolution original) {
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
    public TradeEvolution original() {
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
