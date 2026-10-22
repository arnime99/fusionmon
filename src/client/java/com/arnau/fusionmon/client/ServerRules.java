package com.arnau.fusionmon.client;

import com.arnau.fusionmon.FusionConfig;
import com.arnau.fusionmon.client.model.FusionGraft;

/**
 * Las opciones del servidor al que está conectado el jugador (llegan en FusionRulesPayload al entrar). Las usa lo que
 * el cliente calcula por su cuenta, como los stats del Fusion Album. No se lee el config/fusionmon.json del propio
 * juego para eso: en un servidor manda el del servidor (en un mundo de un jugador es el mismo archivo).
 *
 * El modo colores forzado es la excepción: vale el del propio juego O el del servidor (los dos son interruptores de
 * emergencia; el del juego, para un jugador al que le falla el pintado en su modpack).
 */
public final class ServerRules {

    private static int statBonusPercent = 0;

    private ServerRules() {
    }

    public static int statBonusPercent() {
        return statBonusPercent;
    }

    /** Al arrancar el cliente, sin servidor todavía: solo el archivo del propio juego. */
    static void init() {
        FusionGraft.setForcedColors(FusionConfig.forceColorsMode());
    }

    static void set(int bonusPercent, boolean serverForcesColors) {
        statBonusPercent = bonusPercent;
        FusionGraft.setForcedColors(FusionConfig.forceColorsMode() || serverForcesColors);
    }

    /** Al desconectarse: el siguiente servidor manda las suyas (uno sin Fusionmon actualizado no manda nada). */
    static void reset() {
        statBonusPercent = 0;
        FusionGraft.setForcedColors(FusionConfig.forceColorsMode());
    }
}
