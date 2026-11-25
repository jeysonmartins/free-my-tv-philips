package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 *
 * Volume keys are handled here: once an accessibility service filters keys, this TV's firmware stops repeating the
 * volume on its own. This service repeats it, so holding the button keeps going. A safety timeout stops the repeat
 * if the "release" key never arrives.
 */
public class RemoteKeyService extends AccessibilityService {
    private static final long REPEAT_INTERVAL_MS = 150;
    private static final long SAFETY_TIMEOUT_MS = 5000;

    private Handler handler;
    private Runnable volumeRepeat;
    private int activeVolumeKey = 0;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    startVolumeRepeat(code);
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                stopVolumeRepeat();
                return true;
            }
        }
        return RemoteKeys.onKey(this, event);
    }

    private void startVolumeRepeat(int keyCode) {
        stopVolumeRepeat();
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        activeVolumeKey = keyCode;
        adjustVolumeOnce();
        final long startTime = System.currentTimeMillis();
        volumeRepeat = new Runnable() {
            @Override public void run() {
                if (System.currentTimeMillis() - startTime > SAFETY_TIMEOUT_MS) {
                    stopVolumeRepeat();
                    return;
                }
                adjustVolumeOnce();
                if (volumeRepeat != null) {
                    handler.postDelayed(this, REPEAT_INTERVAL_MS);
                }
            }
        };
        handler.postDelayed(volumeRepeat, REPEAT_INTERVAL_MS);
    }

    private void adjustVolumeOnce() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null || activeVolumeKey == 0) return;
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            activeVolumeKey == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI);
    }

    private void stopVolumeRepeat() {
        if (volumeRepeat != null) {
            if (handler != null) handler.removeCallbacks(volumeRepeat);
            volumeRepeat = null;
        }
        activeVolumeKey = 0;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}
}
