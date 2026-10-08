package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import com.arnau.fusionmon.network.UnfuseChoicePayload;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Pantalla de confirmación para separar una fusión. Solo muestra y recoge la decisión: la separación la hace el
 * servidor cuando recibe UnfuseChoicePayload.
 *
 * Como la de fusionar pero al revés: la fusión en el centro y las dos partes que saldrán a los lados (ver
 * FusionScreenLayout), con la experiencia que recibirá cada una.
 */
public class UnfuseConfirmScreen extends Screen {

    private static final int BUTTON_WIDTH = 110;
    private static final int GAP = 4;
    /** Título, textos de debajo de los visores, la línea de experiencia y los botones. */
    private static final int FIXED_HEIGHT = 12 + 3 + 2 * FusionScreenLayout.LINE + 2
            + GAP + FusionScreenLayout.LINE
            + 2 * GAP + FusionScreenLayout.BUTTON;

    private final OpenUnfuseScreenPayload data;
    /** true cuando ya se ha mandado la respuesta, para no mandarla dos veces. */
    private boolean answered;

    private final FusionScreenLayout.Viewports viewports = new FusionScreenLayout.Viewports();
    private final FloatingState headState = new FloatingState();
    private final FloatingState fusionState = new FloatingState();
    private final FloatingState bodyState = new FloatingState();

    private int top;
    private int labelsY;
    private int experienceY;

    public UnfuseConfirmScreen(OpenUnfuseScreenPayload data) {
        super(Component.translatable("gui.fusionmon.unfuse.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int box = viewports.place(width, height - 2 * FusionScreenLayout.MARGIN - FIXED_HEIGHT);
        top = Math.max(FusionScreenLayout.MARGIN, (height - FIXED_HEIGHT - box) / 2);
        viewports.setTop(top + 12);
        labelsY = top + 12 + box + 3;
        experienceY = labelsY + 2 * FusionScreenLayout.LINE + 2 + GAP;
        int buttonsY = experienceY + FusionScreenLayout.LINE + 2 * GAP;

        int centerX = width / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.unfuse.accept"), button -> answer(true))
                .bounds(centerX - BUTTON_WIDTH - GAP / 2, buttonsY, BUTTON_WIDTH, FusionScreenLayout.BUTTON)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(false))
                .bounds(centerX + GAP / 2, buttonsY, BUTTON_WIDTH, FusionScreenLayout.BUTTON)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, top, FusionScreenLayout.WHITE);

        // Cabeza ← fusión → cuerpo
        viewports.render(graphics, font, data.head(), data.fusion(), data.body(), headState, fusionState, bodyState,
                true, partialTick);
        viewports.renderLabels(graphics, font, data.head(), data.fusion(), data.body(), labelsY);

        Component experience = data.experienceGained() > 0
                ? Component.translatable("gui.fusionmon.unfuse.experience", data.experienceGained())
                : Component.translatable("gui.fusionmon.unfuse.no_experience");
        graphics.drawCenteredString(font, experience, centerX, experienceY, FusionScreenLayout.GRAY);
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

    /** removed() se llama siempre que la pantalla se cierra (Esc, otra pantalla...): cuenta como Cancelar. */
    @Override
    public void removed() {
        if (!answered && ClientPlayNetworking.canSend(UnfuseChoicePayload.TYPE)) {
            ClientPlayNetworking.send(new UnfuseChoicePayload(false));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones de los modelos siguen
        return false;
    }

    private void answer(boolean accepted) {
        answered = true;
        ClientPlayNetworking.send(new UnfuseChoicePayload(accepted));
        onClose();
    }
}
