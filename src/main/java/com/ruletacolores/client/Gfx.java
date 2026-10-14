package com.ruletacolores.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ruletacolores.RuletaMod;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** Utilidades de dibujo 2D propias (quads, gradientes, lineas y la malla de la ruleta). */
final class Gfx {
    /** El radio visible de la ruleta es 0.9617 de la mitad de la textura. */
    static final float TEX_RATIO = 0.9617f;

    static final Identifier[] WHEEL = new Identifier[8];
    static final Identifier POINTER = tex("gui/pointer.png");
    static final Identifier ICON = tex("gui/icon.png");
    static final Identifier GLOW = tex("gui/glow.png");
    static final Identifier RING = tex("gui/ring.png");
    static final Identifier SPARK = tex("gui/spark.png");
    static final Identifier HEART = tex("gui/heart.png");
    static final Identifier FLAKE = tex("gui/flake.png");
    static final Identifier LEAF = tex("gui/leaf.png");
    static final Identifier GLOBO = tex("gui/globo.png");

    static final int DESIGNS = 6;
    static final Identifier[][] DWHEEL = new Identifier[DESIGNS][8];
    static final Identifier[] DPOINTER = new Identifier[DESIGNS];
    static final Identifier WHITE = tex("gui/white.png");
    static final Identifier ORB = tex("gui/orb.png");
    static final String[] DESIGN_NAMES = {"Original", "Minimal Plano", "Minimal Anillo", "Neon", "Casino", "Pixel"};

    static {
        for (int i = 0; i < 8; i++) {
            WHEEL[i] = tex("gui/wheel_" + i + ".png");
        }
        for (int d = 0; d < DESIGNS; d++) {
            for (int c = 0; c < 8; c++) {
                DWHEEL[d][c] = d == 0 ? WHEEL[c] : tex("gui/d" + d + "_" + c + ".png");
            }
            DPOINTER[d] = d == 0 ? POINTER : tex("gui/pointer_" + d + ".png");
        }
    }

    private Gfx() {}

    static Identifier tex(String path) {
        return Identifier.of(RuletaMod.MOD_ID, "textures/" + path);
    }

