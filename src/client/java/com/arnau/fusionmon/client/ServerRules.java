package com.arnau.fusionmon.client;

import com.arnau.fusionmon.FusionConfig;
import com.arnau.fusionmon.client.model.FusionGraft;

/**
 * Las opciones del servidor al que está conectado el jugador (llegan en FusionRulesPayload al entrar). En un servidor
 * manda el del servidor, no el config/fusionmon.json del propio juego (en un mundo de un jugador es el mismo archivo).
 *
 * El bonus de stats se guarda en FusionConfig (código común): lo usa también FusionStatProvider, que en el cliente
 * calcula los stats del resumen de Cobblemon.
 *
 * El modo colores forzado es la excepción: vale el del propio juego O el del servidor (los dos son interruptores de
 * emergencia; el del juego, para un jugador al que le falla el pintado en su modpack).
 */
public final class ServerRules {

    private ServerRules() {
    }

    public static int statBonusPercent() {
        return FusionConfig.statBonusPercent();
    }

    /** Al arrancar el cliente, sin servidor todavía: solo el archivo del propio juego. */
    static void init() {
        FusionGraft.setForcedColors(FusionConfig.forceColorsMode());
    }

    static void set(int bonusPercent, boolean serverForcesColors) {
        FusionConfig.setSyncedStatBonusPercent(bonusPercent);
        FusionGraft.setForcedColors(FusionConfig.forceColorsMode() || serverForcesColors);
    }

    /** Al desconectarse: el siguiente servidor manda las suyas (uno sin Fusionmon actualizado no manda nada). */
    static void reset() {
        FusionConfig.setSyncedStatBonusPercent(null);
        FusionGraft.setForcedColors(FusionConfig.forceColorsMode());
    }
}
