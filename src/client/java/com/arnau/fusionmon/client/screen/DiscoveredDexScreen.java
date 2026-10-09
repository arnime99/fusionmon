package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionCalculator;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FusionDex: las fusiones que ha descubierto el jugador (FusionDiscovery), de la más reciente a la más antigua. A la
 * izquierda la lista con un buscador; a la derecha la elegida en 3D (se gira y se acerca como en la pantalla de
 * fusionar), con su nombre, tipos, de qué cabeza y cuerpo sale y sus stats base.
 *
 * La lista la manda el servidor al abrirla (Shift + clic con el cristal, o /fusiondex); lo demás se calcula aquí, igual
 * que el visor de desarrollo (FusionDexScreen): la fusión se pinta con los aspects de fusión y pasa por el mismo graft
 * que en el mundo.
 */
public class DiscoveredDexScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final int TOP = 22;
    private static final int ROW = 12;
    private static final int SEARCH_HEIGHT = 16;
    /** Debajo del visor: nombre y tipos, cabeza + cuerpo y dos filas de stats. */
    private static final int TEXT_HEIGHT = 2 * FusionScreenLayout.LINE + 4 + FusionScreenLayout.STATS_HEIGHT;

    private static final Stat[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE, Stats.SPEED};

    /**
     * Una fusión descubierta.
     *
     * @param search su nombre y los de sus partes, sin tildes y en minúsculas (para el buscador)
     */
    private record Entry(FormData head, FormData body, String name, RenderablePokemon model, String search) {
    }

    private final List<Entry> all = new ArrayList<>();
    private List<Entry> shown = List.of();
    private Entry selected;
    private int scroll;

    private EditBox searchBox;
    private final ModelViewport viewport = new ModelViewport();
    private final FloatingState state = new FloatingState();
    private boolean draggingModel;

    // Dónde está cada cosa (se calcula en init)
    private int listX;
    private int listWidth;
    private int listTop;
    private int listRows;
    private int boxX;
    private int boxY;
    private int box;
    private int detailCenter;
    private int detailWidth;

    public DiscoveredDexScreen(List<String> discovered) {
        super(Component.translatable("gui.fusionmon.fusiondex.title"));
        for (String key : discovered) {
            Entry entry = entry(key);
            // Una especie que ya no existe (un mod que se ha quitado) no se puede enseñar
            if (entry != null) {
                all.add(entry);
            }
        }
        shown = all;
        selected = all.isEmpty() ? null : all.get(0);
    }

    @Override
    protected void init() {
        listX = MARGIN;
        listWidth = Mth.clamp(width / 3, 110, 180);
        searchBox = addRenderableWidget(new EditBox(font, listX, TOP, listWidth, SEARCH_HEIGHT,
                Component.translatable("gui.fusionmon.fusiondex.search")));
        searchBox.setHint(Component.translatable("gui.fusionmon.fusiondex.search"));
        searchBox.setResponder(this::filter);
        listTop = TOP + SEARCH_HEIGHT + 4;
        listRows = Math.max(1, (height - MARGIN - listTop) / ROW);

        // El visor, cuadrado, en lo que queda a la derecha, con sitio debajo para los textos
        int rightX = listX + listWidth + 2 * GAP;
        detailWidth = width - MARGIN - rightX;
        detailCenter = rightX + detailWidth / 2;
        box = Math.max(40, Math.min(detailWidth, height - TOP - MARGIN - TEXT_HEIGHT - 4));
        boxX = detailCenter - box / 2;
        boxY = TOP;
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, MARGIN, 8, FusionScreenLayout.YELLOW);
        Component count = Component.translatable("gui.fusionmon.fusiondex.count", all.size());
        graphics.drawString(font, count, width - MARGIN - font.width(count), 8, FusionScreenLayout.GRAY);

        if (all.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.fusiondex.empty"), width / 2,
                    height / 2, FusionScreenLayout.GRAY);
            return;
        }
        renderList(graphics, mouseX, mouseY);
        if (selected != null) {
            renderDetail(graphics, partialTick);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(listX - 1, listTop - 1, listX + listWidth + 1, listTop + listRows * ROW + 1, 0xFF555555);
        graphics.fill(listX, listTop, listX + listWidth, listTop + listRows * ROW, 0xFF1E1E1E);
        for (int row = 0; row < listRows && scroll + row < shown.size(); row++) {
            Entry entry = shown.get(scroll + row);
            int y = listTop + row * ROW;
            boolean hovered = mouseX >= listX && mouseX < listX + listWidth && mouseY >= y && mouseY < y + ROW;
            if (entry == selected) {
                graphics.fill(listX, y, listX + listWidth, y + ROW, 0xFF3A3A60);
            } else if (hovered) {
                graphics.fill(listX, y, listX + listWidth, y + ROW, 0xFF2A2A2A);
            }
            Component name = FusionScreenLayout.fit(font, Component.literal(entry.name), listWidth - 6);
            graphics.drawString(font, name, listX + 3, y + 2,
                    entry == selected ? FusionScreenLayout.YELLOW : FusionScreenLayout.WHITE);
        }
        // Barra de desplazamiento, si no caben todas
        if (shown.size() > listRows) {
            int trackHeight = listRows * ROW;
            int thumb = Math.max(8, trackHeight * listRows / shown.size());
            int thumbY = listTop + (trackHeight - thumb) * scroll / Math.max(1, shown.size() - listRows);
            graphics.fill(listX + listWidth - 2, thumbY, listX + listWidth, thumbY + thumb, 0xFF888888);
        }
    }

    private void renderDetail(GuiGraphics graphics, float partialTick) {
        viewport.render(graphics, selected.model, state, "", boxX, boxY, box, partialTick, null, null);

        int y = boxY + box + 4;
        Component name = Component.literal(selected.name).withColor(FusionScreenLayout.YELLOW)
                .append(Component.literal("  ·  ").withColor(FusionScreenLayout.GRAY))
                .append(types(FusionCalculator.types(selected.head, selected.body)).copy()
                        .withColor(FusionScreenLayout.WHITE));
        graphics.drawCenteredString(font, FusionScreenLayout.fit(font, name, detailWidth), detailCenter, y,
                FusionScreenLayout.WHITE);
        y += FusionScreenLayout.LINE;
        Component parts = Component.translatable("gui.fusionmon.fusiondex.parts",
                selected.head.getSpecies().getTranslatedName(), selected.body.getSpecies().getTranslatedName());
        graphics.drawCenteredString(font, parts, detailCenter, y, FusionScreenLayout.GRAY);
        y += FusionScreenLayout.LINE + 4;
        List<Integer> stats = new ArrayList<>();
        for (Stat stat : STATS) {
            stats.add(FusionCalculator.baseStat(selected.head, selected.body, stat));
        }
        // renderStats reparte el ancho que le digan: el de la parte derecha
        FusionScreenLayout.renderStats(graphics, font, stats, detailCenter, y,
                detailWidth + 2 * FusionScreenLayout.MARGIN);
    }

    // ---- Datos ----

    /** Una fusión a partir de su clave ("cobblemon:charizard/normal>cobblemon:pikachu/normal"); null si no existe. */
    private static Entry entry(String key) {
        String[] parts = key.split(">");
        if (parts.length != 2) {
            return null;
        }
        FormData head = form(parts[0]);
        FormData body = form(parts[1]);
        if (head == null || body == null) {
            return null;
        }
        Set<String> aspects = partAspects(head);
        aspects.addAll(FusionAspects.fusionAspects(body.getSpecies(), partAspects(body)));
        RenderablePokemon model = new RenderablePokemon(head.getSpecies(), aspects, ItemStack.EMPTY);
        String headName = head.getSpecies().getTranslatedName().getString();
        String bodyName = body.getSpecies().getTranslatedName().getString();
        String name = FusionCalculator.name(headName, bodyName);
        return new Entry(head, body, name, model, normalize(name + " " + headName + " " + bodyName));
    }

    /** "cobblemon:raichu/alola" → la forma de Alola de Raichu (la normal si ya no la tiene). */
    private static FormData form(String part) {
        int slash = part.lastIndexOf('/');
        ResourceLocation id = ResourceLocation.tryParse(slash < 0 ? part : part.substring(0, slash));
        Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        if (species == null) {
            return null;
        }
        String formName = slash < 0 ? "" : part.substring(slash + 1);
        for (FormData form : species.getForms()) {
            if (form.getName().equalsIgnoreCase(formName)) {
                return form;
            }
        }
        return species.getStandardForm();
    }

    /** Los aspects de una parte: los de su forma (regional...) y su sexo más habitual (para el modelo de macho/hembra). */
    private static Set<String> partAspects(FormData form) {
        Set<String> aspects = new HashSet<>(form.getAspects());
        Set<Gender> genders = form.getSpecies().getPossibleGenders();
        if (genders.contains(Gender.MALE)) {
            aspects.add("male");
        } else if (genders.contains(Gender.FEMALE)) {
            aspects.add("female");
        }
        return aspects;
    }

    private void filter(String text) {
        String wanted = normalize(text);
        shown = wanted.isEmpty() ? all : all.stream().filter(entry -> entry.search.contains(wanted)).toList();
        scroll = 0;
        if (!shown.isEmpty() && !shown.contains(selected)) {
            selected = shown.get(0);
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static Component types(List<ElementalType> types) {
        List<Component> names = new ArrayList<>();
        for (ElementalType type : types) {
            names.add(type.getDisplayName());
        }
        return FusionScreenLayout.joinTypes(names);
    }

    private void select(int index) {
        if (shown.isEmpty()) {
            return;
        }
        index = Mth.clamp(index, 0, shown.size() - 1);
        selected = shown.get(index);
        // Que se vea en la lista
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + listRows) {
            scroll = index - listRows + 1;
        }
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - listRows));
    }

    // ---- Ratón y teclado ----

    private boolean inList(double x, double y) {
        return x >= listX && x < listX + listWidth && y >= listTop && y < listTop + listRows * ROW;
    }

    private boolean inBox(double x, double y) {
        return x >= boxX && x < boxX + box && y >= boxY && y < boxY + box;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (inList(mouseX, mouseY)) {
            int index = scroll + (int) ((mouseY - listTop) / ROW);
            if (index < shown.size()) {
                selected = shown.get(index);
            }
            return true;
        }
        if (inBox(mouseX, mouseY)) {
            viewport.press(button);
            draggingModel = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        viewport.release();
        draggingModel = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingModel && viewport.drag(dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inList(mouseX, mouseY)) {
            scroll -= (int) Math.signum(scrollY) * 3;
            clampScroll();
            return true;
        }
        if (inBox(mouseX, mouseY)) {
            viewport.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Flechas: la anterior o la siguiente de la lista (también mientras se escribe en el buscador)
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            int index = shown.indexOf(selected);
            select(index + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones de los modelos siguen
        return false;
    }
}
