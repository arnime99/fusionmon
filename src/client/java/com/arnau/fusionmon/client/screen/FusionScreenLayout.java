package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionPartView;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Piezas comunes de las pantallas de fusionar y separar: la fila de tres visores (cabeza → fusión ← cuerpo) con sus
 * textos, y las barras de stats. Las medidas van en píxeles de interfaz: con la escala de interfaz 4 en un monitor
 * 1080p la pantalla mide unos 480×270, así que todo se encoge para caber.
 */
final class FusionScreenLayout {

    static final int WHITE = 0xFFFFFF;
    static final int GRAY = 0xAAAAAA;
    static final int YELLOW = 0xFFFF55;
    static final int MARGIN = 4;
    static final int LINE = 10;
    static final int BUTTON = 20;
    /** Dos filas de tres stats. */
    static final int STATS_HEIGHT = 2 * 11;
    /** Con el visor central al menos así de grande, sobra sitio para extras (descripciones). */
    static final int COMFORT_BOX = 90;

    private static final int MIN_BOX = 56;
    private static final int MAX_BOX = 150;
    /** Los visores de los lados, respecto al central. */
    private static final float SIDE_SCALE = 0.75F;
    /** Hueco entre visores, donde va la flecha. */
    private static final int ARROW_GAP = 18;

    private static final String[] STAT_KEYS = {
            "gui.fusionmon.stat.hp", "gui.fusionmon.stat.attack", "gui.fusionmon.stat.defence",
            "gui.fusionmon.stat.special_attack", "gui.fusionmon.stat.special_defence", "gui.fusionmon.stat.speed"
    };
    /** Una barra llena = este stat base (pocos pasan de aquí; los que pasan, llena). */
    private static final float STAT_FULL = 180F;
    private static final int STAT_BAR_HEIGHT = 5;

    private FusionScreenLayout() {
    }

    /** Los tres visores en fila: a la izquierda la cabeza, en el centro la fusión y a la derecha el cuerpo. */
    static final class Viewports {

        private final ModelViewport left = new ModelViewport();
        private final ModelViewport center = new ModelViewport();
        private final ModelViewport right = new ModelViewport();
        /** El visor que se está arrastrando (el que estaba bajo el ratón al pulsar). */
        private ModelViewport dragging;

        private int box;
        private int side;
        private int leftX;
        private int centerX;
        private int rightX;
        private int centerY;
        private int sideY;

        /**
         * Calcula el tamaño de los visores para el ancho de la pantalla y el alto que les queda, y los coloca en
         * horizontal. Devuelve el lado del visor central.
         */
        int place(int width, int freeHeight) {
            box = Mth.clamp(freeHeight, MIN_BOX, MAX_BOX);
            // Que quepan los tres a lo ancho (los de los lados miden SIDE_SCALE del central)
            int maxByWidth = (int) ((width - 2 * MARGIN - 2 * ARROW_GAP) / (1 + 2 * SIDE_SCALE));
            box = Math.min(box, maxByWidth);
            side = Math.round(box * SIDE_SCALE);
            int total = 2 * side + box + 2 * ARROW_GAP;
            leftX = (width - total) / 2;
            centerX = leftX + side + ARROW_GAP;
            rightX = centerX + box + ARROW_GAP;
            return box;
        }

        /** Altura de la fila: el central arriba y los de los lados alineados por abajo (encima, "cabeza"/"cuerpo"). */
        void setTop(int y) {
            centerY = y;
            sideY = y + box - side;
        }

