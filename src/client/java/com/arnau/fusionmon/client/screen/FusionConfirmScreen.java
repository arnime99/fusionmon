package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionChoicePayload;
import com.arnau.fusionmon.network.FusionPartView;
import com.arnau.fusionmon.network.FusionPreview;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Pantalla de confirmación de la fusión. Solo muestra y recoge la decisión: la fusión la hace el servidor cuando
 * recibe FusionChoicePayload.
 *
 * Arriba, los dos Pokémon a los lados (cabeza a la izquierda, cuerpo a la derecha) y la fusión en el centro, cada uno
 * en su visor 3D (el mismo que /fusiondex: se gira arrastrando y se acerca con la rueda). Debajo, intercambiar, stats,
 * naturaleza, habilidad y aceptar. Todo se coloca según el tamaño de la pantalla (FusionScreenLayout): con la escala
 * de interfaz grande hay muy poco alto y los visores se encogen.
 */
public class FusionConfirmScreen extends Screen {

    private static final int OPTION_WIDTH = 110;
    private static final int DESCRIPTION_WIDTH = 2 * OPTION_WIDTH + 60;
    private static final int GAP = 4;

    /**
     * Alto de todo lo que no son los visores: título, textos de debajo, intercambiar, stats, naturaleza, habilidad y
     * aceptar (ver init); las líneas de descripción de la habilidad van aparte, solo si hay sitio.
     */
    private static final int FIXED_HEIGHT = 12 + 3 + 2 * FusionScreenLayout.LINE + 2
            + GAP + FusionScreenLayout.BUTTON
            + GAP + FusionScreenLayout.STATS_HEIGHT
            + GAP + FusionScreenLayout.BUTTON
            + GAP + FusionScreenLayout.BUTTON
            + GAP + FusionScreenLayout.BUTTON;
    private static final int DESCRIPTION_LINES = 2;

    private final OpenFusionScreenPayload data;
    private boolean swapped;
    private boolean natureFromB;
    private boolean abilityFromB;
    /** true cuando ya se ha mandado la respuesta (Aceptar o Cancelar), para no mandarla dos veces. */
    private boolean answered;

    private final FusionScreenLayout.Viewports viewports = new FusionScreenLayout.Viewports();
    /** Estados de animación de los tres visores (como los de los menús de Cobblemon: sin entidad detrás). */
    private final FloatingState leftState = new FloatingState();
    private final FloatingState centerState = new FloatingState();
    private final FloatingState rightState = new FloatingState();

    private int top;
    private int labelsY;
    private int statsY;
    private int natureY;
    private int abilityY;
    private int descriptionLines;

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
        // Visores lo más grandes posible con todo lo demás en pantalla; si no cabe, sin la descripción de la habilidad
        // (sigue en el tooltip del botón)
        int free = height - 2 * FusionScreenLayout.MARGIN - FIXED_HEIGHT;
        descriptionLines = free - DESCRIPTION_LINES * FusionScreenLayout.LINE >= FusionScreenLayout.COMFORT_BOX
                ? DESCRIPTION_LINES : 0;
        int box = viewports.place(width, free - descriptionLines * FusionScreenLayout.LINE);
        int contentHeight = FIXED_HEIGHT + box + descriptionLines * FusionScreenLayout.LINE;
        top = Math.max(FusionScreenLayout.MARGIN, (height - contentHeight) / 2);
        viewports.setTop(top + 12);

