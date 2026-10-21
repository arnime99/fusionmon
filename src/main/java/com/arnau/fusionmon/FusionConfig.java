package com.arnau.fusionmon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opciones del servidor, en config/fusionmon.json (lo crea el mod la primera vez). Solo las cambia quien gestiona el
 * servidor: los jugadores reciben el valor del servidor al conectarse (FusionRulesPayload). Se lee al arrancar; para
 * aplicar un cambio hay que reiniciar.
 *
 * En un mundo de un jugador vale el archivo del propio juego (el servidor integrado lo lee).
 */
public final class FusionConfig {

    private static final String FILE_NAME = "fusionmon.json";
    private static final int MAX_STAT_BONUS = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Lo que se guarda en el archivo (Gson lo rellena por nombre de campo). */
    private static final class Data {
        String _comment = "statBonusPercent: % extra on every base stat of every fusion (0 = Infinite Fusion formula,"
                + " recommended 5-10, max 100; each stat is capped at 255). Restart the server to apply changes.";
        int statBonusPercent = 0;
    }

    private static int statBonusPercent = 0;

    private FusionConfig() {
    }

    /** % que suben los stats base de todas las fusiones (0 = la fórmula de Infinite Fusion tal cual). */
    public static int statBonusPercent() {
        return statBonusPercent;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Data data = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(reader, Data.class);
            } catch (IOException | JsonParseException e) {
                // Un archivo mal escrito no debe tumbar el servidor: se avisa y se juega con los valores por defecto
                Fusionmon.LOGGER.error("No se ha podido leer {}: se usan los valores por defecto", path, e);
            }
        } else {
            data = new Data();
            write(path, data);
        }
        if (data == null) {
            data = new Data();
        }
        statBonusPercent = Math.clamp(data.statBonusPercent, 0, MAX_STAT_BONUS);
        if (statBonusPercent != data.statBonusPercent) {
            Fusionmon.LOGGER.warn("statBonusPercent {} fuera de 0-{}: se usa {}", data.statBonusPercent,
                    MAX_STAT_BONUS, statBonusPercent);
        }
        if (statBonusPercent > 0) {
            Fusionmon.LOGGER.info("Stats base de las fusiones: +{} %", statBonusPercent);
        }
    }

    private static void write(Path path, Data data) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            Fusionmon.LOGGER.warn("No se ha podido crear {}", path, e);
        }
    }
}
