package com.ruletacolores.client;

import com.ruletacolores.RuletaColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Estado y dibujo de la ruleta en pantalla (la ven todos los jugadores) + notificacion. */
final class RuletaHud {
    static final RuletaHud INSTANCE = new RuletaHud();

    // Linea de tiempo sincronizada con ruleta.ogg (15.2 s). Tiempos en segundos desde que empieza el audio.
    private static final double ENTER = 1.0;        // la ruleta se arma (como los cuadros de la carpeta "verde")
    private static final double SPIN_END = 10.7;    // golpe fuerte del audio: cae el color
    private static final double RECOLOR_START = 11.45;
    private static final double RECOLOR_STEP = 0.15;
    private static final double EXIT_START = 14.1;
    private static final double END = 15.2;
    private static final double SPIN = SPIN_END - ENTER;
    private static final double EXIT = END - EXIT_START;

    private static final int NOTE_MAX_LINES = 3;

    private record Req(int color, String title, String text, boolean effects) {}

    private enum Phase { IDLE, ENTER, SPIN, LANDED, EXIT }

    private final Random rnd = new Random();
    private final ArrayDeque<Req> queue = new ArrayDeque<>();
    private final ColorFx fx = new ColorFx();

    private Phase phase = Phase.IDLE;
    private double t;
    private Req cur;
    private boolean fxOn;
    private float pAng = 270f;
    private float theta, drawAng, lastDraw, vel, spinFrom, spinTotal, kick, flash;
    private int dirSign = 1;
    private int design;
    private double lastT;
    private int flashRgb;
    private int winner, target, lastSec;
    private long lastTickMs;
    private long lastNano;
    private float gcx, gcy, gR, gsw, gsh;
    private float clock, frameDt;

    private final int[] prev = new int[8];
    private final int[] shown = new int[8];
    private final float[] fade = new float[8];
    private final boolean[] recolored = new boolean[8];

    // notificacion
    private boolean nActive, nHidden, nLong;
    private String nTitle = "", nText = "";
    private int nRgb;
    private long nAt;

    private RuletaHud() {
        for (int k = 0; k < 8; k++) {
            shown[k] = prev[k] = (k + 2) % 8;
            fade[k] = 1f;
        }
    }

    static int sectorOf(int colorId) {
        return Math.floorMod(colorId - 2, 8);
    }

    void enqueue(int color, String title, String text, boolean effects) {
        queue.add(new Req(Math.floorMod(color, 8), title == null ? "" : title, text == null ? "" : text, effects));
    }

    void reset() {
        queue.clear();
        phase = Phase.IDLE;
        cur = null;
        nActive = false;
        fx.clear();
    }

    void hideNotification() {
        if (nActive) nHidden = true;
    }

