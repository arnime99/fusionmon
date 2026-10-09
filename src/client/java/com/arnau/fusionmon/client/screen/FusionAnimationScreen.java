package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.network.FusionPartView;
import com.cobblemon.mod.common.CobblemonSounds;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Animación al fusionar (o invertir), unos 3 segundos; clic o tecla la saltan, y al final otro clic la cierra:
 *  1. Acercamiento (0 – APPROACH): cabeza y cuerpo entran desde los lados girando cada vez más rápido, con un brillo
 *     detrás que crece.
 *  2. Fusión (APPROACH – REVEAL): chocan en el centro; destello blanco que llena la pantalla y chispas.
 *  3. Revelación (REVEAL – DONE): el destello se apaga y aparece la fusión, creciendo con un pequeño rebote, girando
 *     despacio y con destellos alrededor; debajo, quién se ha fusionado en qué.
 * Es solo del cliente: la fusión ya la ha hecho el servidor al aceptar. Los modelos son los de la pantalla de confirmar
 * (FusionPartView), así que la fusión sale igual que en la vista previa.
 */
public class FusionAnimationScreen extends Screen {

    // Tiempos, en milisegundos desde que se abre (un poco pausado: el usuario la quiso algo más lenta)
    private static final long APPROACH = 1300;
    private static final long FLASH_PEAK = 1500;
    private static final long REVEAL = 1850;
    private static final long FLASH_END = 2400;
    private static final long GROWN = 2500;
    private static final long TEXT_IN = 500;
    private static final long DONE = 3800;

    /** Lo que se pinta por encima de los modelos (destello, chispas, texto): delante de cualquier modelo. */
    private static final float OVERLAY_Z = 1000;
    private static final int BACKGROUND = 0xF0101018;
    private static final int SPARK_COUNT = 48;
    private static final long SPARK_LIFE = 1700;
    private static final int TWINKLES = 10;
    private static final long TWINKLE_PERIOD = 1100;
    private static final int[] SPARK_COLORS = {0xFFFFFF, 0xFFF4A0, 0xA0E8FF, 0xFFD0F0};

    private final FusionPartView head;
    private final FusionPartView body;
    private final FusionPartView fusion;
    private final ModelViewport headView = new ModelViewport();
    private final ModelViewport bodyView = new ModelViewport();
    private final ModelViewport fusionView = new ModelViewport();
    private final FloatingState headState = new FloatingState();
    private final FloatingState bodyState = new FloatingState();
    private final FloatingState fusionState = new FloatingState();
    private final List<Spark> sparks = new ArrayList<>();

    /** Cuándo empezó (al pintarse la primera vez); -1 hasta entonces. */
    private long start = -1;
    private boolean revealSoundPlayed;

    /**
     * Una chispa del choque: sale del centro en una dirección y se apaga.
     *
     * @param speed cuánto se aleja respecto a las demás (unas llegan más lejos que otras)
     */
    private record Spark(float angle, float speed, float size, int color) {
    }

    public FusionAnimationScreen(FusionPartView head, FusionPartView body, FusionPartView fusion) {
        super(fusion.name());
        this.head = head;
        this.body = body;
        this.fusion = fusion;
        Random random = new Random();
        for (int i = 0; i < SPARK_COUNT; i++) {
            sparks.add(new Spark(random.nextFloat() * Mth.TWO_PI, 0.6F + random.nextFloat() * 1.4F,
                    1.5F + random.nextFloat() * 2.5F, SPARK_COLORS[random.nextInt(SPARK_COLORS.length)]));
        }
    }

    @Override
    protected void init() {
        if (start < 0) {
            start = System.currentTimeMillis();
            play(CobblemonSounds.FOSSIL_MACHINE_ASSEMBLE);
        }
    }

    private long elapsed() {
        return System.currentTimeMillis() - start;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long t = elapsed();
        if (t >= REVEAL && !revealSoundPlayed) {
            revealSoundPlayed = true;
            play(CobblemonSounds.EVOLUTION_UI);
        }

        graphics.fill(0, 0, width, height, BACKGROUND);
        float centerX = width / 2F;
        float centerY = height * 0.45F;
        // Lo que mide la fusión ya revelada
        float big = Math.min(width, height) * 0.5F;

        if (t < REVEAL) {
            // 1. Se acercan acelerando (y girando cada vez más rápido) hasta juntarse en el centro
            float approach = Mth.clamp(t / (float) APPROACH, 0F, 1F);
            float eased = approach * approach;
            float offset = width * 0.3F * (1F - eased);
            float size = big * (0.8F - 0.5F * eased);
            float spin = 900F * eased;
            glow(graphics, centerX - offset, centerY, size * (0.35F + 0.4F * eased), eased);
            glow(graphics, centerX + offset, centerY, size * (0.35F + 0.4F * eased), eased);
            headView.renderAt(graphics, head.model(), headState, centerX - offset, centerY, size, 30F + spin,
                    partialTick);
            bodyView.renderAt(graphics, body.model(), bodyState, centerX + offset, centerY, size, 30F - spin,
                    partialTick);
        } else {
            // 3. La fusión crece con un pequeño rebote y gira despacio
            float grow = Mth.clamp((t - REVEAL) / (float) (GROWN - REVEAL), 0F, 1F);
            float size = big * (0.4F + 0.6F * easeOutBack(grow));
            glow(graphics, centerX, centerY, size * 0.6F, 0.5F);
            fusionView.renderAt(graphics, fusion.model(), fusionState, centerX, centerY, size,
                    30F + (t - REVEAL) * 0.03F, partialTick);
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, OVERLAY_Z);
        renderSparks(graphics, t, centerX, centerY, big);
        if (t >= REVEAL) {
            renderTwinkles(graphics, t, centerX, centerY, big);
        }
        renderFlash(graphics, t);
        renderText(graphics, t, centerY + big / 2F + 12);
        pose.popPose();
    }

