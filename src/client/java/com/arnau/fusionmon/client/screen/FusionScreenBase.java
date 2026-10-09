package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionPartView;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base de las pantallas del cristal (fusionar, invertir, separar): arriba lo común, el título y la fila de tres visores
 * (cabeza, fusión, cuerpo) con sus textos; abajo, lo de cada pantalla. Todo lo que se dibuje igual en todas (un fondo
 * o un marco con sprites propios, el día que los haya) va aquí, y vale para las tres.
 *
 * Solo muestran y recogen la decisión: lo hace el servidor cuando le llega la respuesta. Cerrar la pantalla sin
 * responder (Esc, otra pantalla...) cuenta como cancelar.
 */
abstract class FusionScreenBase extends Screen {

    static final int GAP = 4;
    /** Título, hueco y dos líneas de texto debajo de los visores. */
    private static final int TOP_FIXED = 12 + 3 + 2 * FusionScreenLayout.LINE + 2;

    private final FusionScreenLayout.Viewports viewports = new FusionScreenLayout.Viewports();
    private final FloatingState headState = new FloatingState();
    private final FloatingState fusionState = new FloatingState();
    private final FloatingState bodyState = new FloatingState();

    private int top;
    private int labelsY;
    /** Donde empieza la parte de abajo (la de cada pantalla). */
    private int bottomY;
    /** true cuando ya se ha mandado la respuesta, para no mandarla dos veces. */
    private boolean answered;

    protected FusionScreenBase(Component title) {
        super(title);
    }

    // ---- Lo que pone cada pantalla ----

    protected abstract FusionPartView head();

    protected abstract FusionPartView fusion();

    protected abstract FusionPartView body();

    /** Al separar, las flechas salen de la fusión; al fusionar (o invertir), van hacia ella. */
    protected abstract boolean splitting();

    /** Si la fusión aún no se ha descubierto: se pinta como silueta (ver FusionDiscovery). */
    protected boolean silhouette() {
        return false;
    }

    /**
     * Alto de la parte de abajo. Recibe el alto que quedaría para los visores sin ella, por si la pantalla quiere
     * enseñar algo más (descripciones) solo cuando hay sitio.
     */
    protected abstract int bottomHeight(int spaceWithoutBottom);

    /** Botones de la parte de abajo, a partir de y. */
    protected abstract void initBottom(int y);

    /** Textos de la parte de abajo, a partir de y. */
    protected abstract void renderBottom(GuiGraphics graphics, int y);

    /** Manda al servidor que se ha cancelado. */
    protected abstract void sendCancel();

    // ---- Común ----

    @Override
    protected final void init() {
        int space = height - 2 * FusionScreenLayout.MARGIN - TOP_FIXED;
        int bottom = bottomHeight(space);
        // Visores lo más grandes posible con todo lo demás en pantalla
        int box = viewports.place(width, space - bottom);
        top = Math.max(FusionScreenLayout.MARGIN, (height - TOP_FIXED - box - bottom) / 2);
        viewports.setTop(top + 12);
        labelsY = top + 12 + box + 3;
        bottomY = labelsY + 2 * FusionScreenLayout.LINE + 2;
        initBottom(bottomY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top, FusionScreenLayout.WHITE);
        FusionPartView head = head();
        FusionPartView fusion = fusion();
        FusionPartView body = body();
        viewports.render(graphics, font, head, fusion, body, headState, fusionState, bodyState, splitting(),
                silhouette(), partialTick);
        viewports.renderLabels(graphics, font, head, fusion, body, labelsY);
        renderBottom(graphics, bottomY);
    }

    /** Manda la respuesta (una sola vez) y cierra. */
    protected final void answer(Runnable send) {
        answered = true;
        send.run();
        onClose();
    }

    @Override
    public void removed() {
        if (!answered) {
            sendCancel();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones de los modelos siguen
        return false;
    }

    // ---- Ratón: cada visor se gira y se acerca por separado ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || viewports.press(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        viewports.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return viewports.drag(dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return viewports.scroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
