package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote Keys accessibility service. Also supports switching the key filter on/off at runtime via broadcast, so the
 * user can trade between fast volume (filter off) and remapped app buttons (filter on).
 *
 * Toggle with:
 *   adb shell am broadcast -a io.freemytv.SET_FILTER --ez filter_on false   # volume rápido
 *   adb shell am broadcast -a io.freemytv.SET_FILTER --ez filter_on true    # botões remapeados
 */
public class RemoteKeyService extends AccessibilityService {
    private static final String TAG = "FreeMyTV-Keys";
    public static final String ACTION_SET_FILTER = "io.freemytv.SET_FILTER";
    public static final String EXTRA_FILTER_ON = "filter_on";

    private BroadcastReceiver filterReceiver;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");

        filterReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!ACTION_SET_FILTER.equals(intent.getAction())) return;
                boolean on = intent.getBooleanExtra(EXTRA_FILTER_ON, true);
                AccessibilityServiceInfo info = getServiceInfo();
                if (info == null) {
                    Log.w(TAG, "setServiceInfo: info null (service not connected yet?)");
                    return;
                }
                if (on) {
                    info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                } else {
                    info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                }
                setServiceInfo(info);
                Log.d(TAG, "filter set to " + on + ", flags now=" + info.flags);
            }
        };
        registerReceiver(filterReceiver, new IntentFilter(ACTION_SET_FILTER));
    }

    @Override public boolean onUnbind(Intent intent) {
        if (filterReceiver != null) {
            try { unregisterReceiver(filterReceiver); } catch (Exception ignored) {}
            filterReceiver = null;
        }
        return super.onUnbind(intent);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        Log.d(TAG, "onKeyEvent code=" + event.getKeyCode() + " action=" + event.getAction() + " repeat=" + event.getRepeatCount());
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
