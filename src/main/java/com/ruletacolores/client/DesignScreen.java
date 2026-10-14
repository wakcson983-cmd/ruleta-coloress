package com.ruletacolores.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Elegir el diseno de la ruleta, con vista previa animada de cada uno. */
public class DesignScreen extends Screen {
    private static final int[] NAT = new int[8];
    private static final int[] NAT_PREV = new int[8];
    private static final float[] ONE = new float[8];

    static {
        for (int k = 0; k < 8; k++) {
            NAT[k] = NAT_PREV[k] = (k + 2) % 8;
            ONE[k] = 1f;
        }
    }

    private final Screen parent;
    private int cw, ch, gx, gy;
    private float spin = 0f;
    private long last = 0L;

    public DesignScreen(Screen parent) {
        super(Text.literal("Dise\u00f1o"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ch = Math.max(70, Math.min(112, (this.height - 100) / 2));
        cw = Math.min(124, (this.width - 40) / 3 - 8);
        gx = (this.width - (cw * 3 + 16)) / 2;
        gy = 34;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("LISTO"), b -> this.close())
                .dimensions(this.width / 2 - 60, gy + ch * 2 + 14, 120, 20).build());
    }

    private int cardX(int i) {
        return gx + (i % 3) * (cw + 8);
    }

    private int cardY(int i) {
        return gy + (i / 3) * (ch + 6);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        spin += dt * 25f;

        MatrixStack ms = ctx.getMatrices();
        // todo lo "crudo" primero (se dibuja al instante), el texto despues (queda encima)
        Gfx.begin();
        Matrix4f m = ms.peek().getPositionMatrix();
        Gfx.fill(m, 0, 0, this.width, this.height, 0xB0000000);
        for (int d = 0; d < Gfx.DESIGNS; d++) {
            int x = cardX(d), y = cardY(d);
            boolean sel = d == RuletaConfig.design;
            boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + ch;
            int border = sel ? 0xFFFFD84D : (hover ? 0xFFFFFFFF : 0xFF555566);
            Gfx.fill(m, x - 2, y - 2, x + cw + 2, y + ch + 2, border);
            Gfx.fill(m, x, y, x + cw, y + ch, 0xFF14161F);

            float Rv = Math.min(cw * 0.34f, (ch - 34) * 0.40f);
            float R = Rv / Gfx.TEX_RATIO;
            ms.push();
            ms.translate(x + cw / 2f, y + 12f + (ch - 30) / 2f + 6f, 0f);
            Gfx.wheel(ms, R, spin * (hover || sel ? 1f : 0.4f), d, NAT_PREV, NAT, ONE, 1f, 0f, 0f, 0f);
            ms.push();
            ms.translate(0f, -Rv * 0.74f, 0f);
            float ph = Rv * 0.46f, pw = ph * 0.8f;
            Matrix4f pm = ms.peek().getPositionMatrix();
            Gfx.quad(pm, Gfx.DPOINTER[d], -pw / 2f, -ph, pw / 2f, 0f, 1f, 1f, 1f, 1f);
            ms.pop();
            ms.pop();
        }
        Gfx.end();

        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("DISE\u00d1O DE LA RULETA").formatted(Formatting.BOLD),
                this.width / 2, 14, 0xFFFFFFFF);
        for (int d = 0; d < Gfx.DESIGNS; d++) {
            int x = cardX(d), y = cardY(d);
            boolean sel = d == RuletaConfig.design;
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(Gfx.DESIGN_NAMES[d]), x + cw / 2, y + ch - 12,
                    sel ? 0xFFFFD84D : 0xFFFFFFFF);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int d = 0; d < Gfx.DESIGNS; d++) {
                int x = cardX(d), y = cardY(d);
                if (mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + ch) {
                    RuletaConfig.design = d;
                    RuletaConfig.save();
                    Sfx.play("select", 1.0f + d * 0.1f, 0.7f);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
