package io.freemytv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

/** TEMPORARY test version: shows small messages on screen about what it is doing. */
final class VolumeRepeat {
    private VolumeRepeat() {}

    private static final long FIRST_MS = 450, EVERY_MS = 120, MAX_MS = 20_000;
    private static volatile int held;
    private static volatile long token;

    private static void say(Context app, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show());
    }

    static void onKey(Context c, KeyEvent e) {
        final int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() != 0 || held == code) return;
            held = code;
            final long mine = ++token;
            final Context app = c.getApplicationContext();
            say(app, "V1 tecla recebida");
            Thread t = new Thread(() -> loop(app, code, mine));
            t.setDaemon(true);
            t.start();
        } else if (e.getAction() == KeyEvent.ACTION_UP && code == held) {
            held = 0;
            token++;
        }
    }

    private static void loop(Context app, int code, long mine) {
        try {
            Thread.sleep(FIRST_MS);
            if (token != mine) return;
            say(app, "V2 segurando");
            long end = SystemClock.uptimeMillis() + MAX_MS;
            try (Adb adb = Adb.connect(app, 5_000, null)) {
                say(app, "V3 ADB ok");
                while (token == mine && SystemClock.uptimeMillis() < end) {
                    long start = SystemClock.uptimeMillis();
                    adb.shell("input keyevent " + code);
                    long wait = EVERY_MS - (SystemClock.uptimeMillis() - start);
                    if (wait > 0) Thread.sleep(wait);
                }
            }
        } catch (Throwable err) {
            say(app, "V erro: " + err);
        } finally {
            if (token == mine) held = 0;
        }
    }
}