        int centerX = width / 2;
        labelsY = top + 12 + box + 3;
        int swapY = labelsY + 2 * FusionScreenLayout.LINE + 2 + GAP;
        statsY = swapY + FusionScreenLayout.BUTTON + GAP;
        natureY = statsY + FusionScreenLayout.STATS_HEIGHT + GAP;
        abilityY = natureY + FusionScreenLayout.BUTTON + GAP;
        int acceptY = abilityY + FusionScreenLayout.BUTTON + descriptionLines * FusionScreenLayout.LINE + GAP;

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.swap"), button -> swapped = !swapped)
                .bounds(centerX - 60, swapY, 120, FusionScreenLayout.BUTTON)
                .build());

        int leftX = centerX - OPTION_WIDTH - GAP / 2;
        int rightX = centerX + GAP / 2;

        // Al pasar el ratón por encima de una opción se ve su efecto o descripción (Tooltip)
        natureAButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            natureFromB = false;
            refreshOptionLabels();
        }).bounds(leftX, natureY, OPTION_WIDTH, FusionScreenLayout.BUTTON).tooltip(Tooltip.create(data.natureEffectA())).build());
        natureBButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            natureFromB = true;
            refreshOptionLabels();
        }).bounds(rightX, natureY, OPTION_WIDTH, FusionScreenLayout.BUTTON).tooltip(Tooltip.create(data.natureEffectB())).build());

        abilityAButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            abilityFromB = false;
            refreshOptionLabels();
        }).bounds(leftX, abilityY, OPTION_WIDTH, FusionScreenLayout.BUTTON).tooltip(Tooltip.create(data.abilityDescriptionA())).build());
        abilityBButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            abilityFromB = true;
            refreshOptionLabels();
        }).bounds(rightX, abilityY, OPTION_WIDTH, FusionScreenLayout.BUTTON).tooltip(Tooltip.create(data.abilityDescriptionB())).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.accept"), button -> answer(true))
                .bounds(leftX, acceptY, OPTION_WIDTH, FusionScreenLayout.BUTTON)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(false))
                .bounds(rightX, acceptY, OPTION_WIDTH, FusionScreenLayout.BUTTON)
                .build());

        refreshOptionLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        FusionPreview preview = swapped ? data.swappedPreview() : data.preview();
        FusionPartView head = swapped ? data.partB() : data.partA();
        FusionPartView body = swapped ? data.partA() : data.partB();

        graphics.drawCenteredString(font, title, centerX, top, FusionScreenLayout.WHITE);

        // Los tres visores: cabeza → fusión ← cuerpo
        FusionPartView fusion = preview.view();
        viewports.render(graphics, font, head, fusion, body, leftState, centerState, rightState, false, partialTick);
        viewports.renderLabels(graphics, font, head, fusion, body, labelsY);

        FusionScreenLayout.renderStats(graphics, font, preview.baseStats(), centerX, statsY, width);

        // Naturaleza y habilidad: el título a la izquierda de sus botones y, a la derecha, el efecto de la elegida
        int optionsLeft = centerX - OPTION_WIDTH - GAP / 2;
        int optionsRight = centerX + OPTION_WIDTH + GAP / 2;
        int textY = (FusionScreenLayout.BUTTON - font.lineHeight) / 2 + 1;
        Component nature = Component.translatable("gui.fusionmon.confirm.nature");
        graphics.drawString(font, nature, optionsLeft - GAP - font.width(nature), natureY + textY, FusionScreenLayout.WHITE);
        graphics.drawString(font, natureFromB ? data.natureEffectB() : data.natureEffectA(),
                optionsRight + GAP, natureY + textY, FusionScreenLayout.GRAY);
        Component ability = Component.translatable("gui.fusionmon.confirm.ability");
        graphics.drawString(font, ability, optionsLeft - GAP - font.width(ability), abilityY + textY, FusionScreenLayout.WHITE);

        // Las descripciones de habilidad pueden ser largas: se parten en líneas (si no caben, solo en el tooltip)
        List<FormattedCharSequence> lines = font.split(
                abilityFromB ? data.abilityDescriptionB() : data.abilityDescriptionA(), DESCRIPTION_WIDTH);
        for (int i = 0; i < Math.min(lines.size(), descriptionLines); i++) {
            graphics.drawCenteredString(font, lines.get(i), centerX,
                    abilityY + FusionScreenLayout.BUTTON + 2 + i * FusionScreenLayout.LINE, FusionScreenLayout.GRAY);
        }
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
        if (!answered && ClientPlayNetworking.canSend(FusionChoicePayload.TYPE)) {
            ClientPlayNetworking.send(FusionChoicePayload.cancel());
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
}
