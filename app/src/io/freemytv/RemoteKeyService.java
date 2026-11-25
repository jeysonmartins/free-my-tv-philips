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

public class RemoteKeyService extends AccessibilityService {
    private static final String TAG = "FreeMyTV-Keys";
    public static final String ACTION_SET_FILTER = "io.freemytv.SET_FILTER";
    public static final String EXTRA_FILTER_ON = "filter_on";

    private BroadcastReceiver filterReceiver;

    public static boolean isFilterOn(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("key_filter", true);
    }

    public static void setFilter(Context context, boolean on) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("key_filter", on).apply();
        Intent intent = new Intent(ACTION_SET_FILTER);
        intent.putExtra(EXTRA_FILTER_ON, on);
        context.sendBroadcast(intent);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            if (isFilterOn(this)) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            } else {
                info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            }
            setServiceInfo(info);
        }

        filterReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!ACTION_SET_FILTER.equals(intent.getAction())) return;
                boolean on = intent.getBooleanExtra(EXTRA_FILTER_ON, true);
                AccessibilityServiceInfo info = getServiceInfo();
                if (info == null) return;
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
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
}
