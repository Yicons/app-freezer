// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (c) 2026 Yicons

package org.lb2002.appcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import java.util.Map;

public final class RestoreStatesReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        new Thread(() -> {
            try {
                if (context.checkSelfPermission(
                        "android.permission.CHANGE_COMPONENT_ENABLED_STATE")
                        != PackageManager.PERMISSION_GRANTED) return;
                for (Map.Entry<String, ?> state : context
                        .getSharedPreferences("application_states", Context.MODE_PRIVATE)
                        .getAll().entrySet()) {
                    if (!(state.getValue() instanceof Boolean)) continue;
                    try {
                        context.getPackageManager().setApplicationEnabledSetting(state.getKey(),
                            (Boolean) state.getValue()
                                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0);
                    } catch (RuntimeException e) {
                        Log.w("LB2002AppControl", "Cannot restore " + state.getKey(), e);
                    }
                }
            } finally {
                try { LauncherIcon.refresh(context); }
                catch (RuntimeException e) { Log.w("LB2002AppControl", "Icon refresh failed", e); }
                result.finish();
            }
        }, "RestoreApplicationStates").start();
    }
}
