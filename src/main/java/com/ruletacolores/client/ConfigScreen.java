package com.ruletacolores.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Configuracion de la ruleta en 2 paginas: cada boton cambia una opcion al hacer click. */
public class ConfigScreen extends Screen {
    private static int page = 0;

    private final Screen parent;
    private final List<ButtonWidget> buttons = new ArrayList<>();
    private final List<Supplier<Text>> labels = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Text.literal("Configuracion"));
        this.parent = parent;
    }

    private static String yn(boolean b) {
        return b ? "SI" : "NO";
    }

    private void option(int row, Supplier<Text> label, Runnable change) {
        int w = 250;
        int x = this.width / 2 - w / 2;
        int y = 38 + row * 22;
        ButtonWidget b = ButtonWidget.builder(label.get(), btn -> {
            change.run();
            RuletaConfig.save();
            refresh();
        }).dimensions(x, y, w, 20).build();
        buttons.add(b);
        labels.add(label);
        this.addDrawableChild(b);
    }

    private void refresh() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setMessage(labels.get(i).get());
        }
    }

    @Override
    protected void init() {
        buttons.clear();
        labels.clear();
        int r = 0;
        if (page == 0) {
            option(r++, () -> Text.literal("Efectos de pantalla: " + yn(RuletaConfig.effects)), () -> RuletaConfig.effects = !RuletaConfig.effects);
            option(r++, () -> Text.literal("Intensidad de efectos: " + RuletaConfig.fxIntensity + "%"),
                    () -> RuletaConfig.fxIntensity = RuletaConfig.nextInt(RuletaConfig.INTENSITY, RuletaConfig.fxIntensity));
            option(r++, () -> Text.literal("Motion blur al girar: " + yn(RuletaConfig.blur)), () -> RuletaConfig.blur = !RuletaConfig.blur);
            option(r++, () -> Text.literal("Destello al caer: " + yn(RuletaConfig.flash)), () -> RuletaConfig.flash = !RuletaConfig.flash);
            option(r++, () -> Text.literal("Sentido de giro: " + (RuletaConfig.counterClockwise ? "ANTIHORARIO" : "HORARIO")),
                    () -> RuletaConfig.counterClockwise = !RuletaConfig.counterClockwise);
            option(r++, () -> Text.literal("Mensaje en el chat: " + yn(RuletaConfig.chat)), () -> RuletaConfig.chat = !RuletaConfig.chat);
            option(r++, () -> Text.literal("Notificacion: " + yn(RuletaConfig.notification)), () -> RuletaConfig.notification = !RuletaConfig.notification);
            option(r++, () -> Text.literal("Volumen de sonidos: " + RuletaConfig.volume + "%"),
                    () -> RuletaConfig.volume = RuletaConfig.nextInt(RuletaConfig.VOLUMES, RuletaConfig.volume));
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Dise\u00f1o de la ruleta: " + Gfx.DESIGN_NAMES[RuletaConfig.design] + "  >"),
                    b -> this.client.setScreen(new DesignScreen(this))).dimensions(this.width / 2 - 125, 38, 250, 20).build());
            r++;
            option(r++, () -> Text.literal("Tama\u00f1o de la ruleta: " + RuletaConfig.wheelSize + "%"),
                    () -> RuletaConfig.wheelSize = RuletaConfig.nextInt(RuletaConfig.SIZES, RuletaConfig.wheelSize));
            option(r++, () -> Text.literal("Posicion de la ruleta: " + RuletaConfig.POS_NAMES[RuletaConfig.wheelPos]),
                    () -> RuletaConfig.wheelPos = (RuletaConfig.wheelPos + 1) % 5);
            option(r++, () -> Text.literal("Posicion de la flecha: " + RuletaConfig.SIDE_NAMES[RuletaConfig.pointerSide]),
                    () -> RuletaConfig.pointerSide = (RuletaConfig.pointerSide + 1) % 4);
            option(r++, () -> Text.literal("Esquina de la notificacion: " + RuletaConfig.CORNER_NAMES[RuletaConfig.noteCorner]),
                    () -> RuletaConfig.noteCorner = (RuletaConfig.noteCorner + 1) % 4);
            option(r++, () -> Text.literal("Tama\u00f1o de la notificacion: " + RuletaConfig.noteScale + "%"),
                    () -> RuletaConfig.noteScale = RuletaConfig.nextInt(RuletaConfig.NOTE_SCALES, RuletaConfig.noteScale));
            option(r++, () -> Text.literal("Duracion de la notificacion: " + RuletaConfig.noteSeconds + " s"),
                    () -> RuletaConfig.noteSeconds = RuletaConfig.nextInt(RuletaConfig.NOTE_SECONDS, RuletaConfig.noteSeconds));
            option(r++, () -> Text.literal("Modo ligero (menos lag): " + yn(RuletaConfig.lite)), () -> RuletaConfig.lite = !RuletaConfig.lite);
        }

        int by = 38 + 8 * 22 + 6;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(page == 0 ? "Pagina 1/2  >" : "<  Pagina 2/2"), b -> {
            page = 1 - page;
            this.clearAndInit();
        }).dimensions(this.width / 2 - 125, by, 122, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("LISTO"), b -> this.close())
                .dimensions(this.width / 2 + 3, by, 122, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("CONFIGURACION - RULETA").formatted(Formatting.BOLD),
                this.width / 2, 18, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
