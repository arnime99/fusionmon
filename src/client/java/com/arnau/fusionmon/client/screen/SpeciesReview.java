package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.Fusionmon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lo que el usuario ha revisado en el inspector: cada especie bien o con fallo, y una nota. Se guarda en
 * fusionmon/species-review.json dentro de la carpeta del juego ("run/" en el proyecto) en cuanto cambia, para que
 * Claude lo lea y agrupe los fallos por patrón.
 */
final class SpeciesReview {

    enum Status { OK, WRONG }

    record Entry(Status status, String note, String date) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    /** Ordenado por clave: el archivo sale siempre igual y es fácil de comparar. */
    private final Map<String, Entry> entries = new TreeMap<>();

    private SpeciesReview(Path file) {
        this.file = file;
    }

    static SpeciesReview load() {
        SpeciesReview review = new SpeciesReview(FabricLoader.getInstance().getGameDir()
                .resolve(Fusionmon.MOD_ID).resolve("species-review.json"));
        if (!Files.isRegularFile(review.file)) {
            return review;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(review.file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                Status status = Status.valueOf(value.get("status").getAsString().toUpperCase(Locale.ROOT));
                String note = value.has("note") ? value.get("note").getAsString() : "";
                String date = value.has("date") ? value.get("date").getAsString() : "";
                review.entries.put(entry.getKey(), new Entry(status, note, date));
            }
        } catch (IOException | RuntimeException e) {
            Fusionmon.LOGGER.warn("No se pudo leer {}", review.file, e);
        }
        return review;
    }

    Entry get(String key) {
        return entries.get(key);
    }

    void set(String key, Status status, String note) {
        entries.put(key, new Entry(status, note == null ? "" : note.trim(), LocalDate.now().toString()));
        save();
    }

    void clear(String key) {
        if (entries.remove(key) != null) {
            save();
        }
    }

    long count(Status status) {
        return entries.values().stream().filter(entry -> entry.status() == status).count();
    }

    private void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("status", entry.getValue().status().name().toLowerCase(Locale.ROOT));
            if (!entry.getValue().note().isEmpty()) {
                value.addProperty("note", entry.getValue().note());
            }
            value.addProperty("date", entry.getValue().date());
            root.add(entry.getKey(), value);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Fusionmon.LOGGER.warn("No se pudo guardar {}", file, e);
        }
    }
}