    /** Tecla N: abre el texto completo de la notificacion actual. */
    void openFullText() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (nActive && !nHidden && System.currentTimeMillis() - nAt < noteMs()) {
            mc.setScreen(new FullTextScreen(nTitle, nText, nRgb));
        }
    }

    private static long noteMs() {
        return RuletaConfig.noteSeconds * 1000L;
    }

    /** Mensaje del globo de texto: chat + notificacion, sin ruleta. */
    void showBubble(int color, String title, String text) {
        Sfx.play("bubble", 1f, 0.9f);
        postMessage(Math.floorMod(color, 8), title == null ? "" : title, text == null ? "" : text);
    }

    // ------------------------------------------------------------------ logica

    private void begin(Req r) {
        cur = r;
        target = r.color();
        winner = sectorOf(target);
        fxOn = r.effects() && RuletaConfig.effects;
        pAng = RuletaConfig.SIDE_ANGLE[RuletaConfig.pointerSide];
        design = RuletaConfig.design;
        dirSign = RuletaConfig.counterClockwise ? -1 : 1;
        for (int k = 0; k < 8; k++) {
            shown[k] = prev[k] = (k + 2) % 8;
            fade[k] = 1f;
            recolored[k] = false;
        }
        theta = 0f;
        drawAng = lastDraw = theta;
        vel = 0f;
        kick = 0f;
        lastSec = Integer.MIN_VALUE;
        phase = Phase.ENTER;
        t = 0;
        Sfx.play("ruleta", 1f, 1f); // un solo audio para toda la animacion
    }

    private void startSpin() {
        phase = Phase.SPIN;
        t = 0;
        float off = 22.5f + (rnd.nextFloat() - 0.5f) * 26f;
        float aw = 45f * winner + off;
        float diff = (pAng - aw - theta);
        float need;
        if (dirSign > 0) {
            need = (((diff % 360f) + 360f) % 360f) + 360f * (9 + rnd.nextInt(3));
        } else {
            need = -((((-diff) % 360f) + 360f) % 360f) - 360f * (9 + rnd.nextInt(3));
        }
        spinFrom = theta;
        spinTotal = need;
        lastSec = (int) Math.floor((pAng - theta) / 45f);
    }

    /** Perfil de velocidad: arranca rapido, va constante y frena justo en el golpe final del audio. */
    private static float spinProfile(float u) {
        final float a = 0.04f, b = 0.55f;
        float total = a / 2f + (b - a) + (1f - b) / 3f;
        float sv;
        if (u < a) sv = u * u / (2f * a);
        else if (u < b) sv = a / 2f + (u - a);
        else {
            float w = (u - b) / (1f - b);
            sv = a / 2f + (b - a) + (1f - b) * (1f - (float) Math.pow(1f - w, 3)) / 3f;
        }
        return sv / total;
    }

    private void land() {
        theta = spinFrom + spinTotal;
        vel = 0f;
        phase = Phase.LANDED;
        t = 0;
        if (RuletaConfig.flash) {
            flash = 1f;
            flashRgb = RuletaColor.byId(target).rgb;
        }
        if (fxOn) {
            fx.start(target);
            fx.burst(gcx, gcy, gR);
        }
        postMessage(cur.color(), cur.title(), cur.text());
    }

    private void postMessage(int colorId, String rawTitle, String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        RuletaColor c = RuletaColor.byId(colorId);
        int rgb = c.rgb;
        String title = rawTitle.toUpperCase(Locale.ROOT);

        if (RuletaConfig.chat && mc.inGameHud != null) {
            Style lineStyle = Style.EMPTY.withColor(rgb).withStrikethrough(true);
            Style textStyle = Style.EMPTY.withColor(rgb);
            Text line = Text.literal(" ".repeat(60)).setStyle(lineStyle);
            mc.inGameHud.getChatHud().addMessage(line);
            if (!title.isEmpty()) {
                mc.inGameHud.getChatHud().addMessage(Text.literal(title).setStyle(textStyle.withBold(true)));
            }
            if (!text.isEmpty()) {
                for (String s : text.split("\n")) {
                    if (!s.isBlank()) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal(s).setStyle(textStyle));
                    }
                }
            }
            mc.inGameHud.getChatHud().addMessage(line);
        }

        if (RuletaConfig.notification) {
            nTitle = title.isEmpty() ? c.label.toUpperCase(Locale.ROOT) : title;
            nText = text;
            nRgb = rgb;
            nAt = System.currentTimeMillis();
            nHidden = false;
            nActive = true;
            nLong = false;
        }
    }

    private void update(float dt) {
        clock += dt;
        flash = Math.max(0f, flash - dt * 1.6f);
        kick *= (float) Math.exp(-dt * 14f);
        for (int k = 0; k < 8; k++) {
            if (fade[k] < 1f) fade[k] = Math.min(1f, fade[k] + dt / 0.12f);
        }
        switch (phase) {
            case IDLE -> {
                Req r = queue.poll();
                if (r != null) begin(r);
            }
            case ENTER -> {
                t += dt;
                if (t >= ENTER) startSpin();
            }
            case SPIN -> {
                t += dt;
                float u = (float) Math.min(1.0, t / SPIN);
                theta = spinFrom + spinTotal * spinProfile(u);
                int sec = (int) Math.floor((pAng - theta) / 45f);
                if (sec != lastSec) {
                    lastSec = sec;
                    kick = 1f; // la flecha rebota al pasar cada gajo (el sonido ya viene en el audio)
                }
                if (u >= 1f) land();
            }
            case LANDED -> {
                t += dt;
                double abs = ENTER + SPIN + t;
                for (int j = 1; j <= 7; j++) {
                    int k = (winner + j) % 8;
                    double at = RECOLOR_START + (j - 1) * RECOLOR_STEP;
                    if (!recolored[k] && abs >= at) {
                        recolored[k] = true;
                        prev[k] = shown[k];
                        shown[k] = target;
                        fade[k] = 0f;
                    }
                }
                if (abs >= EXIT_START) {
                    phase = Phase.EXIT;
                    t = 0;
                }
            }
            case EXIT -> {
                t += dt;
                if (t >= EXIT) {
                    phase = Phase.IDLE;
                    t = 0;
                    cur = null;
                    fx.clear();
                }
            }
        }
        drawAng = theta;
        if (dt > 0f) {
            vel = vel * 0.5f + 0.5f * (drawAng - lastDraw) / dt;
        }
        lastDraw = drawAng;
    }

    // ------------------------------------------------------------------ dibujo

    void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        long now = System.nanoTime();
        float dt = lastNano == 0 ? 0f : Math.min(0.1f, (now - lastNano) / 1e9f);
        lastNano = now;
        frameDt = dt;

        gsw = mc.getWindow().getScaledWidth();
        gsh = mc.getWindow().getScaledHeight();
        float fxp = 0.5f, fyp = 0.5f;
        switch (RuletaConfig.wheelPos) {
            case 1 -> fyp = 0.32f;
            case 2 -> fyp = 0.68f;
            case 3 -> fxp = 0.28f;
            case 4 -> fxp = 0.72f;
            default -> {}
        }
        gcx = gsw * fxp;
        gcy = gsh * fyp;
        float Rv = Math.min(gsh * 0.30f, gsw * 0.22f) * RuletaConfig.wheelSize / 100f;
        if (RuletaConfig.wheelPos == 1 || RuletaConfig.wheelPos == 2) Rv = Math.min(Rv, gsh * 0.27f);
        gR = Rv;

        update(dt);

        MatrixStack ms = ctx.getMatrices();
        Gfx.begin();
        if (phase != Phase.IDLE) {
            drawWheelScene(ms, Rv);
        }
        if (flash > 0.001f) {
            Gfx.fill(ms.peek().getPositionMatrix(), 0, 0, gsw, gsh, ((int) (flash * 0.35f * 255f) << 24) | (flashRgb & 0xFFFFFF));
        }
        drawNotification(ctx, mc);
        Gfx.end();
    }

    private static float easeOutCubic(float x) {
        x = Math.max(0f, Math.min(1f, x));
        float a = 1f - x;
        return 1f - a * a * a;
    }

    private static float easeOutBack(float x) {
        x = Math.max(0f, Math.min(1f, x));
        float c1 = 1.9f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(x - 1f, 3) + c1 * (float) Math.pow(x - 1f, 2);
    }

    private final float[] prog = new float[8];

    /** Armado de la ruleta segun los cuadros de la carpeta: gajos que crecen uno a uno, destello blanco y flecha. */
    private void drawAssemble(MatrixStack ms, float Rv, float cx, float cy, float tt) {
        float R = Rv / Gfx.TEX_RATIO;
        for (int k = 0; k < 8; k++) {
            int i = Math.floorMod(k - 4, 8); // empieza por el gajo amarillo y sigue en sentido horario
            prog[k] = Math.max(0f, Math.min(1f, (tt - (0.04f + i * 0.065f)) / 0.30f));
        }
        float gw;
        if (tt < 0.68f) gw = 0f;
        else if (tt < 0.80f) gw = (tt - 0.68f) / 0.12f;
        else gw = Math.max(0f, 1f - (tt - 0.80f) / 0.15f);

        ms.push();
        ms.translate(cx, cy, 0f);
        Gfx.wheelAssemble(ms, R, drawAng, design, 1f, prog, gw);

        // esfera dorada: empieza enorme y se encoge hasta el tamano del centro
        float orbScale = 1f + 2.0f * (1f - easeOutCubic(tt / 0.55f));
        float orbA = tt < 0.62f ? 1f : Math.max(0f, 1f - (tt - 0.62f) / 0.20f);
        if (orbA > 0.01f && tt > 0.005f) {
            float h = Rv * 0.105f * orbScale;
            Gfx.quad(ms.peek().getPositionMatrix(), Gfx.ORB, -h, -h, h, h, 1f, 1f, 1f, orbA);
        }
        ms.pop();

        // flecha: aparece con un circulo blanco que rebota
        if (tt >= 0.78f) {
            float pp = easeOutBack((tt - 0.78f) / 0.20f);
            drawPointer(ms, Rv, cx, cy, pp, Math.max(0f, 1f - (tt - 0.78f) / 0.12f), 1f);
        }
    }

    private void drawPointer(MatrixStack ms, float Rv, float cx, float cy, float scale, float white, float alpha) {
        double a = Math.toRadians(pAng);
        float px = (float) Math.sin(a) * Rv * 0.74f;
        float py = (float) -Math.cos(a) * Rv * 0.74f;
        ms.push();
        ms.translate(cx + px, cy + py, 0f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pAng - dirSign * kick * 20f));
        ms.scale(scale, scale, 1f);
        float ph = Rv * 0.46f, pw = ph * 0.8f;
        Matrix4f pm = ms.peek().getPositionMatrix();
        Identifier tex = Gfx.DPOINTER[design];
        Gfx.quad(pm, tex, -pw / 2f + 2f, -ph + 3f, pw / 2f + 2f, 3f, 0f, 0f, 0f, 0.30f * alpha);
        Gfx.quad(pm, tex, -pw / 2f, -ph, pw / 2f, 0f, 1f, 1f, 1f, alpha);
        if (white > 0.01f) {
            Gfx.additive(true);
            Gfx.quad(pm, tex, -pw / 2f, -ph, pw / 2f, 0f, 1f, 1f, 1f, Math.min(1f, white));
            Gfx.quad(pm, tex, -pw / 2f, -ph, pw / 2f, 0f, 1f, 1f, 1f, Math.min(1f, white));
            Gfx.additive(false);
        }
        ms.pop();
    }

    private void drawWheelScene(MatrixStack ms, float Rv) {
        float cx = gcx, cy = gcy;
        float fxK = 1f;
        if (phase == Phase.EXIT) {
            float u = (float) (t / EXIT);
            fxK = Math.max(0f, 1f - u * 1.6f);
        }
        float fxI = fxK * RuletaConfig.fxIntensity / 100f;
        if (fxOn) fx.update(frameDt, gcx, gcy, Rv, gsw, gsh, fxI);
        Matrix4f scr = ms.peek().getPositionMatrix();

        if (phase == Phase.ENTER) {
            drawAssemble(ms, Rv, cx, cy, (float) t);
            return;
        }
        if (phase == Phase.EXIT && t / EXIT > 0.35) {
            // desarmado: la animacion de entrada al reves
            float tt = (float) (ENTER * (1.0 - (t / EXIT - 0.35) / 0.65));
            if (fxOn) {
                fx.drawBack(ms, cx, cy, Rv, fxI);
                fx.drawFront(ms, gsw, gsh, fxI);
            }
            drawAssemble(ms, Rv, cx, cy, Math.max(0f, tt));
            return;
        }

        float R = Rv / Gfx.TEX_RATIO;
        float sx = 1f, sy = 1f;
        if (phase == Phase.LANDED) {
            float pun = Math.max(0f, 1f - (float) t * 3.5f);
            sx = sy = 1f + 0.07f * pun;
        }

        // aura detras
        if (fxOn && !RuletaConfig.lite && (phase == Phase.LANDED || phase == Phase.EXIT)) {
            RuletaColor c = RuletaColor.byId(target);
            float r = ((c.rgb >> 16) & 255) / 255f, g = ((c.rgb >> 8) & 255) / 255f, b = (c.rgb & 255) / 255f;
            float pulse = 0.55f + 0.2f * (float) Math.sin(clock * 3f);
            Gfx.additive(true);
            Gfx.quad(scr, Gfx.GLOW, cx - Rv * 2.1f, cy - Rv * 2.1f, cx + Rv * 2.1f, cy + Rv * 2.1f, r, g, b, Math.min(1f, pulse * fxI));
            Gfx.additive(false);
        }
        if (fxOn) fx.drawBack(ms, cx, cy, Rv, fxI);

        // ruleta (con motion blur al girar)
        ms.push();
        ms.translate(cx, cy, 0f);
        ms.scale(sx, sy, 1f);
        float spd = Math.abs(vel);
        float spread = Math.min(40f, spd * 0.02f);
        int copies = 1;
        if (RuletaConfig.blur && !RuletaConfig.lite && phase == Phase.SPIN) {
            copies = 1 + Math.min(4, (int) (spd / 130f));
        }
        float dir = vel >= 0f ? 1f : -1f;
        for (int c = 0; c < copies; c++) {
            float ang = drawAng - dir * spread * c / copies;
            Gfx.wheel(ms, R, ang, design, prev, shown, fade, 1f / (c + 1), 0f, 0f, 0f);
        }
        ms.pop();

        ms.push();
        ms.translate(cx, cy, 0f);
        ms.scale(sx, sy, 1f);
        ms.translate(-cx, -cy, 0f);
        drawPointer(ms, Rv, cx, cy, 1f, 0f, 1f);
        ms.pop();

        if (fxOn) fx.drawFront(ms, gsw, gsh, fxI);
    }

    private void drawNotification(DrawContext ctx, MinecraftClient mc) {
        if (!nActive) return;
        long age = System.currentTimeMillis() - nAt;
        if (age >= noteMs()) {
            nActive = false;
            return;
        }
        if (nHidden) return;

        TextRenderer tr = mc.textRenderer;
        int w = 236;
        List<OrderedText> lines = tr.wrapLines(Text.literal(nText), w - 16);
        nLong = lines.size() > NOTE_MAX_LINES;
        if (nLong) lines = lines.subList(0, NOTE_MAX_LINES);

        int titleH = 24;
        int shownLines = Math.max(1, lines.size()) + (nLong ? 1 : 0);
        int bodyH = shownLines * 10 + 8;
        int footH = nLong ? 26 : 15;
        int total = titleH + bodyH + footH;
        float sc = RuletaConfig.noteScale / 100f;

        boolean right = RuletaConfig.noteCorner == 1 || RuletaConfig.noteCorner == 3;
        boolean bottom = RuletaConfig.noteCorner >= 2;
        float slide = (float) Math.min(1.0, age / 350.0);
        slide = 1f - (float) Math.pow(1f - slide, 3);
        float off = (w * sc + 8f) * (1f - slide);
        float tx = right ? gsw - 4f - w * sc + off : 4f - off;
        float ty = bottom ? gsh - 4f - total * sc : 4f;

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(tx, ty, 0f);
        ms.scale(sc, sc, 1f);

        int col = 0xFF000000 | nRgb;
        float x = 0f, y = 0f;
        Matrix4f m = ms.peek().getPositionMatrix();
        Gfx.fill(m, x, y, x + w, y + titleH, 0xE6000000);
        Gfx.fill(m, x, y + titleH, x + w, y + total, 0xD90F1420);
        Gfx.fill(m, x, y, x + 3, y + total, col);
        Gfx.quad(m, Gfx.ICON, x + w - 21, y + 4, x + w - 5, y + 20, 1f, 1f, 1f, 1f);

        Text title = Text.literal(nTitle).formatted(Formatting.BOLD);
        float s = Math.min(1.35f, (w - 40f) / Math.max(1, tr.getWidth(title)));
        ms.push();
        ms.translate(x + 9f, y + 12f - 4.5f * s, 0f);
        ms.scale(s, s, 1f);
        ctx.drawText(tr, title, 0, 0, col, false);
        ms.pop();

        for (int i = 0; i < lines.size(); i++) {
            ctx.drawText(tr, lines.get(i), 9, titleH + 5 + i * 10, col, true);
        }
        int fy = titleH + bodyH + 3;
        if (nLong) {
            ctx.drawText(tr, Text.literal("..."), 9, titleH + 5 + lines.size() * 10, col, true);
            MutableText more = Text.literal("Presiona ");
            if (RuletaClient.readKey != null) more.append(RuletaClient.readKey.getBoundKeyLocalizedText());
            more.append(" para leer mas");
            ctx.drawText(tr, more, 9, fy, 0xFFFFE066, false);
            fy += 11;
        }
        MutableText foot = Text.literal("Presiona ");
        if (RuletaClient.hideKey != null) foot.append(RuletaClient.hideKey.getBoundKeyLocalizedText());
        foot.append(" para ocultar este mensaje");
        ctx.drawText(tr, foot, 9, fy, 0xFFFFFFFF, false);
        ms.pop();
    }
}