    static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    static void end() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    static void additive(boolean on) {
        if (on) {
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SrcFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DstFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
    }

    // ------------------------------------------------------------------ quads

    static void quad(Matrix4f m, Identifier tex, float x0, float y0, float x1, float y1,
                     float r, float g, float b, float a) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(m, x0, y0, 0f).texture(0f, 0f).color(r, g, b, a);
        bb.vertex(m, x0, y1, 0f).texture(0f, 1f).color(r, g, b, a);
        bb.vertex(m, x1, y1, 0f).texture(1f, 1f).color(r, g, b, a);
        bb.vertex(m, x1, y0, 0f).texture(1f, 0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static void cv(BufferBuilder bb, Matrix4f m, float x, float y, int argb) {
        bb.vertex(m, x, y, 0f).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    /** Rectangulo con un color por esquina: arriba-izq, arriba-der, abajo-der, abajo-izq. */
    static void quadColors(Matrix4f m, float x0, float y0, float x1, float y1, int c00, int c10, int c11, int c01) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        cv(bb, m, x0, y0, c00);
        cv(bb, m, x0, y1, c01);
        cv(bb, m, x1, y1, c11);
        cv(bb, m, x1, y0, c10);
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    static void fill(Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        quadColors(m, x0, y0, x1, y1, argb, argb, argb, argb);
    }

    static void gradV(Matrix4f m, float x0, float y0, float x1, float y1, int top, int bottom) {
        quadColors(m, x0, y0, x1, y1, top, top, bottom, bottom);
    }

    static void gradH(Matrix4f m, float x0, float y0, float x1, float y1, int left, int right) {
        quadColors(m, x0, y0, x1, y1, left, right, right, left);
    }

    static void line(Matrix4f m, float x0, float y0, float x1, float y1, float w, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * w * 0.5f;
        float ny = dx / len * w * 0.5f;
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        cv(bb, m, x0 - nx, y0 - ny, argb);
        cv(bb, m, x0 + nx, y0 + ny, argb);
        cv(bb, m, x1 + nx, y1 + ny, argb);
        cv(bb, m, x1 - nx, y1 - ny, argb);
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    /** Triangulo con color distinto en el vertice central y en los dos extremos (rayos suaves). */
    static void triGrad(Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, int cCenter, int cEdge) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        cv(bb, m, x0, y0, cCenter);
        cv(bb, m, x1, y1, cEdge);
        cv(bb, m, x2, y2, cEdge);
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    /** Bordes de colores en la pantalla (vineta). */
    static void vignette(Matrix4f m, float w, float h, int rgb, float alpha, float thick) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        int c = (a << 24) | (rgb & 0xFFFFFF);
        int z = rgb & 0xFFFFFF;
        gradV(m, 0, 0, w, thick, c, z);
        gradV(m, 0, h - thick, w, h, z, c);
        gradH(m, 0, 0, thick, h, c, z);
        gradH(m, w - thick, 0, w, h, z, c);
    }

    // ------------------------------------------------------------------ ruleta

    /**
     * Dibuja un gajo de la ruleta como una malla polar (para poder deformarla).
     * R = mitad de la textura. amp/phase/shear = distorsion de entrada y salida.
     */
    static void sector(Matrix4f m, Identifier tex, int k, float R, float alpha, float amp, float phase, float shear) {
        sector(m, tex, k, R, alpha, amp, phase, shear, 1f, 1f);
    }

    /** rFrac = cuanto del radio se ve (0-1); sweep = cuanto del angulo del gajo se ve (0-1). */
    static void sector(Matrix4f m, Identifier tex, int k, float R, float alpha, float amp, float phase, float shear,
                       float rFrac, float sweep) {
        if (rFrac <= 0.002f || sweep <= 0.002f || alpha <= 0.002f) return;
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);
        final boolean distorted = amp != 0f || shear != 0f;
        final int rings = distorted ? 6 : 1;
        final int arcs = distorted ? 5 : 4;
        for (int i = 0; i < rings; i++) {
            float r0 = R * rFrac * i / rings;
            float r1 = R * rFrac * (i + 1) / rings;
            for (int j = 0; j < arcs; j++) {
                double a0 = Math.toRadians(k * 45.0 + j * 45.0 * sweep / arcs);
                double a1 = Math.toRadians(k * 45.0 + (j + 1) * 45.0 * sweep / arcs);
                float x00 = (float) (r0 * Math.sin(a0)), y00 = (float) (-r0 * Math.cos(a0));
                float x10 = (float) (r1 * Math.sin(a0)), y10 = (float) (-r1 * Math.cos(a0));
                float x11 = (float) (r1 * Math.sin(a1)), y11 = (float) (-r1 * Math.cos(a1));
                float x01 = (float) (r0 * Math.sin(a1)), y01 = (float) (-r0 * Math.cos(a1));
                vtx(bb, m, x00, y00, R, alpha, amp, phase, shear);
                vtx(bb, m, x10, y10, R, alpha, amp, phase, shear);
                vtx(bb, m, x11, y11, R, alpha, amp, phase, shear);
                vtx(bb, m, x00, y00, R, alpha, amp, phase, shear);
                vtx(bb, m, x11, y11, R, alpha, amp, phase, shear);
                vtx(bb, m, x01, y01, R, alpha, amp, phase, shear);
            }
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static void vtx(BufferBuilder bb, Matrix4f m, float x, float y, float R, float alpha,
                            float amp, float phase, float shear) {
        float u = 0.5f + x / (2f * R);
        float v = 0.5f + y / (2f * R);
        float px = x;
        if (amp != 0f || shear != 0f) {
            px = x + amp * R * (float) Math.sin(y / R * 2.8f + phase) + shear * y;
        }
        bb.vertex(m, px, y, 0f).texture(u, v).color(1f, 1f, 1f, alpha);
    }

    /**
     * Ruleta completa (8 gajos, cada uno con su color actual). La pila ya debe estar
     * trasladada al centro de la ruleta.
     */
    static void wheel(MatrixStack ms, float R, float angleDeg, int design, int[] prev, int[] shown, float[] fade,
                      float alpha, float amp, float phase, float shear) {
        Identifier[] W = DWHEEL[Math.floorMod(design, DESIGNS)];
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angleDeg));
        Matrix4f m = ms.peek().getPositionMatrix();
        for (int k = 0; k < 8; k++) {
            if (fade[k] >= 0.999f) {
                sector(m, W[shown[k]], k, R, alpha, amp, phase, shear);
            } else {
                sector(m, W[prev[k]], k, R, alpha, amp, phase, shear);
                sector(m, W[shown[k]], k, R, alpha * fade[k], amp, phase, shear);
            }
        }
        ms.pop();
    }

    static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    /**
     * Ruleta "armandose" (animacion de entrada/salida): cada gajo aparece creciendo desde el centro,
     * primero blanco y luego con su color. prog[k] = progreso 0-1 del gajo k. globalWhite = destello blanco total.
     */
    static void wheelAssemble(MatrixStack ms, float R, float angleDeg, int design, float alpha, float[] prog, float globalWhite) {
        Identifier[] W = DWHEEL[Math.floorMod(design, DESIGNS)];
        ms.push();
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angleDeg));
        Matrix4f m = ms.peek().getPositionMatrix();
        for (int k = 0; k < 8; k++) {
            float p = prog[k];
            if (p <= 0f) continue;
            float rf = smooth(p / 0.7f);
            float sw = smooth((p - 0.1f) / 0.7f);
            float white = (1f - smooth((p - 0.3f) / 0.6f)) * Math.min(1f, p * 8f);
            sector(m, W[(k + 2) % 8], k, R, alpha, 0f, 0f, 0f, rf, sw);
            if (white > 0.01f) sector(m, WHITE, k, R, alpha * white, 0f, 0f, 0f, rf, sw);
        }
        if (globalWhite > 0.01f) {
            float h = R * TEX_RATIO / TEX_RATIO;
            quad(m, WHITE, -h, -h, h, h, 1f, 1f, 1f, Math.min(1f, globalWhite) * alpha);
        }
        ms.pop();
    }
}
