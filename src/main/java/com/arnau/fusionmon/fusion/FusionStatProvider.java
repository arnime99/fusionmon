package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.StatProvider;
import com.cobblemon.mod.common.api.pokemon.stats.StatTypeAdapter;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/**
 * Sustituye a la calculadora de stats de Cobblemon (Cobblemon.statProvider).
 * Para una fusión calcula con los stats base de la fusión; para todo lo demás delega en la original.
 */
public class FusionStatProvider implements StatProvider {

    private final StatProvider delegate;

    public FusionStatProvider(StatProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getStatForPokemon(Pokemon pokemon, Stat stat) {
        FormData head = FusionData.headForm(pokemon);
        FormData body = FusionData.bodyForm(pokemon);
        if (head == null || body == null) {
            return delegate.getStatForPokemon(pokemon, stat);
        }

        // Misma fórmula que Cobblemon (la de los juegos), cambiando solo el stat base
        int base = FusionCalculator.baseStat(head, body, stat);
        int iv = pokemon.getIvs().getEffectiveBattleIV(stat);
        int ev = pokemon.getEvs().getOrDefault(stat);
        int level = pokemon.getLevel();

        int scaled = (2 * base + iv + ev / 4) * level / 100;
        if (stat == Stats.HP) {
            return scaled + level + 10;
        }
        return pokemon.getEffectiveNature().modifyStat(stat, scaled + 5);
    }

    // --- El resto se delega tal cual ---

    @Override
    public StatTypeAdapter getTypeAdapter() {
        return delegate.getTypeAdapter();
    }

    @Override
    public Collection<Stat> all() {
        return delegate.all();
    }

    @Override
    public Collection<Stat> ofType(Stat.Type type) {
        return delegate.ofType(type);
    }

    @Override
    public void provide(Species species) {
        delegate.provide(species);
    }

    @Override
    public void provide(FormData form) {
        delegate.provide(form);
    }

    @Override
    public EVs createEmptyEVs() {
        return delegate.createEmptyEVs();
    }

    @Override
    public IVs createEmptyIVs(int minPerfectIVs) {
        return delegate.createEmptyIVs(minPerfectIVs);
    }

    @Override
    public String toShowdown(Species species, FormData form) {
        return delegate.toShowdown(species, form);
    }

    @Override
    public Stat fromIdentifier(ResourceLocation identifier) {
        return delegate.fromIdentifier(identifier);
    }

    @Override
    public Stat fromIdentifierOrThrow(ResourceLocation identifier) {
        return delegate.fromIdentifierOrThrow(identifier);
    }

    @Override
    public Stat decode(RegistryFriendlyByteBuf buffer) {
        return delegate.decode(buffer);
    }

    @Override
    public void encode(RegistryFriendlyByteBuf buffer, Stat stat) {
        delegate.encode(buffer, stat);
    }
}
