package com.arnau.fusionmon.fusion;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;

import java.util.List;

/**
 * Fórmulas de fusión, basadas en las de Pokémon Infinite Fusion.
 * No guarda nada: todo se calcula a partir de la forma de la cabeza y la del cuerpo.
 */
public final class FusionCalculator {

    private FusionCalculator() {
    }

    /**
     * La cabeza pesa 2/3 en PS, Ataque Especial y Defensa Especial;
     * el cuerpo pesa 2/3 en Ataque, Defensa y Velocidad.
     */
    public static int baseStat(FormData head, FormData body, Stat stat) {
        int headValue = head.getBaseStats().getOrDefault(stat, 0);
        int bodyValue = body.getBaseStats().getOrDefault(stat, 0);

        boolean headWeighted = stat == Stats.HP || stat == Stats.SPECIAL_ATTACK || stat == Stats.SPECIAL_DEFENCE;
        return headWeighted
                ? (2 * headValue + bodyValue) / 3
                : (2 * bodyValue + headValue) / 3;
    }

    /**
     * Tipo 1: el primario de la cabeza.
     * Tipo 2: el secundario del cuerpo (o su primario si no tiene). Si coincide con el tipo 1,
     * se usa el primario del cuerpo; si también coincide, la fusión es de un solo tipo.
     */
    public static List<ElementalType> types(FormData head, FormData body) {
        ElementalType primary = head.getPrimaryType();
        ElementalType secondary = body.getSecondaryType() != null ? body.getSecondaryType() : body.getPrimaryType();

        if (secondary == primary) {
            secondary = body.getPrimaryType();
        }

        return secondary == primary ? List.of(primary) : List.of(primary, secondary);
    }

    /**
     * Primera mitad del nombre de la cabeza + segunda mitad del del cuerpo.
     * Bulbasaur + Charmander → Bulba + mander → Bulbamander
     */
    public static String name(String headName, String bodyName) {
        String prefix = headName.substring(0, (headName.length() + 1) / 2);
        String suffix = bodyName.substring(bodyName.length() / 2).toLowerCase();

        // Evita letras dobles en la unión: Pika + achu → Pikachu, no Pikaachu
        if (!suffix.isEmpty() && Character.toLowerCase(prefix.charAt(prefix.length() - 1)) == suffix.charAt(0)) {
            suffix = suffix.substring(1);
        }

        return prefix + suffix;
    }
}
