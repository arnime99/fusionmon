package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import com.arnau.fusionmon.network.UnfuseChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Pantalla de confirmación para separar una fusión. Solo muestra y recoge la decisión:
 * la separación la hace el servidor cuando recibe UnfuseChoicePayload.
 */
public class UnfuseConfirmScreen extends Screen {

    private static final int PANEL_HEIGHT = 100;
    private static final int BUTTON_WIDTH = 110;
    private static final int GAP = 8;
    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int YELLOW = 0xFFFF55;

    private final OpenUnfuseScreenPayload data;
    /** true cuando ya se ha mandado la respuesta, para no mandarla dos veces. */
    private boolean answered;

    public UnfuseConfirmScreen(OpenUnfuseScreenPayload data) {
        super(Component.translatable("gui.fusionmon.unfuse.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = top();

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.unfuse.accept"), button -> answer(true))
                .bounds(centerX - BUTTON_WIDTH - GAP / 2, top + 76, BUTTON_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(false))
                .bounds(centerX + GAP / 2, top + 76, BUTTON_WIDTH, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int top = top();

        graphics.drawCenteredString(font, title, centerX, top, WHITE);
        graphics.drawCenteredString(font, data.fusedName(), centerX, top + 16, YELLOW);
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.unfuse.parts",
                data.headName(), data.headLevel(), data.bodyName(), data.bodyLevel()), centerX, top + 32, WHITE);

        Component experience = data.experienceGained() > 0
                ? Component.translatable("gui.fusionmon.unfuse.experience", data.experienceGained())
                : Component.translatable("gui.fusionmon.unfuse.no_experience");
        graphics.drawCenteredString(font, experience, centerX, top + 48, GRAY);
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
        return false;
    }

    private void answer(boolean accepted) {
        answered = true;
        ClientPlayNetworking.send(new UnfuseChoicePayload(accepted));
        onClose();
    }

    private int top() {
        return Math.max(4, height / 2 - PANEL_HEIGHT / 2);
    }
}