    /** Brillo detrás de un modelo: cuadrados blancos translúcidos, uno dentro de otro (más fuerte en el centro). */
    private static void glow(GuiGraphics graphics, float x, float y, float radius, float strength) {
        int alpha = Math.round(28 * strength);
        if (alpha <= 0) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            float r = radius * (1F - i * 0.22F);
            graphics.fill(Math.round(x - r), Math.round(y - r), Math.round(x + r), Math.round(y + r),
                    alpha << 24 | 0xFFFFFF);
        }
    }

    /** 2. Destello: sube deprisa al chocar, tapa el cambio de modelos y se apaga ya con la fusión delante. */
    private void renderFlash(GuiGraphics graphics, long t) {
        float strength;
        if (t < APPROACH || t >= FLASH_END) {
            return;
        } else if (t < FLASH_PEAK) {
            strength = (t - APPROACH) / (float) (FLASH_PEAK - APPROACH);
        } else if (t < REVEAL) {
            strength = 1F;
        } else {
            strength = 1F - (t - REVEAL) / (float) (FLASH_END - REVEAL);
        }
        graphics.fill(0, 0, width, height, Math.round(255 * strength) << 24 | 0xFFFFFF);
    }

    /** Chispas del choque: salen del centro, frenando, y se apagan. */
    private void renderSparks(GuiGraphics graphics, long t, float centerX, float centerY, float big) {
        long age = t - APPROACH;
        if (age < 0 || age >= SPARK_LIFE) {
            return;
        }
        float life = age / (float) SPARK_LIFE;
        // Frenan: recorren deprisa al principio y casi nada al final
        float distance = big * (1F - (1F - life) * (1F - life));
        int alpha = Math.max(5, Math.round(255 * (1F - life)));
        for (Spark spark : sparks) {
            float x = centerX + Mth.cos(spark.angle) * distance * spark.speed;
            float y = centerY + Mth.sin(spark.angle) * distance * spark.speed;
            square(graphics, x, y, spark.size, alpha << 24 | spark.color);
        }
    }

    /** Destellos alrededor de la fusión: crucecitas que aparecen y se apagan, cada una a su ritmo. */
    private static void renderTwinkles(GuiGraphics graphics, long t, float centerX, float centerY, float big) {
        for (int i = 0; i < TWINKLES; i++) {
            float phase = ((t + i * 371L) % TWINKLE_PERIOD) / (float) TWINKLE_PERIOD;
            // Cada vuelta, en otro sitio alrededor del modelo (pseudoazar fijo por destello y vuelta)
            long round = (t + i * 371L) / TWINKLE_PERIOD;
            float angle = (i * 2.399F + round * 1.7F) % Mth.TWO_PI;
            float radius = big * (0.35F + 0.15F * ((i * 7 + round * 3) % 5) / 4F);
            float x = centerX + Mth.cos(angle) * radius;
            float y = centerY + Mth.sin(angle) * radius;
            float shine = Mth.sin(phase * Mth.PI);
            int alpha = Math.max(5, Math.round(220 * shine));
            int color = alpha << 24 | SPARK_COLORS[i % SPARK_COLORS.length];
            float arm = 1F + 3F * shine;
            graphics.fill(Math.round(x - arm), Math.round(y - 0.5F), Math.round(x + arm), Math.round(y + 0.5F), color);
            graphics.fill(Math.round(x - 0.5F), Math.round(y - arm), Math.round(x + 0.5F), Math.round(y + arm), color);
        }
    }

    /** Quién se ha fusionado en qué, apareciendo poco a poco; al final, cómo seguir. */
    private void renderText(GuiGraphics graphics, long t, float y) {
        long shown = t - GROWN;
        if (shown < 0) {
            return;
        }
        int alpha = Math.max(5, Math.round(255 * Mth.clamp(shown / (float) TEXT_IN, 0F, 1F)));
        Component message = Component.translatable("gui.fusionmon.animation.fused", head.name(), body.name(),
                fusion.name());
        graphics.drawCenteredString(font, message, width / 2, Math.round(y), alpha << 24 | FusionScreenLayout.WHITE);
        if (t >= DONE) {
            // Parpadea despacio
            float blink = 0.6F + 0.4F * Mth.sin((t - DONE) / 300F);
            graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.animation.continue"), width / 2,
                    Math.round(y) + 2 * FusionScreenLayout.LINE,
                    Math.max(5, Math.round(255 * blink)) << 24 | FusionScreenLayout.GRAY);
        }
    }

    private static void square(GuiGraphics graphics, float x, float y, float size, int color) {
        float half = size / 2F;
        graphics.fill(Math.round(x - half), Math.round(y - half), Math.round(x + half), Math.round(y + half), color);
    }

    /** Crece pasándose un poco y vuelve (0 → 1). */
    private static float easeOutBack(float p) {
        float c1 = 1.70158F;
        float c3 = c1 + 1F;
        float q = p - 1F;
        return 1F + c3 * q * q * q + c1 * q * q;
    }

    private void play(SoundEvent sound) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1F));
        }
    }

    // ---- Saltar y cerrar ----

    /** Antes de acabar, un clic o una tecla la llevan al final; después, la cierran. */
    private boolean skipOrClose() {
        if (elapsed() < DONE) {
            start = System.currentTimeMillis() - DONE;
        } else {
            onClose();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return skipOrClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return skipOrClose();
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones de los modelos siguen
        return false;
    }
}
