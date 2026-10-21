package com.arnau.fusionmon.client;

/**
 * Las opciones del servidor al que está conectado el jugador (llegan en FusionRulesPayload al entrar). Las usa lo que
 * el cliente calcula por su cuenta, como los stats del Fusion Album. No se lee el config/fusionmon.json del propio
 * juego: en un servidor manda el del servidor (en un mundo de un jugador es el mismo archivo).
 */
public final class ServerRules {

    private static int statBonusPercent = 0;

    private ServerRules() {
    }

    public static int statBonusPercent() {
        return statBonusPercent;
    }

    static void set(int bonusPercent) {
        statBonusPercent = bonusPercent;
    }

    /** Al desconectarse: el siguiente servidor manda las suyas (uno sin Fusionmon actualizado no manda nada). */
    static void reset() {
        statBonusPercent = 0;
    }
}
