package com.arnau.fusionmon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Opciones del mod, en config/fusionmon.json (lo crea el mod la primera vez). Se lee al arrancar; para aplicar un
 * cambio hay que reiniciar.
 * - statBonusPercent: % extra en los stats de las fusiones. Manda el del servidor: los jugadores lo reciben al entrar
 *   (FusionRulesPayload).
 * - unfuseConsumesInjector / reverseConsumesInjector: con false, separar / invertir no gastan inyector (fusionar
 *   siempre gasta). Por separado, para que cada servidor elija. Para servidores donde el inyector es caro (petición
 *   del servidor CobbleFusion, 2026-10-26). Solo lo mira el servidor.
 * - disabledMixins / forceColorsMode: interruptores de emergencia por si Fusionmon choca con algún mod de un modpack.
 *   Valen en el lado donde estén: el servidor con su archivo, cada jugador con el suyo (forceColorsMode también se
 *   puede forzar a todos desde el servidor).
 *
 * En un mundo de un jugador vale el archivo del propio juego (el servidor integrado lo lee). Esta clase no toca clases
 * de Minecraft ni Fusionmon: el plugin de mixins la usa antes de que se cargue nada del juego (readDisabledMixins).
 */
public final class FusionConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("fusionmon");
    private static final String FILE_NAME = "fusionmon.json";
    private static final int MAX_STAT_BONUS = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Lo que se guarda en el archivo (Gson lo rellena por nombre de campo; lo que falte, con estos valores). */
    private static final class Data {
        String _comment = "statBonusPercent: % extra on every base stat of every fusion (0 = Infinite Fusion formula,"
                + " recommended 5-10, max 100; each stat is capped at 255). unfuseConsumesInjector = false: splitting a"
                + " fusion doesn't use up a Fusion Injector; reverseConsumesInjector = false: same for swapping its head"
                + " and body (you still need one to open the menu; fusing always uses one). Emergency switches, only"
                + " if Fusionmon"
                + " conflicts with another mod: disabledMixins = names from the log or crash report (e.g."
                + " \"PokemonOnShoulderRendererMixin\") to turn off; forceColorsMode = true draws fusions with the head"
                + " recolored instead of head-on-body (a server forces it on every player). creatorTools = true, for"
                + " videos and screenshots: /fusionmon spawn and /fusionmon forget for operators on the server, and"
                + " /fusionviewer (every fusion, P saves a transparent PNG to fusionmon/renders) in your game. Restart to"
                + " apply changes.";
        int statBonusPercent = 0;
        boolean unfuseConsumesInjector = true;
        boolean reverseConsumesInjector = true;
        List<String> disabledMixins = new ArrayList<>();
        boolean forceColorsMode = false;
        boolean creatorTools = false;
    }

    private static final String[] KEYS = {"statBonusPercent", "unfuseConsumesInjector", "reverseConsumesInjector",
            "disabledMixins", "forceColorsMode", "creatorTools"};

    private static int statBonusPercent = 0;
    private static boolean unfuseConsumesInjector = true;
    private static boolean reverseConsumesInjector = true;
    private static boolean forceColorsMode = false;
    private static boolean creatorTools = false;
    /**
     * El statBonusPercent del servidor al que está conectado este juego (FusionRulesPayload), o null si no hay. Los
     * stats también se calculan en el cliente (el resumen de Cobblemon pide getStat a FusionStatProvider): con el
     * archivo del propio jugador (0) el resumen enseñaba los stats sin bonus y el combate con él (aviso del servidor
     * CobbleFusion, 2026-10-22). En un mundo de un jugador es el mismo valor que el archivo.
     */
    // volatile: lo escribe el hilo del cliente y, en un mundo de un jugador, lo lee también el del servidor integrado
    private static volatile Integer syncedStatBonusPercent = null;

    private FusionConfig() {
    }

    /**
     * % que suben los stats base de todas las fusiones (0 = la fórmula de Infinite Fusion tal cual): el del servidor
     * al que está conectado este juego, o el del archivo (un servidor, o sin conectar).
     */
    public static int statBonusPercent() {
        Integer synced = syncedStatBonusPercent;
        return synced != null ? synced : statBonusPercent;
    }

    /** El del archivo de este lado: lo que el servidor manda a los jugadores. */
    public static int configuredStatBonusPercent() {
        return statBonusPercent;
    }

    /** Al recibir las opciones del servidor (null al desconectarse). */
    public static void setSyncedStatBonusPercent(Integer value) {
        syncedStatBonusPercent = value;
    }

    /** Si separar gasta un inyector (por defecto sí). */
    public static boolean unfuseConsumesInjector() {
        return unfuseConsumesInjector;
    }

    /**
     * Si invertir gasta un inyector (por defecto sí). Con false se pueden comparar las dos combinaciones (tipos,
     * stats) sin gastar uno cada vez; fusionar dos Pokémon nuevos siempre gasta.
     */
    public static boolean reverseConsumesInjector() {
        return reverseConsumesInjector;
    }

    /** Pintar las fusiones solo con colores, sin cabeza pegada (en este lado; el cliente mira también el servidor). */
    public static boolean forceColorsMode() {
        return forceColorsMode;
    }

    /**
     * Herramientas para creadores de contenido (vídeos, miniaturas): ver Fusionmon.recordingTools. Apagado por defecto:
     * la gracia del mod es descubrir las fusiones jugando, y esto enseña todas.
     */
    public static boolean creatorTools() {
        return creatorTools;
    }

    public static void load() {
        Data data = read(true);
        statBonusPercent = Math.clamp(data.statBonusPercent, 0, MAX_STAT_BONUS);
        if (statBonusPercent != data.statBonusPercent) {
            LOGGER.warn("statBonusPercent {} fuera de 0-{}: se usa {}", data.statBonusPercent, MAX_STAT_BONUS,
                    statBonusPercent);
        }
        if (statBonusPercent > 0) {
            LOGGER.info("Stats base de las fusiones: +{} %", statBonusPercent);
        }
        unfuseConsumesInjector = data.unfuseConsumesInjector;
        if (!unfuseConsumesInjector) {
            LOGGER.info("Fusionmon: separar no gasta inyector ({})", FILE_NAME);
        }
        reverseConsumesInjector = data.reverseConsumesInjector;
        if (!reverseConsumesInjector) {
            LOGGER.info("Fusionmon: invertir no gasta inyector ({})", FILE_NAME);
        }
        forceColorsMode = data.forceColorsMode;
        if (forceColorsMode) {
            LOGGER.info("Fusionmon: modo colores forzado por {}", FILE_NAME);
        }
        creatorTools = data.creatorTools;
        if (creatorTools) {
            LOGGER.info("Fusionmon: herramientas para creadores activadas por {}", FILE_NAME);
        }
    }

    /**
     * Los mixins que el archivo pide no aplicar (nombre sin paquete, en minúsculas). Para CommonMixinPlugin, que lo
     * pregunta antes de que el mod arranque: no escribe el archivo ni avisa de nada más (eso lo hace load).
     */
    public static Set<String> readDisabledMixins() {
        return read(false).disabledMixins.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /**
     * Lee el archivo; si no existe, o le faltan opciones de una versión nueva, lo (re)escribe con todas (si write),
     * para que quien lo abra las vea. Un archivo mal escrito no debe tumbar el juego: se avisa y valen los de por
     * defecto.
     */
    private static Data read(boolean write) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            Data data = new Data();
            if (write) {
                write(path, data);
            }
            return data;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Data data = GSON.fromJson(json, Data.class);
            if (data.disabledMixins == null) {
                data.disabledMixins = new ArrayList<>();
            }
            boolean missing = false;
            for (String key : KEYS) {
                missing |= !json.has(key);
            }
            if (write && missing) {
                // El texto de ayuda, el de esta versión (explica también las opciones nuevas)
                data._comment = new Data()._comment;
                write(path, data);
            }
            return data;
        } catch (IOException | RuntimeException e) {
            if (write) {
                LOGGER.error("No se ha podido leer {}: se usan los valores por defecto", path, e);
            }
            return new Data();
        }
    }

    private static void write(Path path, Data data) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("No se ha podido escribir {}", path, e);
        }
    }
}
