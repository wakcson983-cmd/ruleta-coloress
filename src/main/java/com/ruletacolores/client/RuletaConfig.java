package com.ruletacolores.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Configuracion local del jugador (se guarda en config/ruleta_colores.properties). */
final class RuletaConfig {
    static boolean effects = true;       // efectos de pantalla por color
    static boolean blur = true;          // motion blur al girar
    static boolean flash = true;         // destello al caer el color
    static boolean chat = true;          // mensaje en el chat
    static boolean notification = true;  // cuadro de notificacion
    static boolean counterClockwise = false; // sentido de giro
    static boolean lite = false;         // modo ligero (menos carga para el juego)
    static int design = 0;               // 0 = original, 1..5 = disenos nuevos
    static int pointerSide = 0;          // 0 izquierda, 1 arriba, 2 derecha, 3 abajo
    static int wheelPos = 0;             // 0 centro, 1 arriba, 2 abajo, 3 izquierda, 4 derecha
    static int noteCorner = 0;           // 0 arriba-izq, 1 arriba-der, 2 abajo-izq, 3 abajo-der
    static int noteScale = 100;          // % tamano de la notificacion
    static int fxIntensity = 100;        // % intensidad de efectos
    static int volume = 100;             // 0-100
    static int noteSeconds = 120;        // duracion de la notificacion
    static int wheelSize = 100;          // tamano de la ruleta en %

    static final int[] VOLUMES = {0, 25, 50, 75, 100};
    static final int[] NOTE_SECONDS = {15, 30, 60, 120};
    static final int[] SIZES = {70, 85, 100, 120};
    static final int[] NOTE_SCALES = {80, 100, 120};
    static final int[] INTENSITY = {50, 100, 150};
    static final float[] SIDE_ANGLE = {270f, 0f, 90f, 180f};
    static final String[] SIDE_NAMES = {"IZQUIERDA", "ARRIBA", "DERECHA", "ABAJO"};
    static final String[] POS_NAMES = {"CENTRO", "ARRIBA", "ABAJO", "IZQUIERDA", "DERECHA"};
    static final String[] CORNER_NAMES = {"ARRIBA-IZQ", "ARRIBA-DER", "ABAJO-IZQ", "ABAJO-DER"};

    private RuletaConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("ruleta_colores.properties");
    }

    static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        effects = bool(p, "effects", effects);
        blur = bool(p, "blur", blur);
        flash = bool(p, "flash", flash);
        chat = bool(p, "chat", chat);
        notification = bool(p, "notification", notification);
        counterClockwise = bool(p, "counterClockwise", counterClockwise);
        lite = bool(p, "lite", lite);
        design = Math.floorMod(num(p, "design", design), Gfx.DESIGNS);
        pointerSide = Math.floorMod(num(p, "pointerSide", pointerSide), 4);
        wheelPos = Math.floorMod(num(p, "wheelPos", wheelPos), 5);
        noteCorner = Math.floorMod(num(p, "noteCorner", noteCorner), 4);
        noteScale = num(p, "noteScale", noteScale);
        fxIntensity = num(p, "fxIntensity", fxIntensity);
        volume = num(p, "volume", volume);
        noteSeconds = num(p, "noteSeconds", noteSeconds);
        wheelSize = num(p, "wheelSize", wheelSize);
    }

    static void save() {
        Properties p = new Properties();
        p.setProperty("effects", String.valueOf(effects));
        p.setProperty("blur", String.valueOf(blur));
        p.setProperty("flash", String.valueOf(flash));
        p.setProperty("chat", String.valueOf(chat));
        p.setProperty("notification", String.valueOf(notification));
        p.setProperty("counterClockwise", String.valueOf(counterClockwise));
        p.setProperty("lite", String.valueOf(lite));
        p.setProperty("design", String.valueOf(design));
        p.setProperty("pointerSide", String.valueOf(pointerSide));
        p.setProperty("wheelPos", String.valueOf(wheelPos));
        p.setProperty("noteCorner", String.valueOf(noteCorner));
        p.setProperty("noteScale", String.valueOf(noteScale));
        p.setProperty("fxIntensity", String.valueOf(fxIntensity));
        p.setProperty("volume", String.valueOf(volume));
        p.setProperty("noteSeconds", String.valueOf(noteSeconds));
        p.setProperty("wheelSize", String.valueOf(wheelSize));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Ruleta de Colores");
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v.trim());
    }

    private static int num(Properties p, String k, int d) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(d)).trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    static int nextInt(int[] arr, int cur) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == cur) return arr[(i + 1) % arr.length];
        }
        return arr[0];
    }
}
