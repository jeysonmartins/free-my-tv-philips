package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class RemoteKeyService extends AccessibilityService {
    private static final String TAG = "FreeMyTV-Keys";
    private static final long REPEAT_INTERVAL_MS = 150;
    private static final long SAFETY_TIMEOUT_MS = 3000;

    private Handler handler;
    private Runnable volumeRepeat;
    private int activeVolumeKey = 0;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        handler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "onServiceConnected");
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();
        int repeat = event.getRepeatCount();
        boolean isVolume = (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN);

        Log.d(TAG, "onKeyEvent code=" + code + " action=" + action + " repeat=" + repeat + " isVolume=" + isVolume);

        if (isVolume) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (repeat == 0) {
                    Log.d(TAG, "volume DOWN first -> startVolumeRepeat");
                    startVolumeRepeat(code);
                } else {
                    Log.d(TAG, "volume DOWN repeat#" + repeat + " (our own handler drives the repeat)");
                }
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                Log.d(TAG, "volume UP -> stopVolumeRepeat");
                stopVolumeRepeat();
                return true;
            }
            return true;
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
                    Log.d(TAG, "safety timeout -> stop");
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
        if (am == null) { Log.w(TAG, "AudioManager null"); return; }
        if (activeVolumeKey == 0) return;
        int direction = (activeVolumeKey == KeyEvent.KEYCODE_VOLUME_UP) ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;

        int musicBefore = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
        int musicAfter = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.d(TAG, "adjustStreamVolume MUSIC " + musicBefore + " -> " + musicAfter);

        if (musicBefore == musicAfter) {
            Log.d(TAG, "MUSIC unchanged, trying adjustSuggestedStreamVolume");
            am.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0);
            int musicAfter2 = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            Log.d(TAG, "after suggested, MUSIC = " + musicAfter2);
        }
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