        /**
         * @param splitting  al separar las flechas salen de la fusión; al fusionar van hacia ella
         * @param silhouette la fusión aún sin descubrir: en negro (ver FusionDiscovery)
         */
        void render(GuiGraphics graphics, Font font, FusionPartView head, FusionPartView fusion, FusionPartView body,
                    FloatingState headState, FloatingState fusionState, FloatingState bodyState, boolean splitting,
                    boolean silhouette, float partialTick) {
            left.render(graphics, head.model(), headState, "", leftX, sideY, side, partialTick, null, null);
            center.render(graphics, fusion.model(), fusionState, "", centerX, centerY, box, partialTick, null, null,
                    silhouette);
            right.render(graphics, body.model(), bodyState, "", rightX, sideY, side, partialTick, null, null);

            int arrowY = sideY + side / 2 - font.lineHeight / 2;
            graphics.drawCenteredString(font, splitting ? "←" : "→", leftX + side + ARROW_GAP / 2, arrowY, GRAY);
            graphics.drawCenteredString(font, splitting ? "→" : "←", rightX - ARROW_GAP / 2, arrowY, GRAY);
            // Qué es cada lado, encima de su visor
            graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.part.head"),
                    leftX + side / 2, sideY - LINE, YELLOW);
            graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.part.body"),
                    rightX + side / 2, sideY - LINE, YELLOW);
        }

        /** Debajo de cada visor: nombre y nivel, y sus tipos. */
        void renderLabels(GuiGraphics graphics, Font font, FusionPartView head, FusionPartView fusion,
                          FusionPartView body, int y) {
            label(graphics, font, head, leftX + side / 2, y, side + ARROW_GAP - 2, WHITE);
            label(graphics, font, fusion, centerX + box / 2, y, box + ARROW_GAP - 2, YELLOW);
            label(graphics, font, body, rightX + side / 2, y, side + ARROW_GAP - 2, WHITE);
        }

        private static void label(GuiGraphics graphics, Font font, FusionPartView part, int x, int y, int maxWidth,
                                  int nameColor) {
            MutableComponent name = part.name().copy().withColor(nameColor)
                    .append(Component.literal(" ").append(Component.translatable("gui.fusionmon.level", part.level()))
                            .withColor(GRAY));
            graphics.drawCenteredString(font, fit(font, name, maxWidth), x, y, WHITE);
            graphics.drawCenteredString(font, fit(font, joinTypes(part.types()), maxWidth), x, y + LINE, GRAY);
        }

        // ---- Ratón: cada visor por separado ----

        boolean press(double mouseX, double mouseY, int button) {
            dragging = at(mouseX, mouseY);
            if (dragging == null) {
                return false;
            }
            dragging.press(button);
            return true;
        }

        void release() {
            if (dragging != null) {
                dragging.release();
                dragging = null;
            }
        }

        boolean drag(double dragX, double dragY) {
            return dragging != null && dragging.drag(dragX, dragY);
        }

        boolean scroll(double mouseX, double mouseY, double scrollY) {
            ModelViewport viewport = at(mouseX, mouseY);
            if (viewport == null) {
                return false;
            }
            viewport.scroll(scrollY);
            return true;
        }

        private ModelViewport at(double x, double y) {
            if (inside(x, y, leftX, sideY, side)) {
                return left;
            }
            if (inside(x, y, centerX, centerY, box)) {
                return center;
            }
            if (inside(x, y, rightX, sideY, side)) {
                return right;
            }
            return null;
        }

        private static boolean inside(double x, double y, int boxX, int boxY, int size) {
            return x >= boxX && x < boxX + size && y >= boxY && y < boxY + size;
        }
    }

    /**
     * Stats base en dos filas de tres, cada uno con su barra (colores por valor, como en los juegos: rojo bajo,
     * verde alto), centradas en centerX.
     */
    static void renderStats(GuiGraphics graphics, Font font, List<Integer> stats, int centerX, int y, int screenWidth) {
        renderStats(graphics, font, stats, centerX, y, screenWidth, false);
    }

    /** @param hidden fusión sin descubrir: barras vacías y "?" en vez de los valores */
    static void renderStats(GuiGraphics graphics, Font font, List<Integer> stats, int centerX, int y, int screenWidth,
                            boolean hidden) {
        int labelWidth = 0;
        for (String key : STAT_KEYS) {
            labelWidth = Math.max(labelWidth, font.width(Component.translatable(key)));
        }
        int valueWidth = font.width("255");
        int column = Math.min(140, (screenWidth - 2 * MARGIN) / 3);
        int barWidth = Math.max(10, column - labelWidth - valueWidth - 14);
        int startX = centerX - 3 * column / 2;
        for (int i = 0; i < Math.min(stats.size(), STAT_KEYS.length); i++) {
            int x = startX + (i % 3) * column;
            int rowY = y + (i / 3) * 11;
            int value = stats.get(i);
            graphics.drawString(font, Component.translatable(STAT_KEYS[i]), x, rowY, GRAY);
            int barX = x + labelWidth + 4;
            int barY = rowY + (font.lineHeight - STAT_BAR_HEIGHT) / 2 - 1;
            graphics.fill(barX, barY, barX + barWidth, barY + STAT_BAR_HEIGHT, 0xFF333333);
            if (hidden) {
                graphics.drawString(font, "?", barX + barWidth + 4, rowY, GRAY);
                continue;
            }
            int filled = Math.round(barWidth * Math.min(1F, value / STAT_FULL));
            graphics.fill(barX, barY, barX + filled, barY + STAT_BAR_HEIGHT, 0xFF000000 | statColor(value));
            graphics.drawString(font, String.valueOf(value), barX + barWidth + 4, rowY, WHITE);
        }
    }

    /** Color de un stat base por tramos (los de las tablas de stats de los juegos y de Showdown). */
    private static int statColor(int value) {
        if (value < 60) {
            return 0xF34444;
        }
        if (value < 90) {
            return 0xFF7F0F;
        }
        if (value < 120) {
            return 0xFFDD57;
        }
        if (value < 150) {
            return 0xA0E515;
        }
        return 0x23CD5E;
    }

    static Component joinTypes(List<Component> types) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                result.append(" / ");
            }
            result.append(types.get(i));
        }
        return result;
    }

    /** El texto tal cual si cabe; si no, cortado con "…" (los nombres largos no pisan el visor de al lado). */
    static Component fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String plain = font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - font.width("…")));
        return Component.literal(plain + "…").withStyle(text.getStyle());
    }
}
