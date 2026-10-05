package com.arnau.fusionmon.client.texture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cambio de paleta: pinta la textura de la cabeza con los colores del cuerpo.
 *
 * Idea: las texturas de Cobblemon son pixel art con pocos colores. Ordenamos de oscuro a claro los colores
 * "con color" de cada textura, cada uno ocupando tanto como píxeles tiene (como una barra apilada de 0 a 1).
 * Cada color de la cabeza se cambia por el color del cuerpo que ocupa su misma posición en la barra:
 * el tono más oscuro de Pikachu pasa a ser el más oscuro de Charizard, el más común el más común, etc.
 * Así se conserva el sombreado de la cabeza (sigue habiendo luces y sombras en el mismo sitio).
 *
 * Los grises, negros y blancos de la cabeza (contornos, ojos, dientes) no se tocan.
 *
 * Trabaja con píxeles ARGB (0xAARRGGBB) y no usa clases de Minecraft, para poder probarlo fuera del juego.
 */
public final class FusionPalette {

    /** Por debajo de esta saturación un color se considera gris (no se recolorea). */
    private static final double MIN_SATURATION = 0.2;
    /** Casi negros (contornos) y casi blancos (brillos de los ojos) tampoco. */
    private static final double MIN_LIGHTNESS = 0.1;
    private static final double MAX_LIGHTNESS = 0.92;
    /**
     * Si el cuerpo apenas tiene colores (p. ej. un Pokémon gris como Onix), su paleta son todos sus píxeles:
     * así la fusión sale gris en vez de quedarse con los cuatro píxeles de color que tenga.
     */
    private static final double MIN_COLORED_FRACTION = 0.1;

    private FusionPalette() {
    }

    public static int[] recolor(int[] head, int[] body) {
        List<Shade> bodyShades = shades(body, true);
        List<Shade> headShades = shades(head, false);
        int[] result = head.clone();
        if (bodyShades.isEmpty() || headShades.isEmpty()) {
            return result;
        }

        Map<Integer, Integer> mapping = new HashMap<>();
        for (Shade shade : headShades) {
            mapping.put(shade.rgb, colorAt(bodyShades, shade.middle()));
        }

        for (int i = 0; i < result.length; i++) {
            int alpha = result[i] >>> 24;
            if (alpha == 0) {
                continue;
            }
            Integer rgb = mapping.get(result[i] & 0xFFFFFF);
            if (rgb != null) {
                // Se conserva la transparencia original del píxel
                result[i] = (alpha << 24) | rgb;
            }
        }
        return result;
    }

    /** Un color de la paleta y el tramo [start, end) que ocupa en la barra de 0 a 1. */
    private record Shade(int rgb, double start, double end) {
        double middle() {
            return (start + end) / 2;
        }
    }

    /**
     * Colores de la textura ordenados de oscuro a claro, con su tramo de la barra.
     * Con fallbackToAll, si casi no hay colores se usan todos los píxeles (ver MIN_COLORED_FRACTION).
     */
    private static List<Shade> shades(int[] pixels, boolean fallbackToAll) {
        Map<Integer, Integer> colored = new HashMap<>();
        Map<Integer, Integer> all = new HashMap<>();
        int coloredCount = 0;
        int opaqueCount = 0;
        for (int pixel : pixels) {
            if (pixel >>> 24 == 0) {
                // Zonas vacías del atlas de la textura
                continue;
            }
            int rgb = pixel & 0xFFFFFF;
            all.merge(rgb, 1, Integer::sum);
            opaqueCount++;
            if (isColored(rgb)) {
                colored.merge(rgb, 1, Integer::sum);
                coloredCount++;
            }
        }

        Map<Integer, Integer> counts = colored;
        int total = coloredCount;
        if (fallbackToAll && coloredCount < opaqueCount * MIN_COLORED_FRACTION) {
            counts = all;
            total = opaqueCount;
        }
        if (total == 0) {
            return List.of();
        }

        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.comparingDouble(entry -> luma(entry.getKey())));

        List<Shade> shades = new ArrayList<>();
        int accumulated = 0;
        for (Map.Entry<Integer, Integer> entry : entries) {
            double start = (double) accumulated / total;
            accumulated += entry.getValue();
            double end = (double) accumulated / total;
            shades.add(new Shade(entry.getKey(), start, end));
        }
        return shades;
    }

    private static int colorAt(List<Shade> shades, double position) {
        for (Shade shade : shades) {
            if (position < shade.end) {
                return shade.rgb;
            }
        }
        return shades.get(shades.size() - 1).rgb;
    }

    private static boolean isColored(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        // Luminosidad y saturación de HSL
        double lightness = (max + min) / 2;
        if (lightness < MIN_LIGHTNESS || lightness > MAX_LIGHTNESS) {
            return false;
        }
        double saturation = (max - min) / (1 - Math.abs(2 * lightness - 1));
        return saturation >= MIN_SATURATION;
    }

    /** Claridad tal como la percibe el ojo (el verde pesa más que el azul). */
    private static double luma(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }
}
