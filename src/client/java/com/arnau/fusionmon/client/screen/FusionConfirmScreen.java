package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionChoicePayload;
import com.arnau.fusionmon.network.FusionPartView;
import com.arnau.fusionmon.network.FusionPreview;
import com.arnau.fusionmon.network.OpenFusionScreenPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Pantalla de confirmación de la fusión: arriba, cabeza → fusión ← cuerpo (FusionScreenBase); abajo, intercambiar,
 * stats, naturaleza, habilidad y aceptar. La fusión la hace el servidor cuando recibe FusionChoicePayload.
 */
public class FusionConfirmScreen extends FusionScreenBase {

    private static final int OPTION_WIDTH = 110;
    private static final int DESCRIPTION_WIDTH = 2 * OPTION_WIDTH + 60;
    private static final int DESCRIPTION_LINES = 2;
    /** Intercambiar, stats, naturaleza, habilidad y aceptar (sin la descripción de la habilidad). */
    private static final int BOTTOM_FIXED = GAP + FusionScreenLayout.BUTTON
            + GAP + FusionScreenLayout.STATS_HEIGHT
            + GAP + FusionScreenLayout.BUTTON
            + GAP + FusionScreenLayout.BUTTON
            + GAP + FusionScreenLayout.BUTTON;

    private final OpenFusionScreenPayload data;
    private boolean swapped;
    private boolean natureFromB;
    private boolean abilityFromB;

    private int statsY;
    private int natureY;
    private int abilityY;
    private int descriptionLines;

    private Button natureAButton;
    private Button natureBButton;
    private Button abilityAButton;
    private Button abilityBButton;

    public FusionConfirmScreen(OpenFusionScreenPayload data) {
        // La misma pantalla sirve para invertir una fusión: solo cambian el título y el botón de aceptar
        super(Component.translatable(data.reverse() ? "gui.fusionmon.reverse.title" : "gui.fusionmon.confirm.title"));
        this.data = data;
    }

    private FusionPreview preview() {
        return swapped ? data.swappedPreview() : data.preview();
    }

    @Override
    protected FusionPartView head() {
        return swapped ? data.partB() : data.partA();
    }

    @Override
    protected FusionPartView fusion() {
        return preview().view();
    }

    @Override
    protected FusionPartView body() {
        return swapped ? data.partA() : data.partB();
    }

    @Override
    protected boolean splitting() {
        return false;
    }

    @Override
    protected int bottomHeight(int spaceWithoutBottom) {
        // La descripción de la habilidad, solo si los visores siguen siendo cómodos; si no, queda en el tooltip
        int withDescription = BOTTOM_FIXED + DESCRIPTION_LINES * FusionScreenLayout.LINE;
        descriptionLines = spaceWithoutBottom - withDescription >= FusionScreenLayout.COMFORT_BOX ? DESCRIPTION_LINES : 0;
        return BOTTOM_FIXED + descriptionLines * FusionScreenLayout.LINE;
    }

    @Override
    protected void initBottom(int y) {
        int centerX = width / 2;
        int swapY = y + GAP;
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

        Component accept = Component.translatable(data.reverse() ? "gui.fusionmon.reverse.accept" : "gui.fusionmon.confirm.accept");
        addRenderableWidget(Button.builder(accept, button -> {
                    // Lo que se ve ahora (con el intercambio elegido) es lo que se anima
                    FusionAnimationScreen animation = new FusionAnimationScreen(head(), body(), fusion());
                    answer(() -> ClientPlayNetworking.send(
                            new FusionChoicePayload(true, swapped, natureFromB, abilityFromB)));
                    // answer ya ha cerrado esta pantalla (sin contar como cancelar): ahora la animación
                    minecraft.setScreen(animation);
                })
                .bounds(leftX, acceptY, OPTION_WIDTH, FusionScreenLayout.BUTTON)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.fusionmon.confirm.cancel"), button -> answer(this::sendCancel))
                .bounds(rightX, acceptY, OPTION_WIDTH, FusionScreenLayout.BUTTON)
                .build());

        refreshOptionLabels();
    }

    @Override
    protected void renderBottom(GuiGraphics graphics, int y) {
        int centerX = width / 2;
        FusionScreenLayout.renderStats(graphics, font, preview().baseStats(), centerX, statsY, width);

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

    @Override
    protected void sendCancel() {
        if (ClientPlayNetworking.canSend(FusionChoicePayload.TYPE)) {
            ClientPlayNetworking.send(FusionChoicePayload.cancel());
        }
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
