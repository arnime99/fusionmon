package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionPartView;
import com.arnau.fusionmon.network.OpenUnfuseScreenPayload;
import com.arnau.fusionmon.network.UnfuseChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Pantalla de confirmación para separar una fusión: arriba, cabeza ← fusión → cuerpo (FusionScreenBase, como la de
 * fusionar pero al revés); abajo, la experiencia que recibirá cada parte y los botones. La separación la hace el
 * servidor cuando recibe UnfuseChoicePayload. Invertir le pide al servidor la pantalla de fusión con las partes al
 * revés (FusionConfirmScreen en modo invertir).
 */
public class UnfuseConfirmScreen extends FusionScreenBase {

    private static final int BUTTON_WIDTH = 100;
    /** La línea de experiencia y los botones. */
    private static final int BOTTOM_HEIGHT = GAP + FusionScreenLayout.LINE + 2 * GAP + FusionScreenLayout.BUTTON;

    private final OpenUnfuseScreenPayload data;

    public UnfuseConfirmScreen(OpenUnfuseScreenPayload data) {
        super(Component.translatable("gui.fusionmon.unfuse.title"));
        this.data = data;
    }

    @Override
    protected FusionPartView head() {
        return data.head();
    }

    @Override
    protected FusionPartView fusion() {
        return data.fusion();
    }

    @Override
    protected FusionPartView body() {
        return data.body();
    }

    @Override
    protected boolean splitting() {
        return true;
    }

    @Override
    protected int bottomHeight(int spaceWithoutBottom) {
        return BOTTOM_HEIGHT;
    }

    @Override
    protected void initBottom(int y) {
        int buttonsY = y + GAP + FusionScreenLayout.LINE + 2 * GAP;
        // [Separar] [Invertir] [Cancelar], centrados
        int x = width / 2 - (3 * BUTTON_WIDTH + 2 * GAP) / 2;
        Button split = addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.unfuse.accept"),
                        button -> answer(() -> send(UnfuseChoicePayload.Action.SPLIT)))
                .bounds(x, buttonsY, BUTTON_WIDTH, FusionScreenLayout.BUTTON)
                .build());
        // Sin sitio para el cuerpo no se puede separar (sí invertir): el botón dice por qué
        if (!data.roomForBody()) {
            split.active = false;
            split.setTooltip(Tooltip.create(Component.translatable("message.fusionmon.no_room", data.body().name())));
        }
        x += BUTTON_WIDTH + GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.unfuse.reverse"),
                        button -> answer(() -> send(UnfuseChoicePayload.Action.REVERSE)))
                .bounds(x, buttonsY, BUTTON_WIDTH, FusionScreenLayout.BUTTON)
                .tooltip(Tooltip.create(Component.translatable("gui.fusionmon.unfuse.reverse.tooltip")))
                .build());
        x += BUTTON_WIDTH + GAP;
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(this::sendCancel))
                .bounds(x, buttonsY, BUTTON_WIDTH, FusionScreenLayout.BUTTON)
                .build());
    }

    private static void send(UnfuseChoicePayload.Action action) {
        ClientPlayNetworking.send(new UnfuseChoicePayload(action));
    }

    @Override
    protected void renderBottom(GuiGraphics graphics, int y) {
        Component experience = data.experienceGained() > 0
                ? Component.translatable("gui.fusionmon.unfuse.experience", data.experienceGained())
                : Component.translatable("gui.fusionmon.unfuse.no_experience");
        graphics.drawCenteredString(font, experience, width / 2, y + GAP, FusionScreenLayout.GRAY);
    }

    @Override
    protected void sendCancel() {
        if (ClientPlayNetworking.canSend(UnfuseChoicePayload.TYPE)) {
            send(UnfuseChoicePayload.Action.CANCEL);
        }
    }
}
