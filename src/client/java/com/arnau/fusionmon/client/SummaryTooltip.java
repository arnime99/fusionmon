package com.arnau.fusionmon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Tooltip de Fusionmon en la pantalla de resumen de Cobblemon: un panel lo pide mientras se pinta (InfoWidgetMixin) y
 * se pinta al final de la pantalla (SummaryMixin), por delante de todo.
 *
 * Cobblemon pinta sus paneles adelantados 1000 en profundidad (translate z antes de super.render, javap de 1.8.1) y
 * los tooltips normales de Minecraft van a 400: quedaban por detrás. Va aparte de los mixins porque una clase mixin no
 * existe en tiempo de ejecución y no se puede llamar desde otra.
 */
public final class SummaryTooltip {

    /** Por delante de los paneles de Cobblemon (1000); renderTooltip suma otros 400. */
    private static final float Z = 1000F;

    private static List<FormattedCharSequence> pending;
    private static int x;
    private static int y;

    private SummaryTooltip() {
    }

    /** Este tooltip sale al final de este fotograma, en (mouseX, mouseY). */
    public static void show(List<FormattedCharSequence> lines, int mouseX, int mouseY) {
        pending = lines;
        x = mouseX;
        y = mouseY;
    }

    /** Al acabar de pintar la pantalla de resumen. */
    public static void render(GuiGraphics graphics) {
        if (pending == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z);
        graphics.renderTooltip(Minecraft.getInstance().font, pending, x, y);
        graphics.pose().popPose();
        pending = null;
    }
}
