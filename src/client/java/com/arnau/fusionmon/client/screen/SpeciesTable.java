package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.Fusionmon;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * La tabla de especies que genera tools/species-table.ps1 (docs/species/especies.csv): forma, etiquetas y avisos de
 * cada especie, para el inspector. Solo existe en el proyecto (se busca junto a la carpeta del juego, "run"): en un
 * juego normal no está y el inspector funciona sin ella.
 */
final class SpeciesTable {

    /** Lo que dice la tabla de una especie (de su modelo base, si tiene varios). */
    record Row(String shape, String tags, List<String> warnings) {
    }

    private final Map<String, Row> rows;

    private SpeciesTable(Map<String, Row> rows) {
        this.rows = rows;
    }

    static SpeciesTable load() {
        Path csv = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().getParent()
                .resolve("docs").resolve("species").resolve("especies.csv");
        Map<String, Row> rows = new HashMap<>();
        if (!Files.isRegularFile(csv)) {
            return new SpeciesTable(rows);
        }
        try {
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return new SpeciesTable(rows);
            }
            // El script lo escribe con BOM para que Excel lea bien las tildes
            List<String> header = parse(lines.get(0).replace("﻿", ""));
            int species = header.indexOf("especie");
            int kind = header.indexOf("tipo");
            int shape = header.indexOf("forma");
            int tags = header.indexOf("etiquetas");
            int warnings = header.indexOf("avisos");
            if (species < 0 || shape < 0 || warnings < 0) {
                return new SpeciesTable(rows);
            }
            Map<String, Boolean> fromBase = new HashMap<>();
            for (String line : lines.subList(1, lines.size())) {
                List<String> fields = parse(line);
                if (fields.size() <= Math.max(species, Math.max(shape, warnings))) {
                    continue;
                }
                List<String> list = new ArrayList<>();
                for (String warning : fields.get(warnings).split(" \\| ")) {
                    if (!warning.isBlank()) {
                        list.add(warning);
                    }
                }
                Row row = new Row(fields.get(shape), tags >= 0 ? fields.get(tags) : "", list);
                boolean base = kind >= 0 && fields.get(kind).equals("base");
                // "pikachu, pikachu [female]": una especie por entrada; entre corchetes, los aspects con que lo usa
                for (String entry : fields.get(species).split(", ")) {
                    String name = entry.split(" ")[0];
                    // Mejor la fila de su modelo base que la de una forma o un cosmético
                    if (!rows.containsKey(name) || base && !fromBase.get(name)) {
                        rows.put(name, row);
                        fromBase.put(name, base);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Fusionmon.LOGGER.warn("No se pudo leer la tabla de especies {}", csv, e);
        }
        return new SpeciesTable(rows);
    }

    /** Campos de una línea del CSV: separados por ";", entre comillas (con "" dentro para una comilla). */
    private static List<String> parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ';') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    /** La fila de una especie (por su nombre interno, "pikachu"), o null. */
    Row get(String species) {
        return rows.get(species);
    }

    /** Las formas que salen en la tabla, por orden alfabético. */
    List<String> shapes() {
        TreeSet<String> shapes = new TreeSet<>();
        for (Row row : rows.values()) {
            shapes.add(row.shape());
        }
        return List.copyOf(shapes);
    }
}
