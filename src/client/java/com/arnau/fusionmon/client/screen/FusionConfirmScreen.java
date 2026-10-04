package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionChoicePayload;
import com.arnau.fusionmon.network.FusionPreview;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Pantalla de confirmación de la fusión. Solo muestra y recoge la decisión:
 * la fusión la hace el servidor cuando recibe FusionChoicePayload.
 */
public class FusionConfirmScreen extends Screen {

    private static final int PANEL_HEIGHT = 214;
    private static final int OPTION_WIDTH = 110;
    private static final int GAP = 8;
    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int YELLOW = 0xFFFF55;

    private static final String[] STAT_KEYS = {
            "gui.fusionmon.stat.hp", "gui.fusionmon.stat.attack", "gui.fusionmon.stat.defence",
            "gui.fusionmon.stat.special_attack", "gui.fusionmon.stat.special_defence", "gui.fusionmon.stat.speed"
    };

    private final OpenFusionScreenPayload data;
    private boolean swapped;
    private boolean natureFromB;
    private boolean abilityFromB;
    /** true cuando ya se ha mandado la respuesta (Aceptar o Cancelar), para no mandarla dos veces. */
    private boolean answered;

    private Button natureAButton;
    private Button natureBButton;
    private Button abilityAButton;
    private Button abilityBButton;

    public FusionConfirmScreen(OpenFusionScreenPayload data) {
        super(Component.translatable("gui.fusionmon.confirm.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = top();

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.swap"), button -> swapped = !swapped)
                .bounds(centerX - 60, top + 86, 120, 20)
                .build());

        int leftX = centerX - OPTION_WIDTH - GAP / 2;
        int rightX = centerX + GAP / 2;

        natureAButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            natureFromB = false;
            refreshOptionLabels();
        }).bounds(leftX, top + 124, OPTION_WIDTH, 20).build());
        natureBButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            natureFromB = true;
            refreshOptionLabels();
        }).bounds(rightX, top + 124, OPTION_WIDTH, 20).build());

        abilityAButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            abilityFromB = false;
            refreshOptionLabels();
        }).bounds(leftX, top + 160, OPTION_WIDTH, 20).build());
        abilityBButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            abilityFromB = true;
            refreshOptionLabels();
        }).bounds(rightX, top + 160, OPTION_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.accept"), button -> answer(true))
                .bounds(leftX, top + 192, OPTION_WIDTH, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(false))
                .bounds(rightX, top + 192, OPTION_WIDTH, 20)
                .build());

        refreshOptionLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int top = top();
        FusionPreview preview = swapped ? data.swappedPreview() : data.preview();
        Component headName = swapped ? data.nameB() : data.nameA();
        Component bodyName = swapped ? data.nameA() : data.nameB();

        graphics.drawCenteredString(font, title, centerX, top, WHITE);
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.parts", headName, bodyName),
                centerX, top + 16, GRAY);
        graphics.drawCenteredString(font, Component.literal("→ ").append(preview.name()), centerX, top + 30, YELLOW);
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.types_level",
                joinTypes(preview.types()), preview.level()), centerX, top + 44, WHITE);

        graphics.drawCenteredString(font, statLine(preview.baseStats(), 0), centerX, top + 60, GRAY);
        graphics.drawCenteredString(font, statLine(preview.baseStats(), 3), centerX, top + 72, GRAY);

        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.nature"), centerX, top + 112, WHITE);
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.confirm.ability"), centerX, top + 148, WHITE);
    }

    /** removed() se llama siempre que la pantalla se cierra (Esc, otra pantalla...): cuenta como Cancelar. */
    @Override
    public void removed() {
        if (!answered && ClientPlayNetworking.canSend(FusionChoicePayload.TYPE)) {
            ClientPlayNetworking.send(FusionChoicePayload.cancel());
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void answer(boolean accepted) {
        answered = true;
        ClientPlayNetworking.send(accepted
                ? new FusionChoicePayload(true, swapped, natureFromB, abilityFromB)
                : FusionChoicePayload.cancel());
        onClose();
    }

    /** Marca con ✔ la opción elegida de cada pareja. */
    private void refreshOptionLabels() {
        natureAButton.setMessage(option(data.natureA(), !natureFromB));
        natureBButton.setMessage(option(data.natureB(), natureFromB));
        abilityAButton.setMessage(option(data.abilityA(), !abilityFromB));
        abilityBButton.setMessage(option(data.abilityB(), abilityFromB));
    }

    private static Component option(Component label, boolean selected) {
        return selected ? Component.literal("✔ ").append(label) : label.copy();
    }

    private static Component joinTypes(List<Component> types) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                result.append(" / ");
            }
            result.append(types.get(i));
        }
        return result;
    }

    /** Tres stats a partir de "first": "PS 43   Ataque 51   Defensa 45". */
    private static Component statLine(List<Integer> stats, int first) {
        MutableComponent line = Component.empty();
        for (int i = first; i < first + 3 && i < stats.size(); i++) {
            if (i > first) {
                line.append("   ");
            }
            line.append(Component.translatable(STAT_KEYS[i])).append(" " + stats.get(i));
        }
        return line;
    }

    private int top() {
        return Math.max(4, height / 2 - PANEL_HEIGHT / 2);
    }
}
