package io.freemytv;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Holding the volume keys: the key filter swallows the repeats, so after a short hold we send them ourselves
 * through the TV's own ADB (keys sent that way skip the filter) until the key is let go.
 */
final class VolumeRepeat {
    private VolumeRepeat() {}

    private static final long FIRST_MS = 450, EVERY_MS = 120, MAX_MS = 20_000;
    private static volatile int held;
    private static volatile long token;

    /** Never consumes the key: the first press goes through as usual. */
    static void onKey(Context c, KeyEvent e) {
        final int code = e.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() != 0 || held == code) return;
            held = code;
            final long mine = ++token;
            final Context app = c.getApplicationContext();
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
            long end = SystemClock.uptimeMillis() + MAX_MS;
            try (Adb adb = Adb.connect(app, 5_000, null)) {
                while (token == mine && SystemClock.uptimeMillis() < end) {
                    long start = SystemClock.uptimeMillis();
                    adb.shell("input keyevent " + code);
                    long wait = EVERY_MS - (SystemClock.uptimeMillis() - start);
                    if (wait > 0) Thread.sleep(wait);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (token == mine) held = 0;
        }
    }
}
